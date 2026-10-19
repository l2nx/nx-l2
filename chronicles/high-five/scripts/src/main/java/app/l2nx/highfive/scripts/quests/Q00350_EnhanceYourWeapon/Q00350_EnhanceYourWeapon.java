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
package app.l2nx.highfive.scripts.quests.Q00350_EnhanceYourWeapon;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.enums.AbsorbCrystalType;
import app.l2nx.highfive.data.holders.LevelingSoulCrystalInfo;
import app.l2nx.highfive.data.holders.SoulCrystal;
import app.l2nx.highfive.data.xml.LevelUpCrystalData;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.npc.AbsorberInfo;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.mechanics.script.QuestSound;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.State;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Enhance Your Weapon (350)
 * @author Gigiikun, Skache
 */
@Slf4j
public class Q00350_EnhanceYourWeapon extends Quest {
    private static final int MIN_LEVEL = 40;

    // NPCs
    private static final int[] STARTING_NPCS = {30115, 30856, 30194};

    // Items
    private static final int RED_SOUL_CRYSTAL0_ID = 4629;
    private static final int GREEN_SOUL_CRYSTAL0_ID = 4640;
    private static final int BLUE_SOUL_CRYSTAL0_ID = 4651;

    public Q00350_EnhanceYourWeapon() {
        super(350);
        addStartNpc(STARTING_NPCS);
        addTalkId(STARTING_NPCS);

        for (int npcId : LevelUpCrystalData.getInstance().getNpcsSoulInfo().keySet()) {
            addSkillSeeId(npcId);
            addKillId(npcId);
        }
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        final String htmltext = event;
        final QuestState qs = getQuestState(player, false);
        if (event.endsWith("-04.htm")) {
            qs.startQuest();
        } else if (event.endsWith("-09.htm")) {
            giveItems(player, RED_SOUL_CRYSTAL0_ID, 1);
            playSound(player, QuestSound.ITEMSOUND_QUEST_MIDDLE);
        } else if (event.endsWith("-10.htm")) {
            giveItems(player, GREEN_SOUL_CRYSTAL0_ID, 1);
            playSound(player, QuestSound.ITEMSOUND_QUEST_MIDDLE);
        } else if (event.endsWith("-11.htm")) {
            giveItems(player, BLUE_SOUL_CRYSTAL0_ID, 1);
            playSound(player, QuestSound.ITEMSOUND_QUEST_MIDDLE);
        } else if (event.equalsIgnoreCase("exit.htm")) {
            qs.exitQuest(true, true);

            final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
            html.setFile(player, "data/scripts/quests/Q00350_EnhanceYourWeapon/exit.htm");
            html.replace("%npcname%", npc.getName());
            player.sendPacket(html);
            return null;
        }

        return htmltext;
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        if (npc.isAttackable()
                && LevelUpCrystalData.getInstance().getNpcsSoulInfo().containsKey(npc.getId())) {
            levelSoulCrystals(npc.asAttackable(), killer);
        }
    }

