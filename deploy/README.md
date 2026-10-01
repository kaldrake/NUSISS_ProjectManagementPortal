# Deployment: 4 EC2 + 1 RDS + 1 ECR (ap-southeast-2)

```
push → GitHub Actions: build 4 images → ECR (nusiss_projectmanagementportal)
                     → deploy login / project / scan EC2s (SSH via the web EC2)
                     → deploy web EC2
```

| EC2 | Runs (`hosts/<role>.yml`) | Reachable from |
|---|---|---|
| #1 web (Elastic IP) | frontend/Nginx :80, SonarQube :9001 + its PostgreSQL | Internet :80; your IP :9001/:22; backends :9001 |
| #2 login | login-service :8081 | web EC2 |
| #3 project | project-service :8082 | web EC2 |
| #4 scan | scan-service :8083 | web and project EC2s |

`login_db`, `project_db` and `scan_db` live on one **Amazon RDS MySQL 8.4** instance that only the
backend EC2s can reach (TLS required). Services find each other by **Route 53 private DNS names**
(`web` / `login` / `project` / `scan` `.portal.internal`, visible only inside the VPC), set in each EC2's
`/opt/portal/.env`. CI copies `hosts/<role>.yml` to `/opt/portal/docker-compose.prod.yml` on each EC2.

**Replacing an EC2:** after launching the new instance, re-run `bash provision-dns.sh` in CloudShell.
It points the name at the new private IP; no `.env` or GitHub secret changes are needed.

## One-time setup

1. **Provision AWS** — in AWS CloudShell (**ap-southeast-2**), upload the files in `aws/`, then run:
   ```bash
   MY_IP=<your-public-ip>/32 bash provision.sh            # web EC2, ECR, IAM role, key pair
   bash provision-rds.sh                                  # RDS MySQL
   MY_IP=<your-public-ip>/32 bash provision-backends.sh   # login / project / scan EC2s
   bash provision-dns.sh                                  # *.portal.internal private DNS names
   ```
   Defaults: web `t3.large`, login/project `t3.small`, scan `t3.medium`, RDS `db.t4g.micro`.

2. **Create `/opt/portal/.env` on every EC2** from `hosts/<role>.env.example` (wait ~3 min after launch):
   ```bash
   nano /opt/portal/.env && chmod 600 /opt/portal/.env
   ```
   `JWT_SECRET` must be identical on login, project and scan.

3. **Create the databases on RDS** (once, for a fresh setup): copy `scripts/init-rds.sh` to a backend EC2,
   add `DB_MASTER_USER` / `DB_MASTER_PASSWORD` and the three `*_DB_PASSWORD` values to its `.env`,
   then run `./init-rds.sh`.

4. **GitHub secrets** (repo → Settings → Secrets → Actions):

   | Secret | Value |
   |---|---|
   | `EC2_HOST` | web EC2 Elastic IP |
   | `EC2_SSH_KEY` | full contents of `project-portal-key.pem` (same key for all EC2s) |
   | `LOGIN_HOST` / `PROJECT_HOST` / `SCAN_HOST` | `login.portal.internal` / `project.portal.internal` / `scan.portal.internal` |
   | `AWS_ACCESS_KEY_ID_JWT_BRANCH` / `AWS_SECRET_ACCESS_KEY_JWT_BRANCH` | IAM user with ECR push |

5. **Push** to `add-JWT-config` → build, then backends, then web.

6. **SonarQube token** — `http://<web-ip>:9001` → My Account → Security → **User Token**; put it in
   `SONAR_TOKEN` on the **scan** EC2, then `docker compose -f docker-compose.prod.yml up -d` there.

## Day-to-day (on any EC2, in `/opt/portal`)

| Task | Command |
|---|---|
| Status | `docker compose -f docker-compose.prod.yml ps` |
| Logs | `docker compose -f docker-compose.prod.yml logs -f` |
| Roll back | `./deploy.sh <older-commit-sha>` (per EC2) |
| MySQL shell (RDS) | `docker run --rm -it mysql:8.4 mysql -h <DB_HOST> -u admin -p --ssl-mode=REQUIRED` (backend EC2s) |

RDS keeps 7 days of automated backups (point-in-time restore) and has deletion protection on.
