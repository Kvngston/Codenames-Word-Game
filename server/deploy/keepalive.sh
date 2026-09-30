#!/usr/bin/env bash
# Stops Oracle reclaiming this Always Free VM as idle. Oracle reclaims an A1
# instance when, over 7 days, its 95th-percentile CPU, network and memory use
# are all under 20%. Keeping CPU above 20% for more than 5% of the time
# is enough, so this burns every core for a few minutes each hour.
#
# The burn runs at the idle scheduling class, so it only uses CPU that
# nothing else wants and never slows the game.
#
#   server/deploy/keepalive.sh install   # copy to /usr/local/bin and start the hourly timer
#   server/deploy/keepalive.sh run       # burn once now (what the timer runs)
set -euo pipefail

MINUTES="${KEEPALIVE_MINUTES:-10}"
NAME=oci-keepalive

run() {
  echo "Burning $(nproc) cores for ${MINUTES}m"
  for _ in $(seq "$(nproc)"); do
    timeout "${MINUTES}m" bash -c 'while :; do :; done' &
  done
  wait || true   # timeout exits 124, which is the normal case here
}

install_timer() {
  sudo install -m 0755 "$0" "/usr/local/bin/$NAME"

  sudo tee "/etc/systemd/system/$NAME.service" > /dev/null <<EOF
[Unit]
Description=Burn idle CPU so Oracle doesn't reclaim this Always Free VM

[Service]
Type=oneshot
ExecStart=/usr/local/bin/$NAME run
Environment=KEEPALIVE_MINUTES=$MINUTES
DynamicUser=yes
Nice=19
CPUSchedulingPolicy=idle
EOF

  sudo tee "/etc/systemd/system/$NAME.timer" > /dev/null <<EOF
[Unit]
Description=Hourly CPU burn to keep the VM from being reclaimed

[Timer]
OnCalendar=hourly
RandomizedDelaySec=5m
Persistent=true

[Install]
WantedBy=timers.target
EOF

  sudo systemctl daemon-reload
  sudo systemctl enable --now "$NAME.timer"
  systemctl list-timers "$NAME.timer" --no-pager
}

case "${1:-}" in
  run) run ;;
  install) install_timer ;;
  *) echo "usage: $0 install|run" >&2; exit 2 ;;
esac
