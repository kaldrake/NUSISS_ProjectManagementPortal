# Deployment: web EC2 + 3 Auto Scaling Groups + RDS + ECR (ap-southeast-2)

```
push → GitHub Actions: build 4 images → ECR (nusiss_projectmanagementportal)
                     → roll login / project / scan Auto Scaling Groups (instance refresh)
                     → deploy web EC2
```

| Tier | Runs | Scaling | Reachable from |
|---|---|---|---|
| web EC2 (Elastic IP) | Caddy :80/:443 (HTTPS) → frontend/Nginx, SonarQube :9001 + its PostgreSQL | single host | Internet :80/:443; your IP :9001/:22; backends :9001 |
| internal ALB | listeners :8081 → login, :8082 → project, :8083 → scan | managed by AWS | web EC2, backends |
| `project-portal-login-asg` | login-service :8081 (t3.small) | 1–3 instances, CPU 60% | internal ALB |
| `project-portal-project-asg` | project-service :8082 (t3.small) | 1–3 instances, CPU 60% | internal ALB |
| `project-portal-scan-asg` | scan-service :8083 (t3.medium) | 1–3 instances, CPU 60% | internal ALB |

`login_db`, `project_db` and `scan_db` live on one **Amazon RDS MySQL 8.4** instance that only the
backend instances can reach (TLS required).

**Service names:** `web.portal.internal` (web EC2) and `login` / `project` / `scan` `.portal.internal`
(aliases to the internal ALB) are Route 53 private DNS names, visible only inside the VPC. Nginx and the
services use these names, so instances can come and go without config changes.

**Auto scaling:** each group keeps average CPU near 60% by adding instances (up to 3) and removing them
(down to 1). A new instance boots from its launch template and reads its config from **SSM Parameter Store**:
`/portal/<role>/env` (SecureString, the `.env`), `/portal/<role>/compose` (`hosts/<role>.yml`) and
`/portal/image-tag` (the deployed commit). The ALB only sends traffic to instances whose `/health` passes.

**Deploys:** CI publishes the compose file and image tag to SSM, then starts an **instance refresh**:
new instances must be healthy before old ones are terminated, so there is no downtime.

**HTTPS:** Caddy on the web EC2 serves `https://<SITE_HOSTNAME>` with a Let's Encrypt certificate it
obtains and renews itself (`hosts/Caddyfile`). Without a domain, use the free `<elastic-ip-with-dashes>.sslip.io`
name, which resolves to the Elastic IP. HTTP and the bare IP redirect to it. The Spring services trust
`X-Forwarded-Proto` from private IPs (`SERVER_FORWARD_HEADERS_STRATEGY=native`), so HTTPS calls stay same-origin.

## One-time setup

In AWS CloudShell (**ap-southeast-2**), upload the files in `aws/` and `hosts/login.yml`, `hosts/project.yml`,
`hosts/scan.yml`, then:

1. **Base infrastructure**
   ```bash
   MY_IP=<your-public-ip>/32 bash provision.sh            # web EC2, ECR, IAM role, key pair
   bash provision-rds.sh                                  # RDS MySQL
   MY_IP=<your-public-ip>/32 bash provision-backends.sh   # backend security group (+ seed EC2s)
   bash provision-dns.sh                                  # *.portal.internal private DNS names
   ```
2. **Web EC2 `.env`** from `hosts/web.env.example` (`nano /opt/portal/.env && chmod 600 /opt/portal/.env`).
3. **Backend config in SSM** — `/portal/login/env`, `/portal/project/env`, `/portal/scan/env` as SecureStrings,
   from `hosts/<role>.env.example` (`JWT_SECRET` identical in all three). When migrating from running seed
   EC2s, `bash migrate-env-to-ssm.sh` copies their `/opt/portal/.env` files.
4. **Databases on RDS** (fresh setup only): `scripts/init-rds.sh` on a backend instance with the
   `DB_MASTER_*` and `*_DB_PASSWORD` values in its `.env`.
5. **Auto scaling** — internal ALB, launch templates, Auto Scaling Groups, scaling policies; switches the
   service names to the ALB once every group has a healthy instance:
   ```bash
   CI_USER=<IAM user used by GitHub Actions> bash provision-autoscaling.sh
   ```
   Then stop (and later terminate) the seed login / project / scan EC2s.
6. **GitHub secrets**: `EC2_HOST` (web Elastic IP), `EC2_SSH_KEY` (`project-portal-key.pem`),
   `AWS_ACCESS_KEY_ID_JWT_BRANCH` / `AWS_SECRET_ACCESS_KEY_JWT_BRANCH` (IAM user with ECR push; step 5
   adds the SSM and instance-refresh permissions).
7. **Push** to `main` (or merge a pull request into it) → tests, SonarCloud, build, then the 3 groups roll, then web.
8. **SonarQube token** — `http://<web-ip>:9001` → My Account → Security → **User Token**; set `SONAR_TOKEN`
   in `/portal/scan/env`, then start an instance refresh of `project-portal-scan-asg` (or re-run a deploy).

## Day-to-day

| Task | How |
|---|---|
| Change a backend setting | Edit `/portal/<role>/env` in SSM Parameter Store, then instance refresh that group (EC2 console → Auto Scaling Groups → Instance refresh) |
| Scaling activity | EC2 console → Auto Scaling Groups → `<group>` → Activity |
| Logs on an instance | SSH as `ec2-user`, `cd /opt/portal && docker compose -f docker-compose.prod.yml logs -f` |
| Restart an instance's service | `sudo /opt/portal/start.sh` on that instance |
| Roll back | Set `/portal/image-tag` to an older commit SHA, then instance refresh |
| Web EC2 status / logs | `docker compose -f docker-compose.prod.yml ps` / `logs -f` in `/opt/portal` |
| MySQL shell (RDS) | `docker run --rm -it mysql:8.4 mysql -h <DB_HOST> -u admin -p --ssl-mode=REQUIRED` (from a backend instance) |

RDS keeps 7 days of automated backups (point-in-time restore) and has deletion protection on.
