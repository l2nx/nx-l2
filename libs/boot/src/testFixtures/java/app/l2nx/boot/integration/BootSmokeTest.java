/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.boot.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The servers keep static state, so nothing runs concurrently and the nested classes run in their {@code @Order}.
 */
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
public abstract class BootSmokeTest {
    // MariaDB reached through MySQL Connector/J, the driver the servers ship with; the stock MariaDB container would
    // hand Spring the MariaDB driver.
    @Container
    @ServiceConnection
    private static final MySQLContainer DATABASE =
            new MySQLContainer(DockerImageName.parse("mariadb:11.8").asCompatibleSubstituteFor("mysql"));

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    @LocalServerPort
    private int managementPort;

    protected HttpResponse<String> get(String path) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + path))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected static Socket connect(int port) throws IOException {
        final Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        return socket;
    }
}
