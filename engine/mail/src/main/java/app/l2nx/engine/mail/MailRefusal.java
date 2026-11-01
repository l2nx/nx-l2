/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import lombok.extern.slf4j.Slf4j;

/** Why a mail action was refused; the protocol mapper turns it into the client's system message. */
@Slf4j
public enum MailRefusal {
    DISABLED,
    ATTACHMENTS_DISABLED,
    NOT_IN_PEACE_ZONE,
    IN_COMBAT,
    DEAD,
    /** Trading, a private store or enchanting, or an item involved is reserved by another transaction. */
    BUSY,
    ACCESS_DENIED,
    SEND_INTERVAL,
    NOT_ENOUGH_ADENA,
    TOO_MANY_ATTACHMENTS,
    ITEM_NOT_MAILABLE,
    COD_WITHOUT_ATTACHMENTS,
    COD_AMOUNT_INVALID,
    INVALID_TEXT,
    RECEIVER_NOT_FOUND,
    RECEIVER_SELF,
    /** The parameter is the receiver's name. */
    RECEIVER_BLOCKED,
    /** The parameter is the receiver's name. */
    RECEIVER_GM_ONLY,
    JAILED,
    RECEIVER_MAILBOX_FULL,
    SENDER_MAILBOX_FULL,
    INVENTORY_FULL,
    WEIGHT_LIMIT,
    /** The adena the character would hold after the move is above {@link MailItems#adenaLimit()}. */
    ADENA_LIMIT,
    /** The sender cannot take the cash on delivery: its adena would be above {@link MailItems#adenaLimit()}. */
    SENDER_ADENA_LIMIT,
    ALREADY_RECEIVED,
    NOT_RETURNABLE,
    HAS_ATTACHMENTS,
    /** The transaction failed or its outcome stayed unknown. */
    FAILED;

    /** The reason a mail transaction refused with; any other enum is a bug and counts as {@link #FAILED}. */
    public static MailRefusal of(Enum<?> reason) {
        if (reason instanceof MailRefusal refusal) {
            return refusal;
        }
        log.error("A mail transaction was refused for a foreign reason {}", reason);
        return FAILED;
    }
}
