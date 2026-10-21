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
package app.l2nx.highfive.managers.games;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.mechanics.krateisCube.KrateiArena;
import app.l2nx.highfive.mechanics.krateisCube.KrateiCubePlayer;
import app.l2nx.highfive.mechanics.krateisCube.KrateiMsgType;
import app.l2nx.highfive.mechanics.krateisCube.KrateisReward;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author LordWinter
 */
@Slf4j
public class KrateisCubeManager implements IXmlReader {

    @Getter
    private final Map<Integer, KrateiArena> arenas = new HashMap<>(3);

    private Npc manager = null;

    @Getter
    private long nextMatchTime;

    @Getter
    private boolean registerTime = false;

    private boolean halfAnHour = false;
    private EventState state = EventState.REGISTRATION;

    private ScheduledFuture<?> periodTask = null;
    private ScheduledFuture<?> eventTask = null;
    private ScheduledFuture<?> msgTask = null;

    private enum EventState {
        REGISTRATION,
        PREPARING,
        STARTED
    }

    public KrateisCubeManager() {
        load();
    }

    @Override
    public void load() {
        parseDatapackFile("data/KrateisCube.xml");

        log.info("Loaded {} arena templates.", arenas.size());

        final Spawn spawn = SpawnTable.getInstance().getAnySpawn(32503);
        if (spawn != null) {
            manager = spawn.getLastSpawn();
            recalcEventTime();
        } else {
            log.info("Event can't be started because npc not found!");
        }
    }

