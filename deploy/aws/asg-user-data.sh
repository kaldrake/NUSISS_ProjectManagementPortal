#!/bin/bash
# Boot script for auto-scaled backend instances (login / project / scan).
# provision-autoscaling.sh replaces __ROLE__ and puts this into each launch template.
# Config comes from SSM Parameter Store, so new instances need no manual setup:
#   /portal/<role>/env      SecureString  contents of /opt/portal/.env
#   /portal/<role>/compose  String        contents of deploy/hosts/<role>.yml
#   /portal/image-tag       String        image tag to run (CI sets the commit SHA)
set -euxo pipefail

ROLE=__ROLE__
REGION=ap-southeast-2

# Docker + Compose v2 plugin (not bundled with the AL2023 docker package)
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

mkdir -p /opt/portal
cat > /opt/portal/start.sh <<EOF
#!/bin/bash
# (Re)start this instance's service from the config in SSM. Safe to run again by hand.
set -euo pipefail
cd /opt/portal
param() { aws ssm get-parameter --region $REGION --name "\$1" --with-decryption --query Parameter.Value --output text; }
umask 077
param /portal/$ROLE/env > .env
param /portal/$ROLE/compose > docker-compose.prod.yml
export IMAGE_TAG="\$(param /portal/image-tag)"
ECR_REGISTRY="\$(grep -E '^ECR_REGISTRY=' .env | cut -d= -f2-)"
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin "\$ECR_REGISTRY"
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --remove-orphans
EOF
chmod 700 /opt/portal/start.sh
chown -R ec2-user:ec2-user /opt/portal

/opt/portal/start.sh
