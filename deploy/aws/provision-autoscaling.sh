#!/bin/bash
# Auto scaling for the login / project / scan services:
#   internal ALB (listeners :8081/:8082/:8083) -> one Auto Scaling Group per service
#   (min 1, max 3, target tracking on average CPU), then login/project/scan.portal.internal
#   are pointed at the ALB once every group has a healthy instance.
# Prerequisites: provision.sh, provision-rds.sh, provision-backends.sh, provision-dns.sh and
#   migrate-env-to-ssm.sh have run. Upload into the same CloudShell folder:
#   provision-autoscaling.sh, asg-user-data.sh, login.yml, project.yml, scan.yml (deploy/hosts/)
# Run from AWS CloudShell (ap-southeast-2):
#   CI_USER=<IAM user GitHub Actions uses> bash provision-autoscaling.sh
# Safe to re-run: existing resources are reused / updated.
set -euo pipefail

REGION=ap-southeast-2
NAME=project-portal
ZONE=portal.internal
ROLE_NAME="${NAME}-ec2-role"
KEY_NAME="${NAME}-key"
ALB_NAME="${NAME}-internal"
MIN_SIZE="${MIN_SIZE:-1}"
MAX_SIZE="${MAX_SIZE:-3}"
CPU_TARGET="${CPU_TARGET:-60}"
DIR="$(cd "$(dirname "$0")" && pwd)"

# role:port:instance type
SERVICES=("login:8081:${LOGIN_TYPE:-t3.small}" "project:8082:${PROJECT_TYPE:-t3.small}" "scan:8083:${SCAN_TYPE:-t3.medium}")

export AWS_DEFAULT_REGION="$REGION"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
allow() { aws ec2 authorize-security-group-ingress "$@" >/dev/null 2>&1 || true; }
sg_id() {
  aws ec2 describe-security-groups --filters Name=group-name,Values="$1" Name=vpc-id,Values="$VPC_ID" \
    --query 'SecurityGroups[0].GroupId' --output text
}

VPC_ID="$(aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)"
SUBNETS="$(aws ec2 describe-subnets --filters Name=vpc-id,Values="$VPC_ID" Name=default-for-az,Values=true \
  --query 'Subnets[].SubnetId' --output text)"
WEB_SG_ID="$(sg_id "${NAME}-sg")"
BACKEND_SG_ID="$(sg_id "${NAME}-backend-sg")"

# ---------- 0. Checks ----------
for role in login project scan; do
  [ -f "$DIR/${role}.yml" ] || { echo "ERROR: upload deploy/hosts/${role}.yml next to this script" >&2; exit 1; }
  aws ssm get-parameter --name "/portal/${role}/env" >/dev/null 2>&1 \
    || { echo "ERROR: /portal/${role}/env missing - run migrate-env-to-ssm.sh first" >&2; exit 1; }
done
[ -f "$DIR/asg-user-data.sh" ] || { echo "ERROR: upload asg-user-data.sh next to this script" >&2; exit 1; }

# ---------- 1. Config in SSM (compose files, image tag) ----------
for role in login project scan; do
  aws ssm put-parameter --name "/portal/${role}/compose" --type String --overwrite \
    --value "file://$DIR/${role}.yml" >/dev/null
done
aws ssm get-parameter --name /portal/image-tag >/dev/null 2>&1 \
  || aws ssm put-parameter --name /portal/image-tag --type String --value latest >/dev/null
echo "SSM config ready (image tag: $(aws ssm get-parameter --name /portal/image-tag --query Parameter.Value --output text))"

