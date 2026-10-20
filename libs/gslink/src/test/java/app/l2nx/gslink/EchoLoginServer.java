/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import app.l2nx.gslink.v1.GsLinkGrpc;
import app.l2nx.gslink.v1.GsLinkProto;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.service.GrpcService;

/** A login server that answers every message with a RegistrationResult and ends its side when the game server does. */
@Configuration(proxyBeanMethods = false)
class EchoLoginServer {
    static final String SERVER_NAME = "Bartz";

    @GrpcService(interceptors = SharedSecretServerInterceptor.class)
    BindableService gsLink() {
        return service();
    }

    static BindableService service() {
        return new GsLinkGrpc.GsLinkImplBase() {
            @Override
            public StreamObserver<GameServerMessage> connect(StreamObserver<LoginServerMessage> responses) {
                return new StreamObserver<>() {
                    @Override
                    public void onNext(GameServerMessage message) {
                        responses.onNext(LoginServerMessage.newBuilder()
                                .setRegistrationResult(GsLinkProto.RegistrationResult.newBuilder()
                                        .setServerId(message.getRegister().getServerId())
                                        .setServerName(SERVER_NAME))
                                .build());
                    }

                    @Override
                    public void onError(Throwable error) {
                        // The game server cancelled; nothing to clean up.
                    }

                    @Override
                    public void onCompleted() {
                        responses.onCompleted();
                    }
                };
            }
        };
    }

    static GameServerMessage register(int serverId) {
        return GameServerMessage.newBuilder()
                .setRegister(
                        GsLinkProto.Register.newBuilder().setServerId(serverId).setChronicle("high-five"))
                .build();
    }
}
