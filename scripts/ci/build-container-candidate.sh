#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 1 ]] || { echo "usage: $0 <release-dir>" >&2; exit 2; }
[[ ${RELEASE_VERSION:-} =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$ ]] \
  || { echo "RELEASE_VERSION must be a version such as 0.0.7 or 0.0.7-rc.1" >&2; exit 2; }

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
release_dir=$(cd "$1" && pwd)
manifest=$release_dir/release-manifest.txt
[[ -f $manifest ]] || { echo "release manifest not found" >&2; exit 1; }

manifest_value() {
  local key=$1 count value
  count=$(grep -cE "^${key}=" "$manifest" || true)
  [[ $count -eq 1 ]] || { echo "manifest key must appear once: $key" >&2; exit 1; }
  value=$(sed -n "s/^${key}=//p" "$manifest")
  printf '%s' "$value"
}

backend_sha=$(manifest_value BACKEND_SHA)
web_sha=$(manifest_value WEB_SHA)
jar_name=$(manifest_value JAR_NAME)
jar_sha=$(manifest_value JAR_SHA256)
[[ $backend_sha =~ ^[0-9a-f]{40}$ ]] || { echo "invalid backend SHA" >&2; exit 1; }
[[ $web_sha =~ ^[0-9a-f]{40}$ ]] || { echo "invalid web SHA" >&2; exit 1; }
[[ $jar_sha =~ ^[0-9a-f]{64}$ ]] || { echo "invalid JAR checksum" >&2; exit 1; }
[[ $jar_name == "yakmogo-enhancement-${backend_sha}.jar" ]] \
  || { echo "invalid JAR name" >&2; exit 1; }
[[ -f $release_dir/$jar_name && ! -L $release_dir/$jar_name ]] \
  || { echo "release JAR is missing or unsafe" >&2; exit 1; }
[[ $(sha256sum "$release_dir/$jar_name" | awk '{print $1}') == "$jar_sha" ]] \
  || { echo "release JAR checksum mismatch" >&2; exit 1; }

image_ref=yakmogo:$RELEASE_VERSION
image_tar_name=yakmogo-${RELEASE_VERSION}-linux-arm64.tar
image_tar=$release_dir/$image_tar_name

docker buildx build \
  --platform linux/arm64 \
  --file "$repository_root/deploy/docker/Dockerfile" \
  --build-arg "JAR_NAME=$jar_name" \
  --tag "$image_ref" \
  --label "com.yakmogo.backend-sha=$backend_sha" \
  --label "com.yakmogo.web-sha=$web_sha" \
  --label "com.yakmogo.jar-sha256=$jar_sha" \
  --label "org.opencontainers.image.version=$RELEASE_VERSION" \
  --label "org.opencontainers.image.source=https://github.com/dlfjsld1/yakmogo" \
  --output "type=docker,dest=$image_tar" \
  "$release_dir"

image_tar_sha=$(sha256sum "$image_tar" | awk '{print $1}')
(cd "$release_dir" && sha256sum "$image_tar_name" > "$image_tar_name.sha256")

manifest_tmp=$(mktemp "$release_dir/release-manifest.XXXXXX")
grep -vE '^(RELEASE_VERSION|IMAGE_REF|IMAGE_ARCH|IMAGE_TAR|IMAGE_TAR_SHA256)=' "$manifest" > "$manifest_tmp"
cat >> "$manifest_tmp" <<EOF
RELEASE_VERSION=$RELEASE_VERSION
IMAGE_REF=$image_ref
IMAGE_ARCH=arm64
IMAGE_TAR=$image_tar_name
IMAGE_TAR_SHA256=$image_tar_sha
EOF
mv -f "$manifest_tmp" "$manifest"

echo "image_tar=$image_tar"
echo "image_checksum=$image_tar.sha256"
