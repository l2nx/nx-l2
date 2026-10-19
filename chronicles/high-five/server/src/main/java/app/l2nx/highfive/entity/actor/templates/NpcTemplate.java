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
package app.l2nx.highfive.entity.actor.templates;

import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.config.custom.ChampionMonstersConfig;
import app.l2nx.highfive.config.custom.NpcStatMultipliersConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.npc.AISkillScope;
import app.l2nx.highfive.entity.actor.enums.npc.AIType;
import app.l2nx.highfive.entity.actor.enums.npc.DropType;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.entity.actor.holders.npc.DropGroupHolder;
import app.l2nx.highfive.entity.actor.holders.npc.DropHolder;
import app.l2nx.highfive.entity.actor.holders.npc.FakePlayerHolder;
import app.l2nx.highfive.entity.actor.stat.PlayerStat;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.MathUtil;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author NosBit, Nobius
 */
@Slf4j
public class NpcTemplate extends CreatureTemplate {

    @Getter
    private int id;

    @Getter
    private int displayId;

    @Getter
    private byte level;

    @Getter
    private String type;

    @Getter
    private String name;

    @Getter
    private boolean usingServerSideName;

    @Getter
    private String title;

    @Getter
    private boolean usingServerSideTitle;

    @Getter
    @Setter
    private StatSet parameters;

    @Getter
    private Sex sex;

    @Getter
    private int chestId;

    @Getter
    private int rHandId;

    @Getter
    private int lHandId;

    @Getter
    private int weaponEnchant;

    @Getter
    private double exp;

    private double sp;

    @Getter
    private double raidPoints;

    @Getter
    private boolean unique;

    @Getter
    private boolean attackable;

    @Getter
    private boolean targetable;

    @Getter
    private boolean talkable;

    @Getter
    private boolean questMonster;

    @Getter
    private boolean undying;

    @Getter
    private boolean showName;

    private boolean randomWalk;

    @Getter
    private boolean randomAnimationEnabled;

    @Getter
    private boolean flying;

    @Getter
    private boolean fakePlayer;

    @Getter
    private FakePlayerHolder fakePlayerInfo;

    private boolean canMove;

    @Getter
    private boolean noSleepMode;

    @Getter
    private boolean passableDoor;

    private boolean hasSummoner;
    private boolean canBeSown;

    @Getter
    private int corpseTime;

    private AIType aiType;

    @Getter
    private int aggroRange;

    @Getter
    private int clanHelpRange;

    @Getter
    private boolean chaos;

    @Getter
    private boolean aggressive;

    @Getter
    private int soulShot;

    @Getter
    private int spiritShot;

    @Getter
    private int soulShotChance;

    @Getter
    private int spiritShotChance;

    @Getter
    private int minSkillChance;

    @Getter
    private int maxSkillChance;

    @Getter
    private int baseAttackAngle;

    @Getter
    private Map<Integer, Skill> skills;

    private Map<AISkillScope, List<Skill>> aiSkillLists;

    @Getter
    private Set<Integer> clans;

    private Set<Integer> ignoreClanNpcIds;

    @Getter
    @Setter
    private List<DropGroupHolder> dropGroups;

    @Getter
    private List<DropHolder> dropList;

    @Getter
    private List<DropHolder> spoilList;

    @Getter
    private double collisionRadiusGrown;

    @Getter
    private double collisionHeightGrown;

    @Getter
    private final List<PlayerClass> teachInfo = new ArrayList<>();

    /**
     * Constructor of Creature.
     * @param set The StatSet object to transfer data to the method
     */
    public NpcTemplate(StatSet set) {
        super(set);
    }

