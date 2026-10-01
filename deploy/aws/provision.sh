#!/bin/bash
# Creates the 1 ECR repo + 1 EC2 Docker host for the portal in ap-southeast-2.
# Run from AWS CloudShell (ap-southeast-2) after uploading this file and ec2-user-data.sh:
#   MY_IP=<your-public-ip>/32 bash provision.sh
# Safe to re-run: existing resources are reused.
set -euo pipefail

REGION=ap-southeast-2
NAME=project-portal
ECR_REPOSITORY=nusiss_projectmanagementportal
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.large}"
VOLUME_GB="${VOLUME_GB:-40}"
KEY_NAME="${NAME}-key"
ROLE_NAME="${NAME}-ec2-role"
SG_NAME="${NAME}-sg"
USER_DATA="$(dirname "$0")/ec2-user-data.sh"

: "${MY_IP:?Set MY_IP to your own public IP in CIDR form, e.g. MY_IP=1.2.3.4/32}"
export AWS_DEFAULT_REGION="$REGION"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

# ---------- 1. ECR repository ----------
if ! aws ecr describe-repositories --repository-names "$ECR_REPOSITORY" >/dev/null 2>&1; then
  echo "Creating ECR repository $ECR_REPOSITORY"
  aws ecr create-repository --repository-name "$ECR_REPOSITORY" \
    --image-scanning-configuration scanOnPush=true >/dev/null
  # 4 images per commit -> keep the last 10 commits' worth
  aws ecr put-lifecycle-policy --repository-name "$ECR_REPOSITORY" --lifecycle-policy-text '{
    "rules": [{
      "rulePriority": 1,
      "description": "Keep last 40 images",
      "selection": {"tagStatus": "any", "countType": "imageCountMoreThan", "countNumber": 40},
      "action": {"type": "expire"}
    }]
  }' >/dev/null
else
  echo "ECR repository $ECR_REPOSITORY already exists"
fi

# ---------- 2. IAM role for the EC2 (pull from ECR, Session Manager access) ----------
if ! aws iam get-role --role-name "$ROLE_NAME" >/dev/null 2>&1; then
  echo "Creating IAM role $ROLE_NAME"
  aws iam create-role --role-name "$ROLE_NAME" --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{"Effect": "Allow", "Principal": {"Service": "ec2.amazonaws.com"}, "Action": "sts:AssumeRole"}]
  }' >/dev/null
  aws iam attach-role-policy --role-name "$ROLE_NAME" \
    --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
  aws iam attach-role-policy --role-name "$ROLE_NAME" \
    --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
fi
if ! aws iam get-instance-profile --instance-profile-name "$ROLE_NAME" >/dev/null 2>&1; then
  aws iam create-instance-profile --instance-profile-name "$ROLE_NAME" >/dev/null
  aws iam add-role-to-instance-profile --instance-profile-name "$ROLE_NAME" --role-name "$ROLE_NAME"
  echo "Waiting for instance profile to propagate..."
  sleep 15
fi

# ---------- 3. Security group (default VPC) ----------
VPC_ID="$(aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)"
SG_ID="$(aws ec2 describe-security-groups --filters Name=group-name,Values="$SG_NAME" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text)"
if [ "$SG_ID" = "None" ]; then
  echo "Creating security group $SG_NAME"
  SG_ID="$(aws ec2 create-security-group --group-name "$SG_NAME" --vpc-id "$VPC_ID" \
    --description "Project portal EC2 Docker host" --query GroupId --output text)"
  # App (frontend + /api proxy) is public
  aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 80 --cidr 0.0.0.0/0 >/dev/null
  # SSH: GitHub Actions runners have no fixed IPs, so key-only SSH is open to all
  aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 22 --cidr 0.0.0.0/0 >/dev/null
  # SonarQube UI: only you
  aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 9001 --cidr "$MY_IP" >/dev/null
fi

# ---------- 4. Key pair ----------
if ! aws ec2 describe-key-pairs --key-names "$KEY_NAME" >/dev/null 2>&1; then
  echo "Creating key pair $KEY_NAME -> ~/${KEY_NAME}.pem (download it from CloudShell!)"
  aws ec2 create-key-pair --key-name "$KEY_NAME" --key-type ed25519 \
    --query KeyMaterial --output text > ~/"${KEY_NAME}.pem"
  chmod 400 ~/"${KEY_NAME}.pem"
fi

# ---------- 5. EC2 instance ----------
INSTANCE_ID="$(aws ec2 describe-instances \
  --filters Name=tag:Name,Values="$NAME" Name=instance-state-name,Values=pending,running,stopped \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)"
if [ "$INSTANCE_ID" = "None" ]; then
  AMI_ID="$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
    --query Parameter.Value --output text)"
  echo "Launching $INSTANCE_TYPE from $AMI_ID"
  INSTANCE_ID="$(aws ec2 run-instances \
    --image-id "$AMI_ID" \
    --instance-type "$INSTANCE_TYPE" \
    --key-name "$KEY_NAME" \
    --security-group-ids "$SG_ID" \
    --iam-instance-profile Name="$ROLE_NAME" \
    --user-data "file://${USER_DATA}" \
    --metadata-options HttpTokens=required,HttpPutResponseHopLimit=2 \
    --block-device-mappings "DeviceName=/dev/xvda,Ebs={VolumeSize=${VOLUME_GB},VolumeType=gp3,DeleteOnTermination=true}" \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${NAME}}]" \
    --query 'Instances[0].InstanceId' --output text)"
fi
echo "Waiting for $INSTANCE_ID to be running..."
aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"

# ---------- 6. Elastic IP ----------
EIP_ALLOC="$(aws ec2 describe-addresses --filters Name=tag:Name,Values="$NAME" \
  --query 'Addresses[0].AllocationId' --output text)"
if [ "$EIP_ALLOC" = "None" ]; then
  EIP_ALLOC="$(aws ec2 allocate-address --domain vpc \
    --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Name,Value=${NAME}}]" \
    --query AllocationId --output text)"
fi
aws ec2 associate-address --instance-id "$INSTANCE_ID" --allocation-id "$EIP_ALLOC" >/dev/null
PUBLIC_IP="$(aws ec2 describe-addresses --allocation-ids "$EIP_ALLOC" --query 'Addresses[0].PublicIp' --output text)"

cat <<EOF

================ DONE ================
EC2 instance : $INSTANCE_ID ($INSTANCE_TYPE)
Public IP    : $PUBLIC_IP
ECR registry : ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com
ECR repo     : $ECR_REPOSITORY
SSH key      : ~/${KEY_NAME}.pem   (download it: Actions > Download file)

GitHub secrets to set:
  EC2_HOST     = $PUBLIC_IP
  EC2_SSH_KEY  = contents of ${KEY_NAME}.pem
App URL        : http://$PUBLIC_IP
SonarQube URL  : http://$PUBLIC_IP:9001
======================================
EOF
