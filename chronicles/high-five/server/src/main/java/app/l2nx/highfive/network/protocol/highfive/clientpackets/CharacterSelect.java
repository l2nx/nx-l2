/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
import app.l2nx.highfive.config.custom.DualboxCheckConfig;
import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.sql.OfflinePlayTable;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.data.xml.SecondaryAuthData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.AntiFeedManager;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerSelect;
import app.l2nx.highfive.mechanics.events.returns.TerminateReturn;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.mechanics.variables.PlayerVariables;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.holders.CharacterInfoHolder;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.CharSelected;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.LeaveWorld;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SSQInfo;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ServerClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 1.5.2.1.2.5 $ $Date: 2005/03/27 15:29:30 $
 */
public class CharacterSelect extends ClientPacket {

    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    // cd
    private int charSlot;

    @SuppressWarnings("unused")
    private int unk1; // new in C4

    @SuppressWarnings("unused")
    private int unk2; // new in C4

    @SuppressWarnings("unused")
    private int unk3; // new in C4

    @SuppressWarnings("unused")
    private int unk4; // new in C4

    @Override
    protected void readImpl() {
        charSlot = readInt();
        unk1 = readShort();
        unk2 = readInt();
        unk3 = readInt();
        unk4 = readInt();
    }

    @Override
    protected void runImpl() {
        final GameClient client = getClient();
        if (!client.getFloodProtectors().canSelectCharacter()) {
            return;
        }

        if (SecondaryAuthData.getInstance().isEnabled()
                && !client.getSecondaryAuth().isAuthed()) {
            client.getSecondaryAuth().openDialog();
            return;
        }

        // We should always be able to acquire the lock.
        // But if we can't lock then nothing should be done (i.e. repeated packet).
        if (client.getPlayerLock().tryLock()) {
            try {
                // Should always be null.
                // But if not then this is repeated packet and nothing should be done here.
                if (client.getPlayer() == null) {
                    final CharacterInfoHolder info = client.getCharSelection(charSlot);
                    if (info == null) {
                        return;
                    }

                    // Disconnect offline trader.
                    final Player player = World.getPlayer(info.getObjectId());
                    if (player != null) {
                        if (OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS && player.isAutoPlaying()) {
                            OfflinePlayTable.getInstance().removeOfflinePlay(player);
                        }

                        Disconnection.of(player).storeAndDelete();
                    }

                    // Banned?
                    if (PunishmentManager.getInstance()
                                    .hasPunishment(
                                            info.getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.BAN) //
                            || PunishmentManager.getInstance()
                                    .hasPunishment(
                                            client.getAccountId(), PunishmentAffect.ACCOUNT, PunishmentType.BAN) //
                            || PunishmentManager.getInstance()
                                    .hasPunishment(client.getIp(), PunishmentAffect.IP, PunishmentType.BAN)) {
                        client.close(ServerClose.STATIC_PACKET);
                        return;
                    }

                    // Selected character is banned (compatibility with previous versions).
                    if (info.getAccessLevel() < 0) {
                        client.close(ServerClose.STATIC_PACKET);
                        return;
                    }

                    if (client.getCharSelection(charSlot).getAccessLevel() < 100) {
                        if ((player != null)
                                && player.hasPremiumStatus()
                                && (DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP > 0)
                                && !AntiFeedManager.getInstance()
                                        .tryAddPlayer(
                                                AntiFeedManager.OFFLINE_PLAY,
                                                player,
                                                DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP)) {
                            final NpcHtmlMessage msg = new NpcHtmlMessage();
                            msg.setFile(null, "data/html/mods/IPRestriction.htm");
                            msg.replace(
                                    "%max%",
                                    String.valueOf(AntiFeedManager.getInstance()
                                            .getLimit(
                                                    client,
                                                    DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP)));
                            client.sendPacket(msg);
                            return;
                        } else if ((DualboxCheckConfig.DUALBOX_CHECK_MAX_PLAYERS_PER_IP > 0)
                                && !AntiFeedManager.getInstance()
                                        .tryAddClient(
                                                AntiFeedManager.GAME_ID,
                                                client,
                                                DualboxCheckConfig.DUALBOX_CHECK_MAX_PLAYERS_PER_IP)) {
                            final NpcHtmlMessage msg = new NpcHtmlMessage();
                            msg.setFile(null, "data/html/mods/IPRestriction.htm");
                            msg.replace(
                                    "%max%",
                                    String.valueOf(AntiFeedManager.getInstance()
                                            .getLimit(client, DualboxCheckConfig.DUALBOX_CHECK_MAX_PLAYERS_PER_IP)));
                            client.sendPacket(msg);
                            return;
                        }
                    }

                    if (FactionSystemConfig.FACTION_SYSTEM_ENABLED
                            && FactionSystemConfig.FACTION_BALANCE_ONLINE_PLAYERS) {
                        if (info.isGood()
                                && (World.getAllGoodPlayers().size()
                                        >= (World.getAllEvilPlayers().size()
                                                + FactionSystemConfig.FACTION_BALANCE_PLAYER_EXCEED_LIMIT))) {
                            final NpcHtmlMessage msg = new NpcHtmlMessage();
                            msg.setFile(null, "data/html/mods/Faction/ExceededOnlineLimit.htm");
                            msg.replace("%more%", FactionSystemConfig.FACTION_GOOD_TEAM_NAME);
                            msg.replace("%less%", FactionSystemConfig.FACTION_EVIL_TEAM_NAME);
                            client.sendPacket(msg);
                            return;
                        }

                        if (info.isEvil()
                                && (World.getAllEvilPlayers().size()
                                        >= (World.getAllGoodPlayers().size()
                                                + FactionSystemConfig.FACTION_BALANCE_PLAYER_EXCEED_LIMIT))) {
                            final NpcHtmlMessage msg = new NpcHtmlMessage();
                            msg.setFile(null, "data/html/mods/Faction/ExceededOnlineLimit.htm");
                            msg.replace("%more%", FactionSystemConfig.FACTION_EVIL_TEAM_NAME);
                            msg.replace("%less%", FactionSystemConfig.FACTION_GOOD_TEAM_NAME);
                            client.sendPacket(msg);
                            return;
                        }
                    }

                    // Load up character from disk.
                    final Player cha = client.load(charSlot);
                    if (cha == null) {
                        return; // handled in GameClient
                    }

                    CharInfoTable.getInstance().addName(cha);

                    // Prevent instant disappear of invisible GMs on login.
                    if (cha.isGM()
                            && GeneralConfig.GM_STARTUP_INVISIBLE
                            && AdminData.getInstance().hasAccess("admin_invisible", cha.getAccessLevel())) {
                        cha.setInvisible(true);
                    }

                    // Restore player location.
                    final PlayerVariables vars = cha.getVariables();
                    final String restore = vars.getString(PlayerVariables.RESTORE_LOCATION, "");
                    if (!restore.isEmpty()) {
                        final String[] split = restore.split(";");
                        cha.setXYZ(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                        vars.remove(PlayerVariables.RESTORE_LOCATION);
                    }

                    cha.setClient(client);
                    client.setPlayer(cha);
                    cha.setOnlineStatus(true, true);

                    if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_SELECT, Containers.Players())) {
                        final TerminateReturn terminate = EventDispatcher.getInstance()
                                .notifyEvent(
                                        OnPlayerSelect.builder()
                                                .player(cha)
                                                .objectId(cha.getObjectId())
                                                .name(cha.getName())
                                                .client(client)
                                                .build(),
                                        Containers.Players(),
                                        TerminateReturn.class);
                        if ((terminate != null) && terminate.terminate()) {
                            Disconnection.of(cha).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                            return;
                        }
                    }

                    client.sendPacket(new SSQInfo());
                    client.sendPacket(
                            new CharSelected(cha, client.getSessionId().playOk1()));
                }
            } finally {
                client.getPlayerLock().unlock();
            }

            ACCOUNTING_LOG.info("Logged in, {}", client);
        }
    }
}
