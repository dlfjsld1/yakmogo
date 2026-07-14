#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <spring-boot-jar> <web-dist-dir>" >&2
  exit 2
fi

jar_path=$1
web_dist=$2

[[ -f "$jar_path" ]] || { echo "jar not found: $jar_path" >&2; exit 1; }
[[ -f "$web_dist/index.html" ]] || { echo "web index not found: $web_dist/index.html" >&2; exit 1; }

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

jar tf "$jar_path" > "$work_dir/jar-entries.txt"
unzip -p "$jar_path" BOOT-INF/classes/static/index.html > "$work_dir/jar-index.html"
unzip -p "$jar_path" BOOT-INF/classes/application-enhancement.yml > "$work_dir/application-enhancement.yml"

cmp -s "$web_dist/index.html" "$work_dir/jar-index.html" || {
  echo "JAR index.html differs from web dist" >&2
  exit 1
}

while IFS= read -r file; do
  relative=${file#"$web_dist"/}
  grep -Fxq "BOOT-INF/classes/static/$relative" "$work_dir/jar-entries.txt" || {
    echo "web asset missing from JAR: $relative" >&2
    exit 1
  }
done < <(find "$web_dist" -type f | sort)

find "$web_dist/assets" -type f -printf '%P\n' 2>/dev/null | sort > "$work_dir/dist-assets.txt"
grep -v '/$' "$work_dir/jar-entries.txt" \
  | sed -n 's#^BOOT-INF/classes/static/assets/##p' \
  | sort > "$work_dir/jar-assets.txt"

diff -u "$work_dir/dist-assets.txt" "$work_dir/jar-assets.txt" || {
  echo "JAR contains missing or stale SPA assets" >&2
  exit 1
}

grep -Eq 'assets/[^"[:space:]]+\.js' "$work_dir/jar-index.html" || {
  echo "index.html does not reference a JavaScript bundle" >&2
  exit 1
}
grep -q 'port: 8081' "$work_dir/application-enhancement.yml"
grep -q 'yakmogo_enhancement' "$work_dir/application-enhancement.yml"
[[ $(grep -c 'enabled: false' "$work_dir/application-enhancement.yml") -ge 2 ]] || {
  echo "enhancement profile must disable Telegram bot and scheduling" >&2
  exit 1
}

echo "Integrated JAR verification passed: $jar_path"
