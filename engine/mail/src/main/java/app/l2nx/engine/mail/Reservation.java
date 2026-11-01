/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.mail.view.AttachmentSnapshot;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * @param adena the adena row the {@code adenaAmount} leaves; {@code null} when {@code adenaAmount} is 0
 * @param snapshots one per item, in the order of {@code items}; the index is the slot
 */
@Builder(toBuilder = true)
public record Reservation(
        int characterHandle,
        UUID characterId,
        List<ReservedItem> items,
        ItemRowState adena,
        long adenaAmount,
        List<AttachmentSnapshot> snapshots) {
    public Reservation {
        items = List.copyOf(items);
        snapshots = List.copyOf(snapshots);
        if (snapshots.size() != items.size()) {
            throw new IllegalArgumentException(
                    "One snapshot per item: " + items.size() + " items, " + snapshots.size() + " snapshots");
        }
        if ((adenaAmount < 0) || ((adenaAmount > 0) && (adena == null))) {
            throw new IllegalArgumentException("Adena " + adenaAmount + " reserved without a row: " + adena);
        }
    }
}
