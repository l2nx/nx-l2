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

import app.l2nx.highfive.data.sql.DatabaseFactory;
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
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FortSiegeManager {

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

    protected FortSiegeManager() {
        load();
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

        boolean register = false;
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT clan_id FROM fortsiege_clans where clan_id=? and fort_id=?")) {
            ps.setInt(1, clan.getId());
            ps.setInt(2, fortid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    register = true;
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Exception: checkIsRegistered(): {}", e.getMessage(), e);
        }

        return register;
    }

    public void removeSiegeSkills(Player character) {
        character.removeSkill(CommonSkill.SEAL_OF_RULER.getSkill());
        character.removeSkill(CommonSkill.BUILD_HEADQUARTERS.getSkill());
    }

    private void load() {
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

    public static FortSiegeManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final FortSiegeManager INSTANCE = new FortSiegeManager();
    }
}
