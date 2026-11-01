/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import java.util.UUID;
import lombok.Builder;

/**
 * @param unstoredRow the whole row for an item created since its last save, which the store inserts first (idempotent
 *     on {@code itemId}); {@code null} when the row is stored
 */
@Builder(toBuilder = true)
public record ItemRowState(
        int itemHandle, UUID itemId, UUID ownerId, int itemTemplateId, long count, CommittedItemRow unstoredRow) {
    public ItemRowState withCount(long newCount) {
        return toBuilder().count(newCount).build();
    }
}
