/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.pet.model;

import lombok.Builder;

/**
 * @param name {@code null} for a pet not named yet
 * @param restore whether the pet is summoned again when its owner logs in
 */
@Builder(toBuilder = true)
public record PetRow(
        int controlItemId,
        int ownerId,
        String name,
        int level,
        int currentHp,
        int currentMp,
        long exp,
        long sp,
        int fed,
        boolean restore) {}
