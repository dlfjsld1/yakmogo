#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 1 ]] || { echo "usage: $0 <portable-package-dir>" >&2; exit 2; }
package_dir=$(cd "$1" && pwd)
work_dir=$(mktemp -d)
mock_bin=$work_dir/mock-bin
mkdir -p "$mock_bin" "$work_dir/package"
cp -R "$package_dir/." "$work_dir/package/"
command -v script >/dev/null 2>&1 || { echo "script command is required" >&2; exit 1; }

cleanup() { rm -rf "$work_dir"; }
trap cleanup EXIT

cat > "$work_dir/package/install.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" > "$(dirname "$0")/.install-arguments"
EOF
cat > "$work_dir/package/install-backup-timer.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" > "$(dirname "$0")/.timer-arguments"
EOF
cat > "$mock_bin/sudo" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
"$@"
EOF
cat > "$mock_bin/curl" <<'EOF'
#!/usr/bin/env bash
printf '200'
EOF
chmod +x "$work_dir/package"/*.sh "$mock_bin"/*

cd "$work_dir/package"
export PATH="$mock_bin:$PATH"
setup_output=$(printf '\nci-admin-password\n' | script -qec './setup.sh' /dev/null)

[[ $setup_output == *'SETUP_RESULT=SUCCESS'* ]]
[[ $setup_output == *'BACKUP_TIMER=yakmogo-backup.timer'* ]]
[[ $(stat -c '%a' .env) == 600 ]]
! grep -Fq 'CHANGE_ME' .env
grep -Eq '^YAKMOGO_FRONTEND_URL=http://[A-Za-z0-9._-]+:8080$' .env
grep -Fxq 'ADMIN_PASSWORD=ci-admin-password' .env
grep -Eq '^YAKMOGO_DB_PASSWORD=[0-9a-f]{48}$' .env
grep -Eq '^YAKMOGO_DB_ROOT_PASSWORD=[0-9a-f]{48}$' .env
grep -Eq '^AUTH_TOKEN_SECRET=[0-9a-f]{64}$' .env
[[ -s .timer-arguments ]]

echo "Portable one-command setup verification passed"
