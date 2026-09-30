#!/usr/bin/env bash
# One-time setup for an Ubuntu 24.04 (aarch64) VM on Oracle Cloud.
# Run as the default "ubuntu" user from the repo checkout: server/deploy/setup-vm.sh
set -euo pipefail

echo "==> Installing Docker Engine and the Compose plugin"
sudo apt-get update -y
sudo apt-get install -y ca-certificates curl git python3-venv iptables-persistent
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"

echo "==> Opening ports 80 and 443 in the VM firewall"
# Oracle's Ubuntu images ship iptables rules that reject everything except SSH.
# The VCN security list must also allow 80/443 (see README).
for port in 80 443; do
  sudo iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null \
    || sudo iptables -I INPUT 6 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
done
sudo iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 6 -p udp --dport 443 -j ACCEPT
sudo netfilter-persistent save

echo "==> Installing the OCI CLI (for backups to Object Storage)"
if ! command -v oci > /dev/null && [ ! -x "$HOME/bin/oci" ]; then
  bash -c "$(curl -fsSL https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)" -s --accept-all-defaults
fi

echo "==> Done. Log out and back in so the docker group applies, then follow the README."