    @Override
    public void onSkillSee(Npc npc, Player caster, Skill skill, List<WorldObject> targets, boolean isSummon) {
        super.onSkillSee(npc, caster, skill, targets, isSummon);

        if ((skill == null) || (skill.getId() != 2096)) {
            return;
        }

        if ((caster == null) || caster.isDead()) {
            return;
        }

        if (!npc.isAttackable()
                || npc.isDead()
                || !LevelUpCrystalData.getInstance().getNpcsSoulInfo().containsKey(npc.getId())) {
            return;
        }

        try {
            npc.asAttackable().addAbsorber(caster);
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public String onTalk(Npc npc, Player player) {
        final QuestState qs = getQuestState(player, true);
        String htmltext = getNoQuestMsg(player);
        if (qs.getState() == State.CREATED) {
            if (player.getLevel() < MIN_LEVEL) {
                htmltext = npc.getId() + "-lvl.htm";
            } else {
                htmltext = npc.getId() + "-01.htm";
            }
        } else if (check(player)) {
            htmltext = npc.getId() + "-03.htm";
        } else if (!hasQuestItems(player, RED_SOUL_CRYSTAL0_ID)
                && !hasQuestItems(player, GREEN_SOUL_CRYSTAL0_ID)
                && !hasQuestItems(player, BLUE_SOUL_CRYSTAL0_ID)) {
            htmltext = npc.getId() + "-21.htm";
        }

        return htmltext;
    }

    private static boolean check(Player player) {
        for (int i = 4629; i < 4665; i++) {
            if (hasQuestItems(player, i)) {
                return true;
            }
        }

        return false;
    }

    private static void exchangeCrystal(Player player, Attackable mob, int takeId, int giveId, boolean broke) {
        Item item = player.getInventory().destroyItemByItemId(ItemProcessType.FEE, takeId, 1, player, mob);
        if (item != null) {
            // Prepare inventory update packet.
            final InventoryUpdate playerIU = new InventoryUpdate();
            playerIU.addRemovedItem(item);

            // Add new crystal to the killer's inventory.
            item = player.getInventory().addItem(ItemProcessType.REWARD, giveId, 1, player, mob);
            playerIU.addItem(item);

            // Send a sound event and text message to the player.
            if (broke) {
                player.sendPacket(
                        SystemMessageId.THE_SOUL_CRYSTAL_BROKE_BECAUSE_IT_WAS_NOT_ABLE_TO_ENDURE_THE_SOUL_ENERGY);
            } else {
                player.sendPacket(SystemMessageId.THE_SOUL_CRYSTAL_SUCCEEDED_IN_ABSORBING_A_SOUL);
            }

            // Send system message.
            final SystemMessage sms = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S1);
            sms.addItemName(giveId);
            player.sendPacket(sms);

            // Send inventory update packet.
            player.sendInventoryUpdate(playerIU);
        }
    }

    private static SoulCrystal getSCForPlayer(Player player) {
        final QuestState qs = player.getQuestState(Q00350_EnhanceYourWeapon.class.getSimpleName());
        if ((qs == null) || !qs.isStarted()) {
            return null;
        }

        SoulCrystal ret = null;
        for (Item item : player.getInventory().getItems()) {
            final int itemId = item.getId();
            if (!LevelUpCrystalData.getInstance().getSoulCrystals().containsKey(itemId)) {
                continue;
            }

            if (ret != null) {
                return null;
            }

            ret = LevelUpCrystalData.getInstance().getSoulCrystals().get(itemId);
        }

        return ret;
    }

    private static boolean isPartyLevelingMonster(int npcId) {
        for (LevelingSoulCrystalInfo info :
                LevelUpCrystalData.getInstance().getNpcSoulInfo(npcId).values()) {
            if (info.absorbCrystalType() != AbsorbCrystalType.LAST_HIT) {
                return true;
            }
        }

        return false;
    }

    private static void levelCrystal(Player player, SoulCrystal sc, Attackable mob) {
        if ((sc == null) || !LevelUpCrystalData.getInstance().getNpcsSoulInfo().containsKey(mob.getId())) {
            return;
        }

        // If the crystal level is way too high for this mob, say that we can't increase it.
        if (!LevelUpCrystalData.getInstance().getNpcsSoulInfo().get(mob.getId()).containsKey(sc.level())) {
            player.sendPacket(SystemMessageId.THE_SOUL_CRYSTAL_IS_REFUSING_TO_ABSORB_THE_SOUL);
            return;
        }

        if (getRandom(100)
                <= LevelUpCrystalData.getInstance()
                        .getNpcsSoulInfo()
                        .get(mob.getId())
                        .get(sc.level())
                        .chance()) {
            exchangeCrystal(player, mob, sc.itemId(), sc.leveledItemId(), false);
        } else {
            player.sendPacket(SystemMessageId.THE_SOUL_CRYSTAL_WAS_NOT_ABLE_TO_ABSORB_THE_SOUL);
        }
    }

    /**
     * Calculate the leveling chance of Soul Crystals based on the attacker that killed this Attackable
     * @param mob
     * @param killer The player that last killed this Attackable
     */
    public static void levelSoulCrystals(Attackable mob, Player killer) {
        // Only Players can absorb souls; reset if no killer is present.
        if (killer == null) {
            mob.resetAbsorbList();
            return;
        }

        // Map to store players and their crystals.
        final Map<Player, SoulCrystal> players = new HashMap<>();
        int maxSCLevel = 0;

        // Gather eligible players and their Soul Crystals.
        if (isPartyLevelingMonster(mob.getId()) && (killer.getParty() != null)) {
            for (Player member : killer.getParty().getMembers()) {
                if ((member == null) || (member.calculateDistance3D(killer) > PlayerConfig.ALT_PARTY_RANGE)) {
                    continue; // Skip invalid or out-of-range members.
                }

                final SoulCrystal sc = getSCForPlayer(member);
                if (sc == null) {
                    continue; // Skip if no valid Soul Crystal is present.
                }

                players.put(member, sc);
                if ((sc.level() > maxSCLevel)
                        && LevelUpCrystalData.getInstance()
                                .getNpcsSoulInfo()
                                .get(mob.getId())
                                .containsKey(sc.level())) {
                    maxSCLevel = sc.level(); // Track the highest-level Soul Crystal.
                }
            }
        } else {
            final SoulCrystal sc = getSCForPlayer(killer);
            if (sc != null) {
                players.put(killer, sc);
                if ((sc.level() > maxSCLevel)
                        && LevelUpCrystalData.getInstance()
                                .getNpcsSoulInfo()
                                .get(mob.getId())
                                .containsKey(sc.level())) {
                    maxSCLevel = sc.level();
                }
            }
        }

        // No eligible players or crystals; exit early.
        if (players.isEmpty()) {
            return;
        }

        // Get leveling info for the mob and highest-level crystal.
        final LevelingSoulCrystalInfo levelInfo = LevelUpCrystalData.getInstance()
                .getNpcsSoulInfo()
                .get(mob.getId())
                .get(maxSCLevel);
        if (levelInfo == null) {
            return; // No leveling info available for this mob.
        }

        // Handle special absorb skill checks.
        if (levelInfo.skillNeeded()) {
            if (!mob.isAbsorbed()) {
                mob.resetAbsorbList(); // Absorption not initialized.
                return;
            }

            final AbsorberInfo ai = mob.getAbsorbersList().get(killer.getObjectId());
            if ((ai == null)
                    || (ai.getObjectId() != killer.getObjectId())
                    || (ai.getAbsorbedHp() > (mob.getMaxHp() / 2.0))) {
                mob.resetAbsorbList(); // Absorption skill was misused.
                return;
            }
        }

        // Process leveling based on absorb type.
        switch (levelInfo.absorbCrystalType()) {
            case PARTY_ONE_RANDOM: {
                if (killer.getParty() != null) {
                    final Player lucky = killer.getParty()
                            .getMembers()
                            .get(getRandom(killer.getParty().getMemberCount()));
                    levelCrystal(lucky, players.get(lucky), mob);
                } else {
                    levelCrystal(killer, players.get(killer), mob);
                }
                break;
            }
            case PARTY_RANDOM: {
                if (killer.getParty() != null) {
                    final List<Player> partyMembers =
                            new ArrayList<>(killer.getParty().getMembers());
                    while ((getRandom(100) < 33) && !partyMembers.isEmpty()) {
                        final Player lucky = partyMembers.remove(getRandom(partyMembers.size()));
                        final SoulCrystal luckyData = players.get(lucky);
                        if (luckyData != null) {
                            levelCrystal(lucky, luckyData, mob);
                        }
                    }
                } else if (getRandom(100) < 33) {
                    levelCrystal(killer, players.get(killer), mob);
                }
                break;
            }
            case FULL_PARTY: {
                if (killer.getParty() != null) {
                    for (Player member : killer.getParty().getMembers()) {
                        levelCrystal(member, players.get(member), mob);
                    }
                } else {
                    levelCrystal(killer, players.get(killer), mob);
                }
                break;
            }
            case LAST_HIT: {
                levelCrystal(killer, players.get(killer), mob);
                break;
            }
        }

        // Reset absorb state after processing.
        mob.resetAbsorbList();
    }
}
