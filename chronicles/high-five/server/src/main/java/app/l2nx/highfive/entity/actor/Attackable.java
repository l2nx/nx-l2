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
package app.l2nx.highfive.entity.actor;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.ai.AttackableAI;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.ai.FortSiegeGuardAI;
import app.l2nx.highfive.ai.Intention;
import app.l2nx.highfive.ai.SiegeGuardAI;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.config.custom.ChampionMonstersConfig;
import app.l2nx.highfive.config.custom.FakePlayersConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.data.enums.CategoryType;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.WorldRegion;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.enums.npc.DropType;
import app.l2nx.highfive.entity.actor.holders.npc.AbsorberInfo;
import app.l2nx.highfive.entity.actor.holders.npc.AggroInfo;
import app.l2nx.highfive.entity.actor.holders.npc.DamageDoneInfo;
import app.l2nx.highfive.entity.actor.instance.GrandBoss;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.entity.actor.status.AttackableStatus;
import app.l2nx.highfive.entity.actor.tasks.attackable.CommandChannelTimer;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.groups.CommandChannel;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.managers.EventDropManager;
import app.l2nx.highfive.managers.PcCafePointsManager;
import app.l2nx.highfive.managers.WalkingManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.attackable.OnAttackableAggroRangeEnter;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.attackable.OnAttackableAttack;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.attackable.OnAttackableKill;
import app.l2nx.highfive.mechanics.siege.manor.Seed;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import app.l2nx.highfive.util.StatSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class Attackable extends Npc {
    private boolean raid = false;

    private boolean raidMinion = false;

    private boolean champion = false;

    // Insertion order makes hate ties and random picks over the list replay the same; identity order changes per run.
    private final Object2ObjectLinkedOpenHashMap<Creature, AggroInfo> aggroList =
            new Object2ObjectLinkedOpenHashMap<>();

    private boolean canReturnToSpawnPoint = true;

    private boolean seeThroughSilentMove = false;

    private boolean seeded = false;

    private Seed seed = null;

    private int seederId = 0;

    private ItemHolder harvestItem;

    private int spoilerObjectId;

    private Collection<ItemHolder> sweepItems;

    private boolean overhit;

    private double overhitDamage;

    private Creature overhitAttacker;

    private CommandChannel firstCommandChannelAttacked = null;

    private CommandChannelTimer commandChannelTimer = null;

    private long commandChannelLastAttack = 0;

    private boolean absorbed;

    private final Int2ObjectOpenHashMap<AbsorberInfo> absorbersList = new Int2ObjectOpenHashMap<>();

    private boolean mustGiveExpSp;

    private long spawnTime = ClockBridge.gameMillis();

    protected int onKillDelay = 2500;

    public Attackable(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.Attackable);
        setInvul(false);
        mustGiveExpSp = true;
    }

    @Override
    public boolean isRaid() {
        WorldPartition.checkOwner();
        return raid;
    }

    @Override
    public boolean isRaidMinion() {
        WorldPartition.checkOwner();
        return raidMinion;
    }

    @Override
    public boolean isChampion() {
        WorldPartition.checkOwner();
        return champion;
    }

    /** A read-only copy of the aggro list; its entries are the live {@link AggroInfo} objects. */
    public Map<Creature, AggroInfo> getAggroList() {
        WorldPartition.checkOwner();
        return Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(aggroList));
    }

    public AggroInfo getAggroInfo(Creature attacker) {
        WorldPartition.checkOwner();
        return aggroList.get(attacker);
    }

    public boolean isAggroListEmpty() {
        WorldPartition.checkOwner();
        return aggroList.isEmpty();
    }

    /**
     * Copies the source's per-attacker hate into this aggro list. Damage is not copied: each attackable rewards only
     * the damage it took itself.
     */
    public void addAggroFrom(Attackable source) {
        WorldPartition.checkOwner();
        if (source == this) {
            return;
        }

        for (AggroInfo info : source.aggroList.values()) {
            aggroList.computeIfAbsent(info.getAttacker(), AggroInfo::new).addHate(info.getHate());
        }
    }

    public void setCanReturnToSpawnPoint(boolean canReturnToSpawnPoint) {
        WorldPartition.checkOwner();
        this.canReturnToSpawnPoint = canReturnToSpawnPoint;
    }

    public void setSeeThroughSilentMove(boolean seeThroughSilentMove) {
        WorldPartition.checkOwner();
        this.seeThroughSilentMove = seeThroughSilentMove;
    }

    public boolean isSeeded() {
        WorldPartition.checkOwner();
        return seeded;
    }

    public Seed getSeed() {
        WorldPartition.checkOwner();
        return seed;
    }

    public int getSeederId() {
        WorldPartition.checkOwner();
        return seederId;
    }

    public int getSpoilerObjectId() {
        WorldPartition.checkOwner();
        return spoilerObjectId;
    }

    public void setSpoilerObjectId(int spoilerObjectId) {
        WorldPartition.checkOwner();
        this.spoilerObjectId = spoilerObjectId;
    }

    public boolean isOverhit() {
        WorldPartition.checkOwner();
        return overhit;
    }

    public double getOverhitDamage() {
        WorldPartition.checkOwner();
        return overhitDamage;
    }

    public Creature getOverhitAttacker() {
        WorldPartition.checkOwner();
        return overhitAttacker;
    }

    public CommandChannel getFirstCommandChannelAttacked() {
        WorldPartition.checkOwner();
        return firstCommandChannelAttacked;
    }

    public void setFirstCommandChannelAttacked(CommandChannel firstCommandChannelAttacked) {
        WorldPartition.checkOwner();
        this.firstCommandChannelAttacked = firstCommandChannelAttacked;
    }

    public CommandChannelTimer getCommandChannelTimer() {
        WorldPartition.checkOwner();
        return commandChannelTimer;
    }

    public void setCommandChannelTimer(CommandChannelTimer commandChannelTimer) {
        WorldPartition.checkOwner();
        this.commandChannelTimer = commandChannelTimer;
    }

    public long getCommandChannelLastAttack() {
        WorldPartition.checkOwner();
        return commandChannelLastAttack;
    }

    public void setCommandChannelLastAttack(long commandChannelLastAttack) {
        WorldPartition.checkOwner();
        this.commandChannelLastAttack = commandChannelLastAttack;
    }

    public boolean isAbsorbed() {
        WorldPartition.checkOwner();
        return absorbed;
    }

    public AbsorberInfo getAbsorberInfo(int objectId) {
        WorldPartition.checkOwner();
        return absorbersList.get(objectId);
    }

    public long getSpawnTime() {
        WorldPartition.checkOwner();
        return spawnTime;
    }

    public int getOnKillDelay() {
        WorldPartition.checkOwner();
        return onKillDelay;
    }

    public void setOnKillDelay(int onKillDelay) {
        WorldPartition.checkOwner();
        this.onKillDelay = onKillDelay;
    }

    @Override
    public AttackableStatus getStatus() {
        WorldPartition.checkOwner();
        return (AttackableStatus) uncheckedStatus();
    }

    @Override
    public void initCharStatus() {
        WorldPartition.checkOwner();
        setStatus(new AttackableStatus(this));
    }

    @Override
    protected CreatureAI initAI() {
        return new AttackableAI(this);
    }

    public boolean canReturnToSpawnPoint() {
        WorldPartition.checkOwner();
        return canReturnToSpawnPoint;
    }

    public boolean canSeeThroughSilentMove() {
        WorldPartition.checkOwner();
        return seeThroughSilentMove;
    }

    public void useMagic(Skill skill) {
        WorldPartition.checkOwner();
        if ((skill == null) || isAlikeDead() || skill.isPassive() || isCastingNow() || isSkillDisabled(skill)) {
            return;
        }

        if ((getCurrentMp() < (getStat().getMpConsume(skill) + getStat().getMpInitialConsume(skill)))
                || (getCurrentHp() <= skill.getHpConsume())) {
            return;
        }

        if (!skill.isStatic()) {
            if (skill.isMagic()) {
                if (isMuted()) {
                    return;
                }
            } else if (isPhysicalMuted()) {
                return;
            }
        }

        final WorldObject target = skill.getFirstOfTargetList(this);
        if (target != null) {
            getAI().setIntentionCast(skill, target);
        }
    }

    @Override
    public void reduceCurrentHp(double damage, Creature attacker, Skill skill) {
        WorldPartition.checkOwner();
        reduceCurrentHp(damage, attacker, true, false, skill);
    }

    @Override
    public void reduceCurrentHp(double damage, Creature attacker, boolean awake, boolean isDOT, Skill skill) {
        WorldPartition.checkOwner();
        if (raid
                && !isMinion()
                && (attacker != null)
                && (attacker.getParty() != null)
                && attacker.getParty().isInCommandChannel()
                && attacker.getParty().getCommandChannel().meetRaidWarCondition(this)) {
            if (firstCommandChannelAttacked == null) {
                firstCommandChannelAttacked = attacker.getParty().getCommandChannel();
                if (firstCommandChannelAttacked != null) {
                    commandChannelTimer = new CommandChannelTimer(this);
                    commandChannelLastAttack = ClockBridge.gameMillis();
                    WorldPartition.loop().schedule(commandChannelTimer, 10000, TimeUnit.MILLISECONDS);
                    firstCommandChannelAttacked.broadcastPacket(new CreatureSay(
                            null, ChatType.PARTYROOM_ALL, "", "You have looting rights!")); // TODO: retail msg
                }
            } else if (attacker.getParty().getCommandChannel().equals(firstCommandChannelAttacked)) {
                commandChannelLastAttack = ClockBridge.gameMillis();
            }
        }

        if (attacker != null) {
            addDamage(attacker, (int) damage, skill);
        }

        if (isMonster()) {
            Monster master = asMonster();

            if (master.hasMinions()) {
                master.getMinionList().onAssist(this, attacker);
            }

            master = master.getLeader();
            if ((master != null) && master.hasMinions()) {
                master.getMinionList().onAssist(this, attacker);
            }
        }

        super.reduceCurrentHp(damage, attacker, awake, isDOT, skill);
    }

    public void setMustRewardExpSp(boolean value) {
        WorldPartition.checkOwner();
        mustGiveExpSp = value;
    }

    public boolean getMustRewardExpSP() {
        WorldPartition.checkOwner();
        return mustGiveExpSp && !isFakePlayer();
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        if (killer != null) {
            WorldPartition.loop().defer(() -> notifyKill(killer));
        }

        if (isMonster()) {
            final Monster mob = asMonster();
            final Monster leader = mob.getLeader();
            if ((leader != null) && leader.hasMinions()) {
                final int respawnTime = NpcConfig.MINIONS_RESPAWN_TIME.containsKey(getId())
                        ? NpcConfig.MINIONS_RESPAWN_TIME.get(getId()) * 1000
                        : -1;
                leader.getMinionList().onMinionDie(mob, respawnTime);
            }

            if (mob.hasMinions()) {
                mob.getMinionList().onMasterDie(false);
            }
        }

        return true;
    }

    protected void notifyKill(Creature killer) {
        final Player player = killer.asPlayer();
        if ((player != null) && EventDispatcher.getInstance().hasListener(EventType.ON_ATTACKABLE_KILL, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsyncDelayed(
                            OnAttackableKill.builder()
                                    .attacker(player)
                                    .target(this)
                                    .summon(killer.isSummon())
                                    .build(),
                            this,
                            onKillDelay);
        }
    }

    private static class PartyContainer {
        public Party party;
        public long damage;

        public PartyContainer(Party party, long damage) {
            this.party = party;
            this.damage = damage;
        }
    }

    /**
     * Rewards run later, in the side-effect phase, so everything they read is snapshotted here: by then the death, or
     * a script deleting the corpse, has cleared the aggro list and over-hit and lowered the maximum HP.
     */
    @Override
    protected void calculateRewards(Creature lastAttacker) {
        if (aggroList.isEmpty()) {
            return;
        }

        final Map<Creature, Long> damage = new LinkedHashMap<>();
        for (AggroInfo info : aggroList.values()) {
            damage.put(info.getAttacker(), info.getDamage());
        }

        final boolean overhitStrike = overhit && (overhitAttacker != null);
        final DeathRewards death = DeathRewards.builder()
                .lastAttacker(lastAttacker)
                .damage(Collections.unmodifiableMap(damage))
                .overhitAttacker(overhitStrike ? overhitAttacker : null)
                .overhitExpShare(overhitStrike ? overhitExpShare() : 0)
                .rewardExpSp(getMustRewardExpSP())
                .region(getWorldRegion())
                .maxHp(getMaxHp())
                .champion(champion)
                .useVitalityRate(useVitalityRate())
                .build();
        WorldPartition.checkOwner();
        WorldPartition.loop().defer(() -> distributeRewards(death));
    }

    private void distributeRewards(DeathRewards death) {
        final Creature lastAttacker = death.lastAttacker();

        final Object2ObjectLinkedOpenHashMap<Player, DamageDoneInfo> rewards = new Object2ObjectLinkedOpenHashMap<>();

        Player maxDealer = null;
        long maxDamage = 0;
        long totalDamage = 0;

        for (Map.Entry<Creature, Long> dealt : death.damage().entrySet()) {
            final Player attacker = dealt.getKey().asPlayer();
            if (attacker == null) {
                continue;
            }

            final long damage = dealt.getValue();

            if (damage > 1) {
                if (calculateDistance3D(attacker) > PlayerConfig.ALT_PARTY_RANGE) {
                    continue;
                }

                totalDamage += damage;

                // Calculate real damages (Summoners should get own damage plus summon's damage).
                final DamageDoneInfo reward = rewards.computeIfAbsent(attacker, DamageDoneInfo::new);
                reward.addDamage(damage);

                if (reward.getDamage() > maxDamage) {
                    maxDealer = attacker;
                    maxDamage = reward.getDamage();
                }
            }
        }

        final List<PartyContainer> damagingParties = new ArrayList<>();
        for (Creature attacker : death.damage().keySet()) {
            long totalMemberDamage = 0;
            final Party party = attacker.getParty();
            if (party == null) {
                continue;
            }

            PartyContainer found = null;
            for (int i = 0, damagingPartiesSize = damagingParties.size(); i < damagingPartiesSize; i++) {
                final PartyContainer p = damagingParties.get(i);
                if (p.party == party) {
                    found = p;
                    break;
                }
            }

            final PartyContainer container = found != null ? found : new PartyContainer(party, 0L);
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                party.collectMembers(members);
                for (Player e : members) {
                    final Long memberDamage = death.damage().get(e);
                    if (memberDamage == null) {
                        continue;
                    }

                    if (memberDamage > 1) {
                        totalMemberDamage += memberDamage;
                    }
                }
            }

            container.damage = totalMemberDamage;

            if (found == null) {
                damagingParties.add(container);
            }
        }

        final PartyContainer mostDamageParty;
        damagingParties.sort(Comparator.comparingLong(c -> -c.damage));
        mostDamageParty = !damagingParties.isEmpty() ? damagingParties.get(0) : null;

        if ((mostDamageParty != null) && (mostDamageParty.damage > maxDamage)) {
            Player leader = mostDamageParty.party.getLeader();
            doItemDrop(leader);
            EventDropManager.getInstance().doEventDrop(leader, this);
        } else {
            doItemDrop((maxDealer != null) && maxDealer.isOnline() ? maxDealer : lastAttacker);
            EventDropManager.getInstance().doEventDrop(lastAttacker, this);
        }

        if (!death.rewardExpSp()) {
            return;
        }

        if (!rewards.isEmpty()) {
            // A party reward removes the members it paid from rewards: the walk is over a copy and skips them.
            try (ScratchBuffers.Snapshot<DamageDoneInfo> toPay = ScratchBuffers.snapshot(rewards.values())) {
                for (DamageDoneInfo reward : toPay) {
                    if (rewards.containsKey(reward.getAttacker())) {
                        payReward(reward, rewards, death, totalDamage);
                    }
                }
            }
        }
    }

    private void payReward(
            DamageDoneInfo reward, Map<Player, DamageDoneInfo> rewards, DeathRewards death, long totalDamage) {
        final Player attacker = reward.getAttacker();

        final long damage = reward.getDamage();

        final Party attackerParty = attacker.getParty();

        final float penalty =
                attacker.hasServitor() ? attacker.getSummon().asServitor().getExpMultiplier() : 1;

        if (attackerParty == null) {
            if (death.isInSurroundingRegion(attacker)) {
                final double[] expSp = calculateExpAndSp(attacker.getLevel(), damage, totalDamage);
                double exp = expSp[0];
                double sp = expSp[1];

                if (ChampionMonstersConfig.CHAMPION_ENABLE && death.champion()) {
                    exp *= ChampionMonstersConfig.CHAMPION_REWARDS_EXP_SP;
                    sp *= ChampionMonstersConfig.CHAMPION_REWARDS_EXP_SP;
                }

                exp *= penalty;

                final Creature overhitAttacker = death.overhitAttacker();
                if (overhitAttacker != null) {
                    final Player player = overhitAttacker.asPlayer();
                    if ((player != null) && (attacker == player)) {
                        attacker.sendPacket(SystemMessageId.OVER_HIT);
                        exp += exp * death.overhitExpShare();
                    }
                }

                if (!attacker.isDead()) {
                    long addExp = Math.round(attacker.calcStat(Stat.EXPSP_RATE, exp, null, null));
                    int addSp = (int) attacker.calcStat(Stat.EXPSP_RATE, sp, null, null);

                    if (attacker.hasPremiumStatus()) {
                        addExp *= PremiumSystemConfig.PREMIUM_RATE_XP;
                        addSp *= PremiumSystemConfig.PREMIUM_RATE_SP;
                    }

                    attacker.addExpAndSp(addExp, addSp, death.useVitalityRate());
                    if ((addExp > 0) && death.useVitalityRate()) {
                        attacker.updateVitalityPoints(
                                getVitalityPoints(attacker.getLevel(), damage, death.maxHp()), true, false);
                        PcCafePointsManager.getInstance().givePcCafePoint(attacker, exp);
                    }
                }
            }
        } else {
            long partyDmg = 0;
            double partyMul = 1;
            int partyLvl = 0;

            final List<Player> rewardedMembers = new ArrayList<>();

            try (ScratchBuffers.Snapshot<Player> groupMembers = ScratchBuffers.empty()) {
                (attackerParty.isInCommandChannel() ? attackerParty.getCommandChannel() : attackerParty)
                        .collectMembers(groupMembers);
                for (Player partyPlayer : groupMembers) {
                    if ((partyPlayer == null) || partyPlayer.isDead()) {
                        continue;
                    }

                    final DamageDoneInfo reward2 = rewards.get(partyPlayer);

                    if (reward2 != null) {
                        if (calculateDistance3D(partyPlayer) < PlayerConfig.ALT_PARTY_RANGE) {
                            partyDmg += reward2.getDamage();
                            rewardedMembers.add(partyPlayer);

                            if (partyPlayer.getLevel() > partyLvl) {
                                if (attackerParty.isInCommandChannel()) {
                                    partyLvl = attackerParty.getCommandChannel().getLevel();
                                } else {
                                    partyLvl = partyPlayer.getLevel();
                                }
                            }
                        }

                        rewards.remove(partyPlayer);
                    } else if (calculateDistance3D(partyPlayer) < PlayerConfig.ALT_PARTY_RANGE) {
                        rewardedMembers.add(partyPlayer);
                        if (partyPlayer.getLevel() > partyLvl) {
                            if (attackerParty.isInCommandChannel()) {
                                partyLvl = attackerParty.getCommandChannel().getLevel();
                            } else {
                                partyLvl = partyPlayer.getLevel();
                            }
                        }
                    }
                }
            }

            if (partyDmg < totalDamage) {
                partyMul = ((double) partyDmg / totalDamage);
            }

            final double[] expSp = calculateExpAndSp(partyLvl, partyDmg, totalDamage);
            double exp = expSp[0];
            double sp = expSp[1];

            if (ChampionMonstersConfig.CHAMPION_ENABLE && death.champion()) {
                exp *= ChampionMonstersConfig.CHAMPION_REWARDS_EXP_SP;
                sp *= ChampionMonstersConfig.CHAMPION_REWARDS_EXP_SP;
            }

            exp *= partyMul;
            sp *= partyMul;

            final Creature overhitAttacker = death.overhitAttacker();
            if (overhitAttacker != null) {
                final Player player = overhitAttacker.asPlayer();
                if ((player != null) && (attacker == player)) {
                    attacker.sendPacket(SystemMessageId.OVER_HIT);
                    exp += exp * death.overhitExpShare();
                }
            }

            if (partyDmg > 0) {
                attackerParty.distributeXpAndSp(
                        exp, sp, rewardedMembers, partyLvl, partyDmg, this, death.maxHp(), death.useVitalityRate());
            }
        }
    }

    @Builder(toBuilder = true)
    private record DeathRewards(
            Creature lastAttacker,
            Map<Creature, Long> damage,
            Creature overhitAttacker,
            double overhitExpShare,
            boolean rewardExpSp,
            WorldRegion region,
            int maxHp,
            boolean champion,
            boolean useVitalityRate) {
        boolean isInSurroundingRegion(Creature attacker) {
            final WorldRegion attackerRegion = attacker.getWorldRegion();
            return (region != null) && (attackerRegion != null) && attackerRegion.isSurroundingRegion(region);
        }
    }

    @Override
    public void addAttackerToAttackByList(Creature creature) {
        WorldPartition.checkOwner();
        if ((creature == null) || (creature == this)) {
            return;
        }

        addToAttackByList(creature);
    }

    public Creature getMainDamageDealer() {
        WorldPartition.checkOwner();
        if (aggroList.isEmpty()) {
            return null;
        }

        long damage = 0;
        Creature damageDealer = null;
        for (AggroInfo info : aggroList.values()) {
            if ((info != null)
                    && (info.getDamage() > damage)
                    && (calculateDistance3D(info.getAttacker()) < PlayerConfig.ALT_PARTY_RANGE)) {
                damage = info.getDamage();
                damageDealer = info.getAttacker();
            }
        }

        return damageDealer;
    }

    public void addDamage(Creature attacker, int damage, Skill skill) {
        WorldPartition.checkOwner();
        if (attacker == null) {
            return;
        }

        if (!isDead()) {
            try {
                if (isWalker()
                        && !isCoreAIDisabled()
                        && WalkingManager.getInstance().isOnWalk(this)) {
                    WalkingManager.getInstance().stopMoving(this, false, true);
                }

                getAI().notifyActionAttacked(attacker);

                final long hateValue = (long) ((((long) damage * 100) / (getLevel() + 7)) * getHateRatio(attacker));
                addDamageHate(attacker, damage, (int) hateValue);

                final Player player = attacker.asPlayer();
                if ((player != null)
                        && EventDispatcher.getInstance().hasListener(EventType.ON_ATTACKABLE_ATTACK, this)) {
                    EventDispatcher.getInstance()
                            .notifyEventAsync(
                                    OnAttackableAttack.builder()
                                            .attacker(player)
                                            .target(this)
                                            .damage(damage)
                                            .skill(skill)
                                            .summon(attacker.isSummon())
                                            .build(),
                                    this);
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    /** Hate multiplier from the AI parameters SetHateGroup, SetHateOccupation and SetHateRace. */
    public double getHateRatio(Creature attacker) {
        WorldPartition.checkOwner();
        if (!attacker.isPlayer()) {
            return 1;
        }

        final StatSet params = getTemplate().getParameters();
        if (params.isEmpty()) {
            return 1;
        }

        final Player player = attacker.asPlayer();
        int ratio = 0;
        final CategoryType group = params.getEnum("SetHateGroup", CategoryType.class, null);
        if ((group != null) && player.isInCategory(group)) {
            ratio += params.getInt("SetHateGroupRatio", 0);
        }

        if (player.getPlayerClass().getId() == params.getInt("SetHateOccupation", -1)) {
            ratio += params.getInt("SetHateOccupationRatio", 0);
        }

        if (player.getRace() == params.getEnum("SetHateRace", Race.class, null)) {
            ratio += params.getInt("SetHateRaceRatio", 0);
        }

        return 1 + (ratio / 100.0);
    }

    public void addDamageHate(Creature attacker, long damage, long aggroValue) {
        WorldPartition.checkOwner();
        if ((attacker == null) || (attacker == this)) {
            return;
        }

        if (isFakePlayer() && !FakePlayersConfig.FAKE_PLAYER_AGGRO_FPC && attacker.isFakePlayer()) {
            return;
        }

        final AggroInfo ai = aggroList.computeIfAbsent(attacker, AggroInfo::new);
        ai.addDamage(damage);

        // Trap damage cannot be told apart, so a triggered trap's hate is skipped.
        long aggro = aggroValue;
        final Player targetPlayer = attacker.asPlayer();
        if ((targetPlayer == null)
                || (targetPlayer.getTrap() == null)
                || !targetPlayer.getTrap().isTriggered()) {
            ai.addHate(aggro);
        }

        if ((targetPlayer != null) && (aggro == 0)) {
            addDamageHate(attacker, 0, 1);

            if (getAI().getIntention() == Intention.IDLE) {
                getAI().setIntentionActive();
            }

            if (EventDispatcher.getInstance().hasListener(EventType.ON_ATTACKABLE_AGGRO_RANGE_ENTER, this)) {
                EventDispatcher.getInstance()
                        .notifyEventAsync(
                                OnAttackableAggroRangeEnter.builder()
                                        .npc(this)
                                        .player(targetPlayer)
                                        .summon(attacker.isSummon())
                                        .build(),
                                this);
            }
        } else if ((targetPlayer == null) && (aggro == 0)) {
            aggro = 1;
            ai.addHate(1);
        }

        if ((aggro != 0) && (getAI().getIntention() == Intention.IDLE)) {
            getAI().setIntentionActive();
        }
    }

    public void reduceHate(Creature target, long amount) {
        WorldPartition.checkOwner();
        if ((getAI() instanceof SiegeGuardAI) || (getAI() instanceof FortSiegeGuardAI)) {
            // TODO: this just prevents error until siege guards are handled properly
            stopHating(target);
            setTarget(null);
            getAI().setIntentionIdle();
            return;
        }

        if (target == null) {
            final Creature mostHated = getMostHated();
            if (mostHated == null) {
                ((AttackableAI) getAI()).setGlobalAggro(-25);
                return;
            }

            for (AggroInfo ai : aggroList.values()) {
                ai.addHate(amount);
            }

            if (getHating(mostHated) >= 0) {
                ((AttackableAI) getAI()).setGlobalAggro(-25);
                clearAggroList();
                getAI().setIntentionActive();
                if (!isFakePlayer()) {
                    setWalking();
                }
            }
            return;
        }

        final AggroInfo ai = aggroList.get(target);
        if (ai == null) {
            return;
        }

        ai.addHate(amount);
        if ((ai.getHate() >= 0) && (getMostHated() == null)) {
            ((AttackableAI) getAI()).setGlobalAggro(-25);
            clearAggroList();
            getAI().setIntentionActive();
            if (!isFakePlayer()) {
                setWalking();
            }
        }
    }

    public void stopHating(Creature target) {
        WorldPartition.checkOwner();
        if (target == null) {
            return;
        }

        final AggroInfo ai = aggroList.get(target);
        if (ai != null) {
            ai.stopHate();
        }
    }

    public Creature getMostHated() {
        WorldPartition.checkOwner();
        if (aggroList.isEmpty() || isAlikeDead()) {
            return null;
        }

        Creature mostHated = null;
        long maxHate = 0;

        for (AggroInfo ai : aggroList.values()) {
            if (ai == null) {
                continue;
            }

            if (ai.checkHate(this) > maxHate) {
                mostHated = ai.getAttacker();
                maxHate = ai.getHate();
            }
        }

        return mostHated;
    }

    public List<Creature> get2MostHated() {
        WorldPartition.checkOwner();
        if (aggroList.isEmpty() || isAlikeDead()) {
            return null;
        }

        Creature mostHated = null;
        Creature secondMostHated = null;
        long maxHate = 0;
        final List<Creature> result = new ArrayList<>();

        for (AggroInfo ai : aggroList.values()) {
            if (ai == null) {
                continue;
            }

            if (ai.checkHate(this) > maxHate) {
                secondMostHated = mostHated;
                mostHated = ai.getAttacker();
                maxHate = ai.getHate();
            }
        }

        result.add(mostHated);

        if (isAttackedBy(secondMostHated)) {
            result.add(secondMostHated);
        } else {
            result.add(null);
        }

        return result;
    }

    /**
     * @return a read-only list of the attackers, {@code null} when the aggro list is empty or this attackable is dead
     */
    public List<Creature> getHateList() {
        WorldPartition.checkOwner();
        if (aggroList.isEmpty() || isAlikeDead()) {
            return null;
        }

        final ObjectArrayList<Creature> result = new ObjectArrayList<>(aggroList.size());
        for (AggroInfo ai : aggroList.values()) {
            if (ai == null) {
                continue;
            }

            ai.checkHate(this);

            result.add(ai.getAttacker());
        }

        return ObjectLists.unmodifiable(result);
    }

    /** Walks a copy of the aggro list, so the filter may call out and change it. */
    public void collectAttackers(Predicate<? super Creature> filter, List<? super Creature> into) {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<AggroInfo> entries = ScratchBuffers.snapshot(aggroList.values())) {
            for (AggroInfo info : entries) {
                if (filter.test(info.getAttacker())) {
                    into.add(info.getAttacker());
                }
            }
        }
    }

    /**
     * Unlike {@link #getMostHated()}, zero and negative hate count. Walks a copy, so the filter may call out. Returns
     * the first in aggro list order on a tie, {@code null} when the filter accepts none.
     */
    public Creature getMostHatedAttacker(Predicate<? super Creature> filter) {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<AggroInfo> entries = ScratchBuffers.snapshot(aggroList.values())) {
            long maxHate = Long.MIN_VALUE;
            Creature mostHated = null;
            for (AggroInfo info : entries) {
                if (filter.test(info.getAttacker()) && (info.getHate() > maxHate)) {
                    maxHate = info.getHate();
                    mostHated = info.getAttacker();
                }
            }

            return mostHated;
        }
    }

    /**
     * Negative {@code hate} lowers it.
     */
    public void addHateToAttackers(long hate) {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Creature> attackers = ScratchBuffers.snapshot(aggroList.keySet())) {
            for (Creature attacker : attackers) {
                addDamageHate(attacker, 0, hate);
            }
        }
    }

    public long getHating(Creature target) {
        WorldPartition.checkOwner();
        if (aggroList.isEmpty() || (target == null)) {
            return 0;
        }

        final AggroInfo ai = aggroList.get(target);
        if (ai == null) {
            return 0;
        }

        if (ai.getAttacker().isPlayer()) {
            final Player act = ai.getAttacker().asPlayer();
            if (act.isInvisible() || act.isInvul() || act.isSpawnProtected()) {
                aggroList.remove(target);
                return 0;
            }
        }

        if (!ai.getAttacker().isSpawned() || ai.getAttacker().isInvisible()) {
            aggroList.remove(target);
            return 0;
        }

        if (ai.getAttacker().isAlikeDead()) {
            ai.stopHate();
            return 0;
        }

        return ai.getHate();
    }

    public void doItemDrop(Creature mainDamageDealer) {
        WorldPartition.checkOwner();
        doItemDrop(getTemplate(), mainDamageDealer);
    }

    public void doItemDrop(NpcTemplate npcTemplate, Creature mainDamageDealer) {
        WorldPartition.checkOwner();
        if (mainDamageDealer == null) {
            return;
        }

        final Player player = mainDamageDealer.asPlayer();

        if (player == null) {
            // Only a fake player may drop without a player owner.
            if (mainDamageDealer.isFakePlayer() && FakePlayersConfig.FAKE_PLAYER_CAN_DROP_ITEMS) {
                final Collection<ItemHolder> deathItems =
                        npcTemplate.calculateDrops(DropType.DROP, this, mainDamageDealer);
                if (deathItems != null) {
                    for (ItemHolder drop : deathItems) {
                        final ItemTemplate item = ItemData.getInstance().getTemplate(drop.getId());

                        if (PlayerConfig.AUTO_LOOT_ITEM_IDS.contains(item.getId())
                                || isFlying()
                                || (!item.hasExImmediateEffect()
                                        && ((!raid && PlayerConfig.AUTO_LOOT)
                                                || (raid && PlayerConfig.AUTO_LOOT_RAIDS)))) {
                            // Do nothing.
                        } else if (PlayerConfig.AUTO_LOOT_HERBS && item.hasExImmediateEffect()) {
                            for (SkillHolder skillHolder : item.getSkills()) {
                                doSimultaneousCast(skillHolder.getSkill());
                            }

                            mainDamageDealer.broadcastInfo();
                        } else {
                            final Item droppedItem = dropItem(mainDamageDealer, drop);
                            if (FakePlayersConfig.FAKE_PLAYER_CAN_PICKUP && (droppedItem != null)) {
                                mainDamageDealer.addFakePlayerDrop(droppedItem);
                            }
                        }
                    }

                    deathItems.clear();
                }
            }
            return;
        }

        CursedWeaponsManager.getInstance().checkDrop(this, player);

        if (isSpoiled()) {
            sweepItems = npcTemplate.calculateDrops(DropType.SPOIL, this, player);
        }

        final Collection<ItemHolder> deathItems = npcTemplate.calculateDrops(DropType.DROP, this, player);
        if (deathItems != null) {
            for (ItemHolder drop : deathItems) {
                final ItemTemplate item = ItemData.getInstance().getTemplate(drop.getId());

                if (PlayerConfig.AUTO_LOOT_ITEM_IDS.contains(item.getId())
                        || isFlying()
                        || (!item.hasExImmediateEffect()
                                && ((!raid && PlayerConfig.AUTO_LOOT) || (raid && PlayerConfig.AUTO_LOOT_RAIDS)))
                        || (item.hasExImmediateEffect() && PlayerConfig.AUTO_LOOT_HERBS)) {
                    player.doAutoLoot(this, drop);
                } else {
                    dropItem(player, drop);
                }

                if (raid && !raidMinion && (drop.getCount() > 0)) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.C1_DIED_AND_DROPPED_S3_S2);
                    sm.addString(getName());
                    sm.addItemName(item);
                    sm.addLong(drop.getCount());
                    broadcastPacket(sm);
                }
            }

            deathItems.clear();
        }
    }

    public Item getActiveWeapon() {
        WorldPartition.checkOwner();
        return null;
    }

    public boolean isInAggroList(Creature creature) {
        WorldPartition.checkOwner();
        return (creature != null) && aggroList.containsKey(creature);
    }

    public void clearAggroList() {
        WorldPartition.checkOwner();
        aggroList.clear();

        overhit = false;
        overhitDamage = 0;
        overhitAttacker = null;
    }

    @Override
    public boolean isSweepActive() {
        WorldPartition.checkOwner();
        return sweepItems != null;
    }

    public List<ItemTemplate> getSpoilLootItems() {
        WorldPartition.checkOwner();
        final List<ItemTemplate> lootItems = new ArrayList<>();
        if (sweepItems != null) {
            for (ItemHolder item : sweepItems) {
                lootItems.add(ItemData.getInstance().getTemplate(item.getId()));
            }
        }

        return lootItems;
    }

    public Collection<ItemHolder> takeSweep() {
        WorldPartition.checkOwner();
        final Collection<ItemHolder> items = sweepItems;
        sweepItems = null;
        return items;
    }

    public ItemHolder takeHarvest() {
        WorldPartition.checkOwner();
        final ItemHolder item = harvestItem;
        harvestItem = null;
        return item;
    }

    public boolean isOldCorpse(Player attacker, int remainingTime, boolean sendMessage) {
        WorldPartition.checkOwner();
        if (!isDead() || (DecayTaskManager.getInstance().getRemainingTime(this) >= remainingTime)) {
            return false;
        }

        if (sendMessage && (attacker != null)) {
            attacker.sendPacket(SystemMessageId.THE_CORPSE_IS_TOO_OLD_THE_SKILL_CANNOT_BE_USED);
        }

        return true;
    }

    public boolean checkSpoilOwner(Player sweeper, boolean sendMessage) {
        WorldPartition.checkOwner();
        if ((sweeper.getObjectId() == spoilerObjectId) || sweeper.isInLooterParty(spoilerObjectId)) {
            return true;
        }

        if (sendMessage) {
            sweeper.sendPacket(SystemMessageId.THERE_ARE_NO_PRIORITY_RIGHTS_ON_A_SWEEPER);
        }

        return false;
    }

    public void overhitEnabled(boolean status) {
        WorldPartition.checkOwner();
        overhit = status;
    }

    public void setOverhitValues(Creature attacker, double damage) {
        WorldPartition.checkOwner();
        // Damage beyond the HP left: 10 HP left and 50 dealt is an over-hit of 40.
        final double overhitDmg = -(getCurrentHp() - damage);
        if (overhitDmg < 0) {
            // The strike did not kill, so it was not an over-hit.
            overhitEnabled(false);
            overhitDamage = 0;
            overhitAttacker = null;
            return;
        }

        overhitEnabled(true);
        overhitDamage = overhitDmg;
        overhitAttacker = attacker;
    }

    public void absorbSoul() {
        WorldPartition.checkOwner();
        absorbed = true;
    }

    public void addAbsorber(Player attacker) {
        WorldPartition.checkOwner();
        final AbsorberInfo ai = absorbersList.get(attacker.getObjectId());

        if (ai == null) {
            absorbersList.put(attacker.getObjectId(), new AbsorberInfo(attacker.getObjectId(), getCurrentHp()));
        } else {
            ai.setAbsorbedHp(getCurrentHp());
        }

        absorbSoul();
    }

    public void resetAbsorbList() {
        WorldPartition.checkOwner();
        absorbed = false;
        absorbersList.clear();
    }

    private double[] calculateExpAndSp(int charLevel, long damage, long totalDamage) {
        final int levelDiff = charLevel - getLevel();
        double xp = 0;
        double sp = 0;

        if ((levelDiff < RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE)
                && (levelDiff > -RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE)) {
            xp = Math.max(0, (getExpReward(charLevel) * damage) / totalDamage);
            sp = Math.max(0, (getSpReward(charLevel) * damage) / totalDamage);

            if ((charLevel > 84) && (levelDiff <= -3)) {
                double mul;
                switch (levelDiff) {
                    case -3: {
                        mul = 0.97;
                        break;
                    }
                    case -4: {
                        mul = 0.67;
                        break;
                    }
                    case -5: {
                        mul = 0.42;
                        break;
                    }
                    case -6: {
                        mul = 0.25;
                        break;
                    }
                    case -7: {
                        mul = 0.15;
                        break;
                    }
                    case -8: {
                        mul = 0.09;
                        break;
                    }
                    case -9: {
                        mul = 0.05;
                        break;
                    }
                    case -10: {
                        mul = 0.03;
                        break;
                    }
                    default: {
                        mul = 1;
                        break;
                    }
                }

                xp *= mul;
                sp *= mul;
            }
        }

        return new double[] {xp, sp};
    }

    private double overhitExpShare() {
        return Math.min(overhitDamage / getMaxHp(), 0.25);
    }

    @Override
    public boolean canBeAttacked() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public void onSpawn() {
        WorldPartition.checkOwner();
        spawnTime = ClockBridge.gameMillis();

        super.onSpawn();

        setSpoilerObjectId(0);

        clearAggroList();

        harvestItem = null;

        if (isFakePlayer()) {
            clearFakePlayerDrops();
            setKarma(0);
            setScriptValue(0);
            setRunning();
        } else {
            setWalking();
        }

        seeded = false;
        seed = null;
        seederId = 0;

        overhitEnabled(false);

        sweepItems = null;
        resetAbsorbList();

        setWalking();

        if (hasAI()) {
            // Roll the aggro timeout again, the AI object survives a respawn.
            if (getAI() instanceof AttackableAI) {
                ((AttackableAI) getAI()).rollGlobalAggro();
            }

            getAI().setIntentionActive();

            if (!isInActiveRegion()) {
                getAI().stopAITask();
            }
        }
    }

    public boolean isSpoiled() {
        WorldPartition.checkOwner();
        return spoilerObjectId != 0;
    }

    /** Needs the seed parameters set first, see {@link #setSeeded(Seed, Player)}. */
    public void setSeeded(Player seeder) {
        WorldPartition.checkOwner();
        if ((seed == null) || (seederId != seeder.getObjectId())) {
            return;
        }

        seeded = true;
        int count = 1;
        for (int skillId : getTemplate().getSkills().keySet()) {
            switch (skillId) {
                case 4303: {
                    count *= 2;
                    break;
                }
                case 4304: {
                    count *= 3;
                    break;
                }
                case 4305: {
                    count *= 4;
                    break;
                }
                case 4306: {
                    count *= 5;
                    break;
                }
                case 4307: {
                    count *= 6;
                    break;
                }
                case 4308: {
                    count *= 7;
                    break;
                }
                case 4309: {
                    count *= 8;
                    break;
                }
                case 4310: {
                    count *= 9;
                    break;
                }
            }
        }

        final int diff = getLevel() - seed.getLevel() - 5;
        if (diff > 0) {
            count += diff;
        }

        harvestItem = new ItemHolder(seed.getCropId(), count * RatesConfig.RATE_DROP_MANOR);
    }

    public void setSeeded(Seed seed, Player seeder) {
        WorldPartition.checkOwner();
        if (seeded) {
            return;
        }

        this.seed = seed;
        seederId = seeder.getObjectId();
    }

    // Here because Monster and FriendlyMob both extend this class.
    @Override
    public boolean hasRandomAnimation() {
        WorldPartition.checkOwner();
        return ((GeneralConfig.MAX_MONSTER_ANIMATION > 0)
                && isRandomAnimationEnabled()
                && !(this instanceof GrandBoss));
    }

    public void returnHome() {
        WorldPartition.checkOwner();
        clearAggroList();

        if (hasAI() && (getSpawn() != null)) {
            getAI().setIntentionMoveTo(getSpawn().getLocation());
        }
    }

    /*
     * Return vitality points decrease (if positive) or increase (if negative) based on damage. Maximum for damage = maxHp.
     */
    public float getVitalityPoints(int level, long damage, int maxHp) {
        WorldPartition.checkOwner();
        if (damage <= 0) {
            return 0;
        }

        final long expReward = getExpReward(level);
        final float divider = (getLevel() > 0) && (expReward > 0)
                ? (getTemplate().getBaseHpMax() * 9 * getLevel() * getLevel()) / (100 * expReward)
                : 0;
        if (divider == 0) {
            return 0;
        }

        return -Math.min(damage, maxHp) / divider;
    }

    public boolean useVitalityRate() {
        WorldPartition.checkOwner();
        return !champion || ChampionMonstersConfig.CHAMPION_ENABLE_VITALITY;
    }

    public void setIsRaid(boolean isRaid) {
        WorldPartition.checkOwner();
        raid = isRaid;
    }

    public void setIsRaidMinion(boolean value) {
        WorldPartition.checkOwner();
        raid = value;
        raidMinion = value;
    }

    @Override
    public boolean isMinion() {
        WorldPartition.checkOwner();
        return getLeader() != null;
    }

    public Attackable getLeader() {
        WorldPartition.checkOwner();
        return null;
    }

    public void setChampion(boolean champ) {
        WorldPartition.checkOwner();
        champion = champ;
        if (ChampionMonstersConfig.SHOW_CHAMPION_AURA) {
            setTeam(champ ? Team.RED : Team.NONE);
        }
    }

    @Override
    public boolean isAttackable() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public Attackable asAttackable() {
        WorldPartition.checkOwner();
        return this;
    }
}
