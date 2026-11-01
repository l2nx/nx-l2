/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

/**
 * Decides whom the client shows as the sender; stored by name in {@code mails.type}. Every type but {@link #PLAYER} is
 * system mail, which cannot be returned or replied to.
 */
public enum MailType {
    PLAYER,
    SYSTEM,
    BIRTHDAY;

    public boolean isSystem() {
        return this != PLAYER;
    }
}