    @Override
    public void set(StatSet set) {
        super.set(set);
        id = set.getInt("id");
        displayId = set.getInt("displayId", id);
        level = set.getByte("level", (byte) 70);
        type = set.getString("type", "Folk");
        name = set.getString("name", "");
        usingServerSideName = set.getBoolean("usingServerSideName", false);
        title = set.getString("title", "");
        usingServerSideTitle = set.getBoolean("usingServerSideTitle", false);
        setRace(set.getEnum("race", Race.class, Race.NONE));
        sex = set.getEnum("sex", Sex.class, Sex.ETC);
        chestId = set.getInt("chestId", 0);
        rHandId = set.getInt("rhandId", 0);
        lHandId = set.getInt("lhandId", 0);
        weaponEnchant = set.getInt("weaponEnchant", 0);
        exp = set.getDouble("exp", 0);
        sp = set.getDouble("sp", 0);
        raidPoints = set.getDouble("raidPoints", 0);
        unique = set.getBoolean("unique", false);
        attackable = set.getBoolean("attackable", true);
        targetable = set.getBoolean("targetable", true);
        talkable = set.getBoolean("talkable", true);
        questMonster = title.contains("Quest");
        undying = set.getBoolean(
                "undying", !type.equals("Monster") && !type.equals("RaidBoss") && !type.equals("GrandBoss"));
        showName = set.getBoolean("showName", true);
        randomWalk = set.getBoolean("randomWalk", !type.equals("Guard"));
        randomAnimationEnabled = set.getBoolean("randomAnimation", true);
        flying = set.getBoolean("flying", false);
        fakePlayer = set.getBoolean("fakePlayer", false);
        if (fakePlayer) {
            fakePlayerInfo = new FakePlayerHolder(set);

            // Check if a character with the same name already exists.
            if (CharInfoTable.getInstance().getIdByName(name) > 0) {
                log.info("Fake player id [{}] conflict. A real player with name [{}] already exists.", id, name);
            }
        }

        canMove = (set.getDouble("baseWalkSpd", 1d) <= 0.1) || set.getBoolean("canMove", true);
        noSleepMode = set.getBoolean("noSleepMode", false);
        passableDoor = set.getBoolean("passableDoor", false);
        hasSummoner = set.getBoolean("hasSummoner", false);
        canBeSown = set.getBoolean("canBeSown", false);
        corpseTime = set.getInt("corpseTime", NpcConfig.DEFAULT_CORPSE_TIME);
        aiType = set.getEnum("aiType", AIType.class, AIType.FIGHTER);

        // L2J aggro range tempfix
        final int aggroValue = set.getInt("aggroRange", 0);
        if (type.equalsIgnoreCase("Monster") && (aggroValue > NpcConfig.MAX_AGGRO_RANGE)) {
            aggroRange = NpcConfig.MAX_AGGRO_RANGE;
        } else {
            aggroRange = aggroValue;
        }

        clanHelpRange = set.getInt("clanHelpRange", 0);
        chaos = set.getBoolean("isChaos", false);
        aggressive = set.getBoolean("isAggressive", true);
        soulShot = set.getInt("soulShot", 0);
        spiritShot = set.getInt("spiritShot", 0);
        soulShotChance = set.getInt("soulShotChance", 0);
        spiritShotChance = set.getInt("spiritShotChance", 0);
        minSkillChance = set.getInt("minSkillChance", 7);
        maxSkillChance = set.getInt("maxSkillChance", 15);
        baseAttackAngle = set.getInt("width", 120);
        collisionRadiusGrown = set.getDouble("collisionRadiusGrown", 0);
        collisionHeightGrown = set.getDouble("collisionHeightGrown", 0);
        if (NpcStatMultipliersConfig.ENABLE_NPC_STAT_MULTIPLIERS) // Custom NPC Stat Multipliers
        {
            switch (type) {
                case "Monster": {
                    baseHpMax *= NpcStatMultipliersConfig.MONSTER_HP_MULTIPLIER;
                    baseMpMax *= NpcStatMultipliersConfig.MONSTER_MP_MULTIPLIER;
                    basePAtk *= NpcStatMultipliersConfig.MONSTER_PATK_MULTIPLIER;
                    baseMAtk *= NpcStatMultipliersConfig.MONSTER_MATK_MULTIPLIER;
                    basePDef *= NpcStatMultipliersConfig.MONSTER_PDEF_MULTIPLIER;
                    baseMDef *= NpcStatMultipliersConfig.MONSTER_MDEF_MULTIPLIER;
                    aggroRange *= NpcStatMultipliersConfig.MONSTER_AGRRO_RANGE_MULTIPLIER;
                    clanHelpRange *= NpcStatMultipliersConfig.MONSTER_CLAN_HELP_RANGE_MULTIPLIER;
                    break;
                }
                case "RaidBoss":
                case "GrandBoss": {
                    baseHpMax *= NpcStatMultipliersConfig.RAIDBOSS_HP_MULTIPLIER;
                    baseMpMax *= NpcStatMultipliersConfig.RAIDBOSS_MP_MULTIPLIER;
                    basePAtk *= NpcStatMultipliersConfig.RAIDBOSS_PATK_MULTIPLIER;
                    baseMAtk *= NpcStatMultipliersConfig.RAIDBOSS_MATK_MULTIPLIER;
                    basePDef *= NpcStatMultipliersConfig.RAIDBOSS_PDEF_MULTIPLIER;
                    baseMDef *= NpcStatMultipliersConfig.RAIDBOSS_MDEF_MULTIPLIER;
                    aggroRange *= NpcStatMultipliersConfig.RAIDBOSS_AGRRO_RANGE_MULTIPLIER;
                    clanHelpRange *= NpcStatMultipliersConfig.RAIDBOSS_CLAN_HELP_RANGE_MULTIPLIER;
                    break;
                }
                case "Guard": {
                    baseHpMax *= NpcStatMultipliersConfig.GUARD_HP_MULTIPLIER;
                    baseMpMax *= NpcStatMultipliersConfig.GUARD_MP_MULTIPLIER;
                    basePAtk *= NpcStatMultipliersConfig.GUARD_PATK_MULTIPLIER;
                    baseMAtk *= NpcStatMultipliersConfig.GUARD_MATK_MULTIPLIER;
                    basePDef *= NpcStatMultipliersConfig.GUARD_PDEF_MULTIPLIER;
                    baseMDef *= NpcStatMultipliersConfig.GUARD_MDEF_MULTIPLIER;
                    aggroRange *= NpcStatMultipliersConfig.GUARD_AGRRO_RANGE_MULTIPLIER;
                    clanHelpRange *= NpcStatMultipliersConfig.GUARD_CLAN_HELP_RANGE_MULTIPLIER;
                    break;
                }
                case "Defender": {
                    baseHpMax *= NpcStatMultipliersConfig.DEFENDER_HP_MULTIPLIER;
                    baseMpMax *= NpcStatMultipliersConfig.DEFENDER_MP_MULTIPLIER;
                    basePAtk *= NpcStatMultipliersConfig.DEFENDER_PATK_MULTIPLIER;
                    baseMAtk *= NpcStatMultipliersConfig.DEFENDER_MATK_MULTIPLIER;
                    basePDef *= NpcStatMultipliersConfig.DEFENDER_PDEF_MULTIPLIER;
                    baseMDef *= NpcStatMultipliersConfig.DEFENDER_MDEF_MULTIPLIER;
                    aggroRange *= NpcStatMultipliersConfig.DEFENDER_AGRRO_RANGE_MULTIPLIER;
                    clanHelpRange *= NpcStatMultipliersConfig.DEFENDER_CLAN_HELP_RANGE_MULTIPLIER;
                    break;
                }
            }
        }
    }

