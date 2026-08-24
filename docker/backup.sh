#!/bin/bash
# Nightly DB backup: dumps postgres, pushes an encrypted/deduplicated snapshot to Backblaze B2 via
# restic, prunes old snapshots, and pings a Better Stack heartbeat on success - so a backup that
# silently stops running gets caught the same way a real outage would (see docs/DEPLOYMENT.md).
#
# Runs on the VPS host, not in a container - restic just needs to read a file and make outbound
# HTTPS calls, no reason to containerize it (same reasoning as ScheduledCleanupJobs staying in-JVM
# rather than becoming its own service).
#
# Expects DB_USERNAME, B2_ACCOUNT_ID, B2_ACCOUNT_KEY, RESTIC_REPOSITORY, RESTIC_PASSWORD in the
# environment (sourced from /opt/hobbs/.env) and BETTER_STACK_HEARTBEAT_URL optionally.
#
# Real incident, since fixed: a run interrupted mid-upload (reboot, OOM, host network blip) left a
# stale lock in the restic repo. The next night's run blocked silently trying to acquire it - no
# error, no heartbeat, just a process sitting idle for hours until someone noticed the heartbeat had
# gone quiet. `restic unlock` upfront clears a stale lock before it can wedge a future run, and
# `timeout` on the backup itself turns "hangs forever" into "fails loudly within the window", so a
# genuine stuck run surfaces as a failed heartbeat within the hour instead of an indefinite hang.
set -euo pipefail

cd "$(dirname "$0")/.."
set -a
source .env
set +a

export B2_ACCOUNT_ID B2_ACCOUNT_KEY RESTIC_REPOSITORY RESTIC_PASSWORD

DUMP_FILE="/tmp/hobbs-backup-$(date +%Y%m%d-%H%M%S).dump"
trap 'rm -f "$DUMP_FILE"' EXIT

echo "backup.sh: clearing any stale lock from an interrupted previous run"
restic unlock

echo "backup.sh: dumping database"
docker compose exec -T postgres pg_dump -U "$DB_USERNAME" -Fc hobbs > "$DUMP_FILE"

echo "backup.sh: pushing snapshot to B2"
timeout 30m restic backup "$DUMP_FILE" --tag hobbs-db

echo "backup.sh: pruning old snapshots"
timeout 10m restic forget --keep-daily 14 --keep-weekly 8 --keep-monthly 6 --prune

if [ -n "${BETTER_STACK_HEARTBEAT_URL:-}" ]; then
  curl -fsS -o /dev/null "$BETTER_STACK_HEARTBEAT_URL"
fi

echo "backup.sh: done"
