/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world.model;

import lombok.Builder;

/**
 * @param characterName read from the character; not written
 * @param rewardType {@code 0} entry of the running period, {@code 1} unclaimed winner, {@code 2} rewarded winner
 */
@Builder(toBuilder = true)
public record FishingChampionshipEntry(int objectId, String characterName, double fishLength, int rewardType) {}
