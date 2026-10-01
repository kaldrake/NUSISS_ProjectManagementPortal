# Deployment: 1 EC2 + 1 RDS + 1 ECR (ap-southeast-2)

```
push → GitHub Actions: build 4 images → ECR (nusiss_projectmanagementportal)
                     → SSH to EC2 → deploy.sh <sha> → docker compose pull && up -d
```

The EC2 runs the containers from `docker-compose.prod.yml`: frontend/Nginx (public :80), login/project/scan
services (internal), SonarQube (:9001, your IP only) + its PostgreSQL. `login_db`, `project_db` and `scan_db`
live on one **Amazon RDS MySQL 8.4** instance that only the EC2's security group can reach (TLS required).

## One-time setup

1. **Provision AWS** — open AWS CloudShell in **ap-southeast-2**, upload `aws/provision.sh`,
   `aws/ec2-user-data.sh` and `aws/provision-rds.sh`, then run:
   ```bash
   MY_IP=<your-public-ip>/32 bash provision.sh
   bash provision-rds.sh
   ```
   Download `~/project-portal-key.pem` from CloudShell and note the RDS endpoint and
   `~/project-portal-rds-master.txt`. Defaults: EC2 `t3.large` (`INSTANCE_TYPE=t3.xlarge` for more headroom),
   RDS `db.t4g.micro` with 20 GB gp3 (`DB_CLASS=` / `STORAGE_GB=` to change).

2. **Create `/opt/portal/.env` on the EC2** (wait ~3 min after launch for user-data to finish):
   ```bash
   ssh -i project-portal-key.pem ec2-user@<public-ip>
   nano /opt/portal/.env      # contents from .env.example, with real values
   chmod 600 /opt/portal/.env
   ```

3. **GitHub secrets** (repo → Settings → Secrets → Actions):

   | Secret | Value |
   |---|---|
   | `EC2_HOST` | Elastic IP printed by `provision.sh` |
   | `EC2_SSH_KEY` | full contents of `project-portal-key.pem` |
   | `AWS_ACCESS_KEY_ID_JWT_BRANCH` / `AWS_SECRET_ACCESS_KEY_JWT_BRANCH` | existing IAM user; needs ECR push |

4. **Push** to `add-JWT-config` → the pipeline builds, pushes and deploys (it also copies `init-rds.sh`).

5. **Create the databases on RDS** (once, on the EC2):
   ```bash
   cd /opt/portal && ./init-rds.sh
   ```
   Hibernate (`ddl-auto=update`) creates the tables when the services start.

6. **SonarQube token** — open `http://<ip>:9001` (admin/admin, change password), create a **User Token**
   under My Account → Security, put it in `SONAR_TOKEN` in `.env`, then on the EC2:
   ```bash
   cd /opt/portal && docker compose -f docker-compose.prod.yml up -d scan-service
   ```

## Day-to-day

| Task | Command (on the EC2, in `/opt/portal`) |
|---|---|
| Status | `docker compose -f docker-compose.prod.yml ps` |
| Logs | `docker compose -f docker-compose.prod.yml logs -f scan-service` |
| Roll back | `./deploy.sh <older-commit-sha>` |
| MySQL shell (RDS) | `docker run --rm -it mysql:8.4 mysql -h <DB_HOST> -u admin -p --ssl-mode=REQUIRED` |

RDS keeps 7 days of automated backups (point-in-time restore) and has deletion protection on.
