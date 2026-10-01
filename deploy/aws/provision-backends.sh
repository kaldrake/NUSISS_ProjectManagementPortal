#!/bin/bash
# Creates 3 backend EC2s (login / project / scan) next to the existing web EC2,
# plus one shared security group for them. Run from AWS CloudShell (ap-southeast-2)
# after provision.sh and provision-rds.sh, with ec2-user-data.sh in the same folder:
#   MY_IP=<your-public-ip>/32 bash provision-backends.sh
# Safe to re-run: existing resources are reused.
set -euo pipefail

REGION=ap-southeast-2
NAME=project-portal
KEY_NAME="${NAME}-key"
ROLE_NAME="${NAME}-ec2-role"
WEB_SG_NAME="${NAME}-sg"
RDS_SG_NAME="${NAME}-rds-sg"
BACKEND_SG_NAME="${NAME}-backend-sg"
VOLUME_GB="${VOLUME_GB:-20}"
USER_DATA="$(dirname "$0")/ec2-user-data.sh"

# role:instance type
BACKENDS=("login:${LOGIN_TYPE:-t3.small}" "project:${PROJECT_TYPE:-t3.small}" "scan:${SCAN_TYPE:-t3.medium}")

: "${MY_IP:?Set MY_IP to your own public IP in CIDR form, e.g. MY_IP=1.2.3.4/32}"
export AWS_DEFAULT_REGION="$REGION"

sg_id() {
  aws ec2 describe-security-groups --filters Name=group-name,Values="$1" Name=vpc-id,Values="$VPC_ID" \
    --query 'SecurityGroups[0].GroupId' --output text
}
# Add an ingress rule; ignore "already exists" on re-runs
allow() {
  aws ec2 authorize-security-group-ingress "$@" >/dev/null 2>&1 || true
}

VPC_ID="$(aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)"
WEB_SG_ID="$(sg_id "$WEB_SG_NAME")"
RDS_SG_ID="$(sg_id "$RDS_SG_NAME")"
if [ "$WEB_SG_ID" = "None" ] || [ "$RDS_SG_ID" = "None" ]; then
  echo "ERROR: run provision.sh and provision-rds.sh first" >&2
  exit 1
fi

# ---------- 1. Backend security group ----------
BACKEND_SG_ID="$(sg_id "$BACKEND_SG_NAME")"
if [ "$BACKEND_SG_ID" = "None" ]; then
  echo "Creating security group $BACKEND_SG_NAME"
  BACKEND_SG_ID="$(aws ec2 create-security-group --group-name "$BACKEND_SG_NAME" --vpc-id "$VPC_ID" \
    --description "Project portal backend services (login/project/scan)" --query GroupId --output text)"
fi
# Service ports from the web host (Nginx) and between backends (project -> scan)
allow --group-id "$BACKEND_SG_ID" --protocol tcp --port 8081-8083 --source-group "$WEB_SG_ID"
allow --group-id "$BACKEND_SG_ID" --protocol tcp --port 8081-8083 --source-group "$BACKEND_SG_ID"
# SSH: CI jumps through the web host; you connect directly from your IP
allow --group-id "$BACKEND_SG_ID" --protocol tcp --port 22 --source-group "$WEB_SG_ID"
allow --group-id "$BACKEND_SG_ID" --protocol tcp --port 22 --cidr "$MY_IP"
# Backends -> RDS MySQL, and scan -> SonarQube on the web host
allow --group-id "$RDS_SG_ID" --protocol tcp --port 3306 --source-group "$BACKEND_SG_ID"
allow --group-id "$WEB_SG_ID" --protocol tcp --port 9001 --source-group "$BACKEND_SG_ID"

# ---------- 2. Backend instances ----------
AMI_ID="$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)"

for entry in "${BACKENDS[@]}"; do
  role="${entry%%:*}"
  type="${entry##*:}"
  tag="${NAME}-${role}"
  id="$(aws ec2 describe-instances \
    --filters Name=tag:Name,Values="$tag" Name=instance-state-name,Values=pending,running,stopped \
    --query 'Reservations[0].Instances[0].InstanceId' --output text)"
  if [ "$id" = "None" ]; then
    echo "Launching $tag ($type)"
    id="$(aws ec2 run-instances \
      --image-id "$AMI_ID" \
      --instance-type "$type" \
      --key-name "$KEY_NAME" \
      --security-group-ids "$BACKEND_SG_ID" \
      --iam-instance-profile Name="$ROLE_NAME" \
      --user-data "file://${USER_DATA}" \
      --metadata-options HttpTokens=required,HttpPutResponseHopLimit=2 \
      --block-device-mappings "DeviceName=/dev/xvda,Ebs={VolumeSize=${VOLUME_GB},VolumeType=gp3,DeleteOnTermination=true}" \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${tag}}]" \
      --query 'Instances[0].InstanceId' --output text)"
  else
    echo "$tag already exists ($id)"
  fi
done

echo "Waiting for instances to be running..."
aws ec2 wait instance-running --filters Name=tag:Name,Values="${NAME}-login","${NAME}-project","${NAME}-scan"

WEB_PRIVATE_IP="$(aws ec2 describe-instances --filters Name=tag:Name,Values="$NAME" Name=instance-state-name,Values=running \
  --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)"

echo
echo "================ DONE ================"
printf "%-10s %-20s %-16s %-16s\n" ROLE INSTANCE PRIVATE_IP PUBLIC_IP
printf "%-10s %-20s %-16s %-16s\n" web "(existing)" "$WEB_PRIVATE_IP" "-"
for role in login project scan; do
  aws ec2 describe-instances --filters Name=tag:Name,Values="${NAME}-${role}" Name=instance-state-name,Values=running \
    --query 'Reservations[0].Instances[0].[InstanceId,PrivateIpAddress,PublicIpAddress]' --output text |
    while read -r iid priv pub; do printf "%-10s %-20s %-16s %-16s\n" "$role" "$iid" "$priv" "$pub"; done
done
cat <<EOF

GitHub secrets to add (private IPs above):
  LOGIN_HOST   = <login PRIVATE_IP>
  PROJECT_HOST = <project PRIVATE_IP>
  SCAN_HOST    = <scan PRIVATE_IP>
SSH from your PC with PuTTY: ec2-user@<PUBLIC_IP>, same project-portal-key.ppk
======================================
EOF
