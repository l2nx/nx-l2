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
package app.l2nx.highfive.scripts.conquerablehalls.RainbowSpringsChateau;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.ConquerableHallSiegeConfig;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.siege.SiegeClan;
import app.l2nx.highfive.mechanics.siege.SiegeClanType;
import app.l2nx.highfive.mechanics.siege.clanhalls.ClanHallSiegeEngine;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegeStatus;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.serverpackets.ExShowScreenMessage;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.ArrayUtil;
import app.l2nx.highfive.util.LocationUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author LordWinter
 */
@Slf4j
public class RainbowSpringsChateau extends ClanHallSiegeEngine {
    private static final DateTimeFormatter SIMPLE_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy");

    private static final int WAR_DECREES = 8034;
    private static final int RAINBOW_NECTAR = 8030;
    private static final int RAINBOW_MWATER = 8031;
    private static final int RAINBOW_WATER = 8032;
    private static final int RAINBOW_SULFUR = 8033;

    private static final int MESSENGER = 35604;
    private static final int CARETAKER = 35603;
    private static final int CHEST = 35593;
    private static final int ENRAGED_YETI = 35592;

    protected static final Map<Integer, Long> warDecreesCount = new HashMap<>();
    protected static final List<Clan> acceptedClans = new ArrayList<>();
    protected static ArrayList<Integer> playersOnArena = new ArrayList<>();
    protected static final List<Npc> chests = new ArrayList<>();

    private static final int ITEM_A = 8035;
    private static final int ITEM_B = 8036;
    private static final int ITEM_C = 8037;
    private static final int ITEM_D = 8038;
    private static final int ITEM_E = 8039;
    private static final int ITEM_F = 8040;
    private static final int ITEM_G = 8041;
    private static final int ITEM_H = 8042;
    private static final int ITEM_I = 8043;
    private static final int ITEM_K = 8045;
    private static final int ITEM_L = 8046;
    private static final int ITEM_N = 8047;
    private static final int ITEM_O = 8048;
    private static final int ITEM_P = 8049;
    private static final int ITEM_R = 8050;
    private static final int ITEM_S = 8051;
    private static final int ITEM_T = 8052;
    private static final int ITEM_U = 8053;
    private static final int ITEM_W = 8054;
    private static final int ITEM_Y = 8055;

    protected static int generated;
    protected Future<?> task = null;
    protected Future<?> chesttask = null;

    @Getter
    private Clan winner;

    private static class Word {
        @Getter
        private final String name;

        @Getter
        private final int[][] items;

        public Word(String name, int[]... items) {
            this.name = name;
            this.items = items;
        }
    }

    private static final int[] GOURDS = {35588, 35589, 35590, 35591};

    private static Npc[] gourds = new Npc[4];
    private static Npc[] yetis = new Npc[4];
    protected Npc chest1;
    protected Npc chest2;
    protected Npc chest3;
    protected Npc chest4;

    private static final Skill[] DEBUFFS = {SkillData.getInstance().getSkill(4991, 1)};

    private static final int[] YETIS = {35596, 35597, 35598, 35599};

    // @formatter:off
    private static final int[][] ARENAS = {
        {151562, -127080, -2214},
        {153141, -125335, -2214},
        {153892, -127530, -2214},
        {155657, -125752, -2214},
    };

    private static final int[][] YETIS_SPAWN = {
        {151560, -127075, -2221},
        {153129, -125337, -2221},
        {153884, -127534, -2221},
        {156657, -125753, -2221},
    };

    protected static final int[][] CHESTS_SPAWN = {
        {151560, -127075, -2221},
        {153129, -125337, -2221},
        {153884, -127534, -2221},
        {155657, -125753, -2221},
    };

    protected final int[] arenaChestsCnt = {0, 0, 0, 0};

