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

package bisq.monitor;


import bisq.monitor.dump.DataDumpMain;
import bisq.monitor.monitor.MonitorMain;
import bisq.monitor.server.ServerMain;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class AppChooserMain {

    /**
     * @param args --app=[BisqNetworkObserverMain | ServerMain]; optional; default MonitorMain
     *             --config={absolute path to monitor property file} ; optional; default is monitor.properties at data directory
     *             Arbitrary Bisq options as defined in Config. optional; ignored for MonitorMain
     */
    public static void main(String[] args) {
        Map<String, String> options = toOptionMap(args);
        String[] arguments = args;
        if (options.containsKey("--app")) {
            String app = options.get("--app");
            options.remove("--app");
            // We have removed the option we handle here and convert it back to args array to be passed to the 
            // main methods of the apps
            arguments = toArguments(options);

            if ("ServerMain".equals(app)) {
                ServerMain.main(arguments);
            } else if ("DataDumpMain".equals(app)) {
                if (options.containsKey("--config")) {
                    String config = options.get("--config");
                    options.remove("--config");
                    arguments = toArguments(options);
                    DataDumpMain.main(arguments, PropertiesUtil.getProperties(config));
                } else {
                    DataDumpMain.main(arguments);
                }
            }
        } else {
            MonitorMain.main(arguments);
        }
    }

    private static Map<String, String> toOptionMap(String[] args) {
        return Stream.of(args)
                .map(e -> e.split("="))
                .filter(e -> e.length == 2)
                .collect(Collectors.toMap(e -> e[0], e -> e[1]));
    }

    private static String[] toArguments(Map<String, String> options) {
        List<String> list = options.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());
        String[] pruned = new String[list.size()];
        list.toArray(pruned);
        return pruned;
    }
}
