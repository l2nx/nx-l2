/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import lombok.Builder;

/**
 * @param level the level of the base class
 * @param powerGrade {@code 0} when not assigned
 * @param apprenticeId the apprentice handle, {@code 0} for none
 * @param sponsorId the sponsor handle, {@code 0} for none
 */
@Builder(toBuilder = true)
public record CharacterClanMember(
        int objectId,
        String name,
        int level,
        int classId,
        String title,
        int powerGrade,
        int pledgeType,
        int apprenticeId,
        int sponsorId,
        Sex sex,
        Race race) {}
