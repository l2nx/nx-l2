/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.config.custom.WalkerBotProtectionConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.handler.ChatHandler;
import app.l2nx.highfive.handler.IChatHandler;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerChat;
import app.l2nx.highfive.mechanics.events.returns.ChatFilterReturn;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.LeaveWorld;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 1.16.2.12.2.7 $ $Date: 2005/04/11 10:06:11 $
 */
@Slf4j
public class Say2 extends ClientPacket {

    private static final Logger CHAT_LOG = LoggerFactory.getLogger("chat");

    private static final String[] WALKER_COMMAND_LIST = {
        "USESKILL",
        "USEITEM",
        "BUYITEM",
        "SELLITEM",
        "SAVEITEM",
        "LOADITEM",
        "MSG",
        "DELAY",
        "LABEL",
        "JMP",
        "CALL",
        "RETURN",
        "MOVETO",
        "NPCSEL",
        "NPCDLG",
        "DLGSEL",
        "CHARSTATUS",
        "POSOUTRANGE",
        "POSINRANGE",
        "GOHOME",
        "SAY",
        "EXIT",
        "PAUSE",
        "STRINDLG",
        "STRNOTINDLG",
        "CHANGEWAITTYPE",
        "FORCEATTACK",
        "ISMEMBER",
        "REQUESTJOINPARTY",
        "REQUESTOUTPARTY",
        "QUITPARTY",
        "MEMBERSTATUS",
        "CHARBUFFS",
        "ITEMCOUNT",
        "FOLLOWTELEPORT"
    };

    private String text;
    private int type;
    private String target;

