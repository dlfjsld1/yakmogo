#!/usr/bin/env bash

set -euo pipefail

readonly PORTABLE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
readonly ENV_FILE=$PORTABLE_DIR/.env
readonly COMPOSE_FILE=$PORTABLE_DIR/compose.yml

die() { echo "yakmogo portable error: $*" >&2; exit 1; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

env_value() {
  local key=$1 count value
  [[ -f $ENV_FILE && ! -L $ENV_FILE ]] || die ".env is missing or unsafe"
  count=$(grep -cE "^${key}=" "$ENV_FILE" || true)
  [[ $count -eq 1 ]] || die ".env key must appear once: $key"
  value=$(sed -n "s/^${key}=//p" "$ENV_FILE" | tr -d '\r')
  [[ -n $value ]] || die ".env value is empty: $key"
  printf '%s' "$value"
}

manifest_value() {
  local file=$1 key=$2 count value
  [[ -f $file && ! -L $file ]] || die "release manifest is missing or unsafe"
  count=$(grep -cE "^${key}=" "$file" || true)
  [[ $count -eq 1 ]] || die "manifest key must appear once: $key"
  value=$(sed -n "s/^${key}=//p" "$file" | tr -d '\r')
  [[ -n $value ]] || die "manifest value is empty: $key"
  printf '%s' "$value"
}

compose() {
  docker compose --project-directory "$PORTABLE_DIR" --env-file "$ENV_FILE" \
    --file "$COMPOSE_FILE" "$@"
}

validate_environment() {
  local key value mode
  require_command docker
  require_command curl
  require_command sha256sum
  docker compose version >/dev/null
  [[ -f $COMPOSE_FILE && ! -L $COMPOSE_FILE ]] || die "compose.yml is missing or unsafe"
  mode=$(stat -c '%a' "$ENV_FILE" 2>/dev/null || true)
  [[ $mode == 600 ]] || die ".env permission must be 600"
  for key in COMPOSE_PROJECT_NAME YAKMOGO_IMAGE YAKMOGO_HTTP_PORT \
    YAKMOGO_FRONTEND_URL YAKMOGO_DB_NAME YAKMOGO_DB_USER \
    YAKMOGO_DB_PASSWORD YAKMOGO_DB_ROOT_PASSWORD ADMIN_PASSWORD AUTH_TOKEN_SECRET; do
    value=$(env_value "$key")
    [[ $value != *CHANGE_ME* ]] || die ".env placeholder must be replaced: $key"
  done
  [[ $(env_value AUTH_TOKEN_SECRET) == ????????????????????????????????* ]] \
    || die "AUTH_TOKEN_SECRET must contain at least 32 characters"
  compose config --quiet
}

wait_for_database() {
  local i
  for ((i = 0; i < 60; i++)); do
    if compose exec -T yakmogo-mariadb healthcheck.sh --connect --innodb_initialized \
      >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

http_code() {
  curl --max-time 3 --silent --output /dev/null --write-out '%{http_code}' "$1" 2>/dev/null || true
}

wait_for_application() {
  local bundle=$1 port i
  port=$(env_value YAKMOGO_HTTP_PORT)
  for ((i = 0; i < 90; i++)); do
    if [[ $(http_code "http://127.0.0.1:${port}/") == 200 ]] \
      && [[ $(http_code "http://127.0.0.1:${port}${bundle}") == 200 ]] \
      && [[ $(http_code "http://127.0.0.1:${port}/api/v1/users") == 401 ]]; then
      return 0
    fi
    sleep 2
  done
  return 1
}

set_env_value() {
  local key=$1 value=$2 tmp
  [[ $value =~ ^[A-Za-z0-9._:/@-]+$ ]] || die "unsafe .env replacement value"
  tmp=$(mktemp "$PORTABLE_DIR/.env.XXXXXX")
  awk -v key="$key" -v value="$value" \
    'index($0, key "=") == 1 { print key "=" value; found=1; next } { print } END { if (!found) exit 1 }' \
    "$ENV_FILE" > "$tmp" || { rm -f "$tmp"; die ".env key not found: $key"; }
  chmod 0600 "$tmp"
  mv -f "$tmp" "$ENV_FILE"
}

verify_release() {
  local image_tar=$1 manifest=$2 expected_name expected_sha actual_sha image_ref
  [[ -f $image_tar && ! -L $image_tar ]] || die "image tar is missing or unsafe"
  expected_name=$(manifest_value "$manifest" IMAGE_TAR)
  expected_sha=$(manifest_value "$manifest" IMAGE_TAR_SHA256)
  image_ref=$(manifest_value "$manifest" IMAGE_REF)
  [[ $(basename "$image_tar") == "$expected_name" ]] || die "image tar name mismatch"
  [[ $expected_sha =~ ^[0-9a-f]{64}$ ]] || die "invalid image checksum"
  [[ $image_ref =~ ^yakmogo:[A-Za-z0-9._-]+$ ]] || die "invalid image reference"
  actual_sha=$(sha256sum "$image_tar" | awk '{print $1}')
  [[ $actual_sha == "$expected_sha" ]] || die "image tar checksum mismatch"
  printf '%s' "$image_ref"
}

find_manifest_for_tar() {
  local image_tar=$1 image_dir parent
  image_dir=$(cd "$(dirname "$image_tar")" && pwd)
  parent=$(dirname "$image_dir")
  if [[ -f $image_dir/release-manifest.txt ]]; then
    printf '%s' "$image_dir/release-manifest.txt"
  elif [[ -f $parent/release-manifest.txt ]]; then
    printf '%s' "$parent/release-manifest.txt"
  else
    die "release-manifest.txt not found next to image tar"
  fi
}
