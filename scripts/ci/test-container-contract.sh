#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
dockerfile=$repository_root/deploy/docker/Dockerfile
compose=$repository_root/deploy/docker/compose.yml
helper=$repository_root/deploy/cicd/yakmogo-enhancement-container-shadow

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

grep -Fq '[[ $mode == shadow ]]' "$helper"
! grep -Eq 'systemctl (stop|start|restart|disable|enable)' "$helper"
! grep -Eq 'docker (system|image|container|volume) prune' "$helper"

echo "Container release safety contract passed"
