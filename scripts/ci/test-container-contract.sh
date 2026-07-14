#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
dockerfile=$repository_root/deploy/docker/Dockerfile
compose=$repository_root/deploy/docker/compose.yml
deploy_helper=$repository_root/deploy/cicd/yakmogo-enhancement-container-deploy

grep -Fxq 'FROM eclipse-temurin:21.0.11_10-jre-noble' "$dockerfile"
grep -Fxq 'USER 10001:10001' "$dockerfile"
! grep -Eq '^RUN ' "$dockerfile"

grep -Fq 'network_mode: host' "$compose"
grep -Fq '/etc/yakmogo/yakmogo-enhancement.env' "$compose"
grep -Fq 'SERVER_PORT: ${YAKMOGO_PORT:' "$compose"
grep -Fq 'TELEGRAM_BOT_ENABLED: "false"' "$compose"
grep -Fq 'SCHEDULING_ENABLED: "false"' "$compose"
grep -Fq 'read_only: true' "$compose"
grep -Fq 'no-new-privileges:true' "$compose"
grep -Fq 'cap_drop:' "$compose"
! grep -Eq '^[[:space:]]+ports:' "$compose"

grep -Fq '[[ $mode == deploy ]]' "$deploy_helper"
grep -Fq 'readonly APP_PORT=8081' "$deploy_helper"
grep -Fq 'systemctl stop "$SERVICE"' "$deploy_helper"
grep -Fq 'systemctl disable "$SERVICE"' "$deploy_helper"
grep -Fq 'systemctl reset-failed "$SERVICE"' "$deploy_helper"
grep -Fq 'restore_systemd_and_shadow' "$deploy_helper"
grep -Fq 'restore_previous_container' "$deploy_helper"
grep -Fq 'production_unchanged' "$deploy_helper"
grep -Fq 'container_security_valid' "$deploy_helper"
! grep -Eq 'systemctl (stop|start|restart|disable|enable) (yakmogo\.service|"?\$PROD_SERVICE)' "$deploy_helper"
! grep -Eq 'docker (system|image|container|volume) prune' "$deploy_helper"

! test -e "$repository_root/deploy/cicd/yakmogo-enhancement-deploy"
! test -e "$repository_root/deploy/cicd/yakmogo-enhancement-container-shadow"
grep -Fq '/etc/sudoers.d/yakmogo-enhancement-deploy' \
  "$repository_root/deploy/cicd/install-enhancement-container-deployer.sh"
grep -Fq '/etc/sudoers.d/yakmogo-enhancement-container-shadow' \
  "$repository_root/deploy/cicd/install-enhancement-container-deployer.sh"

echo "Container release safety contract passed"