    protected static final Word[] WORLD_LIST = {
        new Word(
                "BABYDUCK",
                new int[] {ITEM_B, 2},
                new int[] {ITEM_A, 1},
                new int[] {ITEM_Y, 1},
                new int[] {ITEM_D, 1},
                new int[] {ITEM_U, 1},
                new int[] {ITEM_C, 1},
                new int[] {ITEM_K, 1}),
        new Word(
                "ALBATROS",
                new int[] {ITEM_A, 2},
                new int[] {ITEM_L, 1},
                new int[] {ITEM_B, 1},
                new int[] {ITEM_T, 1},
                new int[] {ITEM_R, 1},
                new int[] {ITEM_O, 1},
                new int[] {ITEM_S, 1}),
        new Word(
                "PELICAN",
                new int[] {ITEM_P, 1},
                new int[] {ITEM_E, 1},
                new int[] {ITEM_L, 1},
                new int[] {ITEM_I, 1},
                new int[] {ITEM_C, 1},
                new int[] {ITEM_A, 1},
                new int[] {ITEM_N, 1}),
        new Word(
                "KINGFISHER",
                new int[] {ITEM_K, 1},
                new int[] {ITEM_I, 1},
                new int[] {ITEM_N, 1},
                new int[] {ITEM_G, 1},
                new int[] {ITEM_F, 1},
                new int[] {ITEM_I, 1},
                new int[] {ITEM_S, 1},
                new int[] {ITEM_H, 1},
                new int[] {ITEM_E, 1},
                new int[] {ITEM_R, 1}),
        new Word(
                "CYGNUS",
                new int[] {ITEM_C, 1},
                new int[] {ITEM_Y, 1},
                new int[] {ITEM_G, 1},
                new int[] {ITEM_N, 1},
                new int[] {ITEM_U, 1},
                new int[] {ITEM_S, 1}),
        new Word("TRITON", new int[] {ITEM_T, 2}, new int[] {ITEM_R, 1}, new int[] {ITEM_I, 1}, new int[] {ITEM_N, 1}),
        new Word(
                "RAINBOW",
                new int[] {ITEM_R, 1},
                new int[] {ITEM_A, 1},
                new int[] {ITEM_I, 1},
                new int[] {ITEM_N, 1},
                new int[] {ITEM_B, 1},
                new int[] {ITEM_O, 1},
                new int[] {ITEM_W, 1}),
        new Word(
                "SPRING",
                new int[] {ITEM_S, 1},
                new int[] {ITEM_P, 1},
                new int[] {ITEM_R, 1},
                new int[] {ITEM_I, 1},
                new int[] {ITEM_N, 1},
                new int[] {ITEM_G, 1})
    };
    // @formatter:on

