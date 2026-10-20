/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record ClanNotice(boolean enabled, String notice) {}