    public boolean isType(String type) {
        return this.type.equalsIgnoreCase(type);
    }

    public double getSP() {
        return sp;
    }

    public boolean isRandomWalkEnabled() {
        return randomWalk && (getParameters().getInt("MoveArounding", 1) != 0);
    }

    public boolean canMove() {
        return canMove;
    }

    public boolean hasSummoner() {
        return hasSummoner;
    }

    public boolean canBeSown() {
        return canBeSown;
    }

    public AIType getAIType() {
        return aiType;
    }

    public void setSkills(Map<Integer, Skill> skills) {
        this.skills = skills != null ? Collections.unmodifiableMap(skills) : Collections.emptyMap();
    }

    public List<Skill> getAISkills(AISkillScope aiSkillScope) {
        return aiSkillLists.getOrDefault(aiSkillScope, Collections.emptyList());
    }

    public void setAISkillLists(Map<AISkillScope, List<Skill>> aiSkillLists) {
        this.aiSkillLists = aiSkillLists != null ? Collections.unmodifiableMap(aiSkillLists) : Collections.emptyMap();
    }

    /**
     * @param clans A sorted array of clan ids
     */
    public void setClans(Set<Integer> clans) {
        this.clans = clans != null ? Collections.unmodifiableSet(clans) : null;
    }

