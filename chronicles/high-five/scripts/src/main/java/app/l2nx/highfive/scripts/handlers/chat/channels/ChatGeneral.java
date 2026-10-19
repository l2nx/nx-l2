/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.scripts.handlers.chat.channels;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.BlockList;
import app.l2nx.highfive.handler.IChatHandler;
import app.l2nx.highfive.handler.IVoicedCommandHandler;
import app.l2nx.highfive.handler.VoicedCommandHandler;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.serverpackets.CreatureSay;
import java.util.StringTokenizer;

/**
 * General Chat Handler.
 * @author durgus
 */
public class ChatGeneral implements IChatHandler {
    private static final ChatType[] CHAT_TYPES = {
        ChatType.GENERAL,
    };

    @Override
    public void onChat(ChatType type, Player activeChar, String paramsValue, String text) {
        boolean vcdUsed = false;
        if (text.startsWith(".")) {
            final StringTokenizer st = new StringTokenizer(text);
            final IVoicedCommandHandler vch;
            String command = "";
            String params = paramsValue;
            if (st.countTokens() > 1) {
                command = st.nextToken().substring(1);
                params = text.substring(command.length() + 2);
            } else {
                command = text.substring(1);
            }

            vch = VoicedCommandHandler.getInstance().getHandler(command);
            if (vch != null) {
                vch.onCommand(command, activeChar, params);
                vcdUsed = true;
            } else {
                vcdUsed = false;
            }
        }

        if (!vcdUsed) {
            if (activeChar.isChatBanned() && GeneralConfig.BAN_CHAT_CHANNELS.contains(type)) {
                activeChar.sendPacket(
                        SystemMessageId
                                .CHATTING_IS_CURRENTLY_PROHIBITED_IF_YOU_TRY_TO_CHAT_BEFORE_THE_PROHIBITION_IS_REMOVED_THE_PROHIBITION_TIME_WILL_INCREASE_EVEN_FURTHER);
                return;
            }

            if ((activeChar.getLevel() < GeneralConfig.MINIMUM_CHAT_LEVEL) && !activeChar.isGM()) {
                activeChar.sendMessage(
                        "Players can use general chat after Lv. " + GeneralConfig.MINIMUM_CHAT_LEVEL + ".");
                return;
            }

            final CreatureSay cs =
                    new CreatureSay(activeChar, type, activeChar.getAppearance().getVisibleName(), text);
            final CreatureSay csRandom = new CreatureSay(
                    activeChar, type, activeChar.getAppearance().getVisibleName(), ChatRandomizer.randomize(text));
            World.forEachVisibleObjectInRange(activeChar, Player.class, 1250, player -> {
                if ((player != null) && !BlockList.isBlocked(player, activeChar)) {
                    if (FactionSystemConfig.FACTION_SYSTEM_ENABLED) {
                        if (FactionSystemConfig.FACTION_SPECIFIC_CHAT) {
                            if ((activeChar.isGood() && player.isEvil()) || (activeChar.isEvil() && player.isGood())) {
                                player.sendPacket(csRandom);
                            } else {
                                player.sendPacket(cs);
                            }
                        } else {
                            player.sendPacket(cs);
                        }
                    } else {
                        player.sendPacket(cs);
                    }
                }
            });

            activeChar.sendPacket(cs);
        }
    }

    @Override
    public ChatType[] getChatTypeList() {
        return CHAT_TYPES;
    }
}
