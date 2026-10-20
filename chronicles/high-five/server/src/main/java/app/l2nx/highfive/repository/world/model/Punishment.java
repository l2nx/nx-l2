/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world.model;

import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Exactly one target is set, the one {@code affect} names: {@code accountId}, {@code characterId} (object id),
 * {@code ip} or {@code hwid}.
 *
 * @param expiration {@code null} for a permanent punishment
 * @param reason {@code null} when none was given
 */
@Builder(toBuilder = true)
public record Punishment(
        long id,
        PunishmentAffect affect,
        UUID accountId,
        Integer characterId,
        String ip,
        String hwid,
        PunishmentType type,
        Instant expiration,
        String reason,
        String punishedBy) {}
