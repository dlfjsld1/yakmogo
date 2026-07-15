#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

[[ $# -eq 1 ]] || die "usage: $0 <yakmogo-version-linux-arm64.tar>"
validate_environment
image_tar=$(realpath "$1")
manifest=$(find_manifest_for_tar "$image_tar")
image_ref=$(verify_release "$image_tar" "$manifest")
expected_bundle=$(manifest_value "$manifest" JS_BUNDLE)
[[ $expected_bundle =~ ^/assets/[A-Za-z0-9_-]+\.js$ ]] || die "invalid JavaScript bundle path"

previous_image=$(env_value YAKMOGO_IMAGE)
previous_env=$(mktemp "$PORTABLE_DIR/.env.rollback.XXXXXX")
previous_manifest=$(mktemp "$PORTABLE_DIR/.manifest.rollback.XXXXXX")
chmod 0600 "$previous_env"
cp "$ENV_FILE" "$previous_env"
[[ -f $PORTABLE_DIR/current-release-manifest.txt ]] \
  || die "current release manifest is unavailable; run install.sh first"
cp "$PORTABLE_DIR/current-release-manifest.txt" "$previous_manifest"
cleanup() { rm -f "$previous_env" "$previous_manifest"; }
trap cleanup EXIT

backup_output=$("$PORTABLE_DIR/backup.sh" "$PORTABLE_DIR/backups/update")
docker load --input "$image_tar" >/dev/null
set_env_value YAKMOGO_IMAGE "$image_ref"

if compose up -d --no-deps yakmogo-app && wait_for_application "$expected_bundle"; then
  install -m 0644 "$manifest" "$PORTABLE_DIR/current-release-manifest.txt"
  echo "$backup_output"
  echo "UPDATE_RESULT=SUCCESS"
  echo "IMAGE_REF=$image_ref"
  exit 0
fi

install -m 0600 "$previous_env" "$ENV_FILE"
compose up -d --no-deps yakmogo-app
previous_bundle=$(manifest_value "$previous_manifest" JS_BUNDLE)
wait_for_application "$previous_bundle" \
  || die "update and previous image restoration both failed"
echo "UPDATE_RESULT=ROLLED_BACK" >&2
echo "PREVIOUS_IMAGE=$previous_image" >&2
exit 1
