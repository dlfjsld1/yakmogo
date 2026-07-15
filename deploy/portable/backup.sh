#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

validate_environment
require_command gzip
require_command flock
require_command sha256sum
wait_for_database || die "MariaDB is not ready"
root_password=$(env_value YAKMOGO_DB_ROOT_PASSWORD)
database=$(env_value YAKMOGO_DB_NAME)
readonly BACKUP_KEEP=3

backup_dir=${1:-$PORTABLE_DIR/backups}
mkdir -p "$backup_dir"
backup_dir=$(cd "$backup_dir" && pwd)
umask 077
exec 9>"$backup_dir/.backup.lock"
flock -n 9 || die "another backup is already running"
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_file=$backup_dir/yakmogo-db-$timestamp.sql.gz
tmp_file=$backup_file.tmp
[[ ! -e $backup_file && ! -e $backup_file.sha256 ]] || die "backup timestamp already exists"

cleanup() { rm -f "$tmp_file"; }
trap cleanup EXIT

compose exec -T -e "MYSQL_PWD=$root_password" yakmogo-mariadb \
  mariadb-dump --single-transaction --routines --triggers -uroot "$database" \
  | gzip -9 > "$tmp_file"
gzip -t "$tmp_file"
mv -f "$tmp_file" "$backup_file"
(cd "$backup_dir" && sha256sum "$(basename "$backup_file")" > "$(basename "$backup_file").sha256")
(cd "$backup_dir" && sha256sum --check "$(basename "$backup_file").sha256" >/dev/null)

mapfile -t backups < <(find "$backup_dir" -maxdepth 1 -type f -name 'yakmogo-db-*.sql.gz' -printf '%f\n' | sort)
for existing_backup in "${backups[@]}"; do
  [[ -f $backup_dir/$existing_backup.sha256 ]] \
    || die "existing backup checksum is missing; retention skipped"
  (cd "$backup_dir" && sha256sum --check "$existing_backup.sha256" >/dev/null) \
    || die "existing backup checksum failed; retention skipped"
done
while (( ${#backups[@]} > BACKUP_KEEP )); do
  oldest=${backups[0]}
  rm -f -- "$backup_dir/$oldest" "$backup_dir/$oldest.sha256"
  backups=("${backups[@]:1}")
done

echo "BACKUP_FILE=$backup_file"
echo "BACKUP_RETAINED=${#backups[@]}"
