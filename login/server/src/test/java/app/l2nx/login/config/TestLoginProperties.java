/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.config;

import app.l2nx.login.protocol.LoginVariant;
import java.io.File;
import java.time.Duration;
import java.util.List;

public final class TestLoginProperties {
    private static final String LOOPBACK = "127.0.0.1";

    private TestLoginProperties() {}

    public static LoginProperties create(
            int clientPort, LoginProperties.ScheduledRestart scheduledRestart, File datapackRoot) {
        return LoginProperties.builder()
                .client(LoginProperties.Client.builder()
                        .host(LOOPBACK)
                        .ports(List.of(new LoginProperties.ClientListener(clientPort, LoginVariant.C621)))
                        .maxConcurrentTasks(4)
                        .build())
                .gameServer(LoginProperties.GameServer.builder().acceptNew(true).build())
                .ban(LoginProperties.Ban.builder()
                        .failedLogins(5)
                        .failedLoginsWindow(Duration.ofMinutes(15))
                        .duration(Duration.ofMinutes(15))
                        .build())
                .password(new LoginProperties.Password(2))
                .scheduledRestart(scheduledRestart)
                .showLicence(true)
                .autoCreateAccounts(true)
                .datapackRoot(datapackRoot)
                .build();
    }

    public static LoginProperties create(int clientPort, LoginProperties.ScheduledRestart scheduledRestart) {
        return create(clientPort, scheduledRestart, new File("."));
    }

    public static LoginProperties create(File datapackRoot) {
        return create(2106, noRestart(), datapackRoot);
    }

    public static LoginProperties.ScheduledRestart noRestart() {
        return new LoginProperties.ScheduledRestart(false, Duration.ofHours(24));
    }
}
