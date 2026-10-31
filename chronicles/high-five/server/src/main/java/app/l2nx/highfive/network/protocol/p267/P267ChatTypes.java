/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.protocol.game.p267.ChatChannel;
import java.util.EnumMap;
import java.util.Map;

public final class P267ChatTypes {
    private static final Map<ChatType, ChatChannel> CHANNELS = new EnumMap<>(ChatType.class);
    private static final Map<ChatChannel, ChatType> TYPES = new EnumMap<>(ChatChannel.class);

    static {
        CHANNELS.put(ChatType.GENERAL, ChatChannel.GENERAL);
        CHANNELS.put(ChatType.SHOUT, ChatChannel.SHOUT);
        CHANNELS.put(ChatType.WHISPER, ChatChannel.WHISPER);
        CHANNELS.put(ChatType.PARTY, ChatChannel.PARTY);
        CHANNELS.put(ChatType.CLAN, ChatChannel.CLAN);
        CHANNELS.put(ChatType.GM, ChatChannel.GM);
        CHANNELS.put(ChatType.PETITION_PLAYER, ChatChannel.PETITION_PLAYER);
        CHANNELS.put(ChatType.PETITION_GM, ChatChannel.PETITION_GM);
        CHANNELS.put(ChatType.TRADE, ChatChannel.TRADE);
        CHANNELS.put(ChatType.ALLIANCE, ChatChannel.ALLIANCE);
        CHANNELS.put(ChatType.ANNOUNCEMENT, ChatChannel.ANNOUNCEMENT);
        CHANNELS.put(ChatType.BOAT, ChatChannel.BOAT);
        CHANNELS.put(ChatType.FRIEND, ChatChannel.FRIEND);
        CHANNELS.put(ChatType.MSNCHAT, ChatChannel.MSNCHAT);
        CHANNELS.put(ChatType.PARTYMATCH_ROOM, ChatChannel.PARTYMATCH_ROOM);
        CHANNELS.put(ChatType.PARTYROOM_COMMANDER, ChatChannel.PARTYROOM_COMMANDER);
        CHANNELS.put(ChatType.PARTYROOM_ALL, ChatChannel.PARTYROOM_ALL);
        CHANNELS.put(ChatType.HERO_VOICE, ChatChannel.HERO_VOICE);
        CHANNELS.put(ChatType.CRITICAL_ANNOUNCE, ChatChannel.CRITICAL_ANNOUNCE);
        CHANNELS.put(ChatType.SCREEN_ANNOUNCE, ChatChannel.SCREEN_ANNOUNCE);
        CHANNELS.put(ChatType.BATTLEFIELD, ChatChannel.BATTLEFIELD);
        CHANNELS.put(ChatType.MPCC_ROOM, ChatChannel.MPCC_ROOM);
        CHANNELS.put(ChatType.NPC_GENERAL, ChatChannel.NPC_GENERAL);
        CHANNELS.put(ChatType.NPC_SHOUT, ChatChannel.NPC_SHOUT);
        CHANNELS.forEach((type, channel) -> TYPES.put(channel, type));
        if ((CHANNELS.size() != ChatType.values().length) || (TYPES.size() != ChatChannel.values().length)) {
            throw new IllegalStateException("Chat types and p267 channels do not pair up: " + CHANNELS);
        }
    }

    private P267ChatTypes() {}

    public static ChatChannel channel(ChatType type) {
        return CHANNELS.get(type);
    }

    /** @return the type, {@code null} for a channel code the client sent that the protocol does not have */
    public static ChatType type(ChatChannel channel) {
        return (channel == null) ? null : TYPES.get(channel);
    }
}
