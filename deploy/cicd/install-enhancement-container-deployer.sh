#!/usr/bin/env bash
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "must run as root" >&2; exit 1; }
[[ $# -eq 1 ]] || { echo "usage: $0 <repository-root>" >&2; exit 2; }

repository_root=$(realpath "$1")
helper_source=$repository_root/deploy/cicd/yakmogo-enhancement-container-deploy
sudoers_source=$repository_root/deploy/cicd/yakmogo-enhancement-container-deploy.sudoers
compose_source=$repository_root/deploy/docker/compose.yml

[[ -f $helper_source && -f $sudoers_source && -f $compose_source ]] \
  || { echo "container deploy sources not found" >&2; exit 1; }
id yakmogo-runner >/dev/null 2>&1 \
  || { echo "yakmogo-runner user must exist" >&2; exit 1; }
command -v docker >/dev/null
docker compose version >/dev/null
visudo -cf "$sudoers_source"

install -o root -g root -m 0755 "$helper_source" \
  /usr/local/sbin/yakmogo-enhancement-container-deploy
install -o root -g root -m 0440 "$sudoers_source" \
  /etc/sudoers.d/yakmogo-enhancement-container-deploy
install -d -o root -g root -m 0750 /home/pi/docker/yakmogo-enhancement
install -o root -g root -m 0644 "$compose_source" \
  /home/pi/docker/yakmogo-enhancement/compose.yml
install -d -o yakmogo-runner -g yakmogo-runner -m 0750 \
  /var/lib/yakmogo-container-deploy/incoming
install -d -o root -g root -m 0700 \
  /var/lib/yakmogo-container-deploy/work \
  /var/lib/yakmogo-container-deploy/state
rm -f -- \
  /etc/sudoers.d/yakmogo-enhancement \
  /etc/sudoers.d/yakmogo-enhancement-deploy \
  /etc/sudoers.d/yakmogo-enhancement-container-shadow
visudo -c >/dev/null

echo "enhancement container deployer installed"
