/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record StoredItemReuse(UUID itemId, int itemTemplateId, long reuseDelayMs, Instant reuseEndsAt) {
    public CharacterItemReuse toReuse(int itemHandle) {
        return CharacterItemReuse.builder()
                .itemObjectId(itemHandle)
                .itemTemplateId(itemTemplateId)
                .reuseDelayMs(reuseDelayMs)
                .reuseEndsAt(reuseEndsAt)
                .build();
    }
}
