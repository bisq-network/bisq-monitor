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

package bisq.monitor.monitor;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
public class MonitorHttpClient {
    public static MonitorHttpClient config(String address, int socketTimeout) {
        return new MonitorHttpClient(address, socketTimeout);
    }

    public static MonitorHttpClient config(String hostName, int port, Socks5Proxy proxy, int socketTimeout) {
        return new MonitorHttpClient(hostName, port, proxy, socketTimeout);
    }

    private String host;
    private int port;
    private Socks5Proxy proxy;
    private String address;
    private final int socketTimeout;

    private MonitorHttpClient(String address, int socketTimeout) {
        this.address = address;
        this.socketTimeout = socketTimeout;
    }

    private MonitorHttpClient(String host, int port, Socks5Proxy proxy, int socketTimeout) {
        this.host = host;
        this.port = port;
        this.proxy = proxy;
        this.socketTimeout = socketTimeout;
    }

    public String get(String query) throws IOException {
        HttpClient httpClient = HttpClient.newHttpClient();
        try {
            URI uri = URI.create(address + "/" + query);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .header("User-Agent", "bisq-monitor")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.error("Response error message: {}", response);
                return "";
            }
        } catch (IOException | InterruptedException e) {
            throw new IOException(e);
        }
    }

    public String getWithTor(String route) throws IOException {
        try (SocksSocket socket = new SocksSocket(proxy, host, port)) {
            socket.setSoTimeout(socketTimeout);
            try (PrintWriter printWriter = new PrintWriter(socket.getOutputStream())) {
                printWriter.println("GET /" + route);
                printWriter.println();
                printWriter.flush();
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    return stringBuilder.toString();
                } catch (IOException e) {
                    throw new IOException(e);
                }
            }
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }
}