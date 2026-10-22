/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.highfive.GameServer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GamePortLifecycle implements SmartLifecycle {
    private final GameServer gameServer;
    private volatile boolean running;

    @Override
    public void start() {
        gameServer.openPort();
        running = true;
    }

    @Override
    public void stop() {
        try {
            gameServer.stopPort();
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return DEFAULT_PHASE;
    }
}
