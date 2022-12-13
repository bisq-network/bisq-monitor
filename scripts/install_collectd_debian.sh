#!/bin/bash
set -e

# Usage: `$ sudo ./install_collectd_debian.sh`

echo "[*] Bisq Server Monitoring installation script"

##### change paths if necessary for your system
BISQ_MONITOR_REPO_URL=https://raw.githubusercontent.com/bisq-network/bisq-monitor
BISQ_MONITOR_REPO_TAG=main
ROOT_USER=root
ROOT_GROUP=root
ROOT_HOME=~root
ROOT_PKG=(curl patch nginx collectd openssl)

SYSTEMD_ENV_HOME=/etc/default

#####

echo "[*] Gathering information"
read -p "Please provide the onion address of your service (eg. 3f3cu2yw7u457ztq): " onionaddress

echo "[*] Updating apt repo sources"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get update -q

echo "[*] Upgrading OS packages"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get upgrade -qq -y

echo "[*] Installing base packages"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get install -qq -y ${ROOT_PKG[@]}

echo "[*] Preparing Bisq init script for monitoring"
# remove stuff it it is there already
for file in "${SYSTEMD_ENV_HOME}/bisq.env" "${SYSTEMD_ENV_HOME}/bisq-pricenode.env"
do
    if [ -f "$file" ];then
        sudo -H -i -u "${ROOT_USER}" sed -i -e 's/-Dcom.sun.management.jmxremote //g' -e 's/-Dcom.sun.management.jmxremote.local.only=true//g' -e 's/ -Dcom.sun.management.jmxremote.host=127.0.0.1//g' -e 's/ -Dcom.sun.management.jmxremote.port=6969//g' -e 's/ -Dcom.sun.management.jmxremote.rmi.port=6969//g' -e 's/ -Dcom.sun.management.jmxremote.ssl=false//g' -e 's/ -Dcom.sun.management.jmxremote.authenticate=false//g' "${file}"
        sudo -H -i -u "${ROOT_USER}" sed -i -e '/JAVA_OPTS/ s/"$/ -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.local.only=true -Dcom.sun.management.jmxremote.host=127.0.0.1 -Dcom.sun.management.jmxremote.port=6969 -Dcom.sun.management.jmxremote.rmi.port=6969 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"/' "${file}"
    fi
done

echo "[*] Seeding entropy from /dev/urandom"
sudo -H -i -u "${ROOT_USER}" /bin/sh -c "head -1500 /dev/urandom > ${ROOT_HOME}/.rnd"
echo "[*] Installing Nginx config"
sudo -H -i -u "${ROOT_USER}" openssl req -x509 -nodes -newkey rsa:2048 -days 3000 -keyout /etc/nginx/cert.key -out /etc/nginx/cert.crt -subj="/O=Bisq/OU=Bisq Infrastructure/CN=$onionaddress"
curl -s "${BISQ_MONITOR_REPO_URL}/${BISQ_MONITOR_REPO_TAG}/scripts/nginx.conf" > /tmp/nginx.conf
sudo -H -i -u "${ROOT_USER}" install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 /tmp/nginx.conf /etc/nginx/nginx.conf

echo "[*] Installing collectd config"
curl -s "${BISQ_MONITOR_REPO_URL}/${BISQ_MONITOR_REPO_TAG}/scripts/collectd.conf" > /tmp/collectd.conf
sudo -H -i -u "${ROOT_USER}" install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 /tmp/collectd.conf /etc/collectd/collectd.conf
sudo -H -i -u "${ROOT_USER}" sed -i -e "s/__ONION_ADDRESS__/$onionaddress/" /etc/collectd/collectd.conf

echo "[*] Updating systemd daemon configuration"
sudo -H -i -u "${ROOT_USER}" systemctl daemon-reload
sudo -H -i -u "${ROOT_USER}" systemctl enable nginx.service
sudo -H -i -u "${ROOT_USER}" systemctl enable collectd.service

echo "[*] Symlink libjvm.so for collectd to work"
ln -s /usr/lib/jvm/openjdk-11.0.2/lib/server/libjvm.so /lib/x86_64-linux-gnu/libjvm.so || true

echo "[*] Add monitor parameter to bisq seednode service"
( patch -u /etc/default/bisq.env || true ) <<EOF
--- bisq.env.old        2022-12-07 12:07:14.481493232 +0000
+++ /etc/default/bisq.env       2022-12-07 12:13:58.370281467 +0000
@@ -40,3 +40,6 @@

 # set to true for BSQ markets
 BISQ_DUMP_STATISTICS=false
+
+# Reporting Server
+BISQ_REPORTINGSERVERURL=http://localhost:13003
EOF

( patch -u /etc/systemd/system/bisq.service || true ) <<EOF
--- bisq.service.old    2022-12-07 12:07:00.653481418 +0000
+++ /etc/systemd/system/bisq.service    2022-12-07 12:07:56.417573388 +0000
@@ -27,6 +27,7 @@
           --rpcPassword=\${BITCOIN_RPC_PASS} \\
           --dumpBlockchainData=\${BISQ_DUMP_BLOCKCHAIN} \\
           --dumpStatistics=\${BISQ_DUMP_STATISTICS} \\
+          --seedNodeReportingServerUrl=\${BISQ_REPORTINGSERVERURL} \\
           --torControlPort=9051

 ExecStop=/bin/kill \${MAINPID}
EOF

sudo -H -i -u "${ROOT_USER}" systemctl daemon-reload


echo "[*] Restarting services"
set +e
service bisq status >/dev/null 2>&1
[ $? != 4 ] && sudo -H -i -u "${ROOT_USER}" systemctl restart bisq.service
service bisq-pricenode status >/dev/null 2>&1
[ $? != 4 ] && sudo -H -i -u "${ROOT_USER}" systemctl restart bisq-pricenode.service
sudo -H -i -u "${ROOT_USER}" systemctl restart nginx.service
sudo -H -i -u "${ROOT_USER}" systemctl restart collectd.service

echo '[*] Done!'

echo '  '
echo '[*] Report this certificate to the monitoring team!'
echo '----------------------------------------------------------------'
echo "Server: $onionaddress"
echo '  '
cat /etc/nginx/cert.crt
echo '----------------------------------------------------------------'
echo '  '
