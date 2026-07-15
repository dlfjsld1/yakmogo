#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

[[ $EUID -eq 0 ]] || die "run with sudo: sudo ./install-backup-timer.sh <service-user>"
[[ $# -eq 1 ]] || die "usage: sudo ./install-backup-timer.sh <service-user>"
service_user=$1
[[ $service_user =~ ^[a-z_][a-z0-9_-]*$ ]] || die "invalid service user"
id "$service_user" >/dev/null 2>&1 || die "service user does not exist"
[[ $PORTABLE_DIR =~ ^/[A-Za-z0-9._/-]+$ ]] || die "portable path contains unsupported characters"
[[ -x $PORTABLE_DIR/backup.sh && -f $ENV_FILE ]] || die "portable installation is incomplete"
[[ $(stat -c '%U' "$PORTABLE_DIR") == "$service_user" ]] \
  || die "service user must own the portable directory"

service_file=/etc/systemd/system/yakmogo-backup.service
timer_file=/etc/systemd/system/yakmogo-backup.timer

cat > "$service_file" <<EOF
[Unit]
Description=Yakmogo monthly MariaDB logical backup
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
User=$service_user
WorkingDirectory=$PORTABLE_DIR
ExecStart=$PORTABLE_DIR/backup.sh $PORTABLE_DIR/backups/scheduled
UMask=0077
EOF

cat > "$timer_file" <<'EOF'
[Unit]
Description=Run Yakmogo backup at 03:35 KST on the first day of each month

[Timer]
OnCalendar=*-*-01 03:35:00 Asia/Seoul
Persistent=true
RandomizedDelaySec=0
Unit=yakmogo-backup.service

[Install]
WantedBy=timers.target
EOF

chmod 0644 "$service_file" "$timer_file"
systemctl daemon-reload
systemctl enable --now yakmogo-backup.timer
systemctl list-timers yakmogo-backup.timer --no-pager
