#!/bin/bash
# Creates the Route 53 private hosted zone "portal.internal" in the default VPC and points
#   web / login / project / scan .portal.internal  at each EC2's current private IP.
# Run from AWS CloudShell (ap-southeast-2) after provision.sh and provision-backends.sh:
#   bash provision-dns.sh
# Re-run it after replacing an EC2: the records are upserted with the new private IPs.
set -euo pipefail

REGION=ap-southeast-2
NAME=project-portal
ZONE=portal.internal
TTL=60

export AWS_DEFAULT_REGION="$REGION"
VPC_ID="$(aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)"

# ---------- 1. Private hosted zone ----------
ZONE_ID="$(aws route53 list-hosted-zones-by-name --dns-name "$ZONE" \
  --query "HostedZones[?Name=='${ZONE}.' && Config.PrivateZone].Id | [0]" --output text)"
if [ "$ZONE_ID" = "None" ]; then
  echo "Creating private hosted zone $ZONE in $VPC_ID"
  ZONE_ID="$(aws route53 create-hosted-zone --name "$ZONE" \
    --vpc VPCRegion="$REGION",VPCId="$VPC_ID" \
    --caller-reference "${NAME}-$(date +%s)" \
    --hosted-zone-config Comment="Project portal internal service names",PrivateZone=true \
    --query 'HostedZone.Id' --output text)"
else
  echo "Private hosted zone $ZONE already exists ($ZONE_ID)"
fi

# ---------- 2. One A record per EC2 (role -> Name tag) ----------
private_ip() {
  aws ec2 describe-instances --filters Name=tag:Name,Values="$1" Name=instance-state-name,Values=running,stopped \
    --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text
}

ROLES="web login project scan"
# With auto scaling, login/project/scan point at the internal ALB (provision-autoscaling.sh)
if aws elbv2 describe-load-balancers --names "${NAME}-internal" >/dev/null 2>&1; then
  echo "Internal ALB exists - login/project/scan are managed by provision-autoscaling.sh"
  ROLES="web"
fi

CHANGES=""
for role in $ROLES; do
  tag="$NAME"; [ "$role" != web ] && tag="${NAME}-${role}"
  ip="$(private_ip "$tag")"
  if [ "$ip" = "None" ]; then
    echo "WARNING: no EC2 tagged $tag - skipping ${role}.${ZONE}" >&2
    continue
  fi
  printf "  %-24s -> %s\n" "${role}.${ZONE}" "$ip"
  CHANGES+="{\"Action\":\"UPSERT\",\"ResourceRecordSet\":{\"Name\":\"${role}.${ZONE}\",\"Type\":\"A\",\"TTL\":${TTL},\"ResourceRecords\":[{\"Value\":\"${ip}\"}]}},"
done

aws route53 change-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --change-batch "{\"Comment\":\"portal service names\",\"Changes\":[${CHANGES%,}]}" >/dev/null

cat <<EOF

================ DONE ================
Use these names instead of private IPs:
  web EC2  .env : LOGIN_UPSTREAM=login.${ZONE}:8081
                  PROJECT_UPSTREAM=project.${ZONE}:8082
                  SCAN_UPSTREAM=scan.${ZONE}:8083
  project  .env : SCAN_SERVICE_URL=http://scan.${ZONE}:8083
  scan     .env : SONAR_HOST_URL=http://web.${ZONE}:9001
  GitHub secrets: LOGIN_HOST=login.${ZONE}  PROJECT_HOST=project.${ZONE}  SCAN_HOST=scan.${ZONE}
======================================
EOF
