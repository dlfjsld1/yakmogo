#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

[[ $# -eq 1 ]] || die "usage: $0 <empty-database-dump.sql|dump.sql.gz>"
validate_environment
dump_file=$(realpath "$1")
[[ -f $dump_file && ! -L $dump_file ]] || die "dump file is missing or unsafe"
wait_for_database || die "MariaDB is not ready"

if [[ -f $dump_file.sha256 ]]; then
  (cd "$(dirname "$dump_file")" && sha256sum --check "$(basename "$dump_file").sha256")
fi

table_count=$(compose exec -T yakmogo-mariadb sh -c \
  'MARIADB_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -N -B -uroot "$MARIADB_DATABASE" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"' \
  | tr -d '\r')
[[ $table_count == 0 ]] || die "restore target must be an empty database"

if [[ $dump_file == *.gz ]]; then
  require_command gzip
  gzip -dc "$dump_file"
else
  cat "$dump_file"
fi | compose exec -T yakmogo-mariadb sh -c \
  'MARIADB_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot "$MARIADB_DATABASE"'

echo "RESTORE_RESULT=SUCCESS"
