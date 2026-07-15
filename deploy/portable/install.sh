#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

[[ $# -le 1 ]] || die "usage: $0 [existing-database-dump.sql.gz]"
validate_environment

mapfile -t image_tars < <(find "$PORTABLE_DIR/images" -maxdepth 1 -type f -name 'yakmogo-*-linux-arm64.tar' | sort)
[[ ${#image_tars[@]} -eq 1 ]] || die "images directory must contain exactly one ARM64 image tar"
image_tar=${image_tars[0]}
manifest=$(find_manifest_for_tar "$image_tar")
image_ref=$(verify_release "$image_tar" "$manifest")
expected_bundle=$(manifest_value "$manifest" JS_BUNDLE)
[[ $expected_bundle =~ ^/assets/[A-Za-z0-9_-]+\.js$ ]] || die "invalid JavaScript bundle path"

docker load --input "$image_tar" >/dev/null
set_env_value YAKMOGO_IMAGE "$image_ref"
compose config --quiet
compose up -d yakmogo-mariadb
wait_for_database || die "MariaDB did not become ready"

if [[ $# -eq 1 ]]; then
  "$PORTABLE_DIR/restore.sh" "$1"
fi

compose up -d yakmogo-app
if ! wait_for_application "$expected_bundle"; then
  compose logs --tail 80 yakmogo-app >&2 || true
  compose stop yakmogo-app >/dev/null 2>&1 || true
  die "application readiness failed"
fi

install -m 0644 "$manifest" "$PORTABLE_DIR/current-release-manifest.txt"

echo "INSTALL_RESULT=SUCCESS"
echo "URL=http://127.0.0.1:$(env_value YAKMOGO_HTTP_PORT)/"