    public RainbowSpringsChateau() {
        super(RAINBOW_SPRINGS);

        addFirstTalkId(MESSENGER);
        addFirstTalkId(CARETAKER);
        addFirstTalkId(YETIS);
        addTalkId(MESSENGER);
        addTalkId(CARETAKER);
        addTalkId(YETIS);

        for (int squashes : GOURDS) {
            addSpawnId(squashes);
            addKillId(squashes);
        }

        addSpawnId(ENRAGED_YETI);

        addSkillSeeId(YETIS);

        addKillId(CHEST);

        generated = -1;
        winner = ClanTable.getInstance().getClan(hall.getOwnerId());
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        String htmltext = event;
        final Clan clan = player.getClan();

        switch (npc.getId()) {
            case MESSENGER: {
                switch (event) {
                    case "Register": {
                        if (!player.isClanLeader()) {
                            htmltext = "35604-07.htm";
                        } else if ((clan.getCastleId() > 0) || (clan.getFortId() > 0) || (clan.getHideoutId() > 0)) {
                            htmltext = "35604-09.htm";
                        } else if (!hall.isRegistering()) {
                            htmltext = "35604-11.htm";
                        } else if (warDecreesCount.containsKey(clan.getId())) {
                            htmltext = "35604-10.htm";
                        } else if (getAttackers().size() >= 4) {
                            htmltext = "35604-18.htm";
                        } else if ((clan.getLevel() < 3) || (clan.getMembersCount() < 5)) {
                            htmltext = "35604-08.htm";
                        } else {
                            final Item warDecrees = player.getInventory().getItemByItemId(WAR_DECREES);
                            if (warDecrees == null) {
                                htmltext = "35604-05.htm";
                            } else {
                                final long count = warDecrees.getCount();
                                warDecreesCount.put(clan.getId(), count);
                                player.destroyItem(ItemProcessType.DESTROY, warDecrees, npc, true);
                                registerClan(clan, count, true);
                                htmltext = "35604-06.htm";
                            }
                        }
                        break;
                    }
                    case "Cancel": {
                        if (!player.isClanLeader()) {
                            htmltext = "35604-08.htm";
                        } else if (!warDecreesCount.containsKey(clan.getId())) {
                            htmltext = "35604-12.htm";
                        } else if (!hall.isRegistering()) {
                            htmltext = "35604-13.htm";
                        } else {
                            registerClan(clan, 0, false);
                            htmltext = "35604-17.htm";
                        }
                        break;
                    }
                    case "Unregister": {
                        if (!player.isClanLeader()) {
                            htmltext = "35604-07.htm";
                        } else if (hall.isRegistering()) {
                            if (warDecreesCount.containsKey(clan.getId())) {
                                player.addItem(
                                        ItemProcessType.REFUND,
                                        WAR_DECREES,
                                        warDecreesCount.get(clan.getId()) / 2,
                                        npc,
                                        true);
                                warDecreesCount.remove(clan.getId());
                                htmltext = "35604-14.htm";
                            } else {
                                htmltext = "35604-16.htm";
                            }
                        } else if (hall.isWaitingBattle()) {
                            acceptedClans.remove(clan);
                            htmltext = "35604-16.htm";
                        }
                        break;
                    }
                }
                break;
            }
            case CARETAKER: {
                switch (event) {
                    case "GoToArena": {
                        final Party party = player.getParty();
                        if (clan == null) {
                            htmltext = "35603-07.htm";
                        } else if (!player.isClanLeader()) {
                            htmltext = "35603-02.htm";
                        } else if (!player.isInParty()) {
                            htmltext = "35603-03.htm";
                        } else if (party.getLeaderObjectId() != player.getObjectId()) {
                            htmltext = "35603-04.htm";
                        } else {
                            final int clanId = player.getId();
                            boolean nonClanMemberInParty = false;
                            for (Player member : party.getMembers()) {
                                if (member.getId() != clanId) {
                                    nonClanMemberInParty = true;
                                    break;
                                }
                            }

                            if (nonClanMemberInParty) {
                                htmltext = "35603-05.htm";
                            } else if (party.getMemberCount() < 5) {
                                htmltext = "35603-06.htm";
                            }

                            if ((clan.getCastleId() > 0) || (clan.getFortId() > 0) || (clan.getHideoutId() > 0)) {
                                htmltext = "35603-08.htm";
                            } else if (clan.getLevel() < ConquerableHallSiegeConfig.CHS_CLAN_MINLEVEL) {
                                htmltext = "35603-09.htm";
                            } else if (!acceptedClans.contains(clan)) {
                                htmltext = "35603-10.htm";
                            } else {
                                portToArena(player, acceptedClans.indexOf(clan));
                            }

                            return null;
                        }
                        break;
                    }
                }
                break;
            }
        }

        if (event.startsWith("getItem")) {
            final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
            boolean has = true;
            if (generated == -1) {
                has = false;
            } else {
                final Word word = WORLD_LIST[generated];
                switch (generated) {
                    case 0: {
                        has = (player.getInventory().getInventoryItemCount(ITEM_B, -1) >= 2)
                                && (player.getInventory().getInventoryItemCount(ITEM_A, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_Y, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_D, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_U, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_C, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_K, -1) >= 1);
                        break;
                    }
                    case 1: {
                        has = (player.getInventory().getInventoryItemCount(ITEM_A, -1) >= 2)
                                && (player.getInventory().getInventoryItemCount(ITEM_L, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_B, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_T, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_R, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_O, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_S, -1) >= 1);
                        break;
                    }
                    case 2: {
                        has = (player.getInventory().getInventoryItemCount(ITEM_P, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_E, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_L, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_I, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_C, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_A, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_N, -1) >= 1);
                        break;
                    }
                    case 3: {
                        has = (player.getInventory().getInventoryItemCount(ITEM_K, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_I, -1) >= 2)
                                && (player.getInventory().getInventoryItemCount(ITEM_N, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_G, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_F, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_S, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_H, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_E, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_R, -1) >= 1);
                        break;
                    }
                    case 4: {
                        has = (player.getInventory().getInventoryItemCount(ITEM_C, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_Y, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_G, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_N, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_U, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_S, -1) >= 1);
                        break;
                    }
                    case 5: {
                        has = (player.getInventory().getInventoryItemCount(ITEM_T, -1) >= 2)
                                && (player.getInventory().getInventoryItemCount(ITEM_R, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_I, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_O, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_N, -1) >= 1);
                        break;
                    }
                    case 6: {
                        has = (player.getInventory().getInventoryItemCount(ITEM_R, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_A, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_I, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_N, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_B, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_O, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_W, -1) >= 1);
                        break;
                    }
                    case 7: {
                        has = (player.getInventory().getInventoryItemCount(ITEM_S, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_P, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_R, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_I, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_N, -1) >= 1)
                                && (player.getInventory().getInventoryItemCount(ITEM_G, -1) >= 1);
                        break;
                    }
                }

                if (has) {
                    for (int[] itemInfo : word.getItems()) {
                        player.destroyItemByItemId(ItemProcessType.DESTROY, itemInfo[0], itemInfo[1], player, true);
                    }

                    final int rnd = ClockBridge.random().nextInt(100);
                    if ((generated >= 0) && (generated <= 5)) {
                        if (rnd < 70) {
                            giveItems(player, RAINBOW_NECTAR, 1);
                        } else if (rnd < 80) {
                            giveItems(player, RAINBOW_MWATER, 1);
                        } else if (rnd < 90) {
                            giveItems(player, RAINBOW_WATER, 1);
                        } else {
                            giveItems(player, RAINBOW_SULFUR, 1);
                        }
                    } else {
                        if (rnd < 10) {
                            giveItems(player, RAINBOW_NECTAR, 1);
                        } else if (rnd < 40) {
                            giveItems(player, RAINBOW_MWATER, 1);
                        } else if (rnd < 70) {
                            giveItems(player, RAINBOW_WATER, 1);
                        } else {
                            giveItems(player, RAINBOW_SULFUR, 1);
                        }
                    }
                }

                if (!has) {
                    html.setFile(player, "data/scripts/conquerablehalls/RainbowSpringsChateau/35596-02.htm");
                } else {
                    html.setFile(player, "data/scripts/conquerablehalls/RainbowSpringsChateau/35596-04.htm");
                }

                player.sendPacket(html);
            }

            return null;
        } else if (event.startsWith("seeItem")) {
            final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
            html.setFile(player, "data/scripts/conquerablehalls/RainbowSpringsChateau/35596-05.htm");
            if (generated == -1) {
                html.replace("%word%", "<fstring>" + NpcStringId.UNDECIDED + "</fstring>");
            } else {
                html.replace("%word%", WORLD_LIST[generated].getName());
            }

            player.sendPacket(html);
            return null;
        }

        return htmltext;
    }

