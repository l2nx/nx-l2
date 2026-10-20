/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.clock;

import app.l2nx.engine.core.clock.GameClock;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** Singletons are created before any lifecycle bean starts, so the bridge is bound before the engine runs. */
@Component
public class ClockBridgeBinding {
    public ClockBridgeBinding(GameClock gameClock) {
        ClockBridge.bind(gameClock, ThreadLocalRandom::current);
    }
}
