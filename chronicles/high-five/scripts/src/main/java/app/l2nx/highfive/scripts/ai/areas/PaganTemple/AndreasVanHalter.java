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
package app.l2nx.highfive.scripts.ai.areas.PaganTemple;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PlaySound;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SocialAction;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SpecialCamera;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Andreas Van Halter raid AI: balcony cleanup -> door swap -> ritual cinematic -> boss combat.
 * @author Altur
 */
public class AndreasVanHalter extends Script {
    // NPCs
    private static final int BOSS = 29062;
    private static final int RITUAL_OFFERING = 32038;
    private static final int ALTAR_GATEKEEPER = 32051;
    private static final int CAMERA_DUMMY = 13014;
    private static final int CAPTAIN_ROYAL_GUARD_A = 22175;
    private static final int CAPTAIN_ROYAL_GUARD_B = 22188;
    private static final int ROYAL_GUARD_BALCONY = 22176;
    private static final int ROYAL_GUARD_AID = 22189;

    private static final int[] BALCONY_MOBS = {
        CAPTAIN_ROYAL_GUARD_A, CAPTAIN_ROYAL_GUARD_B, ROYAL_GUARD_BALCONY, ROYAL_GUARD_AID
    };

    // Doors (retail HighFive Pagan Temple altar 3rd floor).
    private static final int BALCONY_DOOR_1 = 19160014;
    private static final int BALCONY_DOOR_2 = 19160015;
    private static final int ALTAR_DOOR_1 = 19160016;
    private static final int ALTAR_DOOR_2 = 19160017;

    // Locations (retail PTS confirmed).
    private static final Location RITUAL_VICTIM_SPAWN = new Location(-16384, -53197, -10439, 15992);
    private static final Location CAMERA_SPAWN = new Location(-16362, -53754, -10439);
    private static final Location BOSS_FALLBACK = new Location(-16393, -53433, -10439);
    private static final Location BALCONY_DOOR_1_FALLBACK = new Location(-15690, -54030, -10439);
    private static final Location BALCONY_DOOR_2_FALLBACK = new Location(-17150, -54064, -10439);
    private static final Location[] ALTAR_GATEKEEPER_SPAWNS = {
        new Location(-17248, -54832, -10424, 16384),
        new Location(-15547, -54835, -10424, 16384),
        new Location(-18116, -54831, -10579, 16384),
        new Location(-14645, -54836, -10577, 16384)
    };

    // Skill (retail PTS Curse Poison lvl 7 cast on the ritual offering NPC).
    private static final SkillHolder RITUAL_SKILL = new SkillHolder(1168, 7);

    // 16 kills triggers the ritual without waiting for respawns (~80% of the 20 mobs reachable near the altar).
    private static final int BALCONY_KILLS_THRESHOLD = 16;
    private static final long RAID_RESET_DELAY = 5 * 60 * 60 * 1000L;
    // Z below altar floor (-10439) means the boss was dragged toward the balcony floor (-10594).
    private static final int ANTI_GLITCH_Z = -10500;
    private static final int ANTI_GLITCH_DOOR_RANGE = 200;

    // State
    private static final int STATUS_DORMANT = 0;
    private static final int STATUS_CINEMATIC = 1;
    private static final int STATUS_FIGHT = 2;

    private int status = STATUS_DORMANT;
    private final AtomicInteger balconyKills = new AtomicInteger(0);
    private long lastAttack;
    private Npc boss;
    private Npc camera;
    private Npc ritualVictim;
    private final List<Npc> spawned = new CopyOnWriteArrayList<>();

    private AndreasVanHalter() {
        addSpawnId(BOSS);
        addAttackId(BOSS);
        addKillId(BOSS);
        addKillId(BALCONY_MOBS);
    }

    @Override
    public void onSpawn(Npc npc) {
        if (npc.getId() != BOSS) {
            return;
        }
        boss = npc;
        status = STATUS_DORMANT;
        balconyKills.set(0);
        lastAttack = ClockBridge.gameMillis();

        openDoor(BALCONY_DOOR_1);
        openDoor(BALCONY_DOOR_2);
        closeDoor(ALTAR_DOOR_1);
        closeDoor(ALTAR_DOOR_2);

        startQuestTimer("RESET_CHECK", 60_000, npc, null, true);
    }

    @Override
    public void onAttack(Npc npc, Player attacker, int damage, boolean isSummon) {
        if ((npc.getId() != BOSS) || (attacker == null)) {
            return;
        }
        lastAttack = ClockBridge.gameMillis();

        if (npc.getZ() <= ANTI_GLITCH_Z) {
            npc.teleToLocation(BOSS_FALLBACK, false);
            return;
        }

        final Door altar1 = DoorData.getInstance().getDoor(ALTAR_DOOR_1);
        if ((altar1 != null) && (npc.calculateDistance2D(altar1) <= ANTI_GLITCH_DOOR_RANGE)) {
            npc.teleToLocation(BALCONY_DOOR_1_FALLBACK, false);
            return;
        }

        final Door altar2 = DoorData.getInstance().getDoor(ALTAR_DOOR_2);
        if ((altar2 != null) && (npc.calculateDistance2D(altar2) <= ANTI_GLITCH_DOOR_RANGE)) {
            npc.teleToLocation(BALCONY_DOOR_2_FALLBACK, false);
        }
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        if (npc.getId() == BOSS) {
            cleanupRaid();
            return;
        }

        if (status != STATUS_DORMANT) {
            return;
        }

        // Atomic to survive AOE wipes that kill several balcony mobs in the same tick.
        if (balconyKills.incrementAndGet() == BALCONY_KILLS_THRESHOLD) {
            triggerRitual();
        }
    }

