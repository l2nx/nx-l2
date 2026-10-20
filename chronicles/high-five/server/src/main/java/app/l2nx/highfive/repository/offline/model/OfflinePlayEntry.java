/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.offline.model;

import lombok.Builder;

/**
 * @param targetId item template, skill or action id by type; {@code 0} for
 *     {@link OfflinePlayEntryType#CUSTOM_AUTO_POTION_SYSTEM}
 */
@Builder(toBuilder = true)
public record OfflinePlayEntry(OfflinePlayEntryType type, int targetId) {}
