/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.boss.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param respawnTime the next spawn, {@code null} while the boss is alive
 */
@Builder(toBuilder = true)
public record RaidBossState(int npcId, Instant respawnTime, double currentHp, double currentMp) {}