    @Override
    protected void readImpl() {
        text = readString();
        type = readInt();
        target = (type == ChatType.WHISPER.getClientId()) ? readString() : null;
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        ChatType chatType = ChatType.findByClientId(type);
        if (chatType == null) {
            log.warn("Say2: Invalid type: {} Player : {} text: {}", type, player.getName(), text);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            Disconnection.of(player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
            return;
        }

        if (text.isEmpty()) {
            log.warn("{}: sending empty text. Possible packet hack!", player.getName());
            player.sendPacket(ActionFailed.STATIC_PACKET);
            Disconnection.of(player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
            return;
        }

        // Even though the client can handle more characters than it's current limit allows, an overflow (critical
        // error) happens if you pass a huge (1000+) message.
        // July 11, 2011 - Verified on High Five 4 official client as 105.
        // Allow higher limit if player shift some item (text is longer then).
        if (!player.isGM()
                && (((text.indexOf(8) >= 0) && (text.length() > 500))
                        || ((text.indexOf(8) < 0) && (text.length() > 105)))) {
            player.sendPacket(
                    SystemMessageId
                            .WHEN_A_USER_S_KEYBOARD_INPUT_EXCEEDS_A_CERTAIN_CUMULATIVE_SCORE_A_CHAT_BAN_WILL_BE_APPLIED_THIS_IS_DONE_TO_DISCOURAGE_SPAMMING_PLEASE_AVOID_POSTING_THE_SAME_MESSAGE_MULTIPLE_TIMES_DURING_A_SHORT_PERIOD);
            return;
        }

        if (WalkerBotProtectionConfig.L2WALKER_PROTECTION && (chatType == ChatType.WHISPER) && checkBot(text)) {
            PunishmentManager.handleIllegalPlayerAction(
                    player, "Client Emulator Detect: " + player + " using L2Walker.", GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        if (player.isCursedWeaponEquipped() && ((chatType == ChatType.TRADE) || (chatType == ChatType.SHOUT))) {
            player.sendPacket(SystemMessageId.SHOUT_AND_TRADE_CHATTING_CANNOT_BE_USED_WHILE_POSSESSING_A_CURSED_WEAPON);
            return;
        }

        if (player.isChatBanned() && (text.charAt(0) != '.')) {
            if (player.getEffectList().getFirstEffect(EffectType.CHAT_BLOCK) != null) {
                player.sendPacket(
                        SystemMessageId.YOU_HAVE_BEEN_REPORTED_AS_AN_ILLEGAL_PROGRAM_USER_SO_CHATTING_IS_NOT_ALLOWED);
            } else if (GeneralConfig.BAN_CHAT_CHANNELS.contains(chatType)) {
                player.sendPacket(SystemMessageId.CHATTING_IS_CURRENTLY_PROHIBITED);
            }
            return;
        }

        if (player.isJailed()
                && GeneralConfig.JAIL_DISABLE_CHAT
                && ((chatType == ChatType.WHISPER)
                        || (chatType == ChatType.SHOUT)
                        || (chatType == ChatType.TRADE)
                        || (chatType == ChatType.HERO_VOICE))) {
            player.sendMessage("You can not chat with players outside of the jail.");
            return;
        }

        if ((chatType == ChatType.PETITION_PLAYER) && player.isGM()) {
            chatType = ChatType.PETITION_GM;
        }

        if (GeneralConfig.LOG_CHAT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(chatType.name());
            sb.append(" [");
            sb.append(player);
            if (chatType == ChatType.WHISPER) {
                sb.append(" to ");
                sb.append(target);
                sb.append("] ");
                sb.append(text);
                CHAT_LOG.info(sb.toString());
            } else {
                sb.append("] ");
                sb.append(text);
                CHAT_LOG.info(sb.toString());
            }
        }

        if ((text.indexOf(8) >= 0) && !parseAndPublishItem(player)) {
            return;
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CHAT)) {
            final ChatFilterReturn filter = EventDispatcher.getInstance()
                    .notifyEvent(
                            OnPlayerChat.builder()
                                    .player(player)
                                    .target(World.getPlayer(target))
                                    .text(text)
                                    .chatType(chatType)
                                    .build(),
                            ChatFilterReturn.class);
            if (filter != null) {
                text = filter.getFilteredText();
            }
        }

        // Say Filter implementation
        if (GeneralConfig.USE_SAY_FILTER) {
            checkText();
        }

        final IChatHandler handler = ChatHandler.getInstance().getHandler(chatType);
        if (handler != null) {
            handler.onChat(chatType, player, target, text);
        } else {
            log.info("No handler registered for ChatType: {} Player: {}", type, player);
        }
    }

    private boolean checkBot(String text) {
        for (String botCommand : WALKER_COMMAND_LIST) {
            if (text.startsWith(botCommand)) {
                return true;
            }
        }

        return false;
    }

    private void checkText() {
        String filteredText = text;
        for (String pattern : ServerConfig.FILTER_LIST) {
            filteredText = filteredText.replaceAll("(?i)" + pattern, GeneralConfig.CHAT_FILTER_CHARS);
        }

        text = filteredText;
    }

    private boolean parseAndPublishItem(Player owner) {
        int pos1 = -1;
        while ((pos1 = text.indexOf(8, pos1)) > -1) {
            int pos = text.indexOf("ID=", pos1);
            if (pos == -1) {
                return false;
            }

            final StringBuilder result = new StringBuilder(9);
            pos += 3;
            while (Character.isDigit(text.charAt(pos))) {
                result.append(text.charAt(pos++));
            }

            final int id = Integer.parseInt(result.toString());
            final WorldObject item = World.findObject(id);
            if (item instanceof Item) {
                if (owner.getInventory().getItemByObjectId(id) == null) {
                    log.info("{} trying publish item which does not own! ID:{}", owner.getClient(), id);
                    return false;
                }

                ((Item) item).publish();
            } else {
                return false;
            }

            pos1 = text.indexOf(8, pos) + 1;
            if (pos1 == 0) // Missing ending tag.
            {
                log.info("{} sent invalid publish item msg! ID:{}", owner.getClient(), id);
                return false;
            }
        }

        return true;
    }
}
