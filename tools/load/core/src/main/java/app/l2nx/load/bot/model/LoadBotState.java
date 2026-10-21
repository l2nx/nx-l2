/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.model;

public enum LoadBotState {
    LOGIN_CONNECT,
    LOGIN_AUTH,
    SERVER_LIST,
    GAME_CONNECT,
    CHARACTER_SELECT,
    ENTERING,
    IN_GAME,
    CLOSING,
    STOPPED
}
