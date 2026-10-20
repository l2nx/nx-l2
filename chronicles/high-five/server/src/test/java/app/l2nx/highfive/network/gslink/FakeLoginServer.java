/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.gslink;

import app.l2nx.gslink.GsLinkStreamWriter;
import app.l2nx.gslink.test.RecordingStreamObserver;
import app.l2nx.gslink.v1.GsLinkGrpc;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.Setter;

final class FakeLoginServer extends GsLinkGrpc.GsLinkImplBase {
    private final BlockingQueue<Connection> connections = new LinkedBlockingQueue<>();

    // A hung login server: it never ends its side.
    @Setter
    private volatile boolean endsItsSide = true;

    @Override
    public StreamObserver<GameServerMessage> connect(StreamObserver<LoginServerMessage> responses) {
        final GsLinkStreamWriter<LoginServerMessage> writer = new GsLinkStreamWriter<>(responses);
        final RecordingStreamObserver<GameServerMessage> received = new RecordingStreamObserver<>();
        connections.add(new Connection(writer, received));
        return new StreamObserver<>() {
            @Override
            public void onNext(GameServerMessage message) {
                received.onNext(message);
            }

            @Override
            public void onError(Throwable error) {
                writer.abandon();
                received.onError(error);
            }

            // The real login server ends its side when the game server ends its own.
            @Override
            public void onCompleted() {
                received.onCompleted();
                if (endsItsSide) {
                    writer.complete();
                }
            }
        };
    }

    Connection nextConnection() throws InterruptedException {
        final Connection connection = connections.poll(5, TimeUnit.SECONDS);
        if (connection == null) {
            throw new AssertionError("No GS-link stream within 5 s.");
        }
        return connection;
    }

    boolean hasNoMoreConnections() {
        return connections.isEmpty();
    }

    record Connection(
            GsLinkStreamWriter<LoginServerMessage> responses, RecordingStreamObserver<GameServerMessage> received) {}
}
