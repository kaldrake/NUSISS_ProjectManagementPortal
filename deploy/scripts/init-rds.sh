#!/bin/bash
# Runs ON the EC2 from /opt/portal, once, after provision-rds.sh.
# 1. Creates login_db / project_db / scan_db and one user per service on RDS.
# 2. If the old portal-mysql container is still running, copies its data to RDS.
# Needs in .env: DB_HOST, DB_MASTER_USER, DB_MASTER_PASSWORD,
#                LOGIN_DB_PASSWORD, PROJECT_DB_PASSWORD, SCAN_DB_PASSWORD
set -euo pipefail
cd "$(dirname "$0")"

env_get() { grep -E "^$1=" .env | tail -1 | cut -d= -f2-; }
for var in DB_HOST DB_MASTER_USER DB_MASTER_PASSWORD LOGIN_DB_PASSWORD PROJECT_DB_PASSWORD SCAN_DB_PASSWORD; do
  if [ -z "$(env_get "$var")" ]; then
    echo "ERROR: $var is not set in $(pwd)/.env" >&2
    exit 1
  fi
done

# MySQL client in a container; MYSQL_PWD keeps the password off the command line
rds_mysql() {
  docker run --rm -i -e MYSQL_PWD="$(env_get DB_MASTER_PASSWORD)" mysql:8.4 \
    mysql -h "$(env_get DB_HOST)" -u "$(env_get DB_MASTER_USER)" --ssl-mode=REQUIRED "$@"
}

echo "Creating databases and users on $(env_get DB_HOST)"
rds_mysql <<SQL
CREATE DATABASE IF NOT EXISTS login_db;
CREATE DATABASE IF NOT EXISTS project_db;
CREATE DATABASE IF NOT EXISTS scan_db;

CREATE USER IF NOT EXISTS 'login_user'@'%' IDENTIFIED BY '$(env_get LOGIN_DB_PASSWORD)';
CREATE USER IF NOT EXISTS 'project_user'@'%' IDENTIFIED BY '$(env_get PROJECT_DB_PASSWORD)';
CREATE USER IF NOT EXISTS 'scan_user'@'%' IDENTIFIED BY '$(env_get SCAN_DB_PASSWORD)';

GRANT ALL PRIVILEGES ON login_db.* TO 'login_user'@'%';
GRANT ALL PRIVILEGES ON project_db.* TO 'project_user'@'%';
GRANT ALL PRIVILEGES ON scan_db.* TO 'scan_user'@'%';
FLUSH PRIVILEGES;
SQL

if docker ps --format '{{.Names}}' | grep -qx portal-mysql; then
  echo "Copying existing data from the portal-mysql container to RDS"
  docker exec portal-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
      --single-transaction --no-tablespaces --set-gtid-purged=OFF \
      --databases login_db project_db scan_db' | rds_mysql
else
  echo "No portal-mysql container running - skipping data copy (tables are created on first start)"
fi

echo "Row counts on RDS:"
rds_mysql -e "SELECT 'users' AS t, COUNT(*) FROM login_db.users
              UNION ALL SELECT 'projects', COUNT(*) FROM project_db.projects
              UNION ALL SELECT 'repositories', COUNT(*) FROM project_db.repositories;" 2>/dev/null \
  || echo "(tables not created yet - they appear after the services first start)"
