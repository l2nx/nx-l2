/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.ai.system;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.highfive.partition.WorldSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
class AiWorldSystem implements WorldSystem {
    private final AiSystemProperties properties;

    @Override
    public void install(Loop loop) {
        AttackableThinkSystem.getInstance().install(loop, properties.thinkCadence());
    }
}
