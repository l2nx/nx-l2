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
import app.l2nx.highfive.action.view.ActionCancelled;
import app.l2nx.highfive.character.CharacterService;
import app.l2nx.highfive.character.view.RestartAnswered;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.mechanics.variables.PlayerVariables;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.GameContexts;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class RestartHandler implements ContextCommandHandler<Player, Restart> {
    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    private final CharacterService characters;

    @Override
    public ContextResolver<Player> context() {
        return GameContexts.WORLD;
    }

    @Override
    public void handle(Player player, Restart command) {
        if ((player.getActiveEnchantItemId() != Player.ID_NONE)
                || (player.getActiveEnchantAttrItemId() != Player.ID_NONE)) {
            refuse(player);
            return;
        }

        if (player.isChangingClass()) {
            log.warn("{} tried to restart during class change.", player);
            refuse(player);
            return;
        }

        if (player.isInStoreMode()) {
            player.sendMessage("Cannot restart while trading.");
            player.getPort().emit(RestartAnswered.REFUSED);
            return;
        }

        if (AttackStanceTaskManager.getInstance().hasAttackStanceTask(player)
                && !(player.isGM() && GeneralConfig.GM_RESTART_FIGHTING)) {
            player.getPort().emit(MessageShown.of(MessageKey.CANNOT_RESTART_IN_COMBAT));
            refuse(player);
            return;
        }

        if (player.isFestivalParticipant()) {
            if (SevenSignsFestival.getInstance().isFestivalInitialized()) {
                player.sendMessage("You cannot restart while you are a participant in a festival.");
                refuse(player);
                return;
            }

            final Party playerParty = player.getParty();
            if (playerParty != null) {
                playerParty.broadcastString(player.getName() + " has been removed from the upcoming festival.");
            }
        }

        if (!player.canLogout()) {
            refuse(player);
            return;
        }

        player.removeFromBossZone();

        if (OlympiadManager.getInstance().isRegistered(player)) {
            OlympiadManager.getInstance().unRegisterNoble(player);
        }

        if (!GeneralConfig.RESTORE_PLAYER_INSTANCE) {
            leaveInstance(player);
        }

        final GameClient client = player.getClient();
        if (OfflineTraderTable.getInstance().enteredOfflineMode(player)) {
            // The client is detached now: Mobius's answer and list were dropped unwritten.
            ACCOUNTING_LOG.info("Entered offline mode, {}", client);
            return;
        }

        Disconnection.of(client, player).storeAndDelete();
        // The disconnection unbinds the player from the client, so the answer goes through the client's own port.
        client.port().emit(RestartAnswered.ACCEPTED);
        characters.showCharacterList(client);
    }

    private static void refuse(Player player) {
        player.getPort().emit(RestartAnswered.REFUSED);
        player.getPort().emit(ActionCancelled.INSTANCE);
    }

    private static void leaveInstance(Player player) {
        final int instanceId = player.getInstanceId();
        if (instanceId <= 0) {
            return;
        }
        final Instance world = InstanceManager.getInstance().getInstance(instanceId);
        if (world == null) {
            return;
        }
        player.setInstanceId(0);
        Location location = world.getExitLoc();
        if (location == null) {
            location = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.TOWN);
        }

        player.getVariables()
                .set(PlayerVariables.RESTORE_LOCATION, location.getX() + ";" + location.getY() + ";" + location.getZ());
        world.removePlayer(player.getObjectId());
    }
}
