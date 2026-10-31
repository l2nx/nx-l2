/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.message;

/** Messages game code shows; each protocol adapter maps a key to its client's message. */
public enum MessageKey {
    CANNOT_MOVE_TOO_ENCUMBERED,
    CANNOT_MOVE_WHILE_SPEAKING_TO_NPC,
    CANNOT_RESTART_IN_COMBAT,
    CHAT_INPUT_SPAM_BAN,
    CANNOT_SHOUT_WITH_CURSED_WEAPON,
    CHAT_BLOCKED_ILLEGAL_PROGRAM,
    CHAT_PROHIBITED,
    CHAT_PROHIBITED_EXTENDED,
    ACCOUNT_LOGGED_IN_ELSEWHERE,
    MESSAGE_REFUSAL_MODE,
    PLAYER_NOT_ONLINE,
    NOT_IN_PETITION_CHAT
}
