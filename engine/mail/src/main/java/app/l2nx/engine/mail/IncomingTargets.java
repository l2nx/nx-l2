/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.core.persistence.value.ItemMovement;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * @param stacks stacks the incoming items merge into; {@code null} when the holder is not in memory, so rows arrive by
 *     delta and nothing is applied
 * @param adena the adena row the transaction changes; {@code null} when none
 * @param newStackTemplates stackable templates the holder has no stack of; the transaction stores a new row of each
 */
@Builder(toBuilder = true)
public record IncomingTargets(
        int characterHandle,
        UUID characterId,
        List<ItemRowState> stacks,
        ItemRowState adena,
        List<Integer> newStackTemplates) {
    public IncomingTargets {
        stacks = stacks == null ? null : List.copyOf(stacks);
        newStackTemplates = newStackTemplates == null ? List.of() : List.copyOf(newStackTemplates);
    }

    public List<UUID> changedItems(List<ItemMovement> movements) {
        return movements.stream()
                .filter(movement -> (movement.holderType() != HolderType.MAIL)
                        && movement.holderId().equals(characterId))
                .map(ItemMovement::itemId)
                .distinct()
                .toList();
    }
}
