/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor.monitor.tasks.seed;

import bisq.common.proto.network.NetworkEnvelope;
import bisq.monitor.monitor.tasks.TorBasedMonitorTask;
import bisq.monitor.reporter.Metric;
import bisq.monitor.reporter.Reporter;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.peers.keepalive.messages.Pong;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Sends a Ping message to all seed nodes and get the round trip time when we get back the Pong.
 */
public class SeedNodeRoundTripTime extends TorBasedMonitorTask {
    private final Map<String, Integer> rrtByAddress = new ConcurrentHashMap<>();

    public SeedNodeRoundTripTime(Properties properties, Reporter reporter, Map<String, String> seedNodeOperatorByAddress) {
        super(properties, reporter, seedNodeOperatorByAddress);
    }

    @Override
    protected List<NetworkEnvelope> getRequests() {
        // We use the request timestamp as nonce and take it out from the Pong later
        return List.of(new Ping((int) System.currentTimeMillis() / 1000, 0));
    }

    @Override
    protected boolean processMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof Pong) {
            checkNotNull(connection.getPeersNodeAddressProperty(), "nodeAddress must not be null at that moment");
            String address = connection.getPeersNodeAddressProperty().get().getHostNameWithoutPostFix();
            Pong pong = (Pong) networkEnvelope;
            int rrt = (int) System.currentTimeMillis() / 1000 - pong.getRequestNonce();
            rrtByAddress.put(address, rrt);
            connection.shutDown(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER);
            return true;
        }
        return false;
    }

    @Override
    public void report() {
        rrtByAddress.forEach((key, value) -> reporter.report(new Metric(getName() + "." + key, value)));
        rrtByAddress.clear();
    }
}
