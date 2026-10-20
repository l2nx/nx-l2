/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientProperties;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerProperties;

/** The GS-link secret travels in clear only over loopback: a plaintext link anywhere else refuses to start. */
final class GsLinkTransport {
    private static final Pattern SCHEME = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*):(.*)");
    private static final Pattern PORT = Pattern.compile("[0-9]+");

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
        final String hostAndPort = hostAndPort(target);
        if (hostAndPort != null) {
            try {
                final String host =
                        new URI("//" + hostAndPort).parseServerAuthority().getHost();
                if (host != null) {
                    return host;
                }
            } catch (URISyntaxException e) {
                // Falls through to the refusal below, which names the property.
            }
        }
        throw new IllegalStateException(
                prefix + ".target " + target + " is not a host and port, e.g. static://127.0.0.1:9014.");
    }

    /**
     * The address the channel connects to, {@code null} when the target names none. In {@code dns:} it is the path;
     * the authority names the DNS server, not the login server.
     */
    private static String hostAndPort(String target) {
        final Matcher scheme = SCHEME.matcher(target);
        if (!scheme.matches() || PORT.matcher(scheme.group(2)).matches()) {
            // A bare host:port, which gRPC resolves through DNS.
            return target;
        }
        final String rest = scheme.group(2);
        return switch (scheme.group(1)) {
            case "static", "tcp" -> rest.replaceFirst("^/*", "");
            case "dns" -> dnsPath(rest);
            default -> null;
        };
    }

    private static String dnsPath(String rest) {
        if (rest.startsWith("//")) {
            final int pathStart = rest.indexOf('/', 2);
            return (pathStart < 0) ? null : rest.substring(pathStart + 1);
        }
        return rest.startsWith("/") ? rest.substring(1) : null;
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
