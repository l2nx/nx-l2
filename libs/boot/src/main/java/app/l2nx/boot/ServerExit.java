/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

public class ServerExit {
    private final ApplicationContext context;

    public ServerExit(ApplicationContext context) {
        this.context = context;
    }

    /**
     * Returns at once: closing the context from a pool thread would shut that pool down from inside.
     */
    public void exit(ServerExitCode code) {
        Thread.ofPlatform().name("server-exit").start(() -> System.exit(SpringApplication.exit(context, code)));
    }
}
