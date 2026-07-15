#!/usr/bin/env bash
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "run with sudo: sudo ./remove-backup-timer.sh" >&2; exit 1; }
[[ $# -eq 0 ]] || { echo "arguments are not allowed" >&2; exit 2; }

systemctl disable --now yakmogo-backup.timer 2>/dev/null || true
rm -f /etc/systemd/system/yakmogo-backup.timer /etc/systemd/system/yakmogo-backup.service
systemctl daemon-reload
systemctl reset-failed yakmogo-backup.service 2>/dev/null || true