    private void triggerRitual() {
        if ((boss == null) || boss.isDead()) {
            return;
        }
        status = STATUS_CINEMATIC;

        closeDoor(BALCONY_DOOR_1);
        closeDoor(BALCONY_DOOR_2);
        openDoor(ALTAR_DOOR_1);
        openDoor(ALTAR_DOOR_2);

        for (Location loc : ALTAR_GATEKEEPER_SPAWNS) {
            final Npc gatekeeper = addSpawn(ALTAR_GATEKEEPER, loc, false, 0);
            if (gatekeeper != null) {
                spawned.add(gatekeeper);
            }
        }

        ritualVictim = addSpawn(RITUAL_OFFERING, RITUAL_VICTIM_SPAWN, false, 0);
        if (ritualVictim != null) {
            spawned.add(ritualVictim);
        }

        startQuestTimer("CINEMATIC_1", 3000, boss, null);
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        if (event == null) {
            return null;
        }
        switch (event) {
            case "CINEMATIC_1": {
                if ((boss == null) || boss.isDead() || (ritualVictim == null)) {
                    return null;
                }

                boss.setHeading(16384);
                camera = addSpawn(CAMERA_DUMMY, CAMERA_SPAWN, false, 30_000);

                if (camera != null) {
                    spawned.add(camera);
                }

                boss.setTarget(ritualVictim);
                boss.doCast(RITUAL_SKILL.getSkill());
                boss.setImmobilized(true);
                broadcastCinematic(
                        new PlaySound(0, "BS04_A", 1, boss.getObjectId(), boss.getX(), boss.getY(), boss.getZ()));

                if (camera != null) {
                    broadcastCinematic(new SpecialCamera(camera, 1500, 88, 89, 0, 5000, 0, 0, 1, 0, 0));
                }

                startQuestTimer("CINEMATIC_2", 300, boss, null);
                break;
            }
            case "CINEMATIC_2": {
                if ((ritualVictim != null) && !ritualVictim.isDead()) {
                    ritualVictim.broadcastPacket(new SocialAction(ritualVictim.getObjectId(), 1));
                    ritualVictim.setCurrentHp(0);
                }

                if (camera != null) {
                    broadcastCinematic(new SpecialCamera(camera, 1500, 88, 89, 0, 5000, 0, 0, 1, 0, 0));
                }

                startQuestTimer("CINEMATIC_3", 300, boss, null);
                break;
            }
            case "CINEMATIC_3": {
                if ((ritualVictim != null) && !ritualVictim.isDead()) {
                    ritualVictim.deleteMe();
                }
                ritualVictim = null;

                if (camera != null) {
                    broadcastCinematic(new SpecialCamera(camera, 450, 88, 3, 5500, 5000, 0, 0, 1, 0, 0));
                }

                startQuestTimer("CINEMATIC_4", 9400, boss, null);
                break;
            }
            case "CINEMATIC_4": {
                if (camera != null) {
                    broadcastCinematic(new SpecialCamera(camera, 500, 88, 4, 5000, 5000, 0, 0, 1, 0, 0));
                }

                startQuestTimer("CINEMATIC_5", 5000, boss, null);
                break;
            }
            case "CINEMATIC_5": {
                if (camera != null) {
                    broadcastCinematic(new SpecialCamera(camera, 3000, 88, 4, 6000, 5000, 0, 0, 1, 0, 0));
                }

                startQuestTimer("CINEMATIC_END", 6000, boss, null);
                break;
            }
            case "CINEMATIC_END": {
                // Acolyte minions (29063/29064) are spawned automatically from the boss <minions> stats.
                if (boss != null) {
                    boss.setImmobilized(false);
                }

                if (camera != null) {
                    camera.deleteMe();
                    camera = null;
                }

                status = STATUS_FIGHT;
                break;
            }
            case "RESET_CHECK": {
                if ((boss != null)
                        && !boss.isDead()
                        && !boss.isAttackingNow()
                        && ((ClockBridge.gameMillis() - lastAttack) >= RAID_RESET_DELAY)) {
                    resetRaid();
                }
                break;
            }
        }
        return super.onEvent(event, npc, player);
    }

    private void resetRaid() {
        status = STATUS_DORMANT;
        balconyKills.set(0);
        lastAttack = ClockBridge.gameMillis();
        despawnEphemeral();
        openDoor(BALCONY_DOOR_1);
        openDoor(BALCONY_DOOR_2);
        closeDoor(ALTAR_DOOR_1);
        closeDoor(ALTAR_DOOR_2);
    }

    private void cleanupRaid() {
        status = STATUS_DORMANT;
        balconyKills.set(0);
        despawnEphemeral();
        openDoor(BALCONY_DOOR_1);
        openDoor(BALCONY_DOOR_2);
        closeDoor(ALTAR_DOOR_1);
        closeDoor(ALTAR_DOOR_2);
        cancelQuestTimer("RESET_CHECK", boss, null);
        boss = null;
    }

    private void despawnEphemeral() {
        for (Npc npc : spawned) {
            if ((npc != null) && !npc.isDead()) {
                npc.deleteMe();
            }
        }
        spawned.clear();
        camera = null;
        ritualVictim = null;
    }

    private void broadcastCinematic(ServerPacket packet) {
        if (boss == null) {
            return;
        }

        boss.broadcastPacket(packet);
    }

    private static void openDoor(int doorId) {
        final Door door = DoorData.getInstance().getDoor(doorId);
        if ((door != null) && !door.isOpen()) {
            door.openMe();
        }
    }

    private static void closeDoor(int doorId) {
        final Door door = DoorData.getInstance().getDoor(doorId);
        if ((door != null) && door.isOpen()) {
            door.closeMe();
        }
    }

    public static void main(String[] args) {
        new AndreasVanHalter();
    }
}