    /**
     * @param clanName clan name to check if it belongs to this NPC template clans.
     * @param clanNames clan names to check if they belong to this NPC template clans.
     * @return {@code true} if at least one of the clan names belong to this NPC template clans, {@code false} otherwise.
     */
    public boolean isClan(String clanName, String... clanNames) {
        // Using local variable for the sake of reloading since it can be turned to null.
        final Set<Integer> clans = this.clans;
        if (clans == null) {
            return false;
        }

        int clanId = NpcData.getInstance().getGenericClanId();
        if (clans.contains(clanId)) {
            return true;
        }

        clanId = NpcData.getInstance().getClanId(clanName);
        if (clans.contains(clanId)) {
            return true;
        }

        for (String name : clanNames) {
            clanId = NpcData.getInstance().getClanId(name);
            if (clans.contains(clanId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param clans A set of clan names to check if they belong to this NPC template clans.
     * @return {@code true} if at least one of the clan names belong to this NPC template clans, {@code false} otherwise.
     */
    public boolean isClan(Set<Integer> clans) {
        // Using local variable for the sake of reloading since it can be turned to null.
        final Set<Integer> clanSet = this.clans;
        if ((clanSet == null) || (clans == null)) {
            return false;
        }

        final int clanId = NpcData.getInstance().getGenericClanId();
        if (clanSet.contains(clanId)) {
            return true;
        }

        for (Integer id : clans) {
            if (clanSet.contains(id)) {
                return true;
            }
        }

        return false;
    }

    public Set<Integer> getIgnoreClanNpcIds() {
        return ignoreClanNpcIds;
    }

    public boolean hasIgnoreClanNpcIds() {
        return ignoreClanNpcIds != null;
    }

    /**
     * @param ignoreClanNpcIds the ignore clan npc ids
     */
    public void setIgnoreClanNpcIds(Set<Integer> ignoreClanNpcIds) {
        this.ignoreClanNpcIds = ignoreClanNpcIds != null ? Collections.unmodifiableSet(ignoreClanNpcIds) : null;
    }

    public void removeDropGroups() {
        dropGroups = null;
    }

    public void removeDrops() {
        dropList = null;
        spoilList = null;
    }

    public void addDrop(DropHolder dropHolder) {
        if (dropList == null) {
            dropList = new ArrayList<>(1);
        }

        dropList.add(dropHolder);
    }

    public void addSpoil(DropHolder dropHolder) {
        if (spoilList == null) {
            spoilList = new ArrayList<>(1);
        }

        spoilList.add(dropHolder);
    }

    public List<ItemHolder> calculateDrops(DropType dropType, Creature victim, Creature killer) {
        if (dropType == DropType.DROP) {
            // calculate group drops
            List<ItemHolder> groupDrops = null;
            if (dropGroups != null) {
                groupDrops = calculateGroupDrops(victim, killer);

                if ((groupDrops != null)
                        && victim.isMonster()
                        && victim.asMonster().isSeeded()) {
                    groupDrops.removeIf(i -> (i.getId() != 57 /* Adena */)
                            && (i.getId() != 6361 /* Green Seal Stone */)
                            && (i.getId() != 6362 /* Red Seal Stone */)
                            && (i.getId() != 6360 /* Blue Seal Stone */));
                }
            }

            // calculate ungrouped drops
            List<ItemHolder> ungroupedDrops = null;
            if (dropList != null) {
                ungroupedDrops = calculateUngroupedDrops(dropType, victim, killer);

                if ((ungroupedDrops != null)
                        && victim.isMonster()
                        && victim.asMonster().isSeeded()) {
                    ungroupedDrops.removeIf(i -> (i.getId() != 57 /* Adena */)
                            && (i.getId() != 6361 /* Green Seal Stone */)
                            && (i.getId() != 6362 /* Red Seal Stone */)
                            && (i.getId() != 6360 /* Blue Seal Stone */));
                }
            }

            // return results
            if ((groupDrops != null) && (ungroupedDrops != null)) {
                groupDrops.addAll(ungroupedDrops);
                ungroupedDrops.clear();
                return groupDrops;
            }

            if (groupDrops != null) {
                return groupDrops;
            }

            if (ungroupedDrops != null) {
                return ungroupedDrops;
            }
        } else if ((dropType == DropType.SPOIL) && (spoilList != null)) {
            return calculateUngroupedDrops(dropType, victim, killer);
        }

        // no drops
        return null;
    }

    private List<ItemHolder> calculateGroupDrops(Creature victim, Creature killer) {
        // level difference calculations
        final int levelDifference = victim.getLevel() - killer.getLevel();
        final double levelGapChanceToDropAdena = MathUtil.scaleToRange(
                levelDifference,
                -RatesConfig.DROP_ADENA_MAX_LEVEL_DIFFERENCE,
                -RatesConfig.DROP_ADENA_MIN_LEVEL_DIFFERENCE,
                RatesConfig.DROP_ADENA_MIN_LEVEL_GAP_CHANCE,
                100d);
        final double levelGapChanceToDrop = MathUtil.scaleToRange(
                levelDifference,
                -RatesConfig.DROP_ITEM_MAX_LEVEL_DIFFERENCE,
                -RatesConfig.DROP_ITEM_MIN_LEVEL_DIFFERENCE,
                RatesConfig.DROP_ITEM_MIN_LEVEL_GAP_CHANCE,
                100d);

        List<ItemHolder> calculatedDrops = null;
        int dropOccurrenceCounter =
                victim.isRaid() ? RatesConfig.DROP_MAX_OCCURRENCES_RAIDBOSS : RatesConfig.DROP_MAX_OCCURRENCES_NORMAL;
        if (dropOccurrenceCounter > 0) {
            final Player player = killer.asPlayer();
            List<ItemHolder> randomDrops = null;
            ItemHolder cachedItem = null;
            double totalChance; // total group chance is 100
            for (DropGroupHolder group : dropGroups) {
                totalChance = 0;
                GROUP_DROP:
                for (DropHolder dropItem : group.getDropList()) {
                    final int itemId = dropItem.getItemId();
                    final ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
                    final boolean champion = victim.isChampion();

                    // chance
                    double rateChance = 1;
                    if (RatesConfig.RATE_DROP_CHANCE_BY_ID.get(itemId) != null) {
                        rateChance *= RatesConfig.RATE_DROP_CHANCE_BY_ID.get(itemId);
                        if (champion && (itemId == Inventory.ADENA_ID)) {
                            rateChance *= ChampionMonstersConfig.CHAMPION_ADENAS_REWARDS_CHANCE;
                        }

                        if ((itemId == Inventory.ADENA_ID) && (rateChance > 100)) {
                            rateChance = 100;
                        }
                    } else if (item.hasExImmediateEffect()) {
                        rateChance *= RatesConfig.RATE_HERB_DROP_CHANCE_MULTIPLIER;
                    } else if (victim.isRaid()) {
                        rateChance *= RatesConfig.RATE_RAID_DROP_CHANCE_MULTIPLIER;
                    } else {
                        rateChance *= RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER
                                * (champion ? ChampionMonstersConfig.CHAMPION_REWARDS_CHANCE : 1);
                    }

                    // premium chance
                    if (player != null) {
                        if (PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && player.hasPremiumStatus()) {
                            if (PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE_BY_ID.get(itemId) != null) {
                                rateChance *= PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE_BY_ID.get(itemId);
                            } else if (item.hasExImmediateEffect()) {
                                // TODO: Premium herb chance? :)
                            } else if (victim.isRaid()) {
                                // TODO: Premium raid chance? :)
                            } else {
                                rateChance *= PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE;
                            }
                        }

                        // bonus drop rate effect
                        rateChance *= player.getStat().getBonusDropRateMultiplier();
                    }

                    // only use total chance on x1, custom rates break this logic because total chance is more than 100%
                    if (rateChance == 1) {
                        totalChance += dropItem.getChance();
                    } else {
                        totalChance = dropItem.getChance();
                    }

                    final double groupItemChance = totalChance * (group.getChance() / 100) * rateChance;

                    // check if maximum drop occurrences have been reached
                    // items that have 100% drop chance without server rate multipliers drop normally
                    if ((dropOccurrenceCounter == 0)
                            && (groupItemChance < 100)
                            && (randomDrops != null)
                            && (calculatedDrops != null)) {
                        if ((rateChance == 1)
                                && !randomDrops
                                        .isEmpty()) // custom rates break this logic because total chance is more than
                        // 100%
                        {
                            // remove highest chance item (temporarily if no other item replaces it)
                            cachedItem = randomDrops.remove(0);
                            calculatedDrops.remove(cachedItem);
                        }

                        dropOccurrenceCounter = 1;
                    }

                    // check level gap that may prevent to drop item
                    if ((ThreadLocalRandom.current().nextDouble() * 100)
                            > (dropItem.getItemId() == Inventory.ADENA_ID
                                    ? levelGapChanceToDropAdena
                                    : levelGapChanceToDrop)) {
                        continue GROUP_DROP;
                    }

                    // calculate chances
                    final ItemHolder drop = calculateGroupDrop(group, dropItem, victim, killer, groupItemChance);
                    if (drop == null) {
                        continue GROUP_DROP;
                    }

                    // create lists
                    if (randomDrops == null) {
                        randomDrops = new ArrayList<>(dropOccurrenceCounter);
                    }

                    if (calculatedDrops == null) {
                        calculatedDrops = new ArrayList<>(dropOccurrenceCounter);
                    }

                    // finally
                    final Float itemChance = RatesConfig.RATE_DROP_CHANCE_BY_ID.get(dropItem.getItemId());
                    if (itemChance != null) {
                        if ((groupItemChance * itemChance) < 100) {
                            dropOccurrenceCounter--;
                            if (rateChance == 1) // custom rates break this logic because total chance is more than 100%
                            {
                                randomDrops.add(drop);
                            }
                        }
                    } else if (groupItemChance < 100) {
                        dropOccurrenceCounter--;
                        if (rateChance == 1) // custom rates break this logic because total chance is more than 100%
                        {
                            randomDrops.add(drop);
                        }
                    }

                    calculatedDrops.add(drop);

                    // no more drops from this group, only use on x1, custom rates break this logic because total chance
                    // is more than 100%
                    if (rateChance == 1) {
                        break GROUP_DROP;
                    }
                }
            }

            // add temporarily removed item when not replaced
            if ((dropOccurrenceCounter > 0) && (cachedItem != null) && (calculatedDrops != null)) {
                calculatedDrops.add(cachedItem);
            }

            // clear random drops
            if (randomDrops != null) {
                randomDrops.clear();
                randomDrops = null;
            }

            // champion extra drop
            if (victim.isChampion()
                    && (ThreadLocalRandom.current().nextInt(100)
                            < (victim.getLevel() < killer.getLevel()
                                    ? ChampionMonstersConfig.CHAMPION_REWARD_LOWER_LEVEL_ITEM_CHANCE
                                    : ChampionMonstersConfig.CHAMPION_REWARD_HIGHER_LEVEL_ITEM_CHANCE))) {
                // create list
                if (calculatedDrops == null) {
                    calculatedDrops = new ArrayList<>();
                }

                if (!calculatedDrops.containsAll(ChampionMonstersConfig.CHAMPION_REWARD_ITEMS)) {
                    calculatedDrops.addAll(ChampionMonstersConfig.CHAMPION_REWARD_ITEMS);
                }
            }
        }

        return calculatedDrops;
    }

    private List<ItemHolder> calculateUngroupedDrops(DropType dropType, Creature victim, Creature killer) {
        final List<DropHolder> dropList = dropType == DropType.SPOIL ? spoilList : this.dropList;

        // level difference calculations
        final int levelDifference = victim.getLevel() - killer.getLevel();
        final double levelGapChanceToDropAdena = MathUtil.scaleToRange(
                levelDifference,
                -RatesConfig.DROP_ADENA_MAX_LEVEL_DIFFERENCE,
                -RatesConfig.DROP_ADENA_MIN_LEVEL_DIFFERENCE,
                RatesConfig.DROP_ADENA_MIN_LEVEL_GAP_CHANCE,
                100d);
        final double levelGapChanceToDrop = MathUtil.scaleToRange(
                levelDifference,
                -RatesConfig.DROP_ITEM_MAX_LEVEL_DIFFERENCE,
                -RatesConfig.DROP_ITEM_MIN_LEVEL_DIFFERENCE,
                RatesConfig.DROP_ITEM_MIN_LEVEL_GAP_CHANCE,
                100d);

        int dropOccurrenceCounter =
                victim.isRaid() ? RatesConfig.DROP_MAX_OCCURRENCES_RAIDBOSS : RatesConfig.DROP_MAX_OCCURRENCES_NORMAL;
        List<ItemHolder> calculatedDrops = null;
        List<ItemHolder> randomDrops = null;
        ItemHolder cachedItem = null;
        if (dropOccurrenceCounter > 0) {
            for (DropHolder dropItem : dropList) {
                // check if maximum drop occurrences have been reached
                // items that have 100% drop chance without server rate multipliers drop normally
                if ((dropOccurrenceCounter == 0)
                        && (dropItem.getChance() < 100)
                        && (randomDrops != null)
                        && (calculatedDrops != null)) {
                    // remove highest chance item (temporarily if no other item replaces it)
                    cachedItem = randomDrops.remove(0);
                    calculatedDrops.remove(cachedItem);
                    dropOccurrenceCounter = 1;
                }

                // check level gap that may prevent to drop item
                if ((ThreadLocalRandom.current().nextDouble() * 100)
                        > (dropItem.getItemId() == Inventory.ADENA_ID
                                ? levelGapChanceToDropAdena
                                : levelGapChanceToDrop)) {
                    continue;
                }

                // calculate chances
                final ItemHolder drop = calculateUngroupedDrop(dropItem, victim, killer);
                if (drop == null) {
                    continue;
                }

                // create lists
                if (randomDrops == null) {
                    randomDrops = new ArrayList<>(dropOccurrenceCounter);
                }

                if (calculatedDrops == null) {
                    calculatedDrops = new ArrayList<>(dropOccurrenceCounter);
                }

                // finally
                final Float itemChance = RatesConfig.RATE_DROP_CHANCE_BY_ID.get(dropItem.getItemId());
                if (itemChance != null) {
                    if ((dropItem.getChance() * itemChance) < 100) {
                        dropOccurrenceCounter--;
                        randomDrops.add(drop);
                    }
                } else if (dropItem.getChance() < 100) {
                    dropOccurrenceCounter--;
                    randomDrops.add(drop);
                }

                calculatedDrops.add(drop);
            }
        }

        // add temporarily removed item when not replaced
        if ((dropOccurrenceCounter > 0) && (cachedItem != null) && (calculatedDrops != null)) {
            calculatedDrops.add(cachedItem);
        }

        // clear random drops
        if (randomDrops != null) {
            randomDrops.clear();
            randomDrops = null;
        }

        // champion extra drop
        if (victim.isChampion()
                && (ThreadLocalRandom.current().nextInt(100)
                        < (victim.getLevel() < killer.getLevel()
                                ? ChampionMonstersConfig.CHAMPION_REWARD_LOWER_LEVEL_ITEM_CHANCE
                                : ChampionMonstersConfig.CHAMPION_REWARD_HIGHER_LEVEL_ITEM_CHANCE))) {
            // create list
            if (calculatedDrops == null) {
                calculatedDrops = new ArrayList<>();
            }

            if (!calculatedDrops.containsAll(ChampionMonstersConfig.CHAMPION_REWARD_ITEMS)) {
                calculatedDrops.addAll(ChampionMonstersConfig.CHAMPION_REWARD_ITEMS);
            }
        }

        return calculatedDrops;
    }

    /**
     * @param group
     * @param dropItem
     * @param victim
     * @param killer
     * @param chance
     * @return ItemHolder
     */
    private ItemHolder calculateGroupDrop(
            DropGroupHolder group, DropHolder dropItem, Creature victim, Creature killer, double chance) {
        final int itemId = dropItem.getItemId();
        final ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
        final boolean champion = victim.isChampion();

        // calculate if item will drop
        if ((ThreadLocalRandom.current().nextDouble() * 100) < chance) {
            // amount is calculated after chance returned success
            double rateAmount = 1;
            if (RatesConfig.RATE_DROP_AMOUNT_BY_ID.get(itemId) != null) {
                rateAmount *= RatesConfig.RATE_DROP_AMOUNT_BY_ID.get(itemId);
                if (champion && (itemId == Inventory.ADENA_ID)) {
                    rateAmount *= ChampionMonstersConfig.CHAMPION_ADENAS_REWARDS_AMOUNT;
                }
            } else if (item.hasExImmediateEffect()) {
                rateAmount *= RatesConfig.RATE_HERB_DROP_AMOUNT_MULTIPLIER;
            } else if (victim.isRaid()) {
                rateAmount *= RatesConfig.RATE_RAID_DROP_AMOUNT_MULTIPLIER;
            } else {
                rateAmount *= RatesConfig.RATE_DEATH_DROP_AMOUNT_MULTIPLIER
                        * (champion ? ChampionMonstersConfig.CHAMPION_REWARDS_AMOUNT : 1);
            }

            // premium amount
            final Player player = killer.asPlayer();
            if (player != null) {
                if (PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && player.hasPremiumStatus()) {
                    if (PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT_BY_ID.get(itemId) != null) {
                        rateAmount *= PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT_BY_ID.get(itemId);
                    } else if (item.hasExImmediateEffect()) {
                        // TODO: Premium herb amount? :)
                    } else if (victim.isRaid()) {
                        // TODO: Premium raid amount? :)
                    } else {
                        rateAmount *= PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT;
                    }
                }

                // bonus drop amount effect
                final PlayerStat stat = player.getStat();
                rateAmount *= stat.getBonusDropAmountMultiplier();
                if (itemId == Inventory.ADENA_ID) {
                    rateAmount *= stat.getBonusDropAdenaMultiplier();
                }
            }

            // finally
            return new ItemHolder(itemId, (long) ((dropItem.getMin() < dropItem.getMax()
                            ? ThreadLocalRandom.current().nextLong(dropItem.getMin(), dropItem.getMax() + 1)
                            : dropItem.getMin())
                    * rateAmount));
        }

        return null;
    }

    /**
     * @param dropItem
     * @param victim
     * @param killer
     * @return ItemHolder
     */
    private ItemHolder calculateUngroupedDrop(DropHolder dropItem, Creature victim, Creature killer) {
        switch (dropItem.getDropType()) {
            case DROP: {
                final int itemId = dropItem.getItemId();
                final ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
                final boolean champion = victim.isChampion();

                // chance
                double rateChance = 1;
                if (RatesConfig.RATE_DROP_CHANCE_BY_ID.get(itemId) != null) {
                    rateChance *= RatesConfig.RATE_DROP_CHANCE_BY_ID.get(itemId);
                    if (champion && (itemId == Inventory.ADENA_ID)) {
                        rateChance *= ChampionMonstersConfig.CHAMPION_ADENAS_REWARDS_CHANCE;
                    }

                    if ((itemId == Inventory.ADENA_ID) && (rateChance > 100)) {
                        rateChance = 100;
                    }
                } else if (item.hasExImmediateEffect()) {
                    rateChance *= RatesConfig.RATE_HERB_DROP_CHANCE_MULTIPLIER;
                } else if (victim.isRaid()) {
                    rateChance *= RatesConfig.RATE_RAID_DROP_CHANCE_MULTIPLIER;
                } else {
                    rateChance *= RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER
                            * (champion ? ChampionMonstersConfig.CHAMPION_REWARDS_CHANCE : 1);
                }

                // premium chance
                final Player player = killer.asPlayer();
                if (player != null) {
                    if (PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && player.hasPremiumStatus()) {
                        if (PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE_BY_ID.get(itemId) != null) {
                            rateChance *= PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE_BY_ID.get(itemId);
                        } else if (item.hasExImmediateEffect()) {
                            // TODO: Premium herb chance? :)
                        } else if (victim.isRaid()) {
                            // TODO: Premium raid chance? :)
                        } else {
                            rateChance *= PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE;
                        }
                    }

                    // bonus drop rate effect
                    rateChance *= player.getStat().getBonusDropRateMultiplier();
                }

                // calculate if item will drop
                if ((ThreadLocalRandom.current().nextDouble() * 100) < (dropItem.getChance() * rateChance)) {
                    // amount is calculated after chance returned success
                    double rateAmount = 1;
                    if (RatesConfig.RATE_DROP_AMOUNT_BY_ID.get(itemId) != null) {
                        rateAmount *= RatesConfig.RATE_DROP_AMOUNT_BY_ID.get(itemId);
                        if (champion && (itemId == Inventory.ADENA_ID)) {
                            rateAmount *= ChampionMonstersConfig.CHAMPION_ADENAS_REWARDS_AMOUNT;
                        }
                    } else if (item.hasExImmediateEffect()) {
                        rateAmount *= RatesConfig.RATE_HERB_DROP_AMOUNT_MULTIPLIER;
                    } else if (victim.isRaid()) {
                        rateAmount *= RatesConfig.RATE_RAID_DROP_AMOUNT_MULTIPLIER;
                    } else {
                        rateAmount *= RatesConfig.RATE_DEATH_DROP_AMOUNT_MULTIPLIER
                                * (champion ? ChampionMonstersConfig.CHAMPION_REWARDS_AMOUNT : 1);
                    }

                    // premium amount
                    if (player != null) {
                        if (PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && player.hasPremiumStatus()) {
                            if (PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT_BY_ID.get(itemId) != null) {
                                rateAmount *= PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT_BY_ID.get(itemId);
                            } else if (item.hasExImmediateEffect()) {
                                // TODO: Premium herb amount? :)
                            } else if (victim.isRaid()) {
                                // TODO: Premium raid amount? :)
                            } else {
                                rateAmount *= PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT;
                            }
                        }

                        // bonus drop amount effect
                        final PlayerStat stat = player.getStat();
                        rateAmount *= stat.getBonusDropAmountMultiplier();
                        if (itemId == Inventory.ADENA_ID) {
                            rateAmount *= stat.getBonusDropAdenaMultiplier();
                        }
                    }

                    // finally
                    return new ItemHolder(itemId, (long) ((dropItem.getMin() < dropItem.getMax()
                                    ? ThreadLocalRandom.current().nextLong(dropItem.getMin(), dropItem.getMax() + 1)
                                    : dropItem.getMin())
                            * rateAmount));
                }
                break;
            }
            case SPOIL: {
                // chance
                double rateChance = RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER;

                // premium chance
                final Player player = killer.asPlayer();
                if (player != null) {
                    if (PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && player.hasPremiumStatus()) {
                        rateChance *= PremiumSystemConfig.PREMIUM_RATE_SPOIL_CHANCE;
                    }

                    // bonus spoil rate effect
                    rateChance *= player.getStat().getBonusSpoilRateMultiplier();
                }

                // calculate if item will be rewarded
                if ((ThreadLocalRandom.current().nextDouble() * 100) < (dropItem.getChance() * rateChance)) {
                    // amount is calculated after chance returned success
                    double rateAmount = RatesConfig.RATE_SPOIL_DROP_AMOUNT_MULTIPLIER;

                    // premium amount
                    if (PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && (player != null) && player.hasPremiumStatus()) {
                        rateAmount *= PremiumSystemConfig.PREMIUM_RATE_SPOIL_AMOUNT;
                    }

                    // finally
                    return new ItemHolder(dropItem.getItemId(), (long) ((dropItem.getMin() < dropItem.getMax()
                                    ? ThreadLocalRandom.current().nextLong(dropItem.getMin(), dropItem.getMax() + 1)
                                    : dropItem.getMin())
                            * rateAmount));
                }
                break;
            }
        }

        return null;
    }

    public static boolean isAssignableTo(Class<?> subValue, Class<?> clazz) {
        // If clazz represents an interface.
        if (clazz.isInterface()) {
            // Check if obj implements the clazz interface.
            for (Class<?> interface1 : subValue.getInterfaces()) {
                if (clazz.getName().equals(interface1.getName())) {
                    return true;
                }
            }
        } else {
            Class<?> sub = subValue;
            do {
                if (sub.getName().equals(clazz.getName())) {
                    return true;
                }

                sub = sub.getSuperclass();
            } while (sub != null);
        }

        return false;
    }

    /**
     * Checks if obj can be assigned to the Class represented by clazz.<br>
     * This is true if, and only if, obj is the same class represented by clazz, or a subclass of it or obj implements the interface represented by clazz.
     * @param obj
     * @param clazz
     * @return {@code true} if the object can be assigned to the class, {@code false} otherwise
     */
    public static boolean isAssignableTo(Object obj, Class<?> clazz) {
        return isAssignableTo(obj.getClass(), clazz);
    }

    public boolean canTeach(PlayerClass playerClass) {
        // If the player is on a third class, fetch the class teacher information for its parent class.
        if (playerClass.level() == 3) {
            return teachInfo.contains(playerClass.getParent());
        }

        return teachInfo.contains(playerClass);
    }

    public void addTeachInfo(List<PlayerClass> teachInfo) {
        this.teachInfo.addAll(teachInfo);
    }
}
