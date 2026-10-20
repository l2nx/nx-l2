/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientProperties;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerProperties;

/** The GS-link secret travels in clear only over loopback: a plaintext link anywhere else refuses to start. */
final class GsLinkTransport {
    private static final Pattern SCHEME = Pattern.compile("^(static|tcp|dns):/*");

    private GsLinkTransport() {}

    static void requireLoopbackOrTls(GrpcServerProperties server) {
        if (isTls(server.getSsl().getEnabled(), server.getSsl().getBundle())) {
            return;
        }
        final InetAddress address = server.getAddress();
        if ((address == null) || !address.isLoopbackAddress()) {
            final String where = (address == null)
                    ? "is not set, so the gRPC server would listen on every interface"
                    : address.getHostAddress() + " is not a loopback address";
            throw new IllegalStateException("spring.grpc.server.address " + where
                    + ", and without TLS the GS-link secret would travel in clear: bind 127.0.0.1 or set"
                    + " spring.grpc.server.ssl.bundle.");
        }
    }

    static void requireLoopbackOrTls(String name, GrpcClientProperties.Channel channel) {
        final String prefix = "spring.grpc.client.channel." + name;
        if (channel == null) {
            throw new IllegalStateException(prefix + ".target is not set.");
        }
        if (channel.isBypassCertificateValidation()) {
            throw new IllegalStateException(prefix
                    + ".bypass-certificate-validation would accept any server; the GS-link verifies the login server.");
        }
        final String host = host(prefix, channel.getTarget());
        if (!isTls(channel.getSsl().getEnabled(), channel.getSsl().getBundle()) && !isLoopback(host)) {
            throw new IllegalStateException(prefix + ".target " + channel.getTarget()
                    + " is not a loopback address, and without TLS the GS-link secret would travel in clear: set "
                    + prefix + ".ssl.bundle.");
        }
    }

    // Spring Boot's own rule: TLS is on when enabled, or when a bundle is named and enabled is unset.
    private static boolean isTls(Boolean enabled, String bundle) {
        return Boolean.TRUE.equals(enabled) || ((enabled == null) && (bundle != null));
    }

    private static String host(String prefix, String target) {
        try {
            final String host = new URI("//" + SCHEME.matcher(target).replaceFirst(""))
                    .parseServerAuthority()
                    .getHost();
            if (host != null) {
                return host;
            }
        } catch (URISyntaxException e) {
            // Falls through to the refusal below, which names the property.
        }
        throw new IllegalStateException(
                prefix + ".target " + target + " is not a host and port, e.g. static://127.0.0.1:9014.");
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
