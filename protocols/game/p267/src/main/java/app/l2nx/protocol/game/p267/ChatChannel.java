/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267;

import app.l2nx.network.codec.CodeTable;

/** The chat channel ids of the High Five client, in client packets and server packets alike. */
public enum ChatChannel {
    GENERAL(0),
    SHOUT(1),
    WHISPER(2),
    PARTY(3),
    CLAN(4),
    GM(5),
    PETITION_PLAYER(6),
    PETITION_GM(7),
    TRADE(8),
    ALLIANCE(9),
    ANNOUNCEMENT(10),
    BOAT(11),
    FRIEND(12),
    MSNCHAT(13),
    PARTYMATCH_ROOM(14),
    PARTYROOM_COMMANDER(15),
    PARTYROOM_ALL(16),
    HERO_VOICE(17),
    CRITICAL_ANNOUNCE(18),
    SCREEN_ANNOUNCE(19),
    BATTLEFIELD(20),
    MPCC_ROOM(21),
    NPC_GENERAL(22),
    NPC_SHOUT(23);

    private static final CodeTable<ChatChannel> BY_CODE = new CodeTable<>(values(), ChatChannel::code);

    private final int code;

    ChatChannel(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * @return {@code null} when the code is unknown
     */
    public static ChatChannel of(int code) {
        return BY_CODE.of(code);
    }
}
