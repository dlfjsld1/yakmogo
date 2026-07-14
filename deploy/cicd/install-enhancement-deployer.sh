#!/usr/bin/env bash
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "must run as root" >&2; exit 1; }
[[ $# -eq 1 ]] || { echo "usage: $0 <repository-root>" >&2; exit 2; }

repository_root=$(realpath "$1")
helper_source=$repository_root/deploy/cicd/yakmogo-enhancement-deploy
sudoers_source=$repository_root/deploy/cicd/yakmogo-enhancement.sudoers

[[ -f $helper_source && -f $sudoers_source ]] || {
  echo "deploy helper sources not found" >&2
  exit 1
}
id yakmogo-runner >/dev/null 2>&1 || {
  echo "yakmogo-runner user must exist before installing deployer" >&2
  exit 1
}

visudo -cf "$sudoers_source"
install -o root -g root -m 0755 "$helper_source" /usr/local/sbin/yakmogo-enhancement-deploy
install -o root -g root -m 0440 "$sudoers_source" /etc/sudoers.d/yakmogo-enhancement-deploy
install -d -o yakmogo-runner -g yakmogo-runner -m 0750 /var/lib/yakmogo-deploy/incoming
install -d -o root -g root -m 0700 /var/lib/yakmogo-deploy/work
install -d -o root -g root -m 0700 /var/backups/yakmogo/enhancement

echo "enhancement deploy helper installed"
