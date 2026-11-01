/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

/** Stored by name in {@code value_transactions.type}. */
public enum ValueTransactionType {
    MAIL_SEND,
    MAIL_RECEIVE,
    MAIL_RETURN,
    MAIL_CANCEL,
    MAIL_EXPIRE,
    MAIL_CHARACTER_DELETE
}
