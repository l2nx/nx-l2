/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SharedSecretServerInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final byte[] expected;

    public SharedSecretServerInterceptor(String secret) {
        // The form Spring gRPC's BearerTokenAuthenticationInterceptor sends from the game server.
        expected = ("Bearer " + secret).getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        final String presented = headers.get(AUTHORIZATION);
        // Same time for every content of the expected length: a wrong guess tells nothing about the secret.
        if ((presented != null) && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.US_ASCII))) {
            // The service never sees the transport: this line ties its later log lines to the caller's address.
            log.info("Accepted a GS-link call from {}.", call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR));
            return next.startCall(call, headers);
        }
        log.warn(
                "Refused a GS-link call from {}: the secret is missing or wrong.",
                call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR));
        call.close(Status.UNAUTHENTICATED.withDescription("The GS-link secret is missing or wrong."), new Metadata());
        return new ServerCall.Listener<>() {};
    }
}
