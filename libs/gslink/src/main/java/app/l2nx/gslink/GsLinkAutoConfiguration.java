/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import io.grpc.ManagedChannel;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientProperties;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;

/** The GS-link settings on both servers; the login server's secret check and the game server's channel to it. */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration",
            "org.springframework.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration",
            "org.springframework.boot.grpc.client.autoconfigure.CompositeChannelFactoryAutoConfiguration"
        })
@EnableConfigurationProperties(GsLinkProperties.class)
public class GsLinkAutoConfiguration {
    /** The game server's channel to the login server under {@code spring.grpc.client.channel}. */
    public static final String LOGIN_CHANNEL = "login";

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GrpcServerProperties.class)
    @ConditionalOnBean(GrpcServerProperties.class)
    static class LoginServerSide {
        // Created before the server binds its port, so a refused address never listens.
        @Bean
        SharedSecretServerInterceptor sharedSecretServerInterceptor(
                GsLinkProperties properties, GrpcServerProperties server) {
            GsLinkTransport.requireLoopbackOrTls(server);
            return new SharedSecretServerInterceptor(properties.secret());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GrpcClientProperties.class)
    @ConditionalOnBean(GrpcChannelFactory.class)
    static class GameServerSide {
        @Bean
        ManagedChannel gsLinkChannel(
                GrpcChannelFactory channels, GrpcClientProperties clients, GsLinkProperties properties) {
            GsLinkTransport.requireLoopbackOrTls(
                    LOGIN_CHANNEL, clients.getChannel().get(LOGIN_CHANNEL));
            return channels.createChannel(
                    LOGIN_CHANNEL,
                    ChannelBuilderOptions.defaults()
                            .withInterceptors(List.of(new BearerTokenAuthenticationInterceptor(properties.secret()))));
        }
    }
}
