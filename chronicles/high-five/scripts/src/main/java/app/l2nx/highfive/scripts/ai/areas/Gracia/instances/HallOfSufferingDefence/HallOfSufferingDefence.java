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
package app.l2nx.highfive.scripts.ai.areas.Gracia.instances.HallOfSufferingDefence;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.entity.instancezone.InstanceWorld;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExSendUIEvent;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.scripts.quests.Q00695_DefendTheHallOfSuffering.Q00695_DefendTheHallOfSuffering;
import app.l2nx.highfive.util.LocationUtil;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HallOfSufferingDefence extends Script {
    @SingleWriter
    private class HSDWorld extends InstanceWorld {
        public HSDWorld() {}

        private Map<Npc, Boolean> npcList = new HashMap<>();
        private Npc klodekus = null;
        private Npc klanikus = null;
        private boolean bossesAttacked = false;
        private long[] storeTime = {0, 0}; // 0: instance start, 1: finish time

        public Map<Npc, Boolean> getNpcList() {
            WorldPartition.checkOwner();
            return Collections.unmodifiableMap(new LinkedHashMap<>(npcList));
        }

        public void putNpcList(Npc key, Boolean value) {
            WorldPartition.checkOwner();
            npcList.put(key, value);
        }

        public boolean containsNpcList(Npc key) {
            WorldPartition.checkOwner();
            return npcList.containsKey(key);
        }

        public Npc getKlodekus() {
            WorldPartition.checkOwner();
            return klodekus;
        }

        public void setKlodekus(Npc klodekus) {
            WorldPartition.checkOwner();
            this.klodekus = klodekus;
        }

        public Npc getKlanikus() {
            WorldPartition.checkOwner();
            return klanikus;
        }

        public void setKlanikus(Npc klanikus) {
            WorldPartition.checkOwner();
            this.klanikus = klanikus;
        }

        public boolean isBossesAttacked() {
            WorldPartition.checkOwner();
            return bossesAttacked;
        }

        public void setBossesAttacked(boolean bossesAttacked) {
            WorldPartition.checkOwner();
            this.bossesAttacked = bossesAttacked;
        }

        public long getStoreTime(int index) {
            WorldPartition.checkOwner();
            return storeTime[index];
        }

        public void setStoreTime(int index, long value) {
            WorldPartition.checkOwner();
            storeTime[index] = value;
        }
    }

    private static final int INSTANCEID = 116; // this is the client number
    private static final boolean debug = false;

    // NPCs
    private static final int MOUTHOFEKIMUS = 32537;
    private static final int TEPIOS = 32530;

    // mobs
    private static final int KLODEKUS = 25665;
    private static final int KLANIKUS = 25666;
    private static final int TUMOR_ALIVE = 18704;
    private static final int TUMOR_DEAD = 32531;

    // @formatter:off
    private static final int[] ENTER_TELEPORT = {-174701, 218109, -9592};
    private static final int[] TUMOR_MOBIDS = {22509, 22510, 22511, 22512, 22513, 22514, 22515};
    private static final int[] TWIN_MOBIDS = {22509, 22510, 22511, 22512, 22513};

    // Doors/Walls/Zones
    private static final int[][] ROOM_1_MOBS = {
        {22509, -173712, 217838, -9559},
        {22509, -173489, 218281, -9557},
        {22509, -173824, 218389, -9558},
        {22510, -174018, 217970, -9559},
        {22510, -173382, 218198, -9547}
    };
    private static final int[][] ROOM_2_MOBS = {
        {22511, -173456, 217976, -9556}, {22511, -173673, 217951, -9547}, {22509, -173622, 218233, -9547},
        {22510, -173775, 218218, -9545}, {22510, -173660, 217980, -9542}, {22510, -173712, 217838, -9559}
    };
    private static final int[][] ROOM_3_MOBS = {
        {22512, -173489, 218281, -9557}, {22512, -173824, 218389, -9558}, {22512, -174018, 217970, -9559},
        {22509, -173382, 218198, -9547}, {22511, -173456, 217976, -9556}, {22511, -173673, 217951, -9547},
        {22510, -173622, 218233, -9547}, {22510, -173775, 218218, -9545}
    };
    private static final int[][] ROOM_4_MOBS = {
        {22514, -173660, 217980, -9542}, {22514, -173712, 217838, -9559}, {22514, -173489, 218281, -9557},
        {22513, -173824, 218389, -9558}, {22513, -174018, 217970, -9559}, {22511, -173382, 218198, -9547},
        {22511, -173456, 217976, -9556}, {22512, -173673, 217951, -9547}, {22512, -173622, 218233, -9547}
    };
    private static final int[][] ROOM_5_MOBS = {
        {22512, -173775, 218218, -9545}, {22512, -173660, 217980, -9542}, {22512, -173712, 217838, -9559},
        {22513, -173489, 218281, -9557}, {22513, -173824, 218389, -9558}, {22514, -174018, 217970, -9559},
        {22514, -173382, 218198, -9547}, {22514, -173456, 217976, -9556}, {22515, -173673, 217951, -9547},
        {22515, -173622, 218233, -9547}
    };
    private static final int[][] TUMOR_SPAWNS = {
        {-173727, 218109, -9536},
        {-173727, 218109, -9536},
        {-173727, 218109, -9536},
        {-173727, 218109, -9536},
        {-173727, 218109, -9536}
    };
    private static final int[][] TWIN_SPAWNS = {{25665, -173727, 218169, -9536}, {25666, -173727, 218049, -9536}};
    private static final int[] TEPIOS_SPAWN = {-173727, 218109, -9536};
    // @formatter:on

    private static final int BOSS_INVUL_TIME = 30000;
    private static final int BOSS_MINION_SPAWN_TIME = 60000;
    private static final int BOSS_RESSURECT_TIME = 20000;

    private static final int INSTANCEPENALTY = 24;

    public HallOfSufferingDefence() {
        addStartNpc(MOUTHOFEKIMUS);
        addStartNpc(TEPIOS);
        addTalkId(MOUTHOFEKIMUS);
        addTalkId(TEPIOS);

        addKillId(TUMOR_ALIVE);
        addKillId(KLODEKUS);
        addKillId(KLANIKUS);

        addAttackId(KLODEKUS);
        addAttackId(KLANIKUS);

        addSkillSeeId(TUMOR_MOBIDS);
        addKillId(TUMOR_MOBIDS);
    }

    private boolean checkConditions(Player player) {
        if (debug || player.isGM()) {
            return true;
        }

        final Party party = player.getParty();
        if (party == null) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_CURRENTLY_IN_A_PARTY_SO_YOU_CANNOT_ENTER);
            return false;
        }

        if (party.getLeader() != player) {
            player.sendPacket(SystemMessageId.ONLY_A_PARTY_LEADER_CAN_MAKE_THE_REQUEST_TO_ENTER);
            return false;
        }

        try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
            party.collectMembers(members);
            for (Player partyMember : members) {
                if ((partyMember.getLevel() < 75) || (partyMember.getLevel() > 82)) {
                    final SystemMessage sm = new SystemMessage(
                            SystemMessageId.C1_S_LEVEL_DOES_NOT_CORRESPOND_TO_THE_REQUIREMENTS_FOR_ENTRY);
                    sm.addPcName(partyMember);
                    party.broadcastPacket(sm);
                    return false;
                }

                if (!LocationUtil.checkIfInRange(1000, player, partyMember, true)) {
                    final SystemMessage sm = new SystemMessage(
                            SystemMessageId
                                    .C1_IS_IN_A_LOCATION_WHICH_CANNOT_BE_ENTERED_THEREFORE_IT_CANNOT_BE_PROCESSED);
                    sm.addPcName(partyMember);
                    party.broadcastPacket(sm);
                    return false;
                }

                final Long reentertime =
                        InstanceManager.getInstance().getInstanceTime(partyMember.getObjectId(), INSTANCEID);
                if (ClockBridge.wallMillis() < reentertime) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.C1_MAY_NOT_RE_ENTER_YET);
                    sm.addPcName(partyMember);
                    party.broadcastPacket(sm);
                    return false;
                }

                final QuestState qs = partyMember.getQuestState(Q00695_DefendTheHallOfSuffering.class.getSimpleName());
                if ((qs == null) || !qs.isCond(1)) {
                    final SystemMessage sm = new SystemMessage(
                            SystemMessageId.C1_S_QUEST_REQUIREMENT_IS_NOT_SUFFICIENT_AND_CANNOT_BE_ENTERED);
                    sm.addPcName(partyMember);
                    party.broadcastPacket(sm);
                    return false;
                }
            }
        }

        return true;
    }

    private void teleportPlayer(Player player, int[] coords, int instanceId) {
        player.setInstanceId(instanceId);
        player.teleToLocation(coords[0], coords[1], coords[2]);
    }

    protected void enterInstance(Player player, int[] coords) {
        // check for existing instances for this player
        InstanceWorld world = InstanceManager.getInstance().getPlayerWorld(player);

        // existing instance
        if (world != null) {
            if (!(world instanceof HSDWorld)) {
                player.sendPacket(
                        SystemMessageId
                                .YOU_HAVE_ENTERED_ANOTHER_INSTANCE_ZONE_THEREFORE_YOU_CANNOT_ENTER_CORRESPONDING_DUNGEON);
                return;
            }

            teleportPlayer(player, coords, world.getInstanceId());
            return;
        }

        if (checkConditions(player)) {
            world = new HSDWorld();
            world.setInstance(InstanceManager.getInstance().createDynamicInstance(INSTANCEID));
            ((HSDWorld) world).setStoreTime(0, ClockBridge.gameMillis());
            InstanceManager.getInstance().addWorld(world);
            log.info(
                    "Hall Of Suffering Defence started {} Instance: {} created by player: {}",
                    INSTANCEID,
                    world.getInstanceId(),
                    player.getName());
            runTumors((HSDWorld) world);

            final Party party = player.getParty();
            if (party == null) {
                teleportPlayer(player, coords, world.getInstanceId());
                world.addAllowed(player);
            } else {
                try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                    party.collectMembers(members);
                    for (Player partyMember : members) {
                        teleportPlayer(partyMember, coords, world.getInstanceId());
                        world.addAllowed(partyMember);
                    }
                }
            }
        }
    }

    protected boolean checkKillProgress(Npc mob, HSDWorld world) {
        if (world.containsNpcList(mob)) {
            world.putNpcList(mob, true);
        }

        for (boolean isDead : world.getNpcList().values()) {
            if (!isDead) {
                return false;
            }
        }

        return true;
    }

    protected int[][] getRoomSpawns(int room) {
        switch (room) {
            case 0: {
                return ROOM_1_MOBS;
            }
            case 1: {
                return ROOM_2_MOBS;
            }
            case 2: {
                return ROOM_3_MOBS;
            }
            case 3: {
                return ROOM_4_MOBS;
            }
            case 4: {
                return ROOM_5_MOBS;
            }
        }

        return new int[][] {};
    }

    protected void runTumors(HSDWorld world) {
        for (int[] mob : getRoomSpawns(world.getStatus())) {
            final Npc npc = addSpawn(mob[0], mob[1], mob[2], mob[3], 0, false, 0, false, world.getInstanceId());
            world.putNpcList(npc, false);
        }

        final Npc mob = addSpawn(
                TUMOR_ALIVE,
                TUMOR_SPAWNS[world.getStatus()][0],
                TUMOR_SPAWNS[world.getStatus()][1],
                TUMOR_SPAWNS[world.getStatus()][2],
                0,
                false,
                0,
                false,
                world.getInstanceId());
        mob.disableCoreAI(true);
        mob.setImmobilized(true);
        mob.setCurrentHp(mob.getMaxHp() * 0.5);
        world.putNpcList(mob, false);
        world.incStatus();
    }

    protected void runTwins(HSDWorld world) {
        world.incStatus();
        world.setKlodekus(addSpawn(
                TWIN_SPAWNS[0][0],
                TWIN_SPAWNS[0][1],
                TWIN_SPAWNS[0][2],
                TWIN_SPAWNS[0][3],
                0,
                false,
                0,
                false,
                world.getInstanceId()));
        world.setKlanikus(addSpawn(
                TWIN_SPAWNS[1][0],
                TWIN_SPAWNS[1][1],
                TWIN_SPAWNS[1][2],
                TWIN_SPAWNS[1][3],
                0,
                false,
                0,
                false,
                world.getInstanceId()));
        world.getKlanikus().setMortal(false);
        world.getKlodekus().setMortal(false);
    }

    protected void bossSimpleDie(Npc boss) {
        // killing is only possible one time
        synchronized (this) {
            if (boss.isDead()) {
                return;
            }

            // now reset currentHp to zero
            boss.setCurrentHp(0);
            boss.setDead(true);
        }

        // Set target to null and cancel Attack or Cast
        boss.setTarget(null);

        // Stop movement
        boss.stopMove(null);

        // Stop HP/MP/CP Regeneration task
        boss.getStatus().stopHpMpRegeneration();

        boss.stopAllEffectsExceptThoseThatLastThroughDeath();

        // Send the Server->Client packet StatusUpdate with current HP and MP to all other Player to inform
        boss.broadcastStatusUpdate();

        // Notify Creature AI
        boss.getAI().notifyActionDeath();

        // if (boss.getWorldRegion() != null)
        // {
        // boss.getWorldRegion().onDeath(boss);
        // }
    }

    @Override
    public void onSkillSee(Npc npc, Player caster, Skill skill, List<WorldObject> targets, boolean isSummon) {
        if (skill.hasEffectType(EffectType.REBALANCE_HP, EffectType.HEAL)) {
            int hate = 2 * skill.getEffectPoint();
            if (hate < 2) {
                hate = 1000;
            }

            npc.asAttackable().addDamageHate(caster, 0, hate);
        }
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        final InstanceWorld tmpworld = InstanceManager.getInstance().getWorld(npc);
        if (tmpworld instanceof HSDWorld) {
            final HSDWorld world = (HSDWorld) tmpworld;
            if (event.equalsIgnoreCase("spawnBossGuards")) {
                if (!world.getKlanikus().isInCombat() && !world.getKlodekus().isInCombat()) {
                    world.setBossesAttacked(false);
                    return "";
                }

                Npc mob = addSpawn(
                        getRandomEntry(TWIN_MOBIDS),
                        TWIN_SPAWNS[0][1],
                        TWIN_SPAWNS[0][2],
                        TWIN_SPAWNS[0][3],
                        0,
                        false,
                        0,
                        false,
                        npc.getInstanceId());
                mob.asAttackable().addDamageHate(npc.asAttackable().getMostHated(), 0, 1);
                if (getRandom(100) < 33) {
                    mob = addSpawn(
                            getRandomEntry(TWIN_MOBIDS),
                            TWIN_SPAWNS[1][1],
                            TWIN_SPAWNS[1][2],
                            TWIN_SPAWNS[1][3],
                            0,
                            false,
                            0,
                            false,
                            npc.getInstanceId());
                    mob.asAttackable().addDamageHate(npc.asAttackable().getMostHated(), 0, 1);
                }

                startQuestTimer("spawnBossGuards", BOSS_MINION_SPAWN_TIME, npc, null);
            } else if (event.equalsIgnoreCase("isTwinSeparated")) {
                if (LocationUtil.checkIfInRange(500, world.getKlanikus(), world.getKlodekus(), false)) {
                    world.getKlanikus().setInvul(false);
                    world.getKlodekus().setInvul(false);
                } else {
                    world.getKlanikus().setInvul(true);
                    world.getKlodekus().setInvul(true);
                }

                startQuestTimer("isTwinSeparated", 10000, npc, null);
            } else if (event.equalsIgnoreCase("ressurectTwin")) {
                final Skill skill = SkillData.getInstance().getSkill(5824, 1);
                final Npc aliveTwin = (world.getKlanikus() == npc ? world.getKlodekus() : world.getKlanikus());
                npc.doRevive();
                npc.doCast(skill);
                npc.setCurrentHp(aliveTwin.getCurrentHp());

                // get most hated of other boss
                final Creature hated = aliveTwin.asMonster().getMostHated();
                if (hated != null) {
                    npc.getAI().notifyActionAggression(hated, 1000);
                }

                aliveTwin.setInvul(true); // make other boss invul
                startQuestTimer("uninvul", BOSS_INVUL_TIME, aliveTwin, null);
            } else if (event.equals("uninvul")) {
                npc.setInvul(false);
            }
        }

        return "";
    }

    @Override
    public void onAttack(Npc npc, Player attacker, int damage, boolean isSummon, Skill skill) {
        final InstanceWorld tmpworld = InstanceManager.getInstance().getWorld(npc);
        if (tmpworld instanceof HSDWorld) {
            final HSDWorld world = (HSDWorld) tmpworld;
            if (!world.isBossesAttacked()) {
                world.setBossesAttacked(true);
                final Calendar reenter = ClockBridge.calendar();
                reenter.add(Calendar.HOUR, INSTANCEPENALTY);

                final SystemMessage sm = new SystemMessage(
                        SystemMessageId
                                .INSTANT_ZONE_S1_S_ENTRY_HAS_BEEN_RESTRICTED_YOU_CAN_CHECK_THE_NEXT_POSSIBLE_ENTRY_TIME_BY_USING_THE_COMMAND_INSTANCEZONE);
                sm.addInstanceName(INSTANCEID);

                // set instance reenter time for all allowed players
                for (Player player : tmpworld.getAllowed()) {
                    if (player != null) {
                        InstanceManager.getInstance()
                                .setInstanceTime(player.getObjectId(), INSTANCEID, reenter.getTimeInMillis());
                        if (player.isOnline()) {
                            player.sendPacket(sm);
                        }
                    }
                }

                startQuestTimer("spawnBossGuards", BOSS_MINION_SPAWN_TIME, npc, null);
                startQuestTimer("isTwinSeparated", 10000, npc, null);
            } else if (damage >= npc.getCurrentHp()) {
                if (world.getKlanikus().isDead()) {
                    world.getKlanikus().setDead(false);
                    world.getKlanikus().doDie(attacker);
                    world.getKlodekus().doDie(attacker);
                } else if (((HSDWorld) tmpworld).getKlodekus().isDead()) {
                    world.getKlodekus().setDead(false);
                    world.getKlodekus().doDie(attacker);
                    world.getKlanikus().doDie(attacker);
                } else {
                    bossSimpleDie(npc);
                    startQuestTimer("ressurectTwin", BOSS_RESSURECT_TIME, npc, null);
                }
            }
        }
    }

    @Override
    public void onKill(Npc npc, Player player, boolean isSummon) {
        final InstanceWorld tmpworld = InstanceManager.getInstance().getWorld(npc);
        if (tmpworld instanceof HSDWorld) {
            final HSDWorld world = (HSDWorld) tmpworld;

            if (npc.getId() == TUMOR_ALIVE) {
                npc.deleteMe();
                addSpawn(
                        TUMOR_DEAD,
                        npc.getX(),
                        npc.getY(),
                        npc.getZ(),
                        npc.getHeading(),
                        false,
                        0,
                        false,
                        npc.getInstanceId());
            }

            if (world.getStatus() < 5) {
                if (checkKillProgress(npc, world)) {
                    runTumors(world);
                }
            } else if (world.getStatus() == 5) {
                if (checkKillProgress(npc, world)) {
                    runTwins(world);
                }
            } else if (((world.getStatus() == 6) && ((npc.getId() == KLODEKUS) || (npc.getId() == KLANIKUS))) //
                    && (world.getKlanikus().isDead() && world.getKlodekus().isDead())) {
                world.incStatus();
                world.setStoreTime(1, ClockBridge.gameMillis());

                final Long finishDiff = world.getStoreTime(1) - world.getStoreTime(0);
                if (finishDiff < 1260000) {
                    world.setParameter("tag", 13777);
                } else if (finishDiff < 1380000) {
                    world.setParameter("tag", 13778);
                } else if (finishDiff < 1500000) {
                    world.setParameter("tag", 13779);
                } else if (finishDiff < 1620000) {
                    world.setParameter("tag", 13780);
                } else if (finishDiff < 1740000) {
                    world.setParameter("tag", 13781);
                } else if (finishDiff < 1860000) {
                    world.setParameter("tag", 13782);
                } else if (finishDiff < 1980000) {
                    world.setParameter("tag", 13783);
                } else if (finishDiff < 2100000) {
                    world.setParameter("tag", 13784);
                } else if (finishDiff < 2220000) {
                    world.setParameter("tag", 13785);
                } else {
                    world.setParameter("tag", 13786);
                }

                world.setKlanikus(null);
                world.setKlodekus(null);
                cancelQuestTimers("ressurectTwin");
                cancelQuestTimers("spawnBossGuards");
                cancelQuestTimers("isTwinSeparated");
                addSpawn(
                        TEPIOS,
                        TEPIOS_SPAWN[0],
                        TEPIOS_SPAWN[1],
                        TEPIOS_SPAWN[2],
                        0,
                        false,
                        0,
                        false,
                        world.getInstanceId());

                for (Player killer : world.getAllowed()) {
                    if (killer != null) {
                        killer.sendPacket(new ExSendUIEvent(killer, true, true, 0, 0, ""));
                    }
                }

                final Instance inst = InstanceManager.getInstance().getInstance(world.getInstanceId());
                inst.setDuration(5 * 60000);
                inst.setEmptyDestroyTime(0);
            }
        }
    }

    @Override
    public String onTalk(Npc npc, Player player) {
        if (npc.getId() == MOUTHOFEKIMUS) {
            enterInstance(player, ENTER_TELEPORT);
            return null;
        }

        return "";
    }
}
