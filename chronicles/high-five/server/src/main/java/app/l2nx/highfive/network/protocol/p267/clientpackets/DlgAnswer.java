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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.config.custom.WeddingConfig;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.PlayerAction;
import app.l2nx.highfive.entity.actor.holders.creature.DoorRequestHolder;
import app.l2nx.highfive.entity.actor.holders.player.SummonRequestHolder;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.handler.AdminCommandHandler;
import app.l2nx.highfive.managers.ZoneBuildManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerDlgAnswer;
import app.l2nx.highfive.mechanics.events.returns.TerminateReturn;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LeaveWorld;

/**
 * @author Dezmond_snz
 */
public class DlgAnswer extends ClientPacket {
    private int messageId;
    private int answer;
    private int requesterId;

    @Override
    protected void readImpl() {
        messageId = readInt();
        answer = readInt();
        requesterId = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_DLG_ANSWER, player)) {
            final TerminateReturn term = EventDispatcher.getInstance()
                    .notifyEvent(
                            OnPlayerDlgAnswer.builder()
                                    .player(player)
                                    .messageId(messageId)
                                    .answer(answer)
                                    .requesterId(requesterId)
                                    .build(),
                            player,
                            TerminateReturn.class);
            if ((term != null) && term.terminate()) {
                return;
            }
        }

        if (messageId == SystemMessageId.S1_3.getId()) {
            // Custom .offline voiced command dialog.
            if (player.removeAction(PlayerAction.OFFLINE_TRADE)) {
                if ((answer == 0)
                        || !OfflineTradeConfig.ENABLE_OFFLINE_COMMAND
                        || (!OfflineTradeConfig.OFFLINE_TRADE_ENABLE && !OfflineTradeConfig.OFFLINE_CRAFT_ENABLE)) {
                    return;
                }

                if (!player.isInStoreMode()) {
                    player.sendPacket(SystemMessageId.PRIVATE_STORE_ALREADY_CLOSED);
                    player.sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                }

                if (player.isInInstance() || player.isInVehicle() || !player.canLogout()) {
                    player.sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                }

                // Remove player from boss zone.
                player.removeFromBossZone();

                // Unregister from olympiad.
                if (OlympiadManager.getInstance().isRegistered(player)) {
                    OlympiadManager.getInstance().unRegisterNoble(player);
                }

                if (!OfflineTraderTable.getInstance().enteredOfflineMode(player)) {
                    Disconnection.of(getClient(), player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                }
                return;
            }

            // Custom .offlineplay voiced command dialog.
            if (player.removeAction(PlayerAction.OFFLINE_PLAY)) {
                if ((answer == 0) || !OfflinePlayConfig.ENABLE_OFFLINE_PLAY_COMMAND) {
                    return;
                }

                if (OfflinePlayConfig.OFFLINE_PLAY_PREMIUM && !player.hasPremiumStatus()) {
                    player.sendMessage("This command is only available to premium players.");
                    return;
                }

                if (!player.isAutoPlaying()) {
                    player.sendMessage("You need to enable auto play before exiting.");
                    return;
                }

                if (player.isInVehicle() || player.isInsideZone(ZoneId.PEACE)) {
                    player.sendPacket(SystemMessageId.YOU_MAY_NOT_LOG_OUT_FROM_THIS_LOCATION);
                    return;
                }

                if (player.isRegisteredOnEvent()) {
                    player.sendMessage("Cannot use this command while registered on an event.");
                    return;
                }

                // Unregister from olympiad.
                if (OlympiadManager.getInstance().isRegistered(player)) {
                    OlympiadManager.getInstance().unRegisterNoble(player);
                }

                player.startOfflinePlay();
                return;
            }

            if (player.removeAction(PlayerAction.USER_ENGAGE)) {
                if (WeddingConfig.ALLOW_WEDDING) {
                    player.engageAnswer(answer);
                }
                return;
            }

            if (player.removeAction(PlayerAction.ADMIN_SAVE_ZONE)) {
                if (answer == 0) {
                    return;
                }

                ZoneBuildManager.getInstance().buildZone(player);
                return;
            }

            if (player.removeAction(PlayerAction.ADMIN_COMMAND)) {
                final String cmd = player.getAdminConfirmCmd();
                player.setAdminConfirmCmd(null);
                if (answer == 0) {
                    return;
                }

                // The 'useConfirm' must be disabled here, as we don't want to repeat that process.
                AdminCommandHandler.getInstance().onCommand(player, cmd, false);
            }
        } else if ((messageId
                        == SystemMessageId
                                .C1_IS_MAKING_AN_ATTEMPT_TO_RESURRECT_YOU_IF_YOU_CHOOSE_THIS_PATH_S2_EXPERIENCE_POINTS_WILL_BE_RETURNED_TO_YOU_DO_YOU_WANT_TO_BE_RESURRECTED
                                .getId())
                || (messageId
                        == SystemMessageId
                                .YOUR_CHARM_OF_COURAGE_IS_TRYING_TO_RESURRECT_YOU_WOULD_YOU_LIKE_TO_RESURRECT_NOW
                                .getId())) {
            player.reviveAnswer(answer);
        } else if (messageId == SystemMessageId.C1_WISHES_TO_SUMMON_YOU_FROM_S2_DO_YOU_ACCEPT.getId()) {
            final SummonRequestHolder holder = player.removeScript(SummonRequestHolder.class);
            if ((answer == 1) && (holder != null) && (holder.getSummoner().getObjectId() == requesterId)) {
                player.teleToLocation(holder.getLocation(), true);
            }
        } else if (messageId == SystemMessageId.WOULD_YOU_LIKE_TO_OPEN_THE_GATE.getId()) {
            final DoorRequestHolder holder = player.removeScript(DoorRequestHolder.class);
            if ((holder != null) && (holder.door() == player.getTarget()) && (answer == 1)) {
                holder.door().openMe();
            }
        } else if (messageId == SystemMessageId.WOULD_YOU_LIKE_TO_CLOSE_THE_GATE.getId()) {
            final DoorRequestHolder holder = player.removeScript(DoorRequestHolder.class);
            if ((holder != null) && (holder.door() == player.getTarget()) && (answer == 1)) {
                holder.door().closeMe();
            }
        }
    }
}