    @Override
    public String onFirstTalk(Npc npc, Player player) {
        final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
        final int npcId = npc.getId();

        if (npcId == MESSENGER) {
            final String main = (hall.getOwnerId() > 0) ? "35604-01.htm" : "35604-00.htm";
            html.setFile(player, "data/scripts/conquerablehalls/RainbowSpringsChateau/" + main);
            html.replace(
                    "%nextSiege%",
                    Instant.ofEpochMilli(hall.getSiegeDate().getTimeInMillis())
                            .atZone(ClockBridge.wallClock().getZone())
                            .format(SIMPLE_FORMAT));
            if (hall.getOwnerId() > 0) {
                html.replace(
                        "%owner%",
                        ClanTable.getInstance().getClan(hall.getOwnerId()).getName());
            }

            player.sendPacket(html);
        } else if (npcId == CARETAKER) {
            final String main = (hall.isInSiege() || !hall.isWaitingBattle()) ? "35603-00.htm" : "35603-01.htm";
            html.setFile(player, "data/scripts/conquerablehalls/RainbowSpringsChateau/" + main);
            player.sendPacket(html);
        } else if (ArrayUtil.contains(YETIS, npcId)) {
            final Clan clan = player.getClan();
            if (acceptedClans.contains(clan)) {
                final int index = acceptedClans.indexOf(clan);
                if (npcId == YETIS[index]) {
                    if (!player.isClanLeader()) {
                        html.setFile(player, "data/scripts/conquerablehalls/RainbowSpringsChateau/35596-00.htm");
                    } else {
                        html.setFile(player, "data/scripts/conquerablehalls/RainbowSpringsChateau/35596-01.htm");
                    }
                } else {
                    html.setFile(player, "data/scripts/conquerablehalls/RainbowSpringsChateau/35596-06.htm");
                }
            } else {
                html.setFile(player, "data/scripts/conquerablehalls/RainbowSpringsChateau/35596-06.htm");
            }

            player.sendPacket(html);
        }

        player.setLastQuestNpcObject(npc.getObjectId());
        return "";
    }

