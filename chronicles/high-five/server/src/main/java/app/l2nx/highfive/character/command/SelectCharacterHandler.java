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
package app.l2nx.highfive.character.command;

import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.highfive.character.view.CharacterSelected;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.character.view.ServerClosed;
import app.l2nx.highfive.character.view.WorldLeft;
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
import app.l2nx.highfive.session.GameContexts;
import app.l2nx.highfive.session.LegacyNotices;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class SelectCharacterHandler implements ContextCommandHandler<GameClient, SelectCharacter> {
    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final String IP_RESTRICTION = "data/html/mods/IPRestriction.htm";
    private static final String FACTION_LIMIT = "data/html/mods/Faction/ExceededOnlineLimit.htm";

    @Override
    public ContextResolver<GameClient> context() {
        return GameContexts.LOBBY;
    }

    @Override
    public void handle(GameClient client, SelectCharacter command) {
        if (!client.getFloodProtectors().canSelectCharacter()) {
            return;
        }

        if (SecondaryAuthData.getInstance().isEnabled()
                && !client.getSecondaryAuth().isAuthed()) {
            client.getSecondaryAuth().openDialog();
            return;
        }

        // A lock that cannot be taken means a repeated packet: nothing to do.
        if (client.getPlayerLock().tryLock()) {
            try {
                // A player already set means a second select queued behind the first one.
                if (client.getPlayer() == null) {
                    select(client, command.slot());
                }
            } finally {
                client.getPlayerLock().unlock();
            }

            ACCOUNTING_LOG.info("Logged in, {}", client);
        }
    }

    private static void select(GameClient client, int slot) {
        final CharacterSummary info = client.getCharSelection(slot);
        if (info == null) {
            return;
        }

        final Player player = World.getPlayer(info.objectId());
        if (player != null) {
            if (OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS && player.isAutoPlaying()) {
                OfflinePlayTable.getInstance().removeOfflinePlay(player);
            }

            Disconnection.of(player).storeAndDelete();
        }

        if (PunishmentManager.getInstance()
                        .hasPunishment(info.objectId(), PunishmentAffect.CHARACTER, PunishmentType.BAN)
                || PunishmentManager.getInstance()
                        .hasPunishment(client.getAccountId(), PunishmentAffect.ACCOUNT, PunishmentType.BAN)
                || PunishmentManager.getInstance()
                        .hasPunishment(client.getIp(), PunishmentAffect.IP, PunishmentType.BAN)) {
            client.port().emitAndClose(ServerClosed.INSTANCE);
            return;
        }

        // A negative access level is the ban of earlier versions.
        if (info.accessLevel() < 0) {
            client.port().emitAndClose(ServerClosed.INSTANCE);
            return;
        }

        if (info.accessLevel() < 100) {
            if ((player != null)
                    && player.hasPremiumStatus()
                    && (DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP > 0)
                    && !AntiFeedManager.getInstance()
                            .tryAddPlayer(
                                    AntiFeedManager.OFFLINE_PLAY,
                                    player,
                                    DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP)) {
                LegacyNotices.htmlFile(
                        client,
                        IP_RESTRICTION,
                        Map.of(
                                "%max%",
                                String.valueOf(AntiFeedManager.getInstance()
                                        .getLimit(
                                                client,
                                                DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP))));
                return;
            } else if ((DualboxCheckConfig.DUALBOX_CHECK_MAX_PLAYERS_PER_IP > 0)
                    && !AntiFeedManager.getInstance()
                            .tryAddClient(
                                    AntiFeedManager.GAME_ID,
                                    client,
                                    DualboxCheckConfig.DUALBOX_CHECK_MAX_PLAYERS_PER_IP)) {
                LegacyNotices.htmlFile(
                        client,
                        IP_RESTRICTION,
                        Map.of(
                                "%max%",
                                String.valueOf(AntiFeedManager.getInstance()
                                        .getLimit(client, DualboxCheckConfig.DUALBOX_CHECK_MAX_PLAYERS_PER_IP))));
                return;
            }
        }

        if (FactionSystemConfig.FACTION_SYSTEM_ENABLED && FactionSystemConfig.FACTION_BALANCE_ONLINE_PLAYERS) {
            if (info.good()
                    && (World.getGoodPlayersCount()
                            >= (World.getEvilPlayersCount()
                                    + FactionSystemConfig.FACTION_BALANCE_PLAYER_EXCEED_LIMIT))) {
                LegacyNotices.htmlFile(
                        client,
                        FACTION_LIMIT,
                        Map.of(
                                "%more%", FactionSystemConfig.FACTION_GOOD_TEAM_NAME,
                                "%less%", FactionSystemConfig.FACTION_EVIL_TEAM_NAME));
                return;
            }

            if (info.evil()
                    && (World.getEvilPlayersCount()
                            >= (World.getGoodPlayersCount()
                                    + FactionSystemConfig.FACTION_BALANCE_PLAYER_EXCEED_LIMIT))) {
                LegacyNotices.htmlFile(
                        client,
                        FACTION_LIMIT,
                        Map.of(
                                "%more%", FactionSystemConfig.FACTION_EVIL_TEAM_NAME,
                                "%less%", FactionSystemConfig.FACTION_GOOD_TEAM_NAME));
                return;
            }
        }

        final Player cha = client.load(slot);
        if (cha == null) {
            return;
        }

        CharInfoTable.getInstance().addName(cha);

        // Invisible GMs must not appear for a moment on login.
        if (cha.isGM()
                && GeneralConfig.GM_STARTUP_INVISIBLE
                && AdminData.getInstance().hasAccess("admin_invisible", cha.getAccessLevel())) {
            cha.setInvisible(true);
        }

        final PlayerVariables vars = cha.getVariables();
        final String restore = vars.getString(PlayerVariables.RESTORE_LOCATION, null);
        if (restore != null) {
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
                Disconnection.of(cha).storeAndCloseWith(WorldLeft.INSTANCE);
                return;
            }
        }

        LegacyNotices.sevenSignsSky(client);
        client.port()
                .emit(CharacterSelected.of(
                        cha,
                        client.getSessionId().playOk1(),
                        GameTimeTaskManager.getInstance().getGameTime() % MINUTES_PER_DAY));
    }
}
