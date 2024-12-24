#!/bin/bash
set -e

# Usage: `$ sudo ./install_collectd_debian.sh`

echo "[*] Bisq server monitoring installation script"

##### Change parameters if necessary for your system
BISQ_MONITOR_REPO_URL=https://raw.githubusercontent.com/bisq-network/bisq-monitor
BISQ_MONITOR_REPO_TAG=main
ROOT_USER=root
ROOT_GROUP=root
ROOT_HOME=~root
ROOT_PKG=(curl patch nginx libnginx-mod-stream collectd openssl socat tor basez)

SYSTEMD_ENV_HOME=/etc/default

#####

echo "[*] Gathering information"
read -p "Please provide the onion address of your service, without \".onion\" (eg. runbtcsd42pwlfna32ibcrrykrcmozgv6x73sxjrdohkm55v5f6nh6ad): " onionaddress

echo "[*] Updating apt repo sources"
DEBIAN_FRONTEND=noninteractive apt-get update -q

echo "[*] Upgrading OS packages"
DEBIAN_FRONTEND=noninteractive apt-get upgrade -qq -y

echo "[*] Installing base packages"
DEBIAN_FRONTEND=noninteractive apt-get install -qq -y ${ROOT_PKG[@]}

echo "[*] Configuring JVM options to allow for monitoring"
for file in "${SYSTEMD_ENV_HOME}/bisq.env" "${SYSTEMD_ENV_HOME}/bisq-pricenode.env"
do
    if [ -f "$file" ];then
        sed -i -e 's/-Dcom.sun.management.jmxremote //g' -e 's/-Dcom.sun.management.jmxremote.local.only=true//g' -e 's/ -Dcom.sun.management.jmxremote.host=127.0.0.1//g' -e 's/ -Dcom.sun.management.jmxremote.port=6969//g' -e 's/ -Dcom.sun.management.jmxremote.rmi.port=6969//g' -e 's/ -Dcom.sun.management.jmxremote.ssl=false//g' -e 's/ -Dcom.sun.management.jmxremote.authenticate=false//g' -e 's/ -Djava.rmi.server.hostname=127.0.0.1//g' "${file}"
        sed -i -e '/JAVA_OPTS/ s/"$/ -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.local.only=true -Dcom.sun.management.jmxremote.host=127.0.0.1 -Dcom.sun.management.jmxremote.port=6969 -Dcom.sun.management.jmxremote.rmi.port=6969 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Djava.rmi.server.hostname=127.0.0.1"/' "${file}"
    fi
done

echo "[*] Seeding entropy from /dev/urandom"
/bin/sh -c "head -1500 /dev/urandom > ${ROOT_HOME}/.rnd"

echo "[*] Installing Nginx config"
curl -s "${BISQ_MONITOR_REPO_URL}/${BISQ_MONITOR_REPO_TAG}/scripts/nginx.conf" > /tmp/nginx.conf
install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 /tmp/nginx.conf /etc/nginx/nginx.conf

echo "[*] Installing collectd config"
curl -s "${BISQ_MONITOR_REPO_URL}/${BISQ_MONITOR_REPO_TAG}/scripts/collectd.conf" > /tmp/collectd.conf
install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 /tmp/collectd.conf /etc/collectd/collectd.conf
sed -i -e "s/__ONION_ADDRESS__/$onionaddress/" /etc/collectd/collectd.conf

echo "[*] Installing http-to-socks-proxy config"
curl -s "${BISQ_MONITOR_REPO_URL}/${BISQ_MONITOR_REPO_TAG}/scripts/http-to-socks-proxy@.service" > /tmp/http-to-socks-proxy@.service
install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 /tmp/http-to-socks-proxy@.service /etc/systemd/system/http-to-socks-proxy@.service
curl -s "${BISQ_MONITOR_REPO_URL}/${BISQ_MONITOR_REPO_TAG}/scripts/bisq-monitor-2002.conf" > /tmp/bisq-monitor-2002.conf
curl -s "${BISQ_MONITOR_REPO_URL}/${BISQ_MONITOR_REPO_TAG}/scripts/bisq-monitor-13002.conf" > /tmp/bisq-monitor-13002.conf
mkdir -p /etc/http-to-socks-proxy/
install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 /tmp/bisq-monitor-2002.conf /etc/http-to-socks-proxy/bisq-monitor-2002.conf
install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 /tmp/bisq-monitor-13002.conf /etc/http-to-socks-proxy/bisq-monitor-13002.conf

