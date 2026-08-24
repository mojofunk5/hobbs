#!/bin/bash
# Wraps the official postgres image's own entrypoint to turn on SSL with a self-signed cert,
# generated once into a persisted volume (idempotent - skipped if already present) so it survives
# container recreation. No real CA makes sense for a same-host internal Docker network; the point is
# to encrypt the wire (sslmode=require on the app side), not to verify server identity.
set -euo pipefail

CERT_DIR=/certs
CERT_FILE="$CERT_DIR/server.crt"
KEY_FILE="$CERT_DIR/server.key"

if [ ! -f "$CERT_FILE" ] || [ ! -f "$KEY_FILE" ]; then
  echo "postgres-ssl-entrypoint: generating self-signed certificate"
  mkdir -p "$CERT_DIR"
  openssl req -new -x509 -days 3650 -nodes \
    -out "$CERT_FILE" -keyout "$KEY_FILE" \
    -subj "/CN=postgres"
fi

# Postgres refuses to start unless the private key is owned by the server user (or root) and not
# readable by anyone else.
chown postgres:postgres "$CERT_FILE" "$KEY_FILE"
chmod 600 "$KEY_FILE"
chmod 644 "$CERT_FILE"

exec docker-entrypoint.sh postgres \
  -c ssl=on \
  -c ssl_cert_file="$CERT_FILE" \
  -c ssl_key_file="$KEY_FILE" \
  -c shared_buffers=32MB \
  -c max_connections=20 \
  -c maintenance_work_mem=32MB
