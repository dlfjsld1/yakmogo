#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 2 ]] || { echo "usage: $0 <version> <web-dist-dir>" >&2; exit 2; }

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
version=$1
web_dist=$(cd "$2" && pwd)
backend_sha=$(git -C "$repository_root" rev-parse HEAD)
web_sha=$(git -C "$web_dist" rev-parse HEAD 2>/dev/null \
  || git -C "$web_dist/.." rev-parse HEAD)

cd "$repository_root"
rm -rf build/release build/portable
GITHUB_SHA=$backend_sha WEB_SHA=$web_sha \
  scripts/ci/build-release-candidate.sh "$web_dist"
RELEASE_VERSION=$version scripts/ci/build-container-candidate.sh build/release
scripts/release/package-portable.sh build/release build/portable

echo "portable_package=$repository_root/build/portable"
