/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.boss.model;

import java.time.Instant;
import lombok.Builder;

/**
 * State of a grand boss. HP/MP are both set or both {@code null}, and so are the four position components; {@code null}
 * means "spawn at the datapack point at full HP/MP".
 *
 * @param status per-boss script constant, 0..3
 * @param respawnTime the next spawn, {@code null} when none is scheduled
 */
@Builder(toBuilder = true)
public record GrandBossState(
        int npcId,
        int status,
        Instant respawnTime,
        Double currentHp,
        Double currentMp,
        Integer x,
        Integer y,
        Integer z,
        Integer heading) {}
