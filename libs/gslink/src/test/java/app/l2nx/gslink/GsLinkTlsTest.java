/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.gslink.test.RecordingStreamObserver;
import app.l2nx.gslink.v1.GsLinkGrpc;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.CompositeChannelFactoryAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GsLinkTlsTest {
    @TempDir
    static Path certificates;

    @BeforeAll
    static void createCertificate() throws Exception {
        final X509Bundle authority = new CertificateBuilder()
                .subject("CN=GS-link test CA")
                .setIsCertificateAuthority(true)
                .ecp256()
                .buildSelfSigned();
        final X509Bundle certificate = new CertificateBuilder()
                .subject("CN=localhost")
                .addSanDnsName("localhost")
                .ecp256()
                .buildIssuedBy(authority);
        Files.writeString(certificates.resolve("ca.crt"), authority.getCertificatePEM());
        Files.writeString(certificates.resolve("gslink.crt"), certificate.getCertificatePEM());
        Files.writeString(certificates.resolve("gslink.key"), certificate.getPrivateKeyPEM());
    }

    @Test
    void connect_shouldRegisterOverTls_whenTheCertificateNamesTheHost() throws Exception {
        runner(freePort(), "localhost").run(context -> {
            final RecordingStreamObserver<LoginServerMessage> responses = new RecordingStreamObserver<>();
            final StreamObserver<GameServerMessage> requests =
                    GsLinkGrpc.newStub(context.getBean(ManagedChannel.class)).connect(responses);

            requests.onNext(EchoLoginServer.register(1));

            assertThat(responses.next().getRegistrationResult().getServerName()).isEqualTo(EchoLoginServer.SERVER_NAME);
            requests.onCompleted();
            assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.OK);
        });
    }

    @Test
    void connect_shouldFail_whenTheCertificateDoesNotNameTheHost() throws Exception {
        // The certificate names localhost only; 127.0.0.1 is the same server under a name it does not carry.
        runner(freePort(), "127.0.0.1").run(context -> {
            final RecordingStreamObserver<LoginServerMessage> responses = new RecordingStreamObserver<>();

            GsLinkGrpc.newStub(context.getBean(ManagedChannel.class))
                    .connect(responses)
                    .onNext(EchoLoginServer.register(1));

            assertThat(responses.awaitEnd().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        });
    }

    private static ApplicationContextRunner runner(int port, String host) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SslAutoConfiguration.class,
                        GrpcServerAutoConfiguration.class,
                        GrpcClientAutoConfiguration.class,
                        CompositeChannelFactoryAutoConfiguration.class,
                        GsLinkAutoConfiguration.class))
                .withUserConfiguration(EchoLoginServer.class)
                .withPropertyValues(
                        "l2nx.gslink.secret=tls-test-secret-0123456789abcdefgh",
                        "spring.ssl.bundle.pem.gslink.keystore.certificate="
                                + certificates.resolve("gslink.crt").toUri(),
                        "spring.ssl.bundle.pem.gslink.keystore.private-key="
                                + certificates.resolve("gslink.key").toUri(),
                        "spring.ssl.bundle.pem.gslink.truststore.certificate="
                                + certificates.resolve("ca.crt").toUri(),
                        "spring.grpc.server.address=127.0.0.1",
                        "spring.grpc.server.port=" + port,
                        "spring.grpc.server.ssl.bundle=gslink",
                        "spring.grpc.client.channel.login.target=static://" + host + ":" + port,
                        "spring.grpc.client.channel.login.ssl.bundle=gslink");
    }

    // The channel bean needs the port before the server binds it, so the test picks a free one first.
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
