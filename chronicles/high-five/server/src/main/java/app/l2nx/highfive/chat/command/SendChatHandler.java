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
package app.l2nx.highfive.chat.command;

import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.highfive.action.view.ActionCancelled;
import app.l2nx.highfive.character.CharacterService;
import app.l2nx.highfive.character.view.WorldLeft;
import app.l2nx.highfive.chat.ChatType;
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
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.session.GameContexts;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class SendChatHandler implements ContextCommandHandler<Player, SendChat> {
    private static final Logger CHAT_LOG = LoggerFactory.getLogger("chat");

    // The High Five client caps typed chat at 105 characters (verified on retail High Five 4); a longer line is
    // forged, except with item links, which add their markup to the text.
    private static final int MAX_TEXT = 105;
    private static final int MAX_TEXT_WITH_ITEM_LINK = 500;
    // Cap for GMs, who skip the client limits; each line is copied once per receiver.
    private static final int MAX_GM_TEXT = 2048;
    private static final char ITEM_LINK = 8;

    private static final Set<String> WALKER_COMMANDS = Set.of(
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
            "FOLLOWTELEPORT");

    @Override
    public ContextResolver<Player> context() {
        return GameContexts.WORLD;
    }

    @Override
    public void handle(Player player, SendChat command) {
        ChatType chatType = command.type();
        String text = command.text();
        final String target = command.target();
        if (chatType == null) {
            log.warn("Disconnecting {}: chat on a channel the client does not have", player.getName());
            player.getPort().emit(ActionCancelled.INSTANCE);
            Disconnection.of(player).storeAndCloseWith(WorldLeft.INSTANCE);
            return;
        }

        if (text.isEmpty()) {
            log.warn("{}: sending empty text. Possible packet hack!", player.getName());
            player.getPort().emit(ActionCancelled.INSTANCE);
            Disconnection.of(player).storeAndCloseWith(WorldLeft.INSTANCE);
            return;
        }

        final boolean itemLink = text.indexOf(ITEM_LINK) >= 0;
        final int maxText = player.isGM() ? MAX_GM_TEXT : (itemLink ? MAX_TEXT_WITH_ITEM_LINK : MAX_TEXT);
        if (text.length() > maxText) {
            player.getPort().emit(MessageShown.of(MessageKey.CHAT_INPUT_SPAM_BAN));
            return;
        }

        if (WalkerBotProtectionConfig.L2WALKER_PROTECTION && (chatType == ChatType.WHISPER) && isWalkerCommand(text)) {
            PunishmentManager.handleIllegalPlayerAction(
                    player, "Client Emulator Detect: " + player + " using L2Walker.", GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        if (player.isCursedWeaponEquipped() && ((chatType == ChatType.TRADE) || (chatType == ChatType.SHOUT))) {
            player.getPort().emit(MessageShown.of(MessageKey.CANNOT_SHOUT_WITH_CURSED_WEAPON));
            return;
        }

        if (player.isChatBanned() && (text.charAt(0) != '.')) {
            if (player.getEffectList().getFirstEffect(EffectType.CHAT_BLOCK) != null) {
                player.getPort().emit(MessageShown.of(MessageKey.CHAT_BLOCKED_ILLEGAL_PROGRAM));
            } else if (GeneralConfig.BAN_CHAT_CHANNELS.contains(chatType)) {
                player.getPort().emit(MessageShown.of(MessageKey.CHAT_PROHIBITED));
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

        if ((chatType == ChatType.WHISPER)
                && (target != null)
                && (target.length() > CharacterService.MAX_NAME_LENGTH)) {
            player.getPort().emit(MessageShown.of(MessageKey.PLAYER_NOT_ONLINE));
            return;
        }

        if (GeneralConfig.LOG_CHAT) {
            if (chatType == ChatType.WHISPER) {
                CHAT_LOG.info("{} [{} to {}] {}", chatType.name(), player, target, text);
            } else {
                CHAT_LOG.info("{} [{}] {}", chatType.name(), player, text);
            }
        }

        if (itemLink && !publishLinkedItems(player, text)) {
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

        if (GeneralConfig.USE_SAY_FILTER) {
            text = filtered(text);
        }

        final IChatHandler handler = ChatHandler.getInstance().getHandler(chatType);
        if (handler != null) {
            handler.onChat(chatType, player, target, text);
        } else {
            log.info("No handler registered for ChatType: {} Player: {}", chatType, player);
        }
    }

    private static boolean isWalkerCommand(String text) {
        for (String command : WALKER_COMMANDS) {
            if (text.startsWith(command)) {
                return true;
            }
        }
        return false;
    }

    private static String filtered(String text) {
        String filteredText = text;
        for (String pattern : ServerConfig.FILTER_LIST) {
            filteredText = filteredText.replaceAll("(?i)" + pattern, GeneralConfig.CHAT_FILTER_CHARS);
        }
        return filteredText;
    }

    /** Publishes every item the text links; a link to an item the player does not own refuses the whole line. */
    private static boolean publishLinkedItems(Player owner, String text) {
        int linkStart = -1;
        while ((linkStart = text.indexOf(ITEM_LINK, linkStart)) > -1) {
            int pos = text.indexOf("ID=", linkStart);
            if (pos == -1) {
                return false;
            }

            pos += 3;
            final int digits = pos;
            while ((pos < text.length()) && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            final long parsed = (pos == digits) || ((pos - digits) > 10) ? -1 : Long.parseLong(text, digits, pos, 10);
            if ((parsed < 0) || (parsed > Integer.MAX_VALUE)) {
                return false;
            }
            final int id = (int) parsed;
            final WorldObject item = World.findObject(id);
            if (!(item instanceof Item linked)) {
                return false;
            }
            if (owner.getInventory().getItemByObjectId(id) == null) {
                log.info("{} trying publish item which does not own! ID:{}", owner.getClient(), id);
                return false;
            }
            linked.publish();

            linkStart = text.indexOf(ITEM_LINK, pos) + 1;
            if (linkStart == 0) {
                log.info("{} sent invalid publish item msg! ID:{}", owner.getClient(), id);
                return false;
            }
        }
        return true;
    }
}
