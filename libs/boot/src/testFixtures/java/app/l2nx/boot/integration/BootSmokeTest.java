/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.boot.integration;

import app.l2nx.database.PostgresDatabase;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The servers keep static state, so nothing runs concurrently and the nested classes run in their {@code @Order}.
 */
@Execution(ExecutionMode.SAME_THREAD)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
public abstract class BootSmokeTest {
    @ServiceConnection
    private static final PostgreSQLContainer DATABASE = PostgresDatabase.container();

    static {
        // The servers' default schemas; an operator creates them before the first start.
        PostgresDatabase.ensureSuperuserSchema("login");
        PostgresDatabase.ensureSuperuserSchema("game");
    }

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
