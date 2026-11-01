/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.ItemMovement;
import app.l2nx.engine.core.persistence.value.ValueRefusal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Called inside a value transaction except for {@link #rows}. An online side is written with absolute values under an
 * owner condition, an offline side by delta; a failed condition throws and rolls the transaction back.
 */
public interface MailItemStore {
    Optional<Recipient> findRecipient(String name);

    Optional<Recipient> findRecipient(UUID id);

    boolean isBlocked(UUID receiverId, UUID senderId);

    /** A whole row changes its location; a part of a stack becomes a new row. */
    List<ItemMovement> toMail(UUID mailId, List<ReservedItem> items);

    /**
     * Safe on any thread, outside a transaction.
     *
     * @throws IllegalArgumentException for an unknown item template, or a count above one of a non-stackable item
     */
    void checkCreatable(List<NewItem> items);

    CreatedItems createInMail(UUID mailId, List<NewItem> items);

    /**
     * @param online stacks to merge into; {@code null} for an offline target or a holder not in memory (merged by delta)
     * @throws ValueRefusal with {@link MailRefusal#ADENA_LIMIT} when adena would exceed the limit; stacks are never
     *     split to hold the rest, so an expiry waits for room
     */
    ItemTransfer toCharacter(UUID mailId, Holder target, List<ItemRowState> online) throws ValueRefusal;

    List<ItemMovement> toMailOf(UUID fromMailId, UUID toMailId);

    /** @param online the adena row of an online holder; {@code null} for an offline holder or one without adena */
    ItemTransfer adena(Holder holder, long delta, ItemRowState online) throws ValueRefusal;

    int liveItemsInMail(UUID mailId);

    List<ItemMovement> destroyInMail(UUID mailId);

    /** Reads committed rows outside any transaction; a row no longer stored is left out. */
    List<CommittedItemRow> rows(List<UUID> itemIds);
}
