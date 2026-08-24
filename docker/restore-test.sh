#!/bin/bash
# Proves the latest backup is actually restorable, not just that it exists in B2. Restores into a
# throwaway Postgres container on a scratch port - never touches the real database or volume - and
# prints row counts to eyeball. Meant to be re-run periodically as a runbook (see docs/DEPLOYMENT.md),
# not wired into cron: a restore test is worth a human actually looking at the output.
set -euo pipefail

cd "$(dirname "$0")/.."
set -a
source .env
set +a

export B2_ACCOUNT_ID B2_ACCOUNT_KEY RESTIC_REPOSITORY RESTIC_PASSWORD

RESTORE_DIR="/tmp/hobbs-restore-test-$$"
CONTAINER=hobbs-restore-test
PORT=15432

cleanup() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$RESTORE_DIR"
}
trap cleanup EXIT

echo "restore-test.sh: restoring latest snapshot from B2"
restic restore latest --target "$RESTORE_DIR" --tag hobbs-db

DUMP_FILE=$(find "$RESTORE_DIR" -name "hobbs-backup-*.dump" | sort | tail -1)
if [ -z "$DUMP_FILE" ]; then
  echo "restore-test.sh: no dump file found in the restored snapshot" >&2
  exit 1
fi
echo "restore-test.sh: found $DUMP_FILE"

echo "restore-test.sh: starting a throwaway postgres container on 127.0.0.1:$PORT"
docker run --rm -d --name "$CONTAINER" \
  -e POSTGRES_PASSWORD=restore-test \
  -e POSTGRES_DB=hobbs_restore_test \
  -p "127.0.0.1:$PORT:5432" \
  postgres:17 >/dev/null

echo "restore-test.sh: waiting for it to accept connections"
for _ in $(seq 1 30); do
  if docker exec "$CONTAINER" pg_isready -U postgres >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "restore-test.sh: restoring the dump"
# --no-owner/--no-privileges: the dump's ALTER TABLE ... OWNER TO / GRANT statements reference the
# real DB_USERNAME role, which has no reason to exist in this throwaway container - the restore-test
# shouldn't need to know anything about how production is configured. Ownership/privileges don't
# matter here anyway; the container is deleted the moment this script finishes.
docker exec -i "$CONTAINER" pg_restore -U postgres -d hobbs_restore_test --no-owner --no-privileges < "$DUMP_FILE"

echo "restore-test.sh: row counts in the restored database (eyeball these against what you expect)"
for TABLE in $(docker exec "$CONTAINER" psql -U postgres -d hobbs_restore_test -tAc \
  "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;"); do
  COUNT=$(docker exec "$CONTAINER" psql -U postgres -d hobbs_restore_test -tAc "SELECT COUNT(*) FROM \"$TABLE\";")
  printf '%-25s %s\n' "$TABLE" "$COUNT"
done

echo "restore-test.sh: done - throwaway container and files will be cleaned up now"
