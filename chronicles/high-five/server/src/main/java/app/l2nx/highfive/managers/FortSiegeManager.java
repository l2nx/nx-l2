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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.siege.CombatFlag;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.FortSiege;
import app.l2nx.highfive.mechanics.siege.FortSpawnHolder;
import app.l2nx.highfive.mechanics.skill.CommonSkill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.siege.FortSiegeClanRepository;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FortSiegeManager {
    private static volatile FortSiegeManager instance;

    private static final String FORTSIEGE_CONFIG_FILE = "./config/FortSiege.ini";

    @Getter
    private int attackerMaxClans = 500; // Max number of clans.

    // Fort Siege settings
    private Map<Integer, List<FortSpawnHolder>> commanderSpawnList;
    private Map<Integer, List<CombatFlag>> flagList;
    private boolean justToTerritory = true; // Changeable in fortsiege.properties

    @Getter
    private int flagMaxCount = 1; // Changeable in fortsiege.properties

    @Getter
    private int siegeClanMinLevel = 4; // Changeable in fortsiege.properties

    @Getter
    private int siegeLength = 60; // Time in minute. Changeable in fortsiege.properties.

    @Getter
    private int countDownLength = 10; // Time in minute. Changeable in fortsiege.properties.

    @Getter
    private int suspiciousMerchantRespawnDelay = 180; // Time in minute. Changeable in fortsiege.properties.

    @Getter
    private final List<FortSiege> sieges = new ArrayList<>();

    private final FortSiegeClanRepository siegeClanRepository;

    public FortSiegeManager(FortSiegeClanRepository siegeClanRepository) {
        this.siegeClanRepository = siegeClanRepository;
        instance = this;
    }

    public void addSiegeSkills(Player character) {
        character.addSkill(CommonSkill.SEAL_OF_RULER.getSkill(), false);
        character.addSkill(CommonSkill.BUILD_HEADQUARTERS.getSkill(), false);
    }

    /**
     * @param clan The Clan of the player
     * @param fortid
     * @return true if the clan is registered or owner of a fort
     */
    public boolean checkIsRegistered(Clan clan, int fortid) {
        if (clan == null) {
            return false;
        }

        try {
            return siegeClanRepository.exists(fortid, clan.getId());
        } catch (DataAccessException e) {
            log.warn("Exception: checkIsRegistered(): {}", e.getMessage(), e);
            return false;
        }
    }

    public void removeSiegeSkills(Player character) {
        character.removeSkill(CommonSkill.SEAL_OF_RULER.getSkill());
        character.removeSkill(CommonSkill.BUILD_HEADQUARTERS.getSkill());
    }

    public void load() {
        final Properties siegeSettings = new Properties();
        final File file = new File(FORTSIEGE_CONFIG_FILE);
        try (InputStream is = new FileInputStream(file)) {
            siegeSettings.load(is);
        } catch (Exception e) {
            log.warn("Error while loading Fort Siege Manager settings!", e);
        }

        // Siege setting
        justToTerritory = Boolean.parseBoolean(siegeSettings.getProperty("JustToTerritory", "true"));
        attackerMaxClans = Integer.decode(siegeSettings.getProperty("AttackerMaxClans", "500"));
        flagMaxCount = Integer.decode(siegeSettings.getProperty("MaxFlags", "1"));
        siegeClanMinLevel = Integer.decode(siegeSettings.getProperty("SiegeClanMinLevel", "4"));
        siegeLength = Integer.decode(siegeSettings.getProperty("SiegeLength", "60"));
        countDownLength = Integer.decode(siegeSettings.getProperty("CountDownLength", "10"));
        suspiciousMerchantRespawnDelay =
                Integer.decode(siegeSettings.getProperty("SuspiciousMerchantRespawnDelay", "180"));

        // Siege spawns settings
        commanderSpawnList = new ConcurrentHashMap<>();
        flagList = new ConcurrentHashMap<>();
        for (Fort fort : FortManager.getInstance().getForts()) {
            final List<FortSpawnHolder> commanderSpawns = new ArrayList<>();
            final List<CombatFlag> flagSpawns = new ArrayList<>();
            for (int i = 1; i < 5; i++) {
                final String spawnParams =
                        siegeSettings.getProperty(fort.getName().replace(" ", "") + "Commander" + i, "");
                if (spawnParams.isEmpty()) {
                    break;
                }

                final StringTokenizer st = new StringTokenizer(spawnParams.trim(), ",");

                try {
                    final int x = Integer.parseInt(st.nextToken());
                    final int y = Integer.parseInt(st.nextToken());
                    final int z = Integer.parseInt(st.nextToken());
                    final int heading = Integer.parseInt(st.nextToken());
                    final int npcId = Integer.parseInt(st.nextToken());
                    commanderSpawns.add(new FortSpawnHolder(fort.getResidenceId(), x, y, z, heading, npcId, i));
                } catch (Exception e) {
                    log.warn("Error while loading commander(s) for {} fort.", fort.getName());
                }
            }

            commanderSpawnList.put(fort.getResidenceId(), commanderSpawns);

            for (int i = 1; i < 4; i++) {
                final String _spawnParams =
                        siegeSettings.getProperty(fort.getName().replace(" ", "") + "Flag" + i, "");
                if (_spawnParams.isEmpty()) {
                    break;
                }

                final StringTokenizer st = new StringTokenizer(_spawnParams.trim(), ",");

                try {
                    final int x = Integer.parseInt(st.nextToken());
                    final int y = Integer.parseInt(st.nextToken());
                    final int z = Integer.parseInt(st.nextToken());
                    final int flagId = Integer.parseInt(st.nextToken());
                    flagSpawns.add(new CombatFlag(fort.getResidenceId(), x, y, z, 0, flagId));
                } catch (Exception e) {
                    log.warn("Error while loading flag(s) for {} fort.", fort.getName());
                }
            }

            flagList.put(fort.getResidenceId(), flagSpawns);
        }
    }

    public List<FortSpawnHolder> getCommanderSpawnList(int fortId) {
        return commanderSpawnList.get(fortId);
    }

    public List<CombatFlag> getFlagList(int fortId) {
        return flagList.get(fortId);
    }

    public boolean canRegisterJustTerritory() {
        return justToTerritory;
    }

    public FortSiege getSiege(WorldObject activeObject) {
        return getSiege(activeObject.getX(), activeObject.getY(), activeObject.getZ());
    }

    public FortSiege getSiege(int x, int y, int z) {
        for (Fort fort : FortManager.getInstance().getForts()) {
            if (fort.getSiege().checkIfInZone(x, y, z)) {
                return fort.getSiege();
            }
        }

        return null;
    }

    public void addSiege(FortSiege fortSiege) {
        sieges.add(fortSiege);
    }

    public boolean isCombat(int itemId) {
        return itemId == 9819;
    }

    public boolean activateCombatFlag(Player player, Item item) {
        if (!checkIfCanPickup(player)) {
            return false;
        }

        final Fort fort = FortManager.getInstance().getFort(player);
        final List<CombatFlag> fcf = flagList.get(fort.getResidenceId());
        for (CombatFlag cf : fcf) {
            if (cf.getCombatFlagInstance() == item) {
                cf.activate(player, item);
            }
        }

        return true;
    }

    public boolean checkIfCanPickup(Player player) {
        if (player.isCombatFlagEquipped()) {
            return false;
        }

        if (!player.isClanLeader()) {
            player.sendMessage("The flag can only be picked up by a clan leader.");
            return false;
        }

        final Fort fort = FortManager.getInstance().getFort(player);
        if ((fort == null)
                || (fort.getResidenceId() <= 0)
                || (fort.getSiege().getAttackerClan(player.getClan()) == null)) {
            return false;
        }

        if (!fort.getSiege().isInProgress()) {
            player.sendPacket(
                    new SystemMessage(SystemMessageId.THE_FORTRESS_BATTLE_OF_S1_HAS_FINISHED).addItemName(9819));
            return false;
        }

        return true;
    }

    public void dropCombatFlag(Player player, int fortId) {
        final Fort fort = FortManager.getInstance().getFortById(fortId);
        for (CombatFlag cf : flagList.get(fort.getResidenceId())) {
            if (cf.getPlayerObjectId() == player.getObjectId()) {
                cf.dropIt();
                if (fort.getSiege().isInProgress()) {
                    cf.spawnMe();
                }
            }
        }
    }

    public List<Integer> findSiegeClanIds(int fortId) {
        try {
            return siegeClanRepository.findClanIdsByFort(fortId);
        } catch (DataAccessException e) {
            log.warn("Exception: loadSiegeClan(): {}", e.getMessage(), e);
            return List.of();
        }
    }

    public boolean saveSiegeClan(int fortId, int clanId) {
        try {
            siegeClanRepository.insert(fortId, clanId);
            return true;
        } catch (DataAccessException e) {
            log.warn("Exception: saveSiegeClan(Pledge clan): {}", e.getMessage(), e);
            return false;
        }
    }

    public void deleteSiegeClan(int fortId, int clanId) {
        try {
            siegeClanRepository.delete(fortId, clanId);
        } catch (DataAccessException e) {
            log.warn("Exception: removeSiegeClan(): {}", e.getMessage(), e);
        }
    }

    public void deleteSiegeClans(int fortId, int clanId) {
        try {
            siegeClanRepository.deleteByFort(fortId);
            if (clanId > 0) {
                siegeClanRepository.deleteByClan(clanId);
            }
        } catch (DataAccessException e) {
            log.warn("Exception: clearSiegeClan(): {}", e.getMessage(), e);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static FortSiegeManager getInstance() {
        final FortSiegeManager current = instance;
        if (current == null) {
            throw new IllegalStateException("FortSiegeManager is used before the Spring context created it.");
        }
        return current;
    }
}
