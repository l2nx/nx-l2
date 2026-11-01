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
package app.l2nx.highfive.data.sql;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.AutoPlayConfig;
import app.l2nx.highfive.config.custom.AutoPotionsConfig;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.AutoUseSettingsHolder;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LeaveWorld;
import app.l2nx.highfive.repository.offline.OfflinePlayRepository;
import app.l2nx.highfive.repository.offline.model.OfflinePlayEntry;
import app.l2nx.highfive.repository.offline.model.OfflinePlayEntryType;
import app.l2nx.highfive.repository.offline.model.OfflinePlayPartyMember;
import app.l2nx.highfive.taskmanagers.AutoPlayTaskManager;
import app.l2nx.highfive.taskmanagers.AutoPotionTaskManager;
import app.l2nx.highfive.taskmanagers.AutoUseTaskManager;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * @author Mobius
 */
@Slf4j
@Component
public class OfflinePlayTable {
    private static volatile OfflinePlayTable instance;

    private final OfflinePlayRepository offlinePlayRepository;

    public OfflinePlayTable(OfflinePlayRepository offlinePlayRepository) {
        this.offlinePlayRepository = offlinePlayRepository;
        instance = this;
    }

    public void restoreOfflinePlayers() {
        log.info("Loading offline auto players...");

        try {
            int nPlayers = 0;

            for (int characterId : offlinePlayRepository.findCharacterIds()) {
                Player player = null;

                try {
                    player = Player.load(characterId);
                    player.setOnlineStatus(true, false);
                    player.spawnMe(player.getX(), player.getY(), player.getZ());

                    final AutoUseSettingsHolder settings = player.getAutoUseSettings();
                    for (OfflinePlayEntry entry : offlinePlayRepository.findEntries(player.getObjectId())) {
                        final int id = entry.targetId();
                        switch (entry.type()) {
                            case ACTIVE_SOULSHOT -> player.addAutoSoulShot(id);
                            case AUTO_SUPPLY_ITEM -> settings.addAutoSupplyItem(id);
                            case AUTO_BUFF -> settings.addAutoBuff(id);
                            case AUTO_SKILL -> settings.addAutoSkill(id);
                            case AUTO_ACTION -> settings.addAutoAction(id);
                            case AUTO_POTION_ITEM -> settings.setAutoPotionItem(id);
                            case CUSTOM_AUTO_POTION_SYSTEM -> {
                                if (AutoPotionsConfig.AUTO_POTIONS_ENABLED) {
                                    AutoPotionTaskManager.getInstance().add(player);
                                }
                            }
                        }
                    }

                    player.setOfflinePlay(true);
                    player.setOnlineStatus(true, true);
                    player.restoreEffects();
                    player.setRunning();

                    if (OfflineTradeConfig.OFFLINE_SET_NAME_COLOR) {
                        player.getAppearance().setNameColor(OfflinePlayConfig.OFFLINE_PLAY_NAME_COLOR);
                    }

                    if (!OfflinePlayConfig.OFFLINE_PLAY_ABNORMAL_EFFECTS.isEmpty()) {
                        player.startAbnormalVisualEffect(
                                true,
                                OfflinePlayConfig.OFFLINE_PLAY_ABNORMAL_EFFECTS.get(ClockBridge.random()
                                        .nextInt(OfflinePlayConfig.OFFLINE_PLAY_ABNORMAL_EFFECTS.size())));
                    }

                    AutoPlayTaskManager.getInstance().startAutoPlay(player);
                    AutoUseTaskManager.getInstance().startAutoUseTask(player);

                    nPlayers++;
                } catch (Exception e) {
                    log.warn("Error loading auto player {}", player, e);
                    if (player != null) {
                        Disconnection.of(player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                    }
                }
            }

            log.info("Loaded {} offline auto players.", nPlayers);
        } catch (DataAccessException e) {
            log.warn("Error while loading offline auto players. {}", e.getMessage(), e);
        }

        if (AutoPlayConfig.ENABLE_AUTO_ASSIST) {
            try {
                int nParties = 0;

                for (int leaderId : offlinePlayRepository.findPartyLeaderIds()) {
                    final Player leader = World.getPlayer(leaderId);
                    if (leader != null) {
                        nParties++;

                        Party party = null;
                        for (OfflinePlayPartyMember partyMember : offlinePlayRepository.findPartyMembers(leaderId)) {
                            final Player member = World.getPlayer(partyMember.characterId());
                            if (member != null) {
                                if (party == null) {
                                    party = new Party(leader, partyMember.distributionType());
                                }

                                member.joinParty(party);
                            }
                        }
                    }
                }

                log.info("Loaded {} offline groups.", nParties);
            } catch (DataAccessException e) {
                log.warn("Error while restoring offline groups. {}", e.getMessage(), e);
            }
        }

        try {
            offlinePlayRepository.deleteAllPartyMembers();
        } catch (DataAccessException e) {
            log.warn("Error clearing offline group data. {}", e.getMessage(), e);
        }
    }

    public void storeOfflinePlay(Player player) {
        final List<OfflinePlayEntry> entries = new ArrayList<>();
        for (int shotId : player.getAutoSoulShot()) {
            entries.add(entry(OfflinePlayEntryType.ACTIVE_SOULSHOT, shotId));
        }

        for (int itemId : player.getAutoUseSettings().getAutoSupplyItems()) {
            entries.add(entry(OfflinePlayEntryType.AUTO_SUPPLY_ITEM, itemId));
        }

        for (int buffId : player.getAutoUseSettings().getAutoBuffs()) {
            entries.add(entry(OfflinePlayEntryType.AUTO_BUFF, buffId));
        }

        for (int skillId : player.getAutoUseSettings().getAutoSkills()) {
            entries.add(entry(OfflinePlayEntryType.AUTO_SKILL, skillId));
        }

        for (int actionId : player.getAutoUseSettings().getAutoActions()) {
            entries.add(entry(OfflinePlayEntryType.AUTO_ACTION, actionId));
        }

        final int autoPotionItem = player.getAutoUseSettings().getAutoPotionItem();
        if (autoPotionItem > 0) {
            entries.add(entry(OfflinePlayEntryType.AUTO_POTION_ITEM, autoPotionItem));
        }

        if (AutoPotionsConfig.AUTO_POTIONS_ENABLED
                && AutoPotionTaskManager.getInstance().hasPlayer(player)) {
            entries.add(entry(OfflinePlayEntryType.CUSTOM_AUTO_POTION_SYSTEM, 0));
        }

        try {
            offlinePlayRepository.replaceEntries(player.getObjectId(), entries);
        } catch (DataAccessException e) {
            log.warn("Error while saving offline auto player {}", player, e);
        }
    }

    public void storeOfflineGroups() {
        final List<OfflinePlayPartyMember> members = new ArrayList<>();
        for (Player player : World.getPlayers()) {
            if (player.isAutoPlaying()) {
                final Party party = player.getParty();
                if ((party != null) && !party.isLeader(player)) {
                    final Player leader = party.getLeader();
                    if (leader.isAutoPlaying()) {
                        members.add(OfflinePlayPartyMember.builder()
                                .leaderId(leader.getObjectId())
                                .characterId(player.getObjectId())
                                .distributionType(party.getDistributionType())
                                .build());
                    }
                }
            }
        }

        try {
            offlinePlayRepository.insertPartyMembers(members);
        } catch (DataAccessException e) {
            log.warn("Error while storing offline parties. {}", e.getMessage(), e);
        }
    }

    public void removeOfflinePlay(Player player) {
        try {
            offlinePlayRepository.deleteEntries(player.getObjectId());
        } catch (DataAccessException e) {
            log.warn("Error while removing offline auto players. {}", e.getMessage(), e);
        }
    }

    private static OfflinePlayEntry entry(OfflinePlayEntryType type, int targetId) {
        return OfflinePlayEntry.builder().type(type).targetId(targetId).build();
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static OfflinePlayTable getInstance() {
        final OfflinePlayTable current = instance;
        if (current == null) {
            throw new IllegalStateException("OfflinePlayTable is used before the Spring context created it.");
        }
        return current;
    }
}