# ---------- 2. IAM ----------
# Instances read their config from /portal/*
aws iam put-role-policy --role-name "$ROLE_NAME" --policy-name portal-read-config --policy-document "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [{\"Effect\": \"Allow\", \"Action\": [\"ssm:GetParameter\", \"ssm:GetParameters\"],
    \"Resource\": \"arn:aws:ssm:${REGION}:${ACCOUNT_ID}:parameter/portal/*\"}]
}"
# CI publishes config and rolls the groups
if [ -n "${CI_USER:-}" ]; then
  aws iam put-user-policy --user-name "$CI_USER" --policy-name portal-autoscaling-deploy --policy-document "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [
      {\"Effect\": \"Allow\", \"Action\": [\"ssm:PutParameter\", \"ssm:GetParameter\"],
       \"Resource\": \"arn:aws:ssm:${REGION}:${ACCOUNT_ID}:parameter/portal/*\"},
      {\"Effect\": \"Allow\", \"Action\": [\"autoscaling:StartInstanceRefresh\", \"autoscaling:DescribeInstanceRefreshes\",
        \"autoscaling:DescribeAutoScalingGroups\"], \"Resource\": \"*\"}]
  }"
  echo "Granted deploy permissions to IAM user $CI_USER"
else
  echo "WARNING: CI_USER not set - GitHub Actions cannot deploy to the Auto Scaling Groups yet" >&2
fi

# ---------- 3. Security groups ----------
ALB_SG_ID="$(sg_id "${NAME}-alb-sg")"
if [ "$ALB_SG_ID" = "None" ]; then
  ALB_SG_ID="$(aws ec2 create-security-group --group-name "${NAME}-alb-sg" --vpc-id "$VPC_ID" \
    --description "Project portal internal ALB" --query GroupId --output text)"
fi
allow --group-id "$ALB_SG_ID" --protocol tcp --port 8081-8083 --source-group "$WEB_SG_ID"
allow --group-id "$ALB_SG_ID" --protocol tcp --port 8081-8083 --source-group "$BACKEND_SG_ID"
allow --group-id "$BACKEND_SG_ID" --protocol tcp --port 8081-8083 --source-group "$ALB_SG_ID"

# ---------- 4. Internal ALB ----------
ALB_ARN="$(aws elbv2 describe-load-balancers --names "$ALB_NAME" \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || echo None)"
if [ "$ALB_ARN" = "None" ]; then
  echo "Creating internal ALB $ALB_NAME"
  # shellcheck disable=SC2086
  ALB_ARN="$(aws elbv2 create-load-balancer --name "$ALB_NAME" --scheme internal --type application \
    --subnets $SUBNETS --security-groups "$ALB_SG_ID" --tags Key=Name,Value="$ALB_NAME" \
    --query 'LoadBalancers[0].LoadBalancerArn' --output text)"
fi
# Long-running scan calls; quicker draining during instance refreshes
aws elbv2 modify-load-balancer-attributes --load-balancer-arn "$ALB_ARN" \
  --attributes Key=idle_timeout.timeout_seconds,Value=300 >/dev/null
aws elbv2 wait load-balancer-available --load-balancer-arns "$ALB_ARN"
read -r ALB_DNS ALB_ZONE_ID < <(aws elbv2 describe-load-balancers --load-balancer-arns "$ALB_ARN" \
  --query 'LoadBalancers[0].[DNSName,CanonicalHostedZoneId]' --output text)

# ---------- 5. Per service: target group, listener, launch template, ASG, scaling policy ----------
AMI_ID="$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)"
SUBNET_CSV="$(tr '\t ' ',,' <<< "$SUBNETS")"

for entry in "${SERVICES[@]}"; do
  IFS=: read -r role port type <<< "$entry"
  tg_name="${NAME}-${role}"
  lt_name="${NAME}-${role}-lt"
  asg_name="${NAME}-${role}-asg"

  TG_ARN="$(aws elbv2 describe-target-groups --names "$tg_name" \
    --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || echo None)"
  if [ "$TG_ARN" = "None" ]; then
    TG_ARN="$(aws elbv2 create-target-group --name "$tg_name" --protocol HTTP --port "$port" \
      --vpc-id "$VPC_ID" --target-type instance --health-check-path /health \
      --health-check-interval-seconds 15 --healthy-threshold-count 2 --unhealthy-threshold-count 3 \
      --query 'TargetGroups[0].TargetGroupArn' --output text)"
  fi
  aws elbv2 modify-target-group-attributes --target-group-arn "$TG_ARN" \
    --attributes Key=deregistration_delay.timeout_seconds,Value=30 >/dev/null

  has_listener="$(aws elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" \
    --query "Listeners[?Port==\`$port\`].ListenerArn | [0]" --output text)"
  if [ "$has_listener" = "None" ]; then
    aws elbv2 create-listener --load-balancer-arn "$ALB_ARN" --protocol HTTP --port "$port" \
      --default-actions Type=forward,TargetGroupArn="$TG_ARN" >/dev/null
  fi

  user_data="$(sed "s/__ROLE__/${role}/" "$DIR/asg-user-data.sh" | base64 -w0)"
  lt_data="{
    \"ImageId\": \"$AMI_ID\", \"InstanceType\": \"$type\", \"KeyName\": \"$KEY_NAME\",
    \"SecurityGroupIds\": [\"$BACKEND_SG_ID\"], \"IamInstanceProfile\": {\"Name\": \"$ROLE_NAME\"},
    \"UserData\": \"$user_data\",
    \"MetadataOptions\": {\"HttpTokens\": \"required\", \"HttpPutResponseHopLimit\": 2},
    \"BlockDeviceMappings\": [{\"DeviceName\": \"/dev/xvda\", \"Ebs\": {\"VolumeSize\": 20, \"VolumeType\": \"gp3\", \"DeleteOnTermination\": true}}],
    \"TagSpecifications\": [{\"ResourceType\": \"instance\", \"Tags\": [{\"Key\": \"Name\", \"Value\": \"$asg_name\"}]}]
  }"
  if aws ec2 describe-launch-templates --launch-template-names "$lt_name" >/dev/null 2>&1; then
    ver="$(aws ec2 create-launch-template-version --launch-template-name "$lt_name" \
      --launch-template-data "$lt_data" --query LaunchTemplateVersion.VersionNumber --output text)"
    aws ec2 modify-launch-template --launch-template-name "$lt_name" --default-version "$ver" >/dev/null
  else
    aws ec2 create-launch-template --launch-template-name "$lt_name" --launch-template-data "$lt_data" >/dev/null
  fi

  if aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$asg_name" \
       --query 'AutoScalingGroups[0].AutoScalingGroupName' --output text | grep -q "$asg_name"; then
    aws autoscaling update-auto-scaling-group --auto-scaling-group-name "$asg_name" \
      --launch-template "LaunchTemplateName=$lt_name,Version=\$Default" \
      --min-size "$MIN_SIZE" --max-size "$MAX_SIZE"
  else
    echo "Creating Auto Scaling Group $asg_name ($type, $MIN_SIZE-$MAX_SIZE)"
    aws autoscaling create-auto-scaling-group --auto-scaling-group-name "$asg_name" \
      --launch-template "LaunchTemplateName=$lt_name,Version=\$Default" \
      --min-size "$MIN_SIZE" --max-size "$MAX_SIZE" --desired-capacity "$MIN_SIZE" \
      --vpc-zone-identifier "$SUBNET_CSV" --target-group-arns "$TG_ARN" \
      --health-check-type ELB --health-check-grace-period 300 \
      --tags "Key=Name,Value=$asg_name,PropagateAtLaunch=true"
  fi

  # The first group in an account creates the Auto Scaling service-linked role, which takes a moment
  for attempt in $(seq 1 10); do
    if aws autoscaling put-scaling-policy --auto-scaling-group-name "$asg_name" \
         --policy-name "${role}-cpu-target" --policy-type TargetTrackingScaling \
         --target-tracking-configuration "{\"PredefinedMetricSpecification\": {\"PredefinedMetricType\": \"ASGAverageCPUUtilization\"}, \"TargetValue\": $CPU_TARGET}" >/dev/null 2>&1; then
      break
    fi
    [ "$attempt" -eq 10 ] && { echo "ERROR: could not attach scaling policy to $asg_name" >&2; exit 1; }
    echo "  waiting for the Auto Scaling service-linked role..."; sleep 15
  done
