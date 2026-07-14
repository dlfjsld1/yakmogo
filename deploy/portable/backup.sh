#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

validate_environment
require_command gzip
wait_for_database || die "MariaDB is not ready"

backup_dir=${1:-$PORTABLE_DIR/backups}
mkdir -p "$backup_dir"
backup_dir=$(cd "$backup_dir" && pwd)
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_file=$backup_dir/yakmogo-db-$timestamp.sql.gz
tmp_file=$backup_file.tmp
umask 077

cleanup() { rm -f "$tmp_file"; }
trap cleanup EXIT

compose exec -T yakmogo-mariadb sh -c \
  'MARIADB_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb-dump --single-transaction --routines --triggers -uroot "$MARIADB_DATABASE"' \
  | gzip -9 > "$tmp_file"
gzip -t "$tmp_file"
mv -f "$tmp_file" "$backup_file"
(cd "$backup_dir" && sha256sum "$(basename "$backup_file")" > "$(basename "$backup_file").sha256")

echo "BACKUP_FILE=$backup_file"
