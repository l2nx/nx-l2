/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

/** What a character tried when its mail action was refused; the protocol picks the client text of that action. */
public enum MailAction {
    SEND,
    RECEIVE,
    RETURN,
    CANCEL,
    DELETE,
    READ
}
