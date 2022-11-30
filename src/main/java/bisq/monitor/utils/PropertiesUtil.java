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

package bisq.monitor.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class PropertiesUtil {

    public static Properties getProperties(String[] args) throws IOException {
        Properties result = new Properties();

        // if we have a config file load the config file, else, load the default config
        // from the resources
        if (args.length > 0) {
            result.load(new FileInputStream(args[0]));
        } else {
            result.load(PropertiesUtil.class.getClassLoader().getResourceAsStream("example_monitor.properties"));
        }
        return result;
    }
}