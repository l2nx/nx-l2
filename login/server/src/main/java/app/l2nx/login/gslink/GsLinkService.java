/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gslink;

import app.l2nx.gslink.SharedSecretServerInterceptor;
import app.l2nx.gslink.v1.GsLinkGrpc;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import app.l2nx.login.account.AccountService;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.gameserver.GameServerRegistry;
import app.l2nx.login.gameserver.GameServerTable;
import app.l2nx.login.session.LoginController;
import app.l2nx.network.config.NetworkProperties;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.grpc.server.lifecycle.GrpcServerShutdownEvent;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.scheduling.TaskScheduler;

@GrpcService(interceptors = SharedSecretServerInterceptor.class)
@RequiredArgsConstructor
public class GsLinkService extends GsLinkGrpc.GsLinkImplBase {
    private final Set<GameServerLink> links = ConcurrentHashMap.newKeySet();
    private final GameServerRegistry registry;
    private final GameServerTable gameServerTable;
    private final LoginController loginController;
    private final AccountService accountService;
    private final TaskScheduler taskScheduler;
    private final NetworkProperties network;
    private final LoginProperties properties;

    @Override
    public StreamObserver<GameServerMessage> connect(StreamObserver<LoginServerMessage> responses) {
        final GameServerLink link = new GameServerLink(
                responses,
                registry,
                gameServerTable,
                loginController,
                accountService,
                properties.showLicence(),
                links::remove);
        links.add(link);
        link.awaitRegistration(taskScheduler, network.handshakeTimeout());
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
