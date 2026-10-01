#!/bin/bash
# Runs ON the EC2 from /opt/portal. Pulls the given image tag from ECR and restarts the stack.
# Usage: ./deploy.sh [image-tag]   (default: latest; CI passes the commit SHA)
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "ERROR: $(pwd)/.env not found - create it from deploy/hosts/<role>.env.example first" >&2
  exit 1
fi

env_get() { grep -E "^$1=" .env | tail -1 | cut -d= -f2-; }
AWS_REGION="$(env_get AWS_REGION)"
ECR_REGISTRY="$(env_get ECR_REGISTRY)"
export IMAGE_TAG="${1:-latest}"

echo "Logging in to ${ECR_REGISTRY}"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

echo "Deploying image tag: ${IMAGE_TAG}"
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --remove-orphans
docker image prune -f

docker compose -f docker-compose.prod.yml ps
