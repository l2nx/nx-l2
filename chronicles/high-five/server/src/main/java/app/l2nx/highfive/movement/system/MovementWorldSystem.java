/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.system;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.highfive.movement.Movement;
import app.l2nx.highfive.partition.WorldSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
class MovementWorldSystem implements WorldSystem {
    private final MovementSystemProperties properties;
    private final MovementSystems systems = new MovementSystems();

    @Override
    public void install(Loop loop) {
        systems.install(loop, properties.playerCadence(), properties.npcCadence());
        Movement.bind(systems);
    }
}
