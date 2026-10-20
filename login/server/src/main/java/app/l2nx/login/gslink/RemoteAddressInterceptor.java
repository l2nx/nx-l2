/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gslink;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.net.SocketAddress;
import org.springframework.stereotype.Component;

/** Hands the game server's address to its link: a service sees only the stream, not the call's transport. */
@Component
class RemoteAddressInterceptor implements ServerInterceptor {
    static final Context.Key<SocketAddress> REMOTE_ADDRESS = Context.key("l2nx.gslink.remote-address");

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
        final Context context =
                Context.current().withValue(REMOTE_ADDRESS, call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR));
        return Contexts.interceptCall(context, call, headers, next);
    }
}
