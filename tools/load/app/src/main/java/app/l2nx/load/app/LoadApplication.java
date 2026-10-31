/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import app.l2nx.load.app.memory.InMemoryProtocol;
import app.l2nx.load.app.prometheus.PrometheusClient;
import app.l2nx.load.app.scenario.LoginStormLoadScenario;
import app.l2nx.load.protocol.p267.P267Protocol;
import app.l2nx.load.protocol.p267.P267Settings;
import java.net.InetSocketAddress;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LoadApplication {
    public static void main(String[] args) {
        // The command is done once the context has started; the exit code carries the gate verdict.
        System.exit(SpringApplication.exit(SpringApplication.run(LoadApplication.class, args)));
    }

    @Bean
    LoginStormLoadScenario loginStormLoadScenario(LoadProperties properties) {
        return new LoginStormLoadScenario(properties.loginStorm().finalState());
    }

    @Bean
    ClientProtocolFactory inMemoryProtocol(LoadProperties properties) {
        return ClientProtocolFactory.builder()
                .id(InMemoryProtocol.ID)
                .create(() -> new InMemoryProtocol(properties))
                .build();
    }

    @Bean
    ClientProtocolFactory p267Protocol(LoadProperties properties) {
        final LoadProperties.P267 p267 = properties.p267();
        return ClientProtocolFactory.builder()
                .id(P267Protocol.ID)
                .create(() -> new P267Protocol(P267Settings.builder()
                        .login(new InetSocketAddress(p267.loginHost(), p267.loginPort()))
                        .serverId(p267.serverId())
                        .connectTimeout(p267.connectTimeout())
                        .build()))
                .build();
    }

    @Bean
    PrometheusClient prometheusClient(RestClient.Builder builder, LoadProperties properties) {
        return new PrometheusClient(builder, properties.prometheus().url());
    }
}
