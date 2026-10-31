/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.network.SystemMessageId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Domain message keys to High Five system message ids; a key without an id fails the class initialization. */
public final class P267Messages {
    private static final Map<MessageKey, SystemMessageId> IDS = build();

    private P267Messages() {}

    public static SystemMessageId id(MessageKey key) {
        return IDS.get(key);
    }

    private static Map<MessageKey, SystemMessageId> build() {
        final Map<MessageKey, SystemMessageId> table = new EnumMap<>(MessageKey.class);
        table.put(MessageKey.CANNOT_MOVE_TOO_ENCUMBERED, SystemMessageId.YOU_CANNOT_MOVE_YOU_ARE_TOO_ENCUMBERED);
        table.put(
                MessageKey.CANNOT_MOVE_WHILE_SPEAKING_TO_NPC,
                SystemMessageId.YOU_CANNOT_MOVE_WHILE_SPEAKING_TO_AN_NPC_ONE_MOMENT_PLEASE);
        table.put(MessageKey.CANNOT_RESTART_IN_COMBAT, SystemMessageId.YOU_CANNOT_RESTART_WHILE_IN_COMBAT);
        table.put(
                MessageKey.CHAT_INPUT_SPAM_BAN,
                SystemMessageId
                        .WHEN_A_USER_S_KEYBOARD_INPUT_EXCEEDS_A_CERTAIN_CUMULATIVE_SCORE_A_CHAT_BAN_WILL_BE_APPLIED_THIS_IS_DONE_TO_DISCOURAGE_SPAMMING_PLEASE_AVOID_POSTING_THE_SAME_MESSAGE_MULTIPLE_TIMES_DURING_A_SHORT_PERIOD);
        table.put(
                MessageKey.CANNOT_SHOUT_WITH_CURSED_WEAPON,
                SystemMessageId.SHOUT_AND_TRADE_CHATTING_CANNOT_BE_USED_WHILE_POSSESSING_A_CURSED_WEAPON);
        table.put(
                MessageKey.CHAT_BLOCKED_ILLEGAL_PROGRAM,
                SystemMessageId.YOU_HAVE_BEEN_REPORTED_AS_AN_ILLEGAL_PROGRAM_USER_SO_CHATTING_IS_NOT_ALLOWED);
        table.put(MessageKey.CHAT_PROHIBITED, SystemMessageId.CHATTING_IS_CURRENTLY_PROHIBITED);
        table.put(
                MessageKey.CHAT_PROHIBITED_EXTENDED,
                SystemMessageId
                        .CHATTING_IS_CURRENTLY_PROHIBITED_IF_YOU_TRY_TO_CHAT_BEFORE_THE_PROHIBITION_IS_REMOVED_THE_PROHIBITION_TIME_WILL_INCREASE_EVEN_FURTHER);
        table.put(
                MessageKey.ACCOUNT_LOGGED_IN_ELSEWHERE,
                SystemMessageId.ANOTHER_PERSON_HAS_LOGGED_IN_WITH_THE_SAME_ACCOUNT);
        table.put(MessageKey.MESSAGE_REFUSAL_MODE, SystemMessageId.THAT_PERSON_IS_IN_MESSAGE_REFUSAL_MODE);
        table.put(MessageKey.PLAYER_NOT_ONLINE, SystemMessageId.THAT_PLAYER_IS_NOT_ONLINE);
        table.put(MessageKey.NOT_IN_PETITION_CHAT, SystemMessageId.YOU_ARE_CURRENTLY_NOT_IN_A_PETITION_CHAT);
        for (MessageKey key : MessageKey.values()) {
            if (table.get(key) == null) {
                throw new IllegalStateException("No High Five message for " + key);
            }
        }
        return Collections.unmodifiableMap(table);
    }
}