    @Override
    public void parseDocument(Document document, File file) {
        NamedNodeMap map;
        for (Node n = document.getFirstChild(); n != null; n = n.getNextSibling()) {
            if ("list".equalsIgnoreCase(n.getNodeName())) {
                for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                    if ("arena".equalsIgnoreCase(d.getNodeName())) {
                        map = d.getAttributes();
                        final int id = Integer.parseInt(map.getNamedItem("id").getNodeValue());
                        final int manager =
                                Integer.parseInt(map.getNamedItem("manager").getNodeValue());
                        final int minLevel =
                                Integer.parseInt(map.getNamedItem("minLevel").getNodeValue());
                        final int maxLevel =
                                Integer.parseInt(map.getNamedItem("maxLevel").getNodeValue());
                        final int minPlayers =
                                Integer.parseInt(map.getNamedItem("minPlayers").getNodeValue());
                        final int maxPlayers =
                                Integer.parseInt(map.getNamedItem("maxPlayers").getNodeValue());

                        final KrateiArena arena =
                                new KrateiArena(id, manager, minLevel, maxLevel, minPlayers, maxPlayers);
                        final StatSet params = new StatSet();

                        for (Node kc = d.getFirstChild(); kc != null; kc = kc.getNextSibling()) {
                            if ("doorA".equalsIgnoreCase(kc.getNodeName())) {
                                map = kc.getAttributes();
                                final String doorList = map.getNamedItem("list") != null
                                        ? map.getNamedItem("list").getNodeValue()
                                        : "";
                                if (!doorList.isEmpty()) {
                                    final String[] doorSplint = doorList.split(",");
                                    for (String doorId : doorSplint) {
                                        final Door door = DoorData.getInstance().getDoor(Integer.parseInt(doorId));
                                        if (door != null) {
                                            arena.addDoorA(door);
                                        }
                                    }
                                }
                            } else if ("doorB".equalsIgnoreCase(kc.getNodeName())) {
                                map = kc.getAttributes();
                                final String doorList = map.getNamedItem("list") != null
                                        ? map.getNamedItem("list").getNodeValue()
                                        : "";
                                if (!doorList.isEmpty()) {
                                    final String[] doorSplint = doorList.split(",");
                                    for (String doorId : doorSplint) {
                                        final Door door = DoorData.getInstance().getDoor(Integer.parseInt(doorId));
                                        if (door != null) {
                                            arena.addDoorB(door);
                                        }
                                    }
                                }
                            } else if ("waitLocations".equalsIgnoreCase(kc.getNodeName())) {
                                for (Node c = kc.getFirstChild(); c != null; c = c.getNextSibling()) {
                                    if ("point".equalsIgnoreCase(c.getNodeName())) {
                                        map = c.getAttributes();
                                        final int x = Integer.parseInt(
                                                map.getNamedItem("x").getNodeValue());
                                        final int y = Integer.parseInt(
                                                map.getNamedItem("y").getNodeValue());
                                        final int z = Integer.parseInt(
                                                map.getNamedItem("z").getNodeValue());
                                        arena.addWaitLoc(new Location(x, y, z));
                                    }
                                }
                            } else if ("battleLocations".equalsIgnoreCase(kc.getNodeName())) {
                                for (Node c = kc.getFirstChild(); c != null; c = c.getNextSibling()) {
                                    if ("point".equalsIgnoreCase(c.getNodeName())) {
                                        map = c.getAttributes();
                                        final int x = Integer.parseInt(
                                                map.getNamedItem("x").getNodeValue());
                                        final int y = Integer.parseInt(
                                                map.getNamedItem("y").getNodeValue());
                                        final int z = Integer.parseInt(
                                                map.getNamedItem("z").getNodeValue());
                                        arena.addBattleLoc(new Location(x, y, z));
                                    }
                                }
                            } else if ("watcherLocations".equalsIgnoreCase(kc.getNodeName())) {
                                for (Node c = kc.getFirstChild(); c != null; c = c.getNextSibling()) {
                                    if ("point".equalsIgnoreCase(c.getNodeName())) {
                                        map = c.getAttributes();
                                        final int x = Integer.parseInt(
                                                map.getNamedItem("x").getNodeValue());
                                        final int y = Integer.parseInt(
                                                map.getNamedItem("y").getNodeValue());
                                        final int z = Integer.parseInt(
                                                map.getNamedItem("z").getNodeValue());
                                        arena.addWatcherLoc(new Location(x, y, z));
                                    }
                                }
                            } else if ("buffs".equalsIgnoreCase(kc.getNodeName())) {
                                for (Node c = kc.getFirstChild(); c != null; c = c.getNextSibling()) {
                                    if ("skill".equalsIgnoreCase(c.getNodeName())) {
                                        final int skillId = Integer.parseInt(c.getAttributes()
                                                .getNamedItem("id")
                                                .getNodeValue());
                                        final int level = Integer.parseInt(c.getAttributes()
                                                .getNamedItem("level")
                                                .getNodeValue());
                                        final Skill skill =
                                                SkillData.getInstance().getSkill(skillId, level);
                                        if (skill != null) {
                                            arena.addBuff(skill);
                                        }
                                    }
                                }
                            } else if ("rewards".equalsIgnoreCase(kc.getNodeName())) {
                                for (Node c = kc.getFirstChild(); c != null; c = c.getNextSibling()) {
                                    if ("item".equalsIgnoreCase(c.getNodeName())) {
                                        final int itemId = Integer.parseInt(c.getAttributes()
                                                .getNamedItem("id")
                                                .getNodeValue());
                                        final long amount = Long.parseLong(c.getAttributes()
                                                .getNamedItem("amount")
                                                .getNodeValue());
                                        final boolean useModifier =
                                                c.getAttributes().getNamedItem("useModifers") != null
                                                        ? Boolean.parseBoolean(c.getAttributes()
                                                                .getNamedItem("useModifers")
                                                                .getNodeValue())
                                                        : false;
                                        arena.setReward(KrateisReward.builder()
                                                .id(itemId)
                                                .amount(amount)
                                                .allowMidifier(useModifier)
                                                .build());
                                    }
                                }
                            } else if ("add_parameters".equalsIgnoreCase(kc.getNodeName())) {
                                for (Node c = kc.getFirstChild(); c != null; c = c.getNextSibling()) {
                                    if ("set".equalsIgnoreCase(c.getNodeName())) {
                                        params.set(
                                                c.getAttributes()
                                                        .getNamedItem("name")
                                                        .getNodeValue(),
                                                c.getAttributes()
                                                        .getNamedItem("value")
                                                        .getNodeValue());
                                    }
                                }

                                arena.addParam(params);
                            }
                        }

                        arenas.put(arena.getId(), arena);
                    }
                }
            }
        }
    }

    public void prepareEvent() {
        if (registerTime) {
            registerTime = false;
        }

        if (periodTask != null) {
            periodTask.cancel(false);
        }

        if (eventTask != null) {
            eventTask.cancel(false);
        }

        if (msgTask != null) {
            msgTask.cancel(false);
        }

        boolean isActive = false;
        for (KrateiArena arena : arenas.values()) {
            if ((arena != null) && (arena.getPlayers().size() >= arena.getMinPlayers())) {
                int count = 0;
                final List<Player> invalidPlayers = new ArrayList<>();
                for (KrateiCubePlayer pl : arena.getPlayers().values()) {
                    if ((pl != null) && pl.isRegister()) {
                        if (pl.getPlayer().isInsideRadius3D(manager, PlayerConfig.ALT_PARTY_RANGE)) {
                            pl.setIsInside(true);
                            pl.setIsRegister(false);
                            count++;
                        } else {
                            invalidPlayers.add(pl.getPlayer());
                        }
                    }
                }

                if (!invalidPlayers.isEmpty()) {
                    for (Player pl : invalidPlayers) {
                        arena.removePlayer(pl);
                    }
                }

                invalidPlayers.clear();

                if (count > 1) {
                    arena.setIsBattleNow(true);
                    arena.teleportToBattle(null);
                    arena.waitTimeInfo();
                    isActive = true;
                } else {
                    // Match will not start, revert players to their registered state.
                    for (KrateiCubePlayer pl : arena.getPlayers().values()) {
                        if ((pl != null) && pl.isInside()) {
                            pl.setIsInside(false);
                            pl.setIsRegister(true);
                        }
                    }
                }
            }
        }

        if (isActive) {
            eventTask = ThreadPool.schedule(this::startEvent, 11000);
            getManagerMessage(KrateiMsgType.STARTED);
        } else {
            recalcEventTime();
        }
    }

    public void startEvent() {
        if (eventTask != null) {
            eventTask.cancel(false);
        }

        for (KrateiArena arena : arenas.values()) {
            if ((arena != null) && arena.isBattleNow()) {
                arena.startEvent();
                state = EventState.STARTED;
            }
        }

        periodTask = ThreadPool.schedule(this::recalcEventTime, 180000);
    }

    public void abortEvent() {
        for (KrateiArena arena : arenas.values()) {
            if ((arena != null) && arena.isBattleNow()) {
                arena.endEvent();
            }
        }

        if (!registerTime) {
            recalcEventTime();
        }
    }

    public boolean isActive() {
        return state == EventState.STARTED;
    }

    public boolean isPreparing() {
        return state == EventState.PREPARING;
    }

    public void setIsActivate(boolean val) {
        if (!val) {
            state = EventState.REGISTRATION;
        }
    }

    public void recalcEventTime() {
        if (!registerTime) {
            registerTime = true;
        }

        if (periodTask != null) {
            periodTask.cancel(false);
        }

        if (eventTask != null) {
            eventTask.cancel(false);
        }

        final Calendar calendar = ClockBridge.calendar();
        if (calendar.get(Calendar.MINUTE) >= 57) {
            calendar.add(Calendar.HOUR, 1);
            calendar.set(Calendar.MINUTE, 27);
        } else if ((calendar.get(Calendar.MINUTE) >= 0) && (calendar.get(Calendar.MINUTE) <= 26)) {
            calendar.set(Calendar.MINUTE, 27);
            calendar.set(Calendar.SECOND, 0);
            halfAnHour = true;
        } else {
            calendar.set(Calendar.MINUTE, 57);
            calendar.set(Calendar.SECOND, 0);
            halfAnHour = false;
        }

        final long currentTime = ClockBridge.wallMillis();
        if (calendar.getTimeInMillis() < currentTime) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        nextMatchTime = calendar.getTimeInMillis();
        final long lastTime = nextMatchTime - currentTime;
        eventTask = ThreadPool.schedule(this::closeRegistration, lastTime);
        getManagerMessage(KrateiMsgType.INITIALIZED);

        final int time = (int) ((lastTime / 1000) / 60);
        if (time >= 5) {
            msgTask = ThreadPool.schedule(() -> getManagerMessage(KrateiMsgType.REGISTRATION_5), lastTime - 300000);
        } else if ((time >= 3) && (time < 5)) {
            msgTask = ThreadPool.schedule(() -> getManagerMessage(KrateiMsgType.REGISTRATION_3), lastTime - 180000);
        } else if ((time >= 1) && (time < 3)) {
            msgTask = ThreadPool.schedule(() -> getManagerMessage(KrateiMsgType.REGISTRATION_1), lastTime - 60000);
        }

        if (state != EventState.STARTED) {
            state = EventState.REGISTRATION;
        }

        // LOGGER.info(getClass().getSimpleName() + ": Next match " +
        // TimeUtil.getDateTimeString(calendar.getTimeInMillis()));
    }

    public void closeRegistration() {
        if (periodTask != null) {
            periodTask.cancel(false);
        }

        registerTime = false;
        getManagerMessage(KrateiMsgType.PREPATING);
        state = EventState.PREPARING;
        periodTask = ThreadPool.schedule(this::prepareEvent, 180000);
    }

    private void getManagerMessage(KrateiMsgType state) {
        final long lastTime = nextMatchTime - ClockBridge.wallMillis();
        switch (state) {
            case INITIALIZED: {
                final CreatureSay msg = new CreatureSay(
                        manager, ChatType.NPC_SHOUT, NpcStringId.YOU_HAVE_S1_MINUTE_S_TO_REGISTER_FOR_THE_MATCH);
                msg.addStringParameter(String.valueOf(!halfAnHour ? 57 : 27));
                manager.broadcastPacket(msg);
                break;
            }
            case REGISTRATION_5: {
                manager.broadcastPacket(new CreatureSay(
                        manager,
                        ChatType.NPC_SHOUT,
                        NpcStringId.THERE_ARE_5_MINUTES_REMAINING_TO_REGISTER_FOR_KRATEI_S_CUBE_MATCH));
                msgTask = ThreadPool.schedule(() -> getManagerMessage(KrateiMsgType.REGISTRATION_3), lastTime - 180000);
                break;
            }
            case REGISTRATION_3: {
                manager.broadcastPacket(new CreatureSay(
                        manager,
                        ChatType.NPC_SHOUT,
                        NpcStringId.THERE_ARE_3_MINUTES_REMAINING_TO_REGISTER_FOR_KRATEI_S_CUBE_MATCH));
                msgTask = ThreadPool.schedule(() -> getManagerMessage(KrateiMsgType.REGISTRATION_1), lastTime - 60000);
                break;
            }
            case REGISTRATION_1: {
                manager.broadcastPacket(new CreatureSay(
                        manager,
                        ChatType.NPC_SHOUT,
                        NpcStringId.THERE_ARE_1_MINUTES_REMAINING_TO_REGISTER_FOR_KRATEI_S_CUBE_MATCH));
                break;
            }
            case PREPATING: {
                final CreatureSay msg3 =
                        new CreatureSay(manager, ChatType.NPC_SHOUT, NpcStringId.THE_MATCH_WILL_BEGIN_IN_S1_MINUTE_S);
                msg3.addStringParameter(String.valueOf(3));
                manager.broadcastPacket(msg3);
                break;
            }
            case STARTED: {
                manager.broadcastPacket(
                        new CreatureSay(manager, ChatType.NPC_SHOUT, NpcStringId.THE_MATCH_WILL_BEGIN_SHORTLY));
                break;
            }
        }
    }

    public KrateiArena getArenaId(int id) {
        return arenas.get(id);
    }

    public static KrateisCubeManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final KrateisCubeManager INSTANCE = new KrateisCubeManager();
    }
}
