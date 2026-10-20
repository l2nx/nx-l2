/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gslink;

import app.l2nx.gslink.SharedSecretServerInterceptor;
import app.l2nx.gslink.v1.GsLinkGrpc;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.network.config.NetworkProperties;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.grpc.server.lifecycle.GrpcServerShutdownEvent;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.scheduling.TaskScheduler;

@GrpcService(interceptors = {SharedSecretServerInterceptor.class, RemoteAddressInterceptor.class})
public class GsLinkService extends GsLinkGrpc.GsLinkImplBase {
    private final Set<GameServerLink> links = ConcurrentHashMap.newKeySet();
    private final GameServerTable gameServerTable;
    private final LoginController loginController;
    private final TaskScheduler taskScheduler;
    private final Duration handshakeTimeout;
    private final boolean acceptNew;

    public GsLinkService(
            GameServerTable gameServerTable,
            LoginController loginController,
            TaskScheduler taskScheduler,
            NetworkProperties network,
            LoginProperties properties) {
        this.gameServerTable = gameServerTable;
        this.loginController = loginController;
        this.taskScheduler = taskScheduler;
        handshakeTimeout = network.handshakeTimeout();
        acceptNew = properties.gameServer().acceptNew();
    }

    @Override
    public StreamObserver<GameServerMessage> connect(StreamObserver<LoginServerMessage> responses) {
        final GameServerLink link = new GameServerLink(
                responses,
                RemoteAddressInterceptor.REMOTE_ADDRESS.get(),
                gameServerTable,
                loginController,
                acceptNew,
                links::remove);
        links.add(link);
        link.awaitRegistration(taskScheduler, handshakeTimeout);
        return link;
    }

    /**
     * Ends every stream on gRPC shutdown: a GS-link stream never ends by itself, so graceful shutdown would wait out
     * its grace period.
     */
    @EventListener(GrpcServerShutdownEvent.class)
    public void closeAll() {
        links.forEach(link -> link.close(Status.UNAVAILABLE.withDescription("The login server is shutting down.")));
    }
}
