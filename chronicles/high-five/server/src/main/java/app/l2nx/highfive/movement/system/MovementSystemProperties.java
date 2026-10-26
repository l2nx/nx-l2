/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.system;

import app.l2nx.engine.core.loop.Durations;
import java.time.Duration;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Builder(toBuilder = true)
@ConfigurationProperties("l2nx.movement")
public record MovementSystemProperties(
        @DefaultValue("50ms") Duration playerCadence,
        @DefaultValue("100ms") Duration npcCadence) {
    public MovementSystemProperties {
        Durations.requireWholeMillis("l2nx.movement.player-cadence", playerCadence);
        Durations.requireWholeMillis("l2nx.movement.npc-cadence", npcCadence);
    }
}