echo "[*] Generating Tor client authorization key"
openssl genpkey -algorithm x25519 -out /tmp/k1.prv.pem
private_key=$(cat /tmp/k1.prv.pem | grep -v " PRIVATE KEY" | base64pem -d | tail --bytes=32 | base32 | sed 's/=//g')
public_key=$(openssl pkey -in /tmp/k1.prv.pem -pubout | grep -v " PUBLIC KEY" | base64pem -d | tail --bytes=32 | base32 | sed 's/=//g')
rm /tmp/k1.prv.pem

echo "[*] Configuring ClientOnionAuth"
grep -qxF 'ClientOnionAuthDir /var/lib/tor/onion_auth' /etc/tor/torrc || echo 'ClientOnionAuthDir /var/lib/tor/onion_auth' >> /etc/tor/torrc
mkdir -p /var/lib/tor/onion_auth
echo "bisqmonorsysbgqnma5ghacqgc2pyobk5gezlfo4q5wkemq66r47vmqd:descriptor:x25519:$private_key" > /var/lib/tor/onion_auth/bisqmonorsysbgqnma5ghacqgc2pyobk5gezlfo4q5wkemq66r47vmqd.auth_private
chown -R debian-tor:debian-tor /var/lib/tor/onion_auth

echo "[*] Symlink libjvm.so for collectd to work"
ln -s /usr/lib/jvm/openjdk-11.0.2/lib/server/libjvm.so /lib/x86_64-linux-gnu/libjvm.so || true

echo "[*] Add monitor parameter to bisq seednode service"
( patch -u /etc/default/bisq.env || true ) <<EOF
--- bisq.env.old        2022-12-07 12:07:14.481493232 +0000
+++ /etc/default/bisq.env       2022-12-07 12:13:58.370281467 +0000
@@ -46,2 +46,5 @@
 # set to true for BSQ markets
 BISQ_DUMP_STATISTICS=false
+
+# Reporting Server
+BISQ_REPORTINGSERVERURL=http://localhost:13003
EOF

( patch -u /etc/systemd/system/bisq.service || true ) <<EOF
--- bisq.service.old    2022-12-07 12:07:00.653481418 +0000
+++ /etc/systemd/system/bisq.service    2022-12-07 12:07:56.417573388 +0000
@@ -27,4 +27,5 @@
           --rpcPassword=\${BITCOIN_RPC_PASS} \\
           --dumpBlockchainData=\${BISQ_DUMP_BLOCKCHAIN} \\
           --dumpStatistics=\${BISQ_DUMP_STATISTICS} \\
+          --seedNodeReportingServerUrl=\${BISQ_REPORTINGSERVERURL} \\
           --torControlPort=\${BISQ_EXTERNAL_TOR_PORT} \\
EOF

echo "[*] Updating systemd daemon configuration"
systemctl daemon-reload
systemctl enable tor
systemctl enable nginx.service
systemctl enable collectd.service
systemctl enable http-to-socks-proxy@bisq-monitor-2002
systemctl enable http-to-socks-proxy@bisq-monitor-13002

echo "[*] Restarting services"
set +e
systemctl restart tor
systemctl restart nginx.service
systemctl restart collectd.service
systemctl restart http-to-socks-proxy@bisq-monitor-2002
systemctl restart http-to-socks-proxy@bisq-monitor-13002
service bisq status >/dev/null 2>&1
[ $? != 4 ] && systemctl restart bisq.service
service bisq-pricenode status >/dev/null 2>&1
[ $? != 4 ] && systemctl restart bisq-pricenode.service

echo '[*] Done!'

echo '  '
echo '[*] Provide the following to the monitoring team!'
echo '----------------------------------------------------------------'
echo "Server: $onionaddress"
echo '  '
echo "Public key: $public_key"
echo '----------------------------------------------------------------'
echo '  '
