/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import lombok.Builder;

/**
 * @param itemNum the number the client withdraws the item by
 */
@Builder(toBuilder = true)
public record CharacterPremiumItem(int itemNum, int itemTemplateId, long count, String senderName) {}
