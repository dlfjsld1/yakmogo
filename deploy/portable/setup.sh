#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

[[ $EUID -ne 0 ]] || die "run as the installation user, not root"
[[ $# -le 1 ]] || die "usage: $0 [existing-database-dump.sql.gz]"
require_command sudo
require_command od
require_command awk
require_command tr

random_hex() {
  local bytes=$1
  od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n'
}

detect_lan_ip() {
  local address
  if command -v ip >/dev/null 2>&1; then
    address=$(ip -4 route get 1.1.1.1 2>/dev/null \
      | awk '{ for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }')
  fi
  if [[ -z ${address:-} ]]; then
    address=$(hostname -I 2>/dev/null | awk '{ print $1 }')
  fi
  [[ ${address:-} =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || address=localhost
  printf '%s' "$address"
}

[[ ! -L $ENV_FILE ]] || die ".env must not be a symbolic link"
if [[ ! -e $ENV_FILE ]]; then
  [[ -t 0 ]] || die "interactive terminal is required for first setup"
  default_url=http://$(detect_lan_ip):8080
  read -r -p "Yakmogo 접속 URL [$default_url]: " frontend_url
  frontend_url=${frontend_url:-$default_url}
  [[ $frontend_url =~ ^https?://[A-Za-z0-9._-]+:([0-9]{2,5})$ ]] \
    || die "URL must include a host and port, for example http://server:8080"
  http_port=${BASH_REMATCH[1]}
  (( http_port >= 1 && http_port <= 65535 )) || die "URL port is out of range"

  while true; do
    read -r -s -p "관리자 비밀번호(8자 이상, 영문·숫자·._:/@-): " admin_password
    printf '\n'
    [[ ${#admin_password} -ge 8 && $admin_password =~ ^[A-Za-z0-9._:/@-]+$ ]] && break
    echo "관리자 비밀번호 형식이 올바르지 않습니다." >&2
  done

  umask 077
  configuring=true
  cleanup_setup_env() {
    [[ ${configuring:-false} == true ]] && rm -f "$ENV_FILE"
  }
  trap cleanup_setup_env EXIT
  cp "$PORTABLE_DIR/.env.example" "$ENV_FILE"
  chmod 0600 "$ENV_FILE"
  set_env_value YAKMOGO_HTTP_PORT "$http_port"
  set_env_value YAKMOGO_FRONTEND_URL "$frontend_url"
  set_env_value YAKMOGO_DB_PASSWORD "$(random_hex 24)"
  set_env_value YAKMOGO_DB_ROOT_PASSWORD "$(random_hex 24)"
  set_env_value ADMIN_PASSWORD "$admin_password"
  set_env_value AUTH_TOKEN_SECRET "$(random_hex 32)"
  configuring=false
  trap - EXIT
  echo "SETUP_ENV=CREATED"
else
  [[ -f $ENV_FILE && ! -L $ENV_FILE ]] || die ".env is unsafe"
  echo "SETUP_ENV=REUSED"
fi

"$PORTABLE_DIR/install.sh" "$@"
service_user=$(id -un)
sudo "$PORTABLE_DIR/install-backup-timer.sh" "$service_user"

port=$(env_value YAKMOGO_HTTP_PORT)
[[ $(http_code "http://127.0.0.1:${port}/actuator/health") == 200 ]] \
  || die "health endpoint did not become ready"

echo "SETUP_RESULT=SUCCESS"
echo "URL=$(env_value YAKMOGO_FRONTEND_URL)"
echo "BACKUP_TIMER=yakmogo-backup.timer"
