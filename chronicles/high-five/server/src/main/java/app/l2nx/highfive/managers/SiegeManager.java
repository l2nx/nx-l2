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

import app.l2nx.highfive.config.ConfigReader;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Siege;
import app.l2nx.highfive.mechanics.siege.SiegeClanType;
import app.l2nx.highfive.mechanics.siege.TowerSpawn;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.repository.residence.CastleTrapUpgradeRepository;
import app.l2nx.highfive.repository.residence.model.CastleTrapUpgrade;
import app.l2nx.highfive.repository.siege.CastleSiegeClanRepository;
import app.l2nx.highfive.repository.siege.model.CastleSiegeClan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SiegeManager {
    private static volatile SiegeManager instance;

    private static final String SIEGE_CONFIG_FILE = "./config/Siege.ini";

    private final Map<Integer, List<TowerSpawn>> controlTowers = new HashMap<>();
    private final Map<Integer, List<TowerSpawn>> flameTowers = new HashMap<>();

    @Getter
    private int siegeCycle = 2; // 2 weeks by default.

    @Getter
    private int attackerMaxClans = 500; // Max number of clans.

    @Getter
    private int attackerRespawnDelay = 0; // Time in ms. Changeable in siege.config.

    @Getter
    private int defenderMaxClans = 500; // Max number of clans.

    @Getter
    private int flagMaxCount = 1; // Changeable in siege.config.

    @Getter
    private int siegeClanMinLevel = 5; // Changeable in siege.config.

    @Getter
    private int siegeLength = 120; // Time in minute. Changeable in siege.config.

    @Getter
    private int bloodAllianceReward = 0; // Number of Blood Alliance items reward for successful castle defending.

    private final CastleSiegeClanRepository siegeClanRepository;
    private final CastleTrapUpgradeRepository trapUpgradeRepository;

    public SiegeManager(
            CastleSiegeClanRepository siegeClanRepository, CastleTrapUpgradeRepository trapUpgradeRepository) {
        this.siegeClanRepository = siegeClanRepository;
        this.trapUpgradeRepository = trapUpgradeRepository;
        instance = this;
    }

    public void addSiegeSkills(Player character) {
        for (Skill sk : SkillData.getInstance()
                .getSiegeSkills(character.isNoble(), character.getClan().getCastleId() > 0)) {
            character.addSkill(sk, false);
        }
    }

    /**
     * @param clan The Clan of the player
     * @param castleid
     * @return true if the clan is registered or owner of a castle
     */
    public boolean checkIsRegistered(Clan clan, int castleid) {
        if (clan == null) {
            return false;
        }

        if (clan.getCastleId() > 0) {
            return true;
        }

        try {
            return siegeClanRepository.exists(castleid, clan.getId());
        } catch (DataAccessException e) {
            log.warn("Exception: checkIsRegistered(): {}", e.getMessage(), e);
            return false;
        }
    }

    public void removeSiegeSkills(Player character) {
        for (Skill sk : SkillData.getInstance()
                .getSiegeSkills(character.isNoble(), character.getClan().getCastleId() > 0)) {
            character.removeSkill(sk);
        }
    }

    public void load() {
        final ConfigReader siegeConfig = new ConfigReader(SIEGE_CONFIG_FILE);

        // Siege configurations.
        siegeCycle = siegeConfig.getInt("SiegeCycle", 2);
        attackerMaxClans = siegeConfig.getInt("AttackerMaxClans", 500);
        attackerRespawnDelay = siegeConfig.getInt("AttackerRespawn", 0);
        defenderMaxClans = siegeConfig.getInt("DefenderMaxClans", 500);
        flagMaxCount = siegeConfig.getInt("MaxFlags", 1);
        siegeClanMinLevel = siegeConfig.getInt("SiegeClanMinLevel", 5);
        siegeLength = siegeConfig.getInt("SiegeLength", 120);
        bloodAllianceReward = siegeConfig.getInt("BloodAllianceReward", 1);

        for (Castle castle : CastleManager.getInstance().getCastles()) {
            final List<TowerSpawn> controlTowers = new ArrayList<>();
            for (int i = 1; i < 0xFF; i++) {
                final String configKey = castle.getName() + "ControlTower" + i;
                if (!siegeConfig.containsKey(configKey)) {
                    break;
                }

                final StringTokenizer st = new StringTokenizer(siegeConfig.getString(configKey, ""), ",");
                try {
                    final int x = Integer.parseInt(st.nextToken());
                    final int y = Integer.parseInt(st.nextToken());
                    final int z = Integer.parseInt(st.nextToken());
                    final int npcId = Integer.parseInt(st.nextToken());

                    controlTowers.add(new TowerSpawn(npcId, new Location(x, y, z)));
                } catch (Exception e) {
                    log.warn("Error while loading control tower(s) for {} castle.", castle.getName());
                }
            }

            final List<TowerSpawn> flameTowers = new ArrayList<>();
            for (int i = 1; i < 0xFF; i++) {
                final String configKey = castle.getName() + "FlameTower" + i;
                if (!siegeConfig.containsKey(configKey)) {
                    break;
                }

                final StringTokenizer st = new StringTokenizer(siegeConfig.getString(configKey, ""), ",");
                try {
                    final int x = Integer.parseInt(st.nextToken());
                    final int y = Integer.parseInt(st.nextToken());
                    final int z = Integer.parseInt(st.nextToken());
                    final int npcId = Integer.parseInt(st.nextToken());
                    final List<Integer> zoneList = new ArrayList<>();

                    while (st.hasMoreTokens()) {
                        zoneList.add(Integer.parseInt(st.nextToken()));
                    }

                    flameTowers.add(new TowerSpawn(npcId, new Location(x, y, z), zoneList));
                } catch (Exception e) {
                    log.warn("Error while loading flame tower(s) for {} castle.", castle.getName());
                }
            }

            this.controlTowers.put(castle.getResidenceId(), controlTowers);
            this.flameTowers.put(castle.getResidenceId(), flameTowers);
            MercTicketManager.MERCS_MAX_PER_CASTLE[castle.getResidenceId() - 1] = siegeConfig.getInt(
                    castle.getName() + "MaxMercenaries",
                    MercTicketManager.MERCS_MAX_PER_CASTLE[castle.getResidenceId() - 1]);

            if (castle.getOwnerId() != 0) {
                loadTrapUpgrade(castle.getResidenceId());
            }
        }
    }

    public List<TowerSpawn> getControlTowers(int castleId) {
        return controlTowers.get(castleId);
    }

    public List<TowerSpawn> getFlameTowers(int castleId) {
        return flameTowers.get(castleId);
    }

    public Siege getSiege(ILocational loc) {
        return getSiege(loc.getX(), loc.getY(), loc.getZ());
    }

    public Siege getSiege(WorldObject activeObject) {
        return getSiege(activeObject.getX(), activeObject.getY(), activeObject.getZ());
    }

    public Siege getSiege(int x, int y, int z) {
        for (Castle castle : CastleManager.getInstance().getCastles()) {
            if (castle.getSiege().checkIfInZone(x, y, z)) {
                return castle.getSiege();
            }
        }

        return null;
    }

    public List<Siege> getSieges() {
        final List<Siege> sieges = new ArrayList<>();
        for (Castle castle : CastleManager.getInstance().getCastles()) {
            sieges.add(castle.getSiege());
        }

        return sieges;
    }

    private void loadTrapUpgrade(int castleId) {
        try {
            for (CastleTrapUpgrade upgrade : trapUpgradeRepository.findByCastle(castleId)) {
                flameTowers.get(castleId).get(upgrade.towerIndex()).setUpgradeLevel(upgrade.trapLevel());
            }
        } catch (DataAccessException e) {
            log.warn("Exception: loadTrapUpgrade(): {}", e.getMessage(), e);
        }
    }

    public void saveTrapUpgrade(int castleId, int towerIndex, int level) {
        try {
            trapUpgradeRepository.save(
                    castleId,
                    CastleTrapUpgrade.builder()
                            .towerIndex(towerIndex)
                            .trapLevel(level)
                            .build());
        } catch (DataAccessException e) {
            log.warn("Could not save trap upgrade {} of castle {}: {}", towerIndex, castleId, e.getMessage(), e);
        }
    }

    public void deleteTrapUpgrades(int castleId) {
        try {
            trapUpgradeRepository.deleteByCastle(castleId);
        } catch (DataAccessException e) {
            log.warn("Could not delete the trap upgrades of castle {}: {}", castleId, e.getMessage(), e);
        }
    }

    public List<CastleSiegeClan> findSiegeClans(int castleId) {
        try {
            return siegeClanRepository.findByCastle(castleId);
        } catch (DataAccessException e) {
            log.warn("Could not load the siege clans of castle {}: {}", castleId, e.getMessage(), e);
            return List.of();
        }
    }

    public boolean saveSiegeClan(int castleId, int clanId, SiegeClanType side) {
        try {
            siegeClanRepository.insert(castleId, clanId, side);
            return true;
        } catch (DataAccessException e) {
            log.warn("Could not register clan {} for the siege of castle {}: {}", clanId, castleId, e.getMessage(), e);
            return false;
        }
    }

    public boolean updateSiegeClanSide(int castleId, int clanId, SiegeClanType side) {
        try {
            siegeClanRepository.updateSide(castleId, clanId, side);
            return true;
        } catch (DataAccessException e) {
            log.warn("Could not move clan {} in the siege of castle {}: {}", clanId, castleId, e.getMessage(), e);
            return false;
        }
    }

    public void deleteSiegeClan(int castleId, int clanId) {
        try {
            siegeClanRepository.delete(castleId, clanId);
        } catch (DataAccessException e) {
            log.warn("Could not remove clan {} from the siege of castle {}: {}", clanId, castleId, e.getMessage(), e);
        }
    }

    public void deleteSiegeClans(int castleId, int clanId) {
        try {
            siegeClanRepository.deleteByCastle(castleId);
            if (clanId > 0) {
                siegeClanRepository.deleteByClan(clanId);
            }
        } catch (DataAccessException e) {
            log.warn("Could not clear the siege clans of castle {}: {}", castleId, e.getMessage(), e);
        }
    }

    public void deleteSiegeClans(int castleId, SiegeClanType side) {
        try {
            siegeClanRepository.deleteByCastleAndSide(castleId, side);
        } catch (DataAccessException e) {
            log.warn("Could not clear the {} clans of castle {}: {}", side, castleId, e.getMessage(), e);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static SiegeManager getInstance() {
        final SiegeManager current = instance;
        if (current == null) {
            throw new IllegalStateException("SiegeManager is used before the Spring context created it.");
        }
        return current;
    }
}
