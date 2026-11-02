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
package app.l2nx.highfive.entity.actor.holders.npc;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.Collection;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

/**
 * @author luisantonioa, DS, Mobius
 */
@SingleWriter
public class MinionList {
    private static final int SPAWN_OFFSET_RADIUS = 200;
    private static final int MINIMUM_SPAWN_RADIUS = 30;
    private static final int BASE_AGGRO_AMOUNT = 1;
    private static final int MASTER_AGGRO_AMOUNT = 10;
    private static final int RAID_AGGRO_MULTIPLIER = 10;
    private static final int INVALID_RESPAWN_TIME = -1;
    private static final int NO_RESPAWN = 0;

    private final Monster master;

    private final ObjectLinkedOpenHashSet<Monster> spawnedMinions = new ObjectLinkedOpenHashSet<>();
    private final ObjectArrayList<ScheduledFuture<?>> respawnTasks = new ObjectArrayList<>();

    public MinionList(Monster master) {
        if (master == null) {
            throw new NullPointerException("MinionList: Master monster cannot be null!");
        }

        this.master = master;
    }

    public Collection<Monster> getSpawnedMinions() {
        WorldPartition.checkOwner();
        return spawnedMinions.isEmpty()
                ? ObjectLists.emptyList()
                : ObjectLists.unmodifiable(new ObjectArrayList<>(spawnedMinions));
    }

    public void spawnMinions(Collection<MinionHolder> minions) {
        WorldPartition.checkOwner();
        if (master.isAlikeDead() || (minions == null)) {
            return;
        }

        int minionCount;
        int minionId;
        int minionsToSpawn;
        for (MinionHolder minion : minions) {
            minionCount = minion.getCount();
            minionId = minion.getId();
            minionsToSpawn = minionCount - countSpawnedMinionsById(minionId);
            if (minionsToSpawn > 0) {
                for (int i = 0; i < minionsToSpawn; i++) {
                    spawnMinion(minionId);
                }
            }
        }
    }

    public void onMinionSpawn(Monster minion) {
        WorldPartition.checkOwner();
        spawnedMinions.add(minion);
    }

    public void onMasterDie(boolean force) {
        WorldPartition.checkOwner();
        if (master.isRaid() || force || NpcConfig.FORCE_DELETE_MINIONS) {
            if (!spawnedMinions.isEmpty()) {
                try (ScratchBuffers.Snapshot<Monster> walk = ScratchBuffers.snapshot(spawnedMinions)) {
                    for (Monster minion : walk) {
                        if (minion != null) {
                            minion.setLeader(null);
                            minion.deleteMe();
                        }
                    }
                }

                spawnedMinions.clear();
            }

            if (!respawnTasks.isEmpty()) {
                for (ScheduledFuture<?> task : respawnTasks) {
                    if ((task != null) && !task.isCancelled() && !task.isDone()) {
                        task.cancel(true);
                    }
                }

                respawnTasks.clear();
            }
        }
    }

    /** {@code respawnTime} is in milliseconds; -1 uses the default, 0 disables respawn. */
    public void onMinionDie(Monster minion, int respawnTime) {
        WorldPartition.checkOwner();
        // Prevent memory leaks.
        if (respawnTime == NO_RESPAWN) {
            minion.setLeader(null);
        }

        spawnedMinions.remove(minion);

        final int actualRespawnTime = respawnTime <= INVALID_RESPAWN_TIME
                ? master.isRaid() ? (int) NpcConfig.RAID_MINION_RESPAWN_TIMER : NO_RESPAWN
                : respawnTime;
        if ((actualRespawnTime > NO_RESPAWN) && !master.isAlikeDead()) {
            final MinionRespawnTask task = new MinionRespawnTask(minion);
            task.future = WorldPartition.loop().schedule(task, actualRespawnTime, TimeUnit.MILLISECONDS);
            respawnTasks.add(task.future);
        }
    }

    public void onAssist(Creature caller, Creature attacker) {
        WorldPartition.checkOwner();
        if (attacker == null) {
            return;
        }

        if (!master.isAlikeDead() && !master.isInCombat()) {
            master.addDamageHate(attacker, 0, BASE_AGGRO_AMOUNT);
        }

        final boolean callerIsMaster = caller == master;
        int aggroAmount = callerIsMaster ? MASTER_AGGRO_AMOUNT : BASE_AGGRO_AMOUNT;
        if (master.isRaid()) {
            aggroAmount *= RAID_AGGRO_MULTIPLIER;
        }

        try (ScratchBuffers.Snapshot<Monster> walk = ScratchBuffers.snapshot(spawnedMinions)) {
            for (Monster minion : walk) {
                if ((minion != null) && !minion.isDead() && (callerIsMaster || !minion.isInCombat())) {
                    minion.addDamageHate(attacker, 0, aggroAmount);
                }
            }
        }
    }

