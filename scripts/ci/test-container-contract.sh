#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
dockerfile=$repository_root/deploy/docker/Dockerfile
compose=$repository_root/deploy/docker/compose.yml
deploy_helper=$repository_root/deploy/cicd/yakmogo-enhancement-container-deploy
portable_compose=$repository_root/deploy/portable/compose.yml
portable_backup=$repository_root/deploy/portable/backup.sh
portable_setup=$repository_root/deploy/portable/setup.sh
backup_timer_installer=$repository_root/deploy/portable/install-backup-timer.sh
release_workflow=$repository_root/.github/workflows/release-candidate.yml

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
grep -Fq 'previous_frontend_url=$(manifest_value YAKMOGO_FRONTEND_URL' "$deploy_helper"
grep -Fq 'YAKMOGO_FRONTEND_URL=$previous_frontend_url' "$deploy_helper"
! grep -Eq '192\.168\.[0-9]+\.[0-9]+' "$deploy_helper"
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

grep -Fq 'yakmogo-mariadb:' "$portable_compose"
grep -Fq 'yakmogo-app:' "$portable_compose"
grep -Fq 'jdbc:mariadb://yakmogo-mariadb:3306/' "$portable_compose"
grep -Fq 'TELEGRAM_BOT_USERNAME: ${TELEGRAM_BOT_USERNAME:-yakson_bot}' "$portable_compose"
grep -Fq 'yakmogo-mariadb-data:' "$portable_compose"
! awk '/yakmogo-mariadb:/,/yakmogo-app:/' "$portable_compose" | grep -Eq '^[[:space:]]+ports:'
grep -Fq 'readonly BACKUP_KEEP=3' "$portable_backup"
grep -Fq 'while (( ${#backups[@]} > BACKUP_KEEP ))' "$portable_backup"
grep -Fq 'sha256sum --check' "$portable_backup"
grep -Fq 'retention skipped' "$portable_backup"
grep -Fq 'OnCalendar=*-*-01 03:35:00 Asia/Seoul' "$backup_timer_installer"
grep -Fq 'Persistent=true' "$backup_timer_installer"
grep -Fq '"$PORTABLE_DIR/install.sh" "$@"' "$portable_setup"
grep -Fq 'sudo "$PORTABLE_DIR/install-backup-timer.sh" "$service_user"' "$portable_setup"
grep -Fq 'SETUP_RESULT=SUCCESS' "$portable_setup"
grep -Fq 'runs-on: ubuntu-latest' "$release_workflow"
! grep -Fq 'self-hosted' "$release_workflow"
! grep -Fq 'Deploy enhancement container' "$release_workflow"

echo "ARM64 Docker installation contract passed"
