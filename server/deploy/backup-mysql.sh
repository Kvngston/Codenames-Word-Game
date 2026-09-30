#!/usr/bin/env bash
# Dumps MySQL and uploads it to OCI Object Storage, then deletes backups older
# than BACKUP_KEEP_DAYS. Authenticates as the VM itself (instance principal),
# so no API keys live on the machine. Scheduled nightly by cron (see README).
set -euo pipefail

cd "$(dirname "$0")"
set -a; . ./.env; set +a
: "${BACKUP_BUCKET:?set BACKUP_BUCKET in .env}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
OCI="${OCI_CLI:-$(command -v oci || echo "$HOME/bin/oci")}"
export OCI_CLI_AUTH=instance_principal

stamp="$(date -u +%Y-%m-%d)"
file="$(mktemp -t wordagents-XXXXXX.sql.gz)"
trap 'rm -f "$file"' EXIT

# --single-transaction gives a consistent snapshot without locking the game's tables.
docker compose -f compose.prod.yaml exec -T mysql \
  sh -c 'exec mysqldump --single-transaction --routines --no-tablespaces -uroot -p"$MYSQL_ROOT_PASSWORD" wordagents' \
  | gzip > "$file"

"$OCI" os object put --bucket-name "$BACKUP_BUCKET" --name "mysql/wordagents-$stamp.sql.gz" --file "$file" --force > /dev/null
echo "Uploaded mysql/wordagents-$stamp.sql.gz ($(du -h "$file" | cut -f1))"

cutoff="$(date -u -d "-$KEEP_DAYS days" +%Y-%m-%d)"
"$OCI" os object list --bucket-name "$BACKUP_BUCKET" --prefix mysql/ --all --query "join(' ', data[].name)" --raw-output \
  | tr ' ' '\n' | grep -E '^mysql/wordagents-[0-9]{4}-[0-9]{2}-[0-9]{2}\.sql\.gz$' \
  | while read -r name; do
      day="${name#mysql/wordagents-}"; day="${day%.sql.gz}"
      if [[ "$day" < "$cutoff" ]]; then
        "$OCI" os object delete --bucket-name "$BACKUP_BUCKET" --object-name "$name" --force > /dev/null
        echo "Deleted $name"
      fi
    done
