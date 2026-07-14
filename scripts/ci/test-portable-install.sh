#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 1 ]] || { echo "usage: $0 <portable-package-dir>" >&2; exit 2; }
package_dir=$(cd "$1" && pwd)
work_dir=$(mktemp -d)
cp -R "$package_dir/." "$work_dir/"
cd "$work_dir"

cp .env.example .env
sed -i \
  -e 's/^COMPOSE_PROJECT_NAME=.*/COMPOSE_PROJECT_NAME=yakmogo-ci-portable/' \
  -e 's/^YAKMOGO_HTTP_PORT=.*/YAKMOGO_HTTP_PORT=18081/' \
  -e 's|^YAKMOGO_FRONTEND_URL=.*|YAKMOGO_FRONTEND_URL=http://127.0.0.1:18081|' \
  -e 's/^YAKMOGO_DB_PASSWORD=.*/YAKMOGO_DB_PASSWORD=ci-database-password/' \
  -e 's/^YAKMOGO_DB_ROOT_PASSWORD=.*/YAKMOGO_DB_ROOT_PASSWORD=ci-root-password/' \
  -e 's/^ADMIN_PASSWORD=.*/ADMIN_PASSWORD=ci-admin-password/' \
  -e 's/^AUTH_TOKEN_SECRET=.*/AUTH_TOKEN_SECRET=ci-auth-token-secret-at-least-32-characters/' \
  .env
chmod 600 .env
chmod +x ./*.sh

cleanup() {
  docker compose --project-directory "$work_dir" --env-file .env -f compose.yml \
    down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$work_dir"
}
trap cleanup EXIT

./install.sh
docker compose --env-file .env -f compose.yml exec -T yakmogo-mariadb sh -c \
  'MARIADB_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot "$MARIADB_DATABASE" -e "CREATE TABLE portable_restore_probe (id INT PRIMARY KEY); INSERT INTO portable_restore_probe VALUES (1)"'
backup_output=$(./backup.sh "$work_dir/saved-backup")
backup_file=${backup_output#BACKUP_FILE=}

docker compose --env-file .env -f compose.yml down --volumes
./install.sh "$backup_file"
probe_count=$(docker compose --env-file .env -f compose.yml exec -T yakmogo-mariadb sh -c \
  'MARIADB_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -N -B -uroot "$MARIADB_DATABASE" -e "SELECT COUNT(*) FROM portable_restore_probe"' \
  | tr -d '\r')
[[ $probe_count == 1 ]] || { echo "portable restore verification failed" >&2; exit 1; }

docker tag mariadb:11.8 yakmogo:broken-test
docker save --output "$work_dir/yakmogo-broken-test-linux-arm64.tar" yakmogo:broken-test
broken_sha=$(sha256sum "$work_dir/yakmogo-broken-test-linux-arm64.tar" | awk '{print $1}')
cat > "$work_dir/release-manifest.txt" <<EOF
IMAGE_REF=yakmogo:broken-test
IMAGE_TAR=yakmogo-broken-test-linux-arm64.tar
IMAGE_TAR_SHA256=$broken_sha
JS_BUNDLE=/assets/broken.js
EOF
if ./update.sh "$work_dir/yakmogo-broken-test-linux-arm64.tar"; then
  echo "broken update unexpectedly succeeded" >&2
  exit 1
fi
[[ $(curl --silent --output /dev/null --write-out '%{http_code}' http://127.0.0.1:18081/) == 200 ]] \
  || { echo "rollback did not restore the application" >&2; exit 1; }

echo "Portable install, restore, and update rollback verification passed"
