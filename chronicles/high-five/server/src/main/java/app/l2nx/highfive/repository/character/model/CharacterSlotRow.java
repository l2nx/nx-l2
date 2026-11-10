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
 * Character-list columns by UUID; holds no wire handle.
 *
 * @param clanId {@code null} outside a clan
 * @param level the level of the base class; a subclass keeps its own in {@link CharacterSubclass}
 * @param exp the experience of the base class
 * @param sp the skill points of the base class
 * @param faction {@code null} outside a faction
 * @param deleteAt {@code null} when the character is not marked for deletion
 * @param lastAccessAt {@code null} for a character never logged in
 */
@Builder(toBuilder = true)
public record CharacterSlotRow(
        UUID id,
        UUID clanId,
        String name,
        int accessLevel,
        int classId,
        int baseClassId,
        int level,
        long exp,
        long sp,
        int maxHp,
        double currentHp,
        int maxMp,
        double currentMp,
        int karma,
        int pkKills,
        int pvpKills,
        int face,
        int hairStyle,
        int hairColor,
        Sex sex,
        Race race,
        int x,
        int y,
        int z,
        Faction faction,
        int vitalityPoints,
        Instant deleteAt,
        Instant lastAccessAt) {}
