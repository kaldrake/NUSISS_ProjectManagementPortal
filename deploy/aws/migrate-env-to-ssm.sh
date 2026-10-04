#!/bin/bash
# One-time: copy /opt/portal/.env from the current login / project / scan EC2s into
# SSM Parameter Store as SecureStrings (/portal/<role>/env), for the Auto Scaling Groups.
# Reads the files through SSM Run Command, so secrets never leave AWS.
# Run from AWS CloudShell (ap-southeast-2):  bash migrate-env-to-ssm.sh
set -euo pipefail

REGION=ap-southeast-2
NAME=project-portal
export AWS_DEFAULT_REGION="$REGION"

for role in login project scan; do
  iid="$(aws ec2 describe-instances \
    --filters Name=tag:Name,Values="${NAME}-${role}" Name=instance-state-name,Values=running \
    --query 'Reservations[0].Instances[0].InstanceId' --output text)"
  if [ "$iid" = "None" ]; then
    echo "ERROR: no running EC2 tagged ${NAME}-${role}" >&2
    exit 1
  fi

  cmd_id="$(aws ssm send-command --instance-ids "$iid" --document-name AWS-RunShellScript \
    --parameters 'commands=["cat /opt/portal/.env"]' --query Command.CommandId --output text)"
  aws ssm wait command-executed --command-id "$cmd_id" --instance-id "$iid"
  content="$(aws ssm get-command-invocation --command-id "$cmd_id" --instance-id "$iid" \
    --query StandardOutputContent --output text)"

  if ! grep -q '^ECR_REGISTRY=' <<< "$content"; then
    echo "ERROR: .env from $role ($iid) looks wrong - not stored" >&2
    exit 1
  fi
  aws ssm put-parameter --name "/portal/${role}/env" --type SecureString --overwrite \
    --value "$content" >/dev/null
  echo "Stored /portal/${role}/env from $iid ($(grep -c '=' <<< "$content") settings)"
done