    public void onMasterTeleported() {
        WorldPartition.checkOwner();
        final int spawnRadius = SPAWN_OFFSET_RADIUS;
        final int minimumRadius = (int) master.getCollisionRadius() + MINIMUM_SPAWN_RADIUS;
        final RandomGenerator random = ClockBridge.random();
        try (ScratchBuffers.Snapshot<Monster> walk = ScratchBuffers.snapshot(spawnedMinions)) {
            for (Monster minion : walk) {
                if ((minion != null) && !minion.isDead() && !minion.isMovementDisabled()) {
                    int newX = minimumRadius < spawnRadius
                            ? random.nextInt(minimumRadius * 2, (spawnRadius * 2) + 1)
                            : minimumRadius * 2;
                    int newY = newX < (spawnRadius * 2) ? random.nextInt(newX, (spawnRadius * 2) + 1) : newX;
                    newY = (int) Math.sqrt((newY * newY) - (newX * newX));
                    if (newX > (spawnRadius + minimumRadius)) {
                        newX = (master.getX() + newX) - spawnRadius;
                    } else {
                        newX = (master.getX() - newX) + minimumRadius;
                    }

                    if (newY > (spawnRadius + minimumRadius)) {
                        newY = (master.getY() + newY) - spawnRadius;
                    } else {
                        newY = (master.getY() - newY) + minimumRadius;
                    }

                    minion.teleToLocation(new Location(newX, newY, master.getZ()));
                }
            }
        }
    }

    private void spawnMinion(int minionTemplateId) {
        if (minionTemplateId == 0) {
            return;
        }

        spawnMinion(master, minionTemplateId);
    }

    @SingleWriter
    private class MinionRespawnTask implements Runnable {
        private final Monster minion;
        private ScheduledFuture<?> future;

        public MinionRespawnTask(Monster minion) {
            this.minion = minion;
        }

        @Override
        public void run() {
            WorldPartition.checkOwner();
            // A finished respawn leaves the list, so the tasks of a master that never dies do not pile up.
            respawnTasks.remove(future);

            // Minion can be already spawned or deleted.
            if (!master.isAlikeDead() && master.isSpawned() && !minion.isSpawned()) {
                initializeNpc(master, minion);

                if (!master.isAggroListEmpty()) {
                    minion.addAggroFrom(master);
                    final Creature mostHated = minion.getMostHated();
                    if (mostHated != null) {
                        minion.getAI().setIntentionAttack(mostHated);
                    }
                }
            }
        }
    }

    public static Monster spawnMinion(Monster master, int minionTemplateId) {
        WorldPartition.checkOwner();
        final NpcTemplate minionTemplate = NpcData.getInstance().getTemplate(minionTemplateId);
        if (minionTemplate == null) {
            return null;
        }

        return initializeNpc(master, new Monster(minionTemplate));
    }

    protected static Monster initializeNpc(Monster master, Monster minion) {
        minion.stopAllEffects();
        minion.setDead(false);
        minion.setDecayed(false);

        minion.setCurrentHpMp(minion.getMaxHp(), minion.getMaxMp());
        minion.setHeading(master.getHeading());

        minion.setLeader(master);

        minion.setInstanceId(master.getInstanceId());

        if (minion.getTemplate().isUsingServerSideName()) {
            minion.setName(minion.getTemplate().getName());
        }

        if (minion.getTemplate().isUsingServerSideTitle()) {
            minion.setTitle(minion.getTemplate().getTitle());
        }

        final int spawnRadius = SPAWN_OFFSET_RADIUS;
        final int minimumRadius = (int) master.getCollisionRadius() + MINIMUM_SPAWN_RADIUS;
        final RandomGenerator random = ClockBridge.random();
        int newX = minimumRadius < spawnRadius
                ? random.nextInt(minimumRadius * 2, (spawnRadius * 2) + 1)
                : minimumRadius * 2;
        int newY = newX < (spawnRadius * 2) ? random.nextInt(newX, (spawnRadius * 2) + 1) : newX;
        newY = (int) Math.sqrt((newY * newY) - (newX * newX));
        if (newX > (spawnRadius + minimumRadius)) {
            newX = (master.getX() + newX) - spawnRadius;
        } else {
            newX = (master.getX() - newX) + minimumRadius;
        }

        if (newY > (spawnRadius + minimumRadius)) {
            newY = (master.getY() + newY) - spawnRadius;
        } else {
            newY = (master.getY() - newY) + minimumRadius;
        }

        minion.spawnMe(newX, newY, master.getZ());

        if (minion.isInInstance()) {
            minion.broadcastInfo();
        }

        return minion;
    }

    private int countSpawnedMinionsById(int minionTemplateId) {
        int count = 0;
        for (Monster minion : spawnedMinions) {
            if ((minion != null) && (minion.getId() == minionTemplateId)) {
                count++;
            }
        }

        return count;
    }

    public int getSpawnedMinionCount() {
        WorldPartition.checkOwner();
        return spawnedMinions.size();
    }
}
