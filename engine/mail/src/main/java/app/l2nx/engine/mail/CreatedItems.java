/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.core.persistence.value.ItemMovement;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import java.util.List;
import lombok.Builder;

/**
 * @param snapshots one per created item, in the order it was asked for; the index is the slot
 */
@Builder(toBuilder = true)
public record CreatedItems(List<ItemMovement> movements, List<AttachmentSnapshot> snapshots) {
    public CreatedItems {
        movements = List.copyOf(movements);
        snapshots = List.copyOf(snapshots);
    }
}
