# Deploying the API to Oracle Cloud (Always Free)

One Ampere VM runs everything with Docker Compose. Only Caddy is exposed; it
serves HTTPS for your domain and proxies to Spring Boot, which talks to MySQL
and RabbitMQ over the private Compose network. The React app stays on Vercel.

```
Vercel (React) ──HTTPS / WSS──▶ Caddy :443 ──▶ app :8080 ──▶ mysql :3306
                                                        └──▶ rabbitmq :61613 (STOMP)
```

## 1. Create the VM

In the OCI Console: **Compute → Instances → Create instance**.

- **Shape:** Change shape → Ampere → `VM.Standard.A1.Flex`, 2 OCPUs and 12 GB
  of memory. Always Free covers up to 4 OCPUs and 24 GB in total. Pick the shape
  before the image so the console offers ARM images.
- **Image:** Change image → Ubuntu → **Canonical Ubuntu 24.04** (the regular
  one, not Minimal). The aarch64 build is picked automatically.
- **Networking:** keep "Assign a public IPv4 address" on.
- **SSH keys:** upload your public key.
- **Boot volume:** the default 50 GB is fine.

If you see "Out of host capacity", try another availability domain or retry
later.

## 2. Open ports 80 and 443 in the VCN

Instance → Primary VNIC → Subnet → Security list → **Add ingress rules**, with
source `0.0.0.0/0` for each:

| Protocol | Destination port | Why |
|---|---|---|
| TCP | 80 | Let's Encrypt HTTP challenge and the redirect to HTTPS |
| TCP | 443 | HTTPS and WebSockets |
| UDP | 443 | HTTP/3 (optional) |

`setup-vm.sh` opens the same ports in the VM's own firewall. Oracle's Ubuntu
image blocks everything except SSH by default, so both are needed.

## 3. Point your domain at the VM

Create an `A` record, for example `api.yourdomain.com`, set to the instance's
public IP. To keep that IP if the instance is ever recreated, convert it to a
**reserved public IP** (Networking → IP management).

## 4. Install and start

```bash
ssh ubuntu@<public-ip>
git clone https://github.com/Kvngston/Codenames-Word-Game.git
cd Codenames-Word-Game/server/deploy
./setup-vm.sh
exit   # log back in so the docker group applies
```

```bash
ssh ubuntu@<public-ip>
cd Codenames-Word-Game/server/deploy
cp .env.example .env
nano .env   # set API_DOMAIN and ALLOWED_ORIGINS; generate each password with: openssl rand -base64 24
docker compose -f compose.prod.yaml up -d --build
curl https://api.yourdomain.com/api/healthz   # {"status":"ok"}
```

The first build takes a few minutes. Liquibase creates the tables on first
start, and Caddy fetches the certificate once DNS resolves to the VM.

## 5. Point the Vercel app at it

In the Vercel project settings, add the environment variable
`VITE_API_URL=https://api.yourdomain.com` and redeploy. The origin of your
Vercel site must be listed in `ALLOWED_ORIGINS` in `.env`. Preview deployments
use different URLs; add a pattern such as `https://word-agents-*.vercel.app`
if you want them to work.

## 6. Nightly backups to Object Storage

The backup script authenticates as the VM itself (an OCI *instance
principal*), so no API keys are stored on the machine.

1. **Bucket:** Storage → Buckets → Create bucket named `word-agents-backups`,
   in the VM's compartment. The Always Free tier includes 20 GB of Object
   Storage.
2. **Dynamic group:** Identity & Security → Domains → your domain → Dynamic
   groups → Create `word-agents-vm`, with the matching rule
   `instance.id = '<the instance OCID>'`.
3. **Policy:** Identity & Security → Policies → Create, in the same
   compartment:
   ```
   Allow dynamic-group word-agents-vm to manage objects in compartment <compartment-name> where target.bucket.name = 'word-agents-backups'
   ```
   On an identity-domain tenancy, write the group as `'Default'/'word-agents-vm'`.
4. **Test it once, then schedule it:**
   ```bash
   ./backup-mysql.sh
   crontab -e
   # add:
   15 3 * * * $HOME/Codenames-Word-Game/server/deploy/backup-mysql.sh >> $HOME/backup.log 2>&1
   ```

Backups are named `mysql/wordagents-YYYY-MM-DD.sql.gz`, and anything older
than `BACKUP_KEEP_DAYS` (default 14) is deleted.

**Restore** (replaces the current data):

```bash
~/bin/oci os object get --auth instance_principal --bucket-name word-agents-backups \
  --name mysql/wordagents-2026-09-30.sql.gz --file restore.sql.gz
gunzip -c restore.sql.gz | docker compose -f compose.prod.yaml exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" wordagents'
```

## Deploying a new version

```bash
cd ~/Codenames-Word-Game && git pull
cd server/deploy && docker compose -f compose.prod.yaml up -d --build
```

Only the app container is rebuilt and restarted. Players reconnect
automatically after a few seconds, and Liquibase applies any new changesets on
startup.

## Keeping the free VM

Oracle can reclaim Always Free VMs that stay idle for 7 days: CPU, network and
memory all under 20% utilisation. A party-game server is idle most of the time.
To avoid this, **upgrade the account to Pay As You Go**. Always Free resources
stay free and are no longer reclaimed; set a budget alert
(Billing → Budgets) so any accidental paid usage emails you.

## Useful commands

```bash
docker compose -f compose.prod.yaml ps              # status
docker compose -f compose.prod.yaml logs -f app     # server logs
docker compose -f compose.prod.yaml logs caddy      # certificate problems show up here
docker compose -f compose.prod.yaml restart app
```
