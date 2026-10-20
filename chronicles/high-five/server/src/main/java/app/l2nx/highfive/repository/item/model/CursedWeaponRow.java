/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item.model;

import java.time.Instant;
import lombok.Builder;

/**
 * An activated cursed weapon and its holder's karma and PK count from before the weapon.
 */
@Builder(toBuilder = true)
public record CursedWeaponRow(
        int itemId, int playerId, int playerKarma, int playerPkKills, int nbKills, Instant endTime) {}
