/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world.model;

import lombok.Builder;

/**
 * @param leaderId object id of the party leader
 * @param leaderName read from the leader character; not written
 */
@Builder(toBuilder = true)
public record UndergroundColiseumBestTeam(int arenaId, int leaderId, String leaderName, int wins) {}
