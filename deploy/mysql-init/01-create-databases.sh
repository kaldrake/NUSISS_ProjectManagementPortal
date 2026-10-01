#!/bin/bash
# Runs once, on the first start of an empty mysql_data volume.
# Creates one database and one least-privilege user per service.
set -euo pipefail

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS login_db;
CREATE DATABASE IF NOT EXISTS project_db;
CREATE DATABASE IF NOT EXISTS scan_db;

CREATE USER IF NOT EXISTS 'login_user'@'%' IDENTIFIED BY '${LOGIN_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'project_user'@'%' IDENTIFIED BY '${PROJECT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'scan_user'@'%' IDENTIFIED BY '${SCAN_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON login_db.* TO 'login_user'@'%';
GRANT ALL PRIVILEGES ON project_db.* TO 'project_user'@'%';
GRANT ALL PRIVILEGES ON scan_db.* TO 'scan_user'@'%';
FLUSH PRIVILEGES;
SQL
