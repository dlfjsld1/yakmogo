#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 2 ]] || { echo "usage: $0 <release-dir> <output-dir>" >&2; exit 2; }

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
release_dir=$(cd "$1" && pwd)
output_dir=$(realpath -m "$2")
[[ $output_dir == "$repository_root"/build/* ]] \
  || { echo "output directory must be under the repository build directory" >&2; exit 2; }
manifest=$release_dir/release-manifest.txt
[[ -f $manifest ]] || { echo "release manifest not found" >&2; exit 1; }

image_tar=$(sed -n 's/^IMAGE_TAR=//p' "$manifest")
[[ $image_tar =~ ^yakmogo-[A-Za-z0-9.-]+-linux-arm64\.tar$ ]] \
  || { echo "invalid image tar in manifest" >&2; exit 1; }
[[ -f $release_dir/$image_tar ]] || { echo "image tar not found" >&2; exit 1; }

rm -rf "$output_dir"
mkdir -p "$output_dir/images"
for file in compose.yml .env.example README.md lib.sh install.sh update.sh backup.sh restore.sh; do
  [[ -f $repository_root/deploy/portable/$file ]] \
    || { echo "portable package source is missing: $file" >&2; exit 1; }
  cp "$repository_root/deploy/portable/$file" "$output_dir/$file"
done
cp "$release_dir/$image_tar" "$release_dir/$image_tar.sha256" "$output_dir/images/"
cp "$manifest" "$output_dir/release-manifest.txt"
chmod +x "$output_dir"/*.sh

echo "package_dir=$output_dir"