    @Override
    public void onSkillSee(Npc npc, Player caster, Skill skill, List<WorldObject> targets, boolean isSummon) {
        if (targets.contains(npc)) {
            final Clan clan = caster.getClan();
            if ((clan == null) || !acceptedClans.contains(clan)) {
                return;
            }

            final int index = acceptedClans.indexOf(clan);
            int warIndex = Integer.MIN_VALUE;

            if (npc.isInsideRadius2D(caster, 60)) {
                switch (skill.getId()) {
                    case 2240: {
                        if (getRandom(100) < 10) {
                            addSpawn(
                                    ENRAGED_YETI,
                                    caster.getX() + 10,
                                    caster.getY() + 10,
                                    caster.getZ(),
                                    0,
                                    false,
                                    0,
                                    false);
                        }

                        reduceGourdHp(index, caster);
                        break;
                    }
                    case 2241: {
                        warIndex = rndEx(acceptedClans.size(), index);
                        if (warIndex == Integer.MIN_VALUE) {
                            return;
                        }

                        increaseGourdHp(warIndex);
                        break;
                    }
                    case 2242: {
                        warIndex = rndEx(acceptedClans.size(), index);
                        if (warIndex == Integer.MIN_VALUE) {
                            return;
                        }

                        moveGourds(warIndex);
                        break;
                    }
                    case 2243: {
                        warIndex = rndEx(acceptedClans.size(), index);
                        if (warIndex == Integer.MIN_VALUE) {
                            return;
                        }

                        castDebuffsOnEnemies(caster, warIndex);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        final Clan clan = killer.getClan();
        if ((clan == null) || !acceptedClans.contains(clan)) {
            return;
        }

        final int index = acceptedClans.indexOf(clan);

        if (npc.getId() == CHEST) {
            chestDie(npc);
            chests.remove(npc);

            final int chance = ClockBridge.random().nextInt(100);
            if (chance <= 5) {
                npc.dropItem(killer, ITEM_A, 1);
            } else if ((chance > 5) && (chance <= 10)) {
                npc.dropItem(killer, ITEM_B, 1);
            } else if ((chance > 10) && (chance <= 15)) {
                npc.dropItem(killer, ITEM_C, 1);
            } else if ((chance > 15) && (chance <= 20)) {
                npc.dropItem(killer, ITEM_D, 1);
            } else if ((chance > 20) && (chance <= 25)) {
                npc.dropItem(killer, ITEM_E, 1);
            } else if ((chance > 25) && (chance <= 30)) {
                npc.dropItem(killer, ITEM_F, 1);
            } else if ((chance > 30) && (chance <= 35)) {
                npc.dropItem(killer, ITEM_G, 1);
            } else if ((chance > 35) && (chance <= 40)) {
                npc.dropItem(killer, ITEM_H, 1);
            } else if ((chance > 40) && (chance <= 45)) {
                npc.dropItem(killer, ITEM_I, 1);
            } else if ((chance > 45) && (chance <= 50)) {
                npc.dropItem(killer, ITEM_K, 1);
            } else if ((chance > 50) && (chance <= 55)) {
                npc.dropItem(killer, ITEM_L, 1);
            } else if ((chance > 55) && (chance <= 60)) {
                npc.dropItem(killer, ITEM_N, 1);
            } else if ((chance > 60) && (chance <= 65)) {
                npc.dropItem(killer, ITEM_O, 1);
            } else if ((chance > 65) && (chance <= 70)) {
                npc.dropItem(killer, ITEM_P, 1);
            } else if ((chance > 70) && (chance <= 75)) {
                npc.dropItem(killer, ITEM_R, 1);
            } else if ((chance > 75) && (chance <= 80)) {
                npc.dropItem(killer, ITEM_S, 1);
            } else if ((chance > 80) && (chance <= 85)) {
                npc.dropItem(killer, ITEM_T, 1);
            } else if ((chance > 85) && (chance <= 90)) {
                npc.dropItem(killer, ITEM_U, 1);
            } else if ((chance > 90) && (chance <= 95)) {
                npc.dropItem(killer, ITEM_W, 1);
            } else if (chance > 95) {
                npc.dropItem(killer, ITEM_Y, 1);
            }
        }

        if (npc.getId() == GOURDS[index]) {
            missionAccomplished = true;
            winner = ClanTable.getInstance().getClan(clan.getId());

            synchronized (this) {
                cancelSiegeTask();
                endSiege();

                ThreadPool.schedule(
                        () -> {
                            for (int id : playersOnArena) {
                                final Player pl = World.getPlayer(id);
                                if (pl != null) {
                                    pl.teleToLocation(TeleportWhereType.TOWN);
                                }
                            }

                            playersOnArena = new ArrayList<>();
                        },
                        120 * 1000);
            }
        }
    }

    @Override
    public void onSpawn(Npc npc) {
        if (npc.getId() == ENRAGED_YETI) {
            npc.broadcastSay(ChatType.NPC_SHOUT, NpcStringId.OOOH_WHO_POURED_NECTAR_ON_MY_HEAD_WHILE_I_WAS_SLEEPING);
        }

        if (ArrayUtil.contains(GOURDS, npc.getId())) {
            npc.disableCoreAI(true);
            npc.setImmobilized(true);
        }
    }

    @Override
    public void startSiege() {
        if ((acceptedClans == null) || acceptedClans.isEmpty() || (acceptedClans.size() < 2)) {
            onSiegeEnds();
            acceptedClans.clear();
            hall.updateNextSiege();
            final SystemMessage sm =
                    new SystemMessage(SystemMessageId.THE_SIEGE_OF_S1_HAS_BEEN_CANCELED_DUE_TO_LACK_OF_INTEREST);
            sm.addString(
                    ClanHallTable.getInstance().getClanHallById(hall.getId()).getName());
            World.broadcastToAllOnlinePlayers(sm);
            return;
        }

        spawnGourds();
        spawnYetis();
    }

    @Override
    public void prepareOwner() {
        if (hall.getOwnerId() > 0) {
            registerClan(ClanTable.getInstance().getClan(hall.getOwnerId()), 10000, true);
        }

        hall.banishForeigners();
        final SystemMessage msg = new SystemMessage(SystemMessageId.THE_REGISTRATION_TERM_FOR_S1_HAS_ENDED);
        msg.addString(ClanHallTable.getInstance().getClanHallById(hall.getId()).getName());
        World.broadcastToAllOnlinePlayers(msg);
        hall.updateSiegeStatus(SiegeStatus.WAITING_BATTLE);
        siegeTask = ThreadPool.schedule(new SiegeStarts(), 3600000);
    }

    @Override
    public void endSiege() {
        if (hall.getOwnerId() > 0) {
            final Clan clan = ClanTable.getInstance().getClan(hall.getOwnerId());
            clan.setHideoutId(0);
            hall.free();
        }

        super.endSiege();
    }

    @Override
    public void onSiegeEnds() {
        unSpawnGourds();
        unSpawnYetis();
        unSpawnChests();
        clearTables();
    }

    protected void portToArena(Player leader, int arena) {
        if ((arena < 0) || (arena > 3)) {
            log.warn("Wrong arena id passed: {}", arena);
            return;
        }

        for (Player pc : leader.getParty().getMembers()) {
            if (pc != null) {
                pc.stopAllEffects();
                if (pc.hasSummon()) {
                    pc.getSummon().unSummon(pc);
                }

                playersOnArena.add(pc.getObjectId());
                pc.teleToLocation(ARENAS[arena][0], ARENAS[arena][1], ARENAS[arena][2], true);
            }
        }
    }

    protected void spawnYetis() {
        if ((acceptedClans == null) || acceptedClans.isEmpty()) {
            return;
        }

        for (int i = 0; i < acceptedClans.size(); i++) {
            if (yetis[i] == null) {
                try {
                    yetis[i] = addSpawn(
                            YETIS[i], YETIS_SPAWN[i][0], YETIS_SPAWN[i][1], YETIS_SPAWN[i][2], 0, false, 0, false);
                    yetis[i].setHeading(1);
                    task = ThreadPool.scheduleAtFixedRate(new GenerateTask(yetis[i]), 10000, 300000);
                } catch (Exception e) {
                    log.warn("Problem with spawnYetis: {}", e.getMessage());
                }
            }
        }
    }

    protected void spawnGourds() {
        if ((acceptedClans == null) || acceptedClans.isEmpty() || (acceptedClans.size() < 2)) {
            return;
        }

        for (int i = 0; i < acceptedClans.size(); i++) {

            try {
                gourds[i] = addSpawn(GOURDS[i], ARENAS[i][0] + 150, ARENAS[i][1] + 150, ARENAS[i][2], 1);
            } catch (Exception e) {
                log.warn("Problem with spawnGourds: {}", e.getMessage());
            }
        }

        chesttask = ThreadPool.scheduleAtFixedRate(new ChestsSpawn(), 5000, 5000);
    }

    protected void unSpawnYetis() {
        if ((acceptedClans == null) || acceptedClans.isEmpty()) {
            return;
        }

        for (int i = 0; i < acceptedClans.size(); i++) {
            if (yetis[i] != null) {
                yetis[i].deleteMe();
            }

            if (task != null) {
                task.cancel(false);
                task = null;
            }
        }
    }

    protected void unSpawnGourds() {
        if ((acceptedClans == null) || acceptedClans.isEmpty()) {
            return;
        }

        for (int i = 0; i < acceptedClans.size(); i++) {
            if ((gourds[i] != null) && (gourds[i].getSpawn().getLastSpawn() != null)) {
                gourds[i].getSpawn().getLastSpawn().deleteMe();
            }
        }
    }

    protected void unSpawnChests() {
        if (!chests.isEmpty()) {
            for (Npc chest : chests) {
                if (chest != null) {
                    chest.deleteMe();
                    if (chesttask != null) {
                        chesttask.cancel(false);
                        chesttask = null;
                    }
                }
            }
        }
    }

    private static void moveGourds(int index) {
        final Npc[] tempArray = gourds;
        for (int i = 0; i < index; i++) {
            final Npc oldSpawn = gourds[(index - 1) - i];
            final Npc curSpawn = tempArray[i];
            gourds[(index - 1) - i] = curSpawn;

            final int newX = oldSpawn.getX();
            final int newY = oldSpawn.getY();
            final int newZ = oldSpawn.getZ();
            curSpawn.getSpawn().getLastSpawn().teleToLocation(newX, newY, newZ, true);
        }
    }

    private static void reduceGourdHp(int index, Player player) {
        final Npc gourd = gourds[index];
        if ((gourd != null) && (gourd.getSpawn().getLastSpawn() != null)) {
            gourd.getSpawn().getLastSpawn().reduceCurrentHp(1000, player, null);
        }
    }

    private static void increaseGourdHp(int index) {
        final Npc gourd = gourds[index];
        if (gourd != null) {
            final Npc gourdNpc = gourd.getSpawn().getLastSpawn();
            if (gourdNpc != null) {
                gourdNpc.setCurrentHp(gourdNpc.getCurrentHp() + 1000);
            }
        }
    }

    private void castDebuffsOnEnemies(Player player, int myArena) {
        final Clan clan = player.getClan();
        if (acceptedClans.contains(clan)) {
            final int index = acceptedClans.indexOf(clan);
            if (playersOnArena.contains(player.getObjectId())) {
                for (Player pl : player.getParty().getMembers()) {
                    if (index != myArena) {
                        continue;
                    }

                    if (pl != null) {
                        for (Skill sk : DEBUFFS) {
                            sk.applyEffects(pl, pl);
                        }
                    }
                }
            }
        }
    }

    private void registerClan(Clan clan, long count, boolean register) {
        if (register) {
            final SiegeClan sc = new SiegeClan(clan.getId(), SiegeClanType.ATTACKER);
            getAttackers().put(clan.getId(), sc);

            final int spotLeft = 4;
            for (int i = 0; i < spotLeft; i++) {
                long counter = 0;
                Clan fightclan = null;
                for (int clanId : warDecreesCount.keySet()) {
                    final Clan actingClan = ClanTable.getInstance().getClan(clanId);
                    if ((actingClan == null) || (actingClan.getDissolvingExpiryTime() > 0)) {
                        warDecreesCount.remove(clanId);
                        continue;
                    }

                    final long counts = warDecreesCount.get(clanId);
                    if (counts > counter) {
                        counter = counts;
                        fightclan = actingClan;
                    }
                }

                if ((fightclan != null) && (acceptedClans.size() < 4)) {
                    acceptedClans.add(clan);
                }
            }

            updateAttacker(clan.getId(), count, false);
        } else {
            updateAttacker(clan.getId(), 0, true);
        }
    }

    private void updateAttacker(int clanId, long count, boolean remove) {
        try (Connection con = DatabaseFactory.getConnection()) {
            PreparedStatement statement;
            if (remove) {
                statement = con.prepareStatement("DELETE FROM rainbowsprings_attacker_list WHERE clanId = ?");
                statement.setInt(1, clanId);
            } else {
                statement = con.prepareStatement("INSERT INTO rainbowsprings_attacker_list VALUES (?,?)");
                statement.setInt(1, clanId);
                statement.setLong(2, count);
            }

            statement.execute();
            statement.close();
        } catch (Exception e) {
            log.warn("Problem with updateAttacker: {}", e.getMessage());
        }
    }

    @Override
    public void loadAttackers() {
        try (Connection con = DatabaseFactory.getConnection()) {
            final PreparedStatement statement = con.prepareStatement("SELECT * FROM rainbowsprings_attacker_list");
            final ResultSet rset = statement.executeQuery();
            while (rset.next()) {
                final int clanId = rset.getInt("clanId");
                final long count = rset.getLong("war_decrees_count");
                warDecreesCount.put(clanId, count);
                for (int clan : warDecreesCount.keySet()) {
                    final Clan loadClan = ClanTable.getInstance().getClan(clan);
                    acceptedClans.add(loadClan);
                }
            }

            rset.close();
            statement.close();
        } catch (Exception e) {
            log.warn("loadAttackers() failed", e);
        }
    }

    private void clearTables() {
        try (Connection con = DatabaseFactory.getConnection()) {
            final PreparedStatement stat1 = con.prepareStatement("DELETE FROM rainbowsprings_attacker_list");
            stat1.execute();
            stat1.close();
        } catch (Exception e) {
            log.warn("clearTables() failed", e);
        }
    }

    protected class ChestsSpawn implements Runnable {
        @Override
        public void run() {
            for (int i = 0; i < acceptedClans.size(); i++) {
                if (arenaChestsCnt[i] < 4) {
                    final Npc chest = addSpawn(
                            CHEST,
                            CHESTS_SPAWN[i][0] + getRandom(-400, 400),
                            CHESTS_SPAWN[i][1] + getRandom(-400, 400),
                            CHESTS_SPAWN[i][2],
                            0,
                            false,
                            0,
                            false);
                    if (chest != null) {
                        chests.add(chest);
                    }

                    arenaChestsCnt[i]++;
                }

                if (arenaChestsCnt[i] < 4) {
                    final Npc chest = addSpawn(
                            CHEST,
                            CHESTS_SPAWN[i][0] + getRandom(-400, 400),
                            CHESTS_SPAWN[i][1] + getRandom(-400, 400),
                            CHESTS_SPAWN[i][2],
                            0,
                            false,
                            0,
                            false);
                    if (chest != null) {
                        chests.add(chest);
                    }

                    arenaChestsCnt[i]++;
                }

                if (arenaChestsCnt[i] < 4) {
                    final Npc chest = addSpawn(
                            CHEST,
                            CHESTS_SPAWN[i][0] + getRandom(-400, 400),
                            CHESTS_SPAWN[i][1] + getRandom(-400, 400),
                            CHESTS_SPAWN[i][2],
                            0,
                            false,
                            0,
                            false);
                    if (chest != null) {
                        chests.add(chest);
                    }

                    arenaChestsCnt[i]++;
                }

                if (arenaChestsCnt[i] < 4) {
                    final Npc chest = addSpawn(
                            CHEST,
                            CHESTS_SPAWN[i][0] + getRandom(-400, 400),
                            CHESTS_SPAWN[i][1] + getRandom(-400, 400),
                            CHESTS_SPAWN[i][2],
                            0,
                            false,
                            0,
                            false);
                    if (chest != null) {
                        chests.add(chest);
                    }

                    arenaChestsCnt[i]++;
                }
            }
        }
    }

    protected class GenerateTask implements Runnable {
        protected final Npc npc;

        protected GenerateTask(Npc npc) {
            this.npc = npc;
        }

        @Override
        public void run() {
            generated = getRandom(WORLD_LIST.length);
            final Word word = WORLD_LIST[generated];
            final ExShowScreenMessage msg = new ExShowScreenMessage(word.getName(), 5000);
            final int region = MapRegionData.getInstance().getMapRegionLocId(npc.getX(), npc.getY());
            for (Player player : World.getPlayers()) {
                if ((region == MapRegionData.getInstance().getMapRegionLocId(player.getX(), player.getY()))
                        && LocationUtil.checkIfInRange(750, npc, player, false)) {
                    player.sendPacket(msg);
                }
            }
        }
    }

    protected void chestDie(Npc npc) {
        for (int i = 0; i < acceptedClans.size(); i++) {
            arenaChestsCnt[i]--;
        }
    }

    private int rndEx(int size, int ex) {
        int rnd = Integer.MIN_VALUE;
        for (int i = 0; i < Byte.MAX_VALUE; i++) {
            rnd = ClockBridge.random().nextInt(size);
            if (rnd != ex) {
                break;
            }
        }

        return rnd;
    }

    public static void main(String[] args) {
        new RainbowSpringsChateau();
    }
}
