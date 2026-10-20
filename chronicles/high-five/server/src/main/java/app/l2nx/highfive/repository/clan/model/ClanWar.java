/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan.model;

import lombok.Builder;

/**
 * @param clanId the clan that declared the war
 */
@Builder(toBuilder = true)
public record ClanWar(int clanId, int enemyClanId) {}
