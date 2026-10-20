/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
package app.l2nx.highfive.managers;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.CursedWeapon;
import app.l2nx.highfive.entity.actor.instance.Defender;
import app.l2nx.highfive.entity.actor.instance.FeedableBeast;
import app.l2nx.highfive.entity.actor.instance.FestivalMonster;
import app.l2nx.highfive.entity.actor.instance.FortCommander;
import app.l2nx.highfive.entity.actor.instance.GrandBoss;
import app.l2nx.highfive.entity.actor.instance.Guard;
import app.l2nx.highfive.entity.actor.instance.RiftInvader;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.item.CursedWeaponRepository;
import app.l2nx.highfive.repository.item.model.CursedWeaponRow;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author Micht
 */
@Slf4j
@Component
public class CursedWeaponsManager {
    private static volatile CursedWeaponsManager instance;

    private Map<Integer, CursedWeapon> cursedWeapons = new HashMap<>();

    private final CursedWeaponRepository cursedWeaponRepository;
    private final CharacterRepository characterRepository;
    private final ItemManager itemManager;
    private final TransactionTemplate transactionTemplate;

    public CursedWeaponsManager(
            CursedWeaponRepository cursedWeaponRepository,
            CharacterRepository characterRepository,
            ItemManager itemManager,
            TransactionTemplate transactionTemplate) {
        this.cursedWeaponRepository = cursedWeaponRepository;
        this.characterRepository = characterRepository;
        this.itemManager = itemManager;
        this.transactionTemplate = transactionTemplate;
        instance = this;
    }

    public void init() {
        cursedWeapons = new HashMap<>();
        if (!GeneralConfig.ALLOW_CURSED_WEAPONS) {
            return;
        }

        load();
        restore();
        controlPlayers();
        log.info("Loaded : {} cursed weapon(s).", cursedWeapons.size());
    }

    public void reload() {
        init();
    }

