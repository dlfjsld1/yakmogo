#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <web-dist-dir>" >&2
  exit 2
fi

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
web_dist=$(cd "$1" && pwd)
release_dir="$repository_root/build/release"

cd "$repository_root"
./gradlew clean test bootJar --no-daemon -PwebDistDir="$web_dist"

built_jar=$(find build/libs -maxdepth 1 -type f -name '*.jar' | head -n 1)
[[ -n "$built_jar" ]] || { echo "bootJar output not found" >&2; exit 1; }

mkdir -p "$release_dir"
release_name="yakmogo-enhancement-${GITHUB_SHA:-local}.jar"
release_jar="$release_dir/$release_name"
cp "$built_jar" "$release_jar"

scripts/ci/verify-integrated-jar.sh "$release_jar" "$web_dist"
release_sha=$(sha256sum "$release_jar" | awk '{print $1}')
js_bundle=$(grep -oE '/assets/[A-Za-z0-9_-]+\.js' "$web_dist/index.html" | head -n 1)
backend_sha=${GITHUB_SHA:-local}
web_sha=${WEB_SHA:-local}
[[ $backend_sha == local || $backend_sha =~ ^[0-9a-f]{40}$ ]] \
  || { echo "invalid backend SHA" >&2; exit 1; }
[[ $web_sha == local || $web_sha =~ ^[0-9a-f]{40}$ ]] \
  || { echo "invalid web SHA" >&2; exit 1; }
[[ $js_bundle =~ ^/assets/[A-Za-z0-9_-]+\.js$ ]] \
  || { echo "invalid JavaScript bundle path" >&2; exit 1; }

(cd "$release_dir" && sha256sum "$release_name" > "$release_name.sha256")
cat > "$release_dir/release-manifest.txt" <<EOF
BACKEND_SHA=$backend_sha
WEB_SHA=$web_sha
JAR_NAME=$release_name
JAR_SHA256=$release_sha
JS_BUNDLE=$js_bundle
EOF

echo "release_jar=$release_jar"
echo "release_checksum=$release_jar.sha256"
echo "release_manifest=$release_dir/release-manifest.txt"
