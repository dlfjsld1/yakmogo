#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
verifier="$repository_root/scripts/ci/verify-integrated-jar.sh"
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

make_fixture() {
  local root=$1
  mkdir -p "$root/dist/assets" "$root/jar/BOOT-INF/classes/static/assets" "$root/jar/BOOT-INF/classes"
  printf '<script src="/assets/app.js"></script>\n' > "$root/dist/index.html"
  printf 'bundle\n' > "$root/dist/assets/app.js"
  cp "$root/dist/index.html" "$root/jar/BOOT-INF/classes/static/index.html"
  cp "$root/dist/assets/app.js" "$root/jar/BOOT-INF/classes/static/assets/app.js"
  printf 'server:\n  port: 8081\nspring:\n  datasource:\n    url: jdbc:mariadb://localhost/yakmogo_enhancement\ntelegram:\n  bot:\n    enabled: false\nscheduling:\n  enabled: false\n' \
    > "$root/jar/BOOT-INF/classes/application-enhancement.yml"
  (cd "$root/jar" && jar cf "$root/app.jar" BOOT-INF)
}

make_fixture "$work_dir/valid"
"$verifier" "$work_dir/valid/app.jar" "$work_dir/valid/dist"

make_fixture "$work_dir/stale"
printf 'stale\n' > "$work_dir/stale/jar/BOOT-INF/classes/static/assets/old.js"
(cd "$work_dir/stale/jar" && jar cf "$work_dir/stale/app.jar" BOOT-INF)
if "$verifier" "$work_dir/stale/app.jar" "$work_dir/stale/dist"; then
  echo "verifier accepted a stale asset" >&2
  exit 1
fi

make_fixture "$work_dir/missing"
rm "$work_dir/missing/jar/BOOT-INF/classes/static/assets/app.js"
(cd "$work_dir/missing/jar" && jar cf "$work_dir/missing/app.jar" BOOT-INF)
if "$verifier" "$work_dir/missing/app.jar" "$work_dir/missing/dist"; then
  echo "verifier accepted a missing asset" >&2
  exit 1
fi

echo "Integrated JAR verifier tests passed"
