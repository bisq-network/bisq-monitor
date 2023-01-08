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

import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@Slf4j
public class PropertiesUtil {
    public static Properties getProperties(String path) {
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream(path));
            return properties;
        } catch (IOException e) {
            log.error("Error at parsing monitorPropertyOptions", e);
            log.error("path={}", path);
            throw new RuntimeException(e);
        }
    }

    public static Properties getProperties() {
        try {
            Properties properties = new Properties();
            properties.load(PropertiesUtil.class.getClassLoader().getResourceAsStream("example_monitor.properties"));
            return properties;
        } catch (IOException e) {
            log.error("Error at parsing monitorPropertyOptions", e);
            throw new RuntimeException(e);
        }
    }
}