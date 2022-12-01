/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor.monitor.tor;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import java.io.File;

@Slf4j
public class TorNode {
    @Getter
    private static File torDir;

    public TorNode(File appDir) {
        // blocking start
        try {
            torDir = new File(appDir + "/tor");
            Tor.setDefault(new NativeTor(torDir, null, null, false));
        } catch (TorCtlException e) {
            log.error("Error at starting tor", e);
            throw new RuntimeException(e);
        }
    }

    public void shutDown() {
        Tor tor = Tor.getDefault();
        if (tor != null) {
            tor.shutdown();
        }
    }
}