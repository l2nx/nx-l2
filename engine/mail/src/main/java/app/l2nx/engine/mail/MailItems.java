/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import java.util.List;

/** Called on the world loop only. A reservation marks its rows pending until released or applied. */
public interface MailItems {
    List<Integer> mailable(int characterHandle);

    /** @param adena a fee on top of the items, checked against the balance left after attached adena */
    ReserveResult<Reservation> reserve(int characterHandle, List<ItemCount> items, long adena);

    void release(Reservation reservation);

    /** After the commit: changes memory as the reservation says without writing the database, and releases it. */
    void applyOutgoing(Reservation reservation);

    /**
     * @param holder inventory, or warehouse for expiring attachments; a warehouse not in memory answers targets
     *     without stacks
     * @param adena signed change of the inventory adena row: negative is paid and checked against the balance;
     *     {@code 0} for a warehouse
     */
    ReserveResult<IncomingTargets> incomingTargets(
            int characterHandle, HolderType holder, List<AttachmentSnapshot> items, long adena);

    void release(IncomingTargets targets);

    /** After the commit: puts the committed rows into memory without writing the database, and releases the targets. */
    void applyIncoming(IncomingTargets targets, List<CommittedItemRow> committedRows);

    /** The most adena a character may hold; a cash on delivery above it is malformed. */
    long adenaLimit();
}
