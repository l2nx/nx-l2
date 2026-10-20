/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * The stored base of a player character. Handles ({@code objectId}, {@code clanId}, {@code apprenticeId},
 * {@code sponsorId}) and the legacy fields {@code powerGrade} and {@code academyJoinLevel} read {@code 0} for none;
 * the {@code *EndsAt} and {@code deleteAt} instants are {@code null} for none.
 *
 * @param level the level of the base class; a subclass keeps its own in {@link CharacterSubclass}
 * @param exp the experience of the base class
 * @param sp the skill points of the base class
 * @param faction {@code null} outside a faction
 */
@Builder(toBuilder = true)
public record CharacterRow(
        int objectId,
        UUID accountId,
        String name,
        String title,
        int titleColor,
        Race race,
        Sex sex,
        int face,
        int hairStyle,
        int hairColor,
        int classId,
        int baseClassId,
        int level,
        long exp,
        long expBeforeDeath,
        long sp,
        int maxHp,
        double currentHp,
        int maxCp,
        double currentCp,
        int maxMp,
        double currentMp,
        int x,
        int y,
        int z,
        int heading,
        int karma,
        int fame,
        int pvpKills,
        int pkKills,
        int accessLevel,
        OnlineStatus onlineStatus,
        long onlineTimeSeconds,
        int newbie,
        boolean noble,
        boolean inSevenSignsDungeon,
        Faction faction,
        String language,
        int vitalityPoints,
        int deathPenaltyLevel,
        int bookmarkSlots,
        int pcCafePoints,
        int clanId,
        int clanPrivileges,
        int pledgeType,
        int powerGrade,
        int academyJoinLevel,
        int apprenticeId,
        int sponsorId,
        boolean wantsPeace,
        Instant clanJoinPenaltyEndsAt,
        Instant clanCreatePenaltyEndsAt,
        Instant deleteAt,
        Instant createdAt,
        Instant lastAccessAt) {}