done

# ---------- 6. Wait for a healthy instance in every group ----------
for entry in "${SERVICES[@]}"; do
  IFS=: read -r role port type <<< "$entry"
  TG_ARN="$(aws elbv2 describe-target-groups --names "${NAME}-${role}" --query 'TargetGroups[0].TargetGroupArn' --output text)"
  echo -n "Waiting for a healthy ${role} instance"
  for i in $(seq 1 60); do
    healthy="$(aws elbv2 describe-target-health --target-group-arn "$TG_ARN" \
      --query "length(TargetHealthDescriptions[?TargetHealth.State=='healthy'])" --output text)"
    [ "$healthy" -ge 1 ] && { echo " - OK"; break; }
    echo -n "."; sleep 15
    if [ "$i" -eq 60 ]; then
      echo; echo "ERROR: no healthy ${role} instance after 15 min - DNS not switched. Check the instance's" >&2
      echo "       /var/log/cloud-init-output.log; the old EC2s still serve traffic." >&2
      exit 1
    fi
  done
done

# ---------- 7. Cutover: service names -> ALB ----------
ZONE_ID="$(aws route53 list-hosted-zones-by-name --dns-name "$ZONE" \
  --query "HostedZones[?Name=='${ZONE}.' && Config.PrivateZone].Id | [0]" --output text)"
CHANGES=""
for role in login project scan; do
  CHANGES+="{\"Action\":\"UPSERT\",\"ResourceRecordSet\":{\"Name\":\"${role}.${ZONE}\",\"Type\":\"A\",\"AliasTarget\":{\"HostedZoneId\":\"${ALB_ZONE_ID}\",\"DNSName\":\"${ALB_DNS}\",\"EvaluateTargetHealth\":false}}},"
done
aws route53 change-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --change-batch "{\"Comment\":\"service names -> internal ALB\",\"Changes\":[${CHANGES%,}]}" >/dev/null

cat <<EOF

================ DONE ================
Internal ALB : $ALB_DNS
Groups       : ${NAME}-login-asg / ${NAME}-project-asg / ${NAME}-scan-asg  (min $MIN_SIZE, max $MAX_SIZE, CPU $CPU_TARGET%)
DNS          : login / project / scan.${ZONE} now point at the ALB

The old login / project / scan EC2s no longer receive traffic. After testing, stop them
(EC2 console) and later terminate them.
======================================
EOF
