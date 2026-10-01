#!/bin/bash
# Creates 1 RDS MySQL 8.4 instance for login_db / project_db / scan_db,
# reachable only from the portal EC2's security group (created by provision.sh).
# Run from AWS CloudShell (ap-southeast-2):
#   bash provision-rds.sh
# Safe to re-run: existing resources are reused.
set -euo pipefail

REGION=ap-southeast-2
NAME=project-portal
DB_ID="${NAME}-mysql"
DB_CLASS="${DB_CLASS:-db.t4g.micro}"
STORAGE_GB="${STORAGE_GB:-20}"
MASTER_USER=admin
EC2_SG_NAME="${NAME}-sg"
DB_SG_NAME="${NAME}-rds-sg"
PASSWORD_FILE=~/"${NAME}-rds-master.txt"

export AWS_DEFAULT_REGION="$REGION"

# ---------- 1. Security group: MySQL from the EC2 only ----------
VPC_ID="$(aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)"
EC2_SG_ID="$(aws ec2 describe-security-groups --filters Name=group-name,Values="$EC2_SG_NAME" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text)"
if [ "$EC2_SG_ID" = "None" ]; then
  echo "ERROR: security group $EC2_SG_NAME not found - run provision.sh first" >&2
  exit 1
fi

DB_SG_ID="$(aws ec2 describe-security-groups --filters Name=group-name,Values="$DB_SG_NAME" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text)"
if [ "$DB_SG_ID" = "None" ]; then
  echo "Creating security group $DB_SG_NAME"
  DB_SG_ID="$(aws ec2 create-security-group --group-name "$DB_SG_NAME" --vpc-id "$VPC_ID" \
    --description "Project portal RDS - MySQL from the portal EC2 only" --query GroupId --output text)"
  aws ec2 authorize-security-group-ingress --group-id "$DB_SG_ID" \
    --protocol tcp --port 3306 --source-group "$EC2_SG_ID" >/dev/null
fi

# ---------- 2. RDS instance ----------
if ! aws rds describe-db-instances --db-instance-identifier "$DB_ID" >/dev/null 2>&1; then
  # MySQL 8.4 LTS (8.0 is past RDS standard support and incurs Extended Support charges)
  ENGINE_VERSION="$(aws rds describe-db-engine-versions --engine mysql --db-parameter-group-family mysql8.4 \
    --query 'DBEngineVersions[-1].EngineVersion' --output text)"
  MASTER_PASSWORD="$(openssl rand -hex 16)"
  (umask 077 && echo "$MASTER_PASSWORD" > "$PASSWORD_FILE")

  echo "Creating RDS instance $DB_ID (MySQL $ENGINE_VERSION, $DB_CLASS)"
  aws rds create-db-instance \
    --db-instance-identifier "$DB_ID" \
    --engine mysql \
    --engine-version "$ENGINE_VERSION" \
    --db-instance-class "$DB_CLASS" \
    --allocated-storage "$STORAGE_GB" \
    --storage-type gp3 \
    --storage-encrypted \
    --master-username "$MASTER_USER" \
    --master-user-password "$MASTER_PASSWORD" \
    --vpc-security-group-ids "$DB_SG_ID" \
    --no-publicly-accessible \
    --no-multi-az \
    --backup-retention-period 7 \
    --deletion-protection \
    --copy-tags-to-snapshot \
    --tags Key=Name,Value="$NAME" >/dev/null
else
  echo "RDS instance $DB_ID already exists"
fi

echo "Waiting for $DB_ID to become available (usually 5-15 minutes)..."
aws rds wait db-instance-available --db-instance-identifier "$DB_ID"
ENDPOINT="$(aws rds describe-db-instances --db-instance-identifier "$DB_ID" \
  --query 'DBInstances[0].Endpoint.Address' --output text)"

cat <<EOF

================ DONE ================
RDS instance    : $DB_ID ($DB_CLASS)
Endpoint        : $ENDPOINT
Master user     : $MASTER_USER
Master password : saved in $PASSWORD_FILE  (cat it once, then copy into .env)

Add to /opt/portal/.env on the EC2:
  DB_HOST=$ENDPOINT
  DB_MASTER_USER=$MASTER_USER
  DB_MASTER_PASSWORD=<contents of $PASSWORD_FILE>
======================================
EOF
