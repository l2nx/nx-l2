/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.model;

/** The lifecycle of a load bot, declared in the order it walks it. */
public enum LoadBotState {
    LOGIN_CONNECT,
    LOGIN_AUTH,
    SERVER_LIST,
    GAME_CONNECT,
    CHARACTER_SELECT,
    ENTERING,
    IN_GAME,
    CLOSING,
    STOPPED;

    public boolean inLifecycle() {
        return compareTo(IN_GAME) <= 0;
    }

    /** Not {@code LOGIN_CONNECT}: a load bot enters it before its connection opens, so stopping measures nothing. */
    public boolean canBeFinal() {
        return inLifecycle() && this != LOGIN_CONNECT;
    }
}
