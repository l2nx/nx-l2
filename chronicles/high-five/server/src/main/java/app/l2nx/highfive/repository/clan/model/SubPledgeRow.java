/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan.model;

import lombok.Builder;

/**
 * @param leaderId 0 for none; the academy never has one
 */
@Builder(toBuilder = true)
public record SubPledgeRow(int pledgeType, String name, int leaderId) {}
