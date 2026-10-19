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
package app.l2nx.highfive.scripts.ai.others.ManorManager;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Merchant;
import app.l2nx.highfive.managers.CastleManorManager;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.ListenerRegisterType;
import app.l2nx.highfive.mechanics.events.annotations.Id;
import app.l2nx.highfive.mechanics.events.annotations.RegisterEvent;
import app.l2nx.highfive.mechanics.events.annotations.RegisterType;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcManorBypass;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.BuyListSeed;
import app.l2nx.highfive.network.serverpackets.ExShowCropInfo;
import app.l2nx.highfive.network.serverpackets.ExShowManorDefaultInfo;
import app.l2nx.highfive.network.serverpackets.ExShowProcureCropDetail;
import app.l2nx.highfive.network.serverpackets.ExShowSeedInfo;
import app.l2nx.highfive.network.serverpackets.ExShowSellCropList;
import app.l2nx.highfive.network.serverpackets.SystemMessage;

/**
 * Manor manager AI.
 * @author malyelfik
 */
public class ManorManager extends Script {
    private static final int[] NPC = {
        35644, 35645, 35319, 35366, 36456, 35512, 35558, 35229, 35230, 35231, 35277, 35103, 35145, 35187
    };

    public ManorManager() {
        addStartNpc(NPC);
        addFirstTalkId(NPC);
        addTalkId(NPC);
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        String htmltext = null;
        switch (event) {
            case "manager-help-01.htm":
            case "manager-help-02.htm":
            case "manager-help-03.htm": {
                htmltext = event;
                break;
            }
        }

        return htmltext;
    }

    @Override
    public String onFirstTalk(Npc npc, Player player) {
        if (GeneralConfig.ALLOW_MANOR) {
            final int castleId = npc.getTemplate().getParameters().getInt("manor_id", -1);
            if (!player.isGM()
                    && player.isClanLeader()
                    && (castleId == player.getClan().getCastleId())) {
                return "manager-lord.htm";
            }

            return "manager.htm";
        }

        return getHtm(player, "data/html/npcdefault.htm");
    }

    // @formatter:off
    @RegisterEvent(EventType.ON_NPC_MANOR_BYPASS)
    @RegisterType(ListenerRegisterType.NPC)
    @Id({35644, 35645, 35319, 35366, 36456, 35512, 35558, 35229, 35230, 35231, 35277, 35103, 35145, 35187})
    // @formatter:on
    public void onNpcManorBypass(OnNpcManorBypass event) {
        final Player player = event.getPlayer();
        if (CastleManorManager.getInstance().isUnderMaintenance()) {
            player.sendPacket(SystemMessageId.THE_MANOR_SYSTEM_IS_CURRENTLY_UNDER_MAINTENANCE);
            return;
        }

        final Npc npc = event.getTarget();
        final int templateId = npc.getTemplate().getParameters().getInt("manor_id", -1);
        final int castleId = (event.getManorId() == -1) ? templateId : event.getManorId();
        switch (event.getRequest()) {
            case 1: // Seed purchase
            {
                if (templateId != castleId) {
                    player.sendPacket(new SystemMessage(SystemMessageId.HERE_YOU_CAN_BUY_ONLY_SEEDS_OF_S1_MANOR)
                            .addCastleId(templateId));
                    return;
                }

                player.sendPacket(new BuyListSeed(player.getAdena(), castleId));
                break;
            }
            case 2: // Crop sales
            {
                player.sendPacket(new ExShowSellCropList(player.getInventory(), castleId));
                break;
            }
            case 3: // Seed info
            {
                player.sendPacket(new ExShowSeedInfo(castleId, event.isNextPeriod(), false));
                break;
            }
            case 4: // Crop info
            {
                player.sendPacket(new ExShowCropInfo(castleId, event.isNextPeriod(), false));
                break;
            }
            case 5: // Basic info
            {
                player.sendPacket(new ExShowManorDefaultInfo(false));
                break;
            }
            case 6: // Buy harvester
            {
                ((Merchant) npc).showBuyWindow(player, 300000 + npc.getId());
                break;
            }
            case 9: // Edit sales (Crop sales)
            {
                player.sendPacket(new ExShowProcureCropDetail(event.getManorId()));
                break;
            }
            default: {
                LOGGER.warning(getClass().getSimpleName() + ": " + player + " send unknown request id "
                        + event.getRequest() + "!");
            }
        }
    }

    public static void main(String[] args) {
        new ManorManager();
    }
}