    private void load() {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setIgnoringComments(true);

            final File file = new File(ServerConfig.DATAPACK_ROOT + "/data/CursedWeapons.xml");
            if (!file.exists()) {
                log.warn("Couldn't find data/{}", file.getName());
                return;
            }

            for (Node n = factory.newDocumentBuilder().parse(file).getFirstChild(); n != null; n = n.getNextSibling()) {
                if ("list".equalsIgnoreCase(n.getNodeName())) {
                    for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                        if ("item".equalsIgnoreCase(d.getNodeName())) {
                            NamedNodeMap attrs = d.getAttributes();
                            final int id =
                                    Integer.parseInt(attrs.getNamedItem("id").getNodeValue());
                            final int skillId = Integer.parseInt(
                                    attrs.getNamedItem("skillId").getNodeValue());
                            final String name = attrs.getNamedItem("name").getNodeValue();
                            final CursedWeapon cw = new CursedWeapon(id, skillId, name);
                            int val;
                            for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling()) {
                                if ("dropRate".equalsIgnoreCase(cd.getNodeName())) {
                                    attrs = cd.getAttributes();
                                    val = Integer.parseInt(
                                            attrs.getNamedItem("val").getNodeValue());
                                    cw.setDropRate(val);
                                } else if ("duration".equalsIgnoreCase(cd.getNodeName())) {
                                    attrs = cd.getAttributes();
                                    val = Integer.parseInt(
                                            attrs.getNamedItem("val").getNodeValue());
                                    cw.setDuration(val);
                                } else if ("durationLost".equalsIgnoreCase(cd.getNodeName())) {
                                    attrs = cd.getAttributes();
                                    val = Integer.parseInt(
                                            attrs.getNamedItem("val").getNodeValue());
                                    cw.setDurationLost(val);
                                } else if ("disapearChance".equalsIgnoreCase(cd.getNodeName())) {
                                    attrs = cd.getAttributes();
                                    val = Integer.parseInt(
                                            attrs.getNamedItem("val").getNodeValue());
                                    cw.setDisapearChance(val);
                                } else if ("stageKills".equalsIgnoreCase(cd.getNodeName())) {
                                    attrs = cd.getAttributes();
                                    val = Integer.parseInt(
                                            attrs.getNamedItem("val").getNodeValue());
                                    cw.setStageKills(val);
                                }
                            }

                            // Store cursed weapon
                            cursedWeapons.put(id, cw);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing cursed weapons file.", e);
        }
    }

    private void restore() {
        for (CursedWeaponRow row : cursedWeaponRepository.findAll()) {
            final CursedWeapon cw = cursedWeapons.get(row.itemId());
            if (cw == null) {
                log.warn("Stored cursed weapon {} is not in CursedWeapons.xml.", row.itemId());
                continue;
            }

            cw.setPlayerId(row.playerId());
            cw.setPlayerKarma(row.playerKarma());
            cw.setPlayerPkKills(row.playerPkKills());
            cw.setNbKills(row.nbKills());
            cw.setEndTime(epochMillisOrZero(row.endTime()));
            cw.reActivate();
        }
    }

    private void controlPlayers() {
        // TODO: See comments below...
        // This entire for loop should NOT be necessary, since it is already handled by
        // CursedWeapon.endOfLife(). However, if we indeed *need* to duplicate it for safety,
        // then we'd better make sure that it FULLY cleans up inactive cursed weapons!
        // Undesired effects result otherwise, such as player with no zariche but with karma
        // or a lost-child entry in the cursed weapons table, without a corresponding one in items...

        for (CursedWeapon cw : cursedWeapons.values()) {
            if (cw.isActivated()) {
                continue;
            }

            // Do an item check to be sure that the cursed weapon isn't hold by someone
            final int itemId = cw.getItemId();
            itemManager.findAnyOwner(itemId).ifPresent(playerId -> {
                // A player has the cursed weapon in his inventory ...
                log.info("PROBLEM : Player {} owns the cursed weapon {} but he shouldn't.", playerId, itemId);
                removeFromOfflinePlayer(cw, playerId);

                // clean up the cursed weapons table.
                removeFromDb(itemId);
            });
        }
    }

    /**
     * Deletes the weapon from an offline holder and gives back the karma and PK count it had before the weapon.
     */
    public void removeFromOfflinePlayer(CursedWeapon cw, int playerId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (itemManager.deleteItems(playerId, cw.getItemId()) != 1) {
                    log.warn("Error while deleting cursed weapon {} from userId {}", cw.getItemId(), playerId);
                }

                if (!characterRepository.updateKarmaAndPkKills(playerId, cw.getPlayerKarma(), cw.getPlayerPkKills())) {
                    log.warn("Error while updating karma & pkkills for userId {}", playerId);
                }
            });
        } catch (DataAccessException e) {
            log.warn("Could not delete cursed weapon {} of {}:", cw.getItemId(), playerId, e);
        }
    }

    public synchronized void checkDrop(Attackable attackable, Player player) {
        if ((attackable instanceof Defender)
                || (attackable instanceof RiftInvader)
                || (attackable instanceof FestivalMonster)
                || (attackable instanceof Guard)
                || (attackable instanceof GrandBoss)
                || (attackable instanceof FeedableBeast)
                || (attackable instanceof FortCommander)) {
            return;
        }

        for (CursedWeapon cw : cursedWeapons.values()) {
            if (cw.isActive()) {
                continue;
            }

            if (cw.checkDrop(attackable, player)) {
                break;
            }
        }
    }

    public void activate(Player player, Item item) {
        final CursedWeapon cw = cursedWeapons.get(item.getId());
        if (player.isCursedWeaponEquipped()) // cannot own 2 cursed swords
        {
            final CursedWeapon cw2 = cursedWeapons.get(player.getCursedWeaponEquippedId());

            // TODO: give the bonus level in a more appropriate manner.
            // The following code adds "stageKills" levels. This will also show in the char status.
            // I do not have enough info to know if the bonus should be shown in the pk count, or if it
            // should be a full "stageKills" bonus or just the remaining from the current count till the of the current
            // stage...
            // This code is a TEMP fix, so that the cursed weapon's bonus level can be observed with as little change in
            // the code as possible, until proper info arises.
            cw2.setNbKills(cw2.getStageKills() - 1);
            cw2.increaseKills();

            // erase the newly obtained cursed weapon
            cw.setPlayer(player); // NECESSARY in order to find which inventory the weapon is in!
            cw.endOfLife(); // expire the weapon and clean up.
        } else {
            cw.activate(player, item);
        }
    }

    public void drop(int itemId, Creature killer) {
        cursedWeapons.get(itemId).dropIt(killer);
    }

    public void increaseKills(int itemId) {
        cursedWeapons.get(itemId).increaseKills();
    }

    public int getLevel(int itemId) {
        return cursedWeapons.get(itemId).getLevel();
    }

    public static void announce(SystemMessage sm) {
        World.broadcastToAllOnlinePlayers(sm);
    }

    public void checkPlayer(Player player) {
        if (player == null) {
            return;
        }

        for (CursedWeapon cw : cursedWeapons.values()) {
            if (cw.isActivated() && (player.getObjectId() == cw.getPlayerId())) {
                cw.setPlayer(player);
                cw.setItem(player.getInventory().getItemByItemId(cw.getItemId()));
                cw.giveSkill();
                player.setCursedWeaponEquippedId(cw.getItemId());

                final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_S2_MINUTE_S_OF_USAGE_TIME_REMAINING);
                sm.addString(cw.getName());
                // sm.addItemName(cw.getItemId());
                sm.addInt((int) ((cw.getEndTime() - ClockBridge.wallMillis()) / 60000));
                player.sendPacket(sm);
            }
        }
    }

    public int checkOwnsWeaponId(int ownerId) {
        for (CursedWeapon cw : cursedWeapons.values()) {
            if (cw.isActivated() && (ownerId == cw.getPlayerId())) {
                return cw.getItemId();
            }
        }

        return -1;
    }

    public void removeFromDb(int itemId) {
        try {
            cursedWeaponRepository.delete(itemId);
        } catch (DataAccessException e) {
            log.error("Failed to remove data: {}", e.getMessage(), e);
        }
    }

    /**
     * Stores an activated weapon and its holder, or deletes the row of a weapon that is not activated.
     */
    public void saveData(CursedWeapon cw) {
        try {
            if (cw.isActivated()) {
                cursedWeaponRepository.save(CursedWeaponRow.builder()
                        .itemId(cw.getItemId())
                        .playerId(cw.getPlayerId())
                        .playerKarma(cw.getPlayerKarma())
                        .playerPkKills(cw.getPlayerPkKills())
                        .nbKills(cw.getNbKills())
                        .endTime(instantOrNull(cw.getEndTime()))
                        .build());
            } else {
                cursedWeaponRepository.delete(cw.getItemId());
            }
        } catch (DataAccessException e) {
            log.error("Failed to save data.", e);
        }
    }

    public void saveData() {
        for (CursedWeapon cw : cursedWeapons.values()) {
            cw.saveData();
        }
    }

    public boolean isCursed(int itemId) {
        return cursedWeapons.containsKey(itemId);
    }

    public Collection<CursedWeapon> getCursedWeapons() {
        return cursedWeapons.values();
    }

    public Set<Integer> getCursedWeaponsIds() {
        return cursedWeapons.keySet();
    }

    public CursedWeapon getCursedWeapon(int itemId) {
        return cursedWeapons.get(itemId);
    }

    public void givePassive(int itemId) {
        try {
            cursedWeapons.get(itemId).giveSkill();
        } catch (Exception e) {
            /***/
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CursedWeaponsManager getInstance() {
        final CursedWeaponsManager current = instance;
        if (current == null) {
            throw new IllegalStateException("CursedWeaponsManager is used before the Spring context created it.");
        }
        return current;
    }
}
