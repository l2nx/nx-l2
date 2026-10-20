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
package app.l2nx.highfive.mechanics.olympiad;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;

import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.config.OlympiadConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.ClassListData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.serverpackets.SocialAction;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.model.CharacterClanAlly;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.olympiad.HeroDiaryRepository;
import app.l2nx.highfive.repository.olympiad.HeroRepository;
import app.l2nx.highfive.repository.olympiad.OlympiadFightRepository;
import app.l2nx.highfive.repository.olympiad.model.HeroDiaryEntry;
import app.l2nx.highfive.repository.olympiad.model.HeroRow;
import app.l2nx.highfive.repository.olympiad.model.OlympiadFight;
import app.l2nx.highfive.util.StatSet;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Hero entity.
 * @author godson
 */
@Slf4j
@Component
public class Hero {
    private static volatile Hero instance;

    private static final DateTimeFormatter DIARY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");
    private static final DateTimeFormatter FIGHT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final List<Integer> HERO_ITEM_IDS =
            List.of(6842, 6611, 6612, 6613, 6614, 6615, 6616, 6617, 6618, 6619, 6620, 6621, 9388, 9389, 9390);

    private static final Map<Integer, StatSet> HEROES = new ConcurrentHashMap<>();
    private static final Map<Integer, StatSet> COMPLETE_HEROS = new ConcurrentHashMap<>();
    private static final Map<Integer, StatSet> HERO_COUNTS = new ConcurrentHashMap<>();
    private static final Map<Integer, List<StatSet>> HERO_FIGHTS = new ConcurrentHashMap<>();
    private static final Map<Integer, List<StatSet>> HERO_DIARY = new ConcurrentHashMap<>();
    private static final Map<Integer, String> HERO_MESSAGE = new ConcurrentHashMap<>();

    public static final String COUNT = "count";
    public static final String PLAYED = "played";
    public static final String CLAIMED = "claimed";
    public static final String CLAN_NAME = "clan_name";
    public static final String CLAN_CREST = "clan_crest";
    public static final String ALLY_NAME = "ally_name";
    public static final String ALLY_CREST = "ally_crest";

    public static final int ACTION_RAID_KILLED = 1;
    public static final int ACTION_HERO_GAINED = 2;
    public static final int ACTION_CASTLE_TAKEN = 3;

    private final HeroRepository heroRepository;
    private final HeroDiaryRepository diaryRepository;
    private final OlympiadFightRepository fightRepository;
    private final CharacterRepository characterRepository;
    private final ItemRepository itemRepository;

    public Hero(
            HeroRepository heroRepository,
            HeroDiaryRepository diaryRepository,
            OlympiadFightRepository fightRepository,
            CharacterRepository characterRepository,
            ItemRepository itemRepository) {
        this.heroRepository = heroRepository;
        this.diaryRepository = diaryRepository;
        this.fightRepository = fightRepository;
        this.characterRepository = characterRepository;
        this.itemRepository = itemRepository;
        instance = this;
    }

    public void load() {
        if (OlympiadConfig.OLYMPIAD_ENABLED) {
            init();
        }
    }

    private void init() {
        HEROES.clear();
        COMPLETE_HEROS.clear();
        HERO_COUNTS.clear();
        HERO_FIGHTS.clear();
        HERO_DIARY.clear();
        HERO_MESSAGE.clear();

        try {
            for (HeroRow row : heroRepository.findPlayed()) {
                final int charId = row.objectId();
                final StatSet hero = toStatSet(row);
                loadFights(charId);
                loadDiary(charId);
                loadMessage(charId);
                processHeros(charId, hero);
                HEROES.put(charId, hero);
            }

            for (HeroRow row : heroRepository.findAll()) {
                final StatSet hero = toStatSet(row);
                processHeros(row.objectId(), hero);
                COMPLETE_HEROS.put(row.objectId(), hero);
            }
        } catch (DataAccessException e) {
            log.warn("Hero System: Could not load Heroes: {}", e.getMessage());
        }

        log.info("Hero System: Loaded {} Heroes.", HEROES.size());
        log.info("Hero System: Loaded {} all time Heroes.", COMPLETE_HEROS.size());
    }

    private static StatSet toStatSet(HeroRow row) {
        final StatSet hero = new StatSet();
        hero.set(Olympiad.CHAR_NAME, row.characterName());
        hero.set(Olympiad.CLASS_ID, row.classId());
        hero.set(COUNT, row.count());
        hero.set(PLAYED, row.played() ? 1 : 0);
        hero.set(CLAIMED, row.claimed());
        return hero;
    }

    private static HeroRow toRow(int objectId, StatSet hero) {
        return HeroRow.builder()
                .objectId(objectId)
                .classId(hero.getInt(Olympiad.CLASS_ID))
                .count(hero.getInt(COUNT, 0))
                .played(hero.getInt(PLAYED, 0) == 1)
                .claimed(hero.getBoolean(CLAIMED, false))
                .build();
    }

    void processHeros(int charId, StatSet hero) {
        final CharacterClanAlly clanAlly =
                characterRepository.findClanAndAlly(charId).orElse(null);
        if (clanAlly == null) {
            return;
        }

        final int clanId = clanAlly.clanId();
        final Clan clan = clanId > 0 ? ClanTable.getInstance().getClan(clanId) : null;
        final boolean inAlly = (clan != null) && (clanAlly.allyId() > 0);
        hero.set(CLAN_CREST, clan == null ? 0 : clan.getCrestId());
        hero.set(ALLY_CREST, inAlly ? clan.getAllyCrestId() : 0);
        // Without a clan or ally the name stays absent; ExHeroList writes the empty string the protocol expects.
        hero.remove(CLAN_NAME);
        hero.remove(ALLY_NAME);
        if (clan != null) {
            hero.set(CLAN_NAME, clan.getName());
        }

        if (inAlly) {
            hero.set(ALLY_NAME, clan.getAllyName());
        }
    }

    private String calcFightTime(long fightTimeValue) {
        final String format = String.format("%%0%dd", 2);
        final long fightTime = fightTimeValue / 1000;
        return String.format(format, (fightTime % 3600) / 60) + ":" + String.format(format, fightTime % 60);
    }

    /**
     * Restore hero message from Db.
     * @param charId
     */
    public void loadMessage(int charId) {
        try {
            heroRepository.findMessage(charId).ifPresent(message -> HERO_MESSAGE.put(charId, message));
        } catch (DataAccessException e) {
            log.warn("Hero System: Could not load Hero Message for CharId: {}: {}", charId, e.getMessage());
        }
    }

    public void loadDiary(int charId) {
        final List<StatSet> diary = new ArrayList<>();
        int diaryentries = 0;
        try {
            for (HeroDiaryEntry entry : diaryRepository.findByCharacter(charId)) {
                final StatSet diaryEntry = new StatSet();
                final long time = epochMillisOrZero(entry.recordedAt());
                final int action = entry.action();
                final int param = entry.param();
                final String date = DIARY_DATE_FORMAT.format(Instant.ofEpochMilli(time)
                        .atZone(ClockBridge.wallClock().getZone()));
                diaryEntry.set("date", date);
                if (action == ACTION_RAID_KILLED) {
                    final NpcTemplate template = NpcData.getInstance().getTemplate(param);
                    if (template != null) {
                        diaryEntry.set("action", template.getName() + " was defeated");
                    }
                } else if (action == ACTION_HERO_GAINED) {
                    diaryEntry.set("action", "Gained Hero status");
                } else if (action == ACTION_CASTLE_TAKEN) {
                    final Castle castle = CastleManager.getInstance().getCastleById(param);
                    if (castle != null) {
                        diaryEntry.set("action", castle.getName() + " Castle was successfuly taken");
                    }
                }

                diary.add(diaryEntry);
                diaryentries++;
            }

            HERO_DIARY.put(charId, diary);

            log.info(
                    "Hero System: Loaded {} diary entries for Hero: {}",
                    diaryentries,
                    CharInfoTable.getInstance().getNameById(charId));
        } catch (DataAccessException e) {
            log.warn("Hero System: Could not load Hero Diary for CharId: {}: {}", charId, e.getMessage());
        }
    }

    public void loadFights(int charId) {
        final List<StatSet> fights = new ArrayList<>();
        final StatSet heroCountData = new StatSet();
        final Calendar data = ClockBridge.calendar();
        data.set(Calendar.DAY_OF_MONTH, 1);
        data.set(Calendar.HOUR_OF_DAY, 0);
        data.set(Calendar.MINUTE, 0);
        data.set(Calendar.MILLISECOND, 0);

        final long from = data.getTimeInMillis();
        int numberOfFights = 0;
        int victories = 0;
        int losses = 0;
        int draws = 0;

        try {
            for (OlympiadFight row : fightRepository.findByCharacterStartedBefore(charId, Instant.ofEpochMilli(from))) {
                final int charOneId = row.firstObjectId();
                final int charOneClass = row.firstClassId();
                final int charTwoId = row.secondObjectId();
                final int charTwoClass = row.secondClassId();
                final int winner = row.winner();
                final long start = epochMillisOrZero(row.startedAt());
                final long time = row.durationMs();
                final int classed = row.classed() ? 1 : 0;
                if (charId == charOneId) {
                    final String name = CharInfoTable.getInstance().getNameById(charTwoId);
                    final String cls =
                            ClassListData.getInstance().getClass(charTwoClass).className();
                    if ((name != null) && (cls != null)) {
                        final StatSet fight = new StatSet();
                        fight.set("oponent", name);
                        fight.set("oponentclass", cls);
                        fight.set("time", calcFightTime(time));
                        final String date = FIGHT_DATE_FORMAT.format(Instant.ofEpochMilli(start)
                                .atZone(ClockBridge.wallClock().getZone()));
                        fight.set("start", date);
                        fight.set("classed", classed);
                        if (winner == 1) {
                            fight.set("result", "<font color=\"00ff00\">victory</font>");
                            victories++;
                        } else if (winner == 2) {
                            fight.set("result", "<font color=\"ff0000\">loss</font>");
                            losses++;
                        } else if (winner == 0) {
                            fight.set("result", "<font color=\"ffff00\">draw</font>");
                            draws++;
                        }

                        fights.add(fight);
                        numberOfFights++;
                    }
                } else if (charId == charTwoId) {
                    final String name = CharInfoTable.getInstance().getNameById(charOneId);
                    final String cls =
                            ClassListData.getInstance().getClass(charOneClass).className();
                    if ((name != null) && (cls != null)) {
                        final StatSet fight = new StatSet();
                        fight.set("oponent", name);
                        fight.set("oponentclass", cls);
                        fight.set("time", calcFightTime(time));
                        final String date = FIGHT_DATE_FORMAT.format(Instant.ofEpochMilli(start)
                                .atZone(ClockBridge.wallClock().getZone()));
                        fight.set("start", date);
                        fight.set("classed", classed);
                        if (winner == 1) {
                            fight.set("result", "<font color=\"ff0000\">loss</font>");
                            losses++;
                        } else if (winner == 2) {
                            fight.set("result", "<font color=\"00ff00\">victory</font>");
                            victories++;
                        } else if (winner == 0) {
                            fight.set("result", "<font color=\"ffff00\">draw</font>");
                            draws++;
                        }

                        fights.add(fight);
                        numberOfFights++;
                    }
                }
            }

            heroCountData.set("victory", victories);
            heroCountData.set("draw", draws);
            heroCountData.set("loss", losses);
            HERO_COUNTS.put(charId, heroCountData);
            HERO_FIGHTS.put(charId, fights);

            log.info(
                    "Hero System: Loaded {} fights for Hero: {}",
                    numberOfFights,
                    CharInfoTable.getInstance().getNameById(charId));
        } catch (DataAccessException e) {
            log.warn("Hero System: Could not load Hero fights history for CharId: {}", charId, e);
        }
    }

    public Map<Integer, StatSet> getHeroes() {
        return HEROES;
    }

    public int getHeroByClass(int classid) {
        for (Entry<Integer, StatSet> e : HEROES.entrySet()) {
            if (e.getValue().getInt(Olympiad.CLASS_ID) == classid) {
                return e.getKey();
            }
        }

        return 0;
    }

    public void resetData() {
        HERO_DIARY.clear();
        HERO_FIGHTS.clear();
        HERO_COUNTS.clear();
        HERO_MESSAGE.clear();
    }

    public void showHeroDiary(Player player, int heroclass, int charid, int page) {
        final int perpage = 10;
        final List<StatSet> mainList = HERO_DIARY.get(charid);
        if (mainList != null) {
            final NpcHtmlMessage diaryReply = new NpcHtmlMessage();
            final String htmContent = HtmCache.getInstance().getHtm(player, "data/html/olympiad/herodiary.htm");
            final String heroMessage = HERO_MESSAGE.get(charid);
            if (htmContent != null) {
                diaryReply.setHtml(htmContent);
                diaryReply.replace("%heroname%", CharInfoTable.getInstance().getNameById(charid));
                diaryReply.replace("%message%", heroMessage == null ? "" : heroMessage);
                diaryReply.disableValidation();

                if (!mainList.isEmpty()) {
                    final List<StatSet> list = new ArrayList<>(mainList);
                    Collections.reverse(list);

                    boolean color = true;
                    final StringBuilder fList = new StringBuilder(500);
                    int counter = 0;
                    int breakat = 0;
                    for (int i = (page - 1) * perpage; i < list.size(); i++) {
                        breakat = i;
                        final StatSet diaryEntry = list.get(i);
                        fList.append("<tr><td>");
                        if (color) {
                            fList.append("<table width=270 bgcolor=\"131210\">");
                        } else {
                            fList.append("<table width=270>");
                        }
                        fList.append("<tr><td width=270><font color=\"LEVEL\">" + diaryEntry.getString("date")
                                + ":xx</font></td></tr>");
                        fList.append("<tr><td width=270>" + diaryEntry.getString("action", "") + "</td></tr>");
                        fList.append("<tr><td>&nbsp;</td></tr></table>");
                        fList.append("</td></tr>");
                        color = !color;
                        counter++;
                        if (counter >= perpage) {
                            break;
                        }
                    }

                    if (breakat < (list.size() - 1)) {
                        diaryReply.replace(
                                "%buttprev%",
                                "<button value=\"Prev\" action=\"bypass _diary?class=" + heroclass + "&page="
                                        + (page + 1)
                                        + "\" width=60 height=25 back=\"L2UI_ct1.button_df\""
                                        + " fore=\"L2UI_ct1.button_df\">");
                    } else {
                        diaryReply.replace("%buttprev%", "");
                    }

                    if (page > 1) {
                        diaryReply.replace(
                                "%buttnext%",
                                "<button value=\"Next\" action=\"bypass _diary?class=" + heroclass + "&page="
                                        + (page - 1)
                                        + "\" width=60 height=25 back=\"L2UI_ct1.button_df\""
                                        + " fore=\"L2UI_ct1.button_df\">");
                    } else {
                        diaryReply.replace("%buttnext%", "");
                    }

                    diaryReply.replace("%list%", fList.toString());
                } else {
                    diaryReply.replace("%list%", "");
                    diaryReply.replace("%buttprev%", "");
                    diaryReply.replace("%buttnext%", "");
                }

                player.sendPacket(diaryReply);
            }
        }
    }

    public void showHeroFights(Player player, int heroclass, int charid, int page) {
        final int perpage = 20;
        int win = 0;
        int loss = 0;
        int draw = 0;

        final List<StatSet> heroFights = HERO_FIGHTS.get(charid);
        if (heroFights != null) {
            final NpcHtmlMessage fightReply = new NpcHtmlMessage();
            final String htmContent = HtmCache.getInstance().getHtm(player, "data/html/olympiad/herohistory.htm");
            if (htmContent != null) {
                fightReply.setHtml(htmContent);
                fightReply.replace("%heroname%", CharInfoTable.getInstance().getNameById(charid));
                if (!heroFights.isEmpty()) {
                    final StatSet heroCount = HERO_COUNTS.get(charid);
                    if (heroCount != null) {
                        win = heroCount.getInt("victory");
                        loss = heroCount.getInt("loss");
                        draw = heroCount.getInt("draw");
                    }

                    boolean color = true;
                    final StringBuilder fList = new StringBuilder(500);
                    int counter = 0;
                    int breakat = 0;
                    for (int i = (page - 1) * perpage; i < heroFights.size(); i++) {
                        breakat = i;
                        final StatSet fight = heroFights.get(i);
                        fList.append("<tr><td>");
                        if (color) {
                            fList.append("<table width=270 bgcolor=\"131210\">");
                        } else {
                            fList.append("<table width=270>");
                        }
                        fList.append("<tr><td width=220><font color=\"LEVEL\">" + fight.getString("start")
                                + "</font>&nbsp;&nbsp;" + fight.getString("result") + "</td><td width=50 align=right>"
                                + (fight.getInt("classed") > 0
                                        ? "<font color=\"FFFF99\">cls</font>"
                                        : "<font color=\"999999\">non-cls<font>")
                                + "</td></tr>");
                        fList.append("<tr><td width=220>vs " + fight.getString("oponent") + " ("
                                + fight.getString("oponentclass") + ")</td><td width=50 align=right>("
                                + fight.getString("time") + ")</td></tr>");
                        fList.append("<tr><td colspan=2>&nbsp;</td></tr></table>");
                        fList.append("</td></tr>");
                        color = !color;
                        counter++;
                        if (counter >= perpage) {
                            break;
                        }
                    }

                    if (breakat < (heroFights.size() - 1)) {
                        fightReply.replace(
                                "%buttprev%",
                                "<button value=\"Prev\" action=\"bypass _match?class=" + heroclass + "&page="
                                        + (page + 1)
                                        + "\" width=60 height=25 back=\"L2UI_ct1.button_df\""
                                        + " fore=\"L2UI_ct1.button_df\">");
                    } else {
                        fightReply.replace("%buttprev%", "");
                    }

                    if (page > 1) {
                        fightReply.replace(
                                "%buttnext%",
                                "<button value=\"Next\" action=\"bypass _match?class=" + heroclass + "&page="
                                        + (page - 1)
                                        + "\" width=60 height=25 back=\"L2UI_ct1.button_df\""
                                        + " fore=\"L2UI_ct1.button_df\">");
                    } else {
                        fightReply.replace("%buttnext%", "");
                    }

                    fightReply.replace("%list%", fList.toString());
                } else {
                    fightReply.replace("%list%", "");
                    fightReply.replace("%buttprev%", "");
                    fightReply.replace("%buttnext%", "");
                }

                fightReply.replace("%win%", String.valueOf(win));
                fightReply.replace("%draw%", String.valueOf(draw));
                fightReply.replace("%loos%", String.valueOf(loss));
                player.sendPacket(fightReply);
            }
        }
    }

    public synchronized void computeNewHeroes(List<StatSet> newHeroes) {
        updateHeroes(true);

        for (Integer objectId : HEROES.keySet()) {
            final Player player = World.getPlayer(objectId);
            if (player == null) {
                continue;
            }

            player.setHero(false);

            for (int i = 0; i < Inventory.PAPERDOLL_TOTALSLOTS; i++) {
                final Item equippedItem = player.getInventory().getPaperdollItem(i);
                if ((equippedItem != null) && equippedItem.isHeroItem()) {
                    player.getInventory().unEquipItemInSlot(i);
                }
            }

            final InventoryUpdate iu = new InventoryUpdate();
            for (Item item : player.getInventory().getItems()) {
                if ((item != null) && item.isHeroItem()) {
                    player.destroyItem(ItemProcessType.DESTROY, item, null, true);
                    iu.addRemovedItem(item);
                }
            }

            if (!iu.getItems().isEmpty()) {
                player.sendInventoryUpdate(iu);
            }

            player.broadcastUserInfo();
        }

        deleteItemsInDb();
        HEROES.clear();

        if (newHeroes.isEmpty()) {
            return;
        }

        for (StatSet hero : newHeroes) {
            final int charId = hero.getInt(Olympiad.CHAR_ID);
            final StatSet oldHero = COMPLETE_HEROS.get(charId);
            if (oldHero != null) {
                final int count = oldHero.getInt(COUNT);
                oldHero.set(COUNT, count + 1);
                oldHero.set(PLAYED, 1);
                oldHero.set(CLAIMED, false);
                HEROES.put(charId, oldHero);
            } else {
                final StatSet newHero = new StatSet();
                newHero.set(Olympiad.CHAR_NAME, hero.getString(Olympiad.CHAR_NAME));
                newHero.set(Olympiad.CLASS_ID, hero.getInt(Olympiad.CLASS_ID));
                newHero.set(COUNT, 1);
                newHero.set(PLAYED, 1);
                newHero.set(CLAIMED, false);
                HEROES.put(charId, newHero);
            }
        }

        updateHeroes(false);
    }

    public void updateHeroes(boolean setDefault) {
        try {
            if (setDefault) {
                heroRepository.resetPlayed();
                return;
            }

            final List<HeroRow> rows = new ArrayList<>();
            for (Entry<Integer, StatSet> entry : HEROES.entrySet()) {
                rows.add(toRow(entry.getKey(), entry.getValue()));
            }

            heroRepository.saveAll(rows);
            for (Entry<Integer, StatSet> entry : HEROES.entrySet()) {
                final int heroId = entry.getKey();
                if (!COMPLETE_HEROS.containsKey(heroId)) {
                    final StatSet hero = entry.getValue();
                    processHeros(heroId, hero);
                    COMPLETE_HEROS.put(heroId, hero);
                }
            }
        } catch (DataAccessException e) {
            log.warn("Hero System: Could not update Heroes: {}", e.getMessage());
        }
    }

    public void setHeroGained(int charId) {
        setDiaryData(charId, ACTION_HERO_GAINED, 0);
    }

    public void setRBkilled(int charId, int npcId) {
        setDiaryData(charId, ACTION_RAID_KILLED, npcId);

        final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
        final List<StatSet> list = HERO_DIARY.get(charId);
        if ((list == null) || (template == null)) {
            return;
        }

        // Prepare new data
        final StatSet diaryEntry = new StatSet();
        final String date = DIARY_DATE_FORMAT.format(Instant.ofEpochMilli(ClockBridge.wallMillis())
                .atZone(ClockBridge.wallClock().getZone()));
        diaryEntry.set("date", date);
        diaryEntry.set("action", template.getName() + " was defeated");

        // Add to old list.
        list.add(diaryEntry);
    }

    public void setCastleTaken(int charId, int castleId) {
        setDiaryData(charId, ACTION_CASTLE_TAKEN, castleId);

        final Castle castle = CastleManager.getInstance().getCastleById(castleId);
        final List<StatSet> list = HERO_DIARY.get(charId);
        if ((list == null) || (castle == null)) {
            return;
        }

        // Prepare new data
        final StatSet diaryEntry = new StatSet();
        final String date = DIARY_DATE_FORMAT.format(Instant.ofEpochMilli(ClockBridge.wallMillis())
                .atZone(ClockBridge.wallClock().getZone()));
        diaryEntry.set("date", date);
        diaryEntry.set("action", castle.getName() + " Castle was successfuly taken");

        // Add to old list.
        list.add(diaryEntry);
    }

    public void setDiaryData(int charId, int action, int param) {
        try {
            diaryRepository.insert(charId, ClockBridge.wallClock().instant(), action, param);
        } catch (DataAccessException e) {
            log.error("SQL exception while saving DiaryData: {}", e.getMessage());
        }
    }

    /**
     * Set new hero message for hero
     * @param player the player instance
     * @param message String to set
     */
    public void setHeroMessage(Player player, String message) {
        if (StringUtils.isBlank(message)) {
            clearHeroMessage(player.getObjectId());
        } else {
            HERO_MESSAGE.put(player.getObjectId(), message);
        }
    }

    private void clearHeroMessage(int charId) {
        HERO_MESSAGE.remove(charId);
        try {
            heroRepository.updateMessage(charId, null);
        } catch (DataAccessException e) {
            log.error("SQL exception while clearing HeroMessage:{}", e.getMessage());
        }
    }

    /**
     * Update hero message in database
     * @param charId character objid
     */
    public void saveHeroMessage(int charId) {
        if (!HERO_MESSAGE.containsKey(charId)) {
            return;
        }

        try {
            heroRepository.updateMessage(charId, HERO_MESSAGE.get(charId));
        } catch (DataAccessException e) {
            log.error("SQL exception while saving HeroMessage:{}", e.getMessage());
        }
    }

    private void deleteItemsInDb() {
        try {
            itemRepository.deleteExceptStaff(HERO_ITEM_IDS);
        } catch (DataAccessException e) {
            log.warn("Heroes: {}", e.getMessage());
        }
    }

    /**
     * Saving task for {@link Hero}<br>
     * Save all hero messages to DB.
     */
    public void shutdown() {
        HERO_MESSAGE.keySet().forEach(this::saveHeroMessage);
    }

    /**
     * Verifies if the given object ID belongs to a claimed hero.
     * @param objectId the player's object ID to verify
     * @return {@code true} if there are heros and the player is in the list, {@code false} otherwise
     */
    public boolean isHero(int objectId) {
        return HEROES.containsKey(objectId) && HEROES.get(objectId).getBoolean(CLAIMED);
    }

    /**
     * Verifies if the given object ID belongs to an unclaimed hero.
     * @param objectId the player's object ID to verify
     * @return {@code true} if player is unclaimed hero
     */
    public boolean isUnclaimedHero(int objectId) {
        return HEROES.containsKey(objectId) && !HEROES.get(objectId).getBoolean(CLAIMED);
    }

    /**
     * Claims the hero status for the given player.
     * @param player the player to become hero
     */
    public void claimHero(Player player) {
        StatSet hero = HEROES.get(player.getObjectId());
        if (hero == null) {
            hero = new StatSet();
            HEROES.put(player.getObjectId(), hero);
        }

        hero.set(CLAIMED, true);

        final Clan clan = player.getClan();
        if ((clan != null) && (clan.getLevel() >= 5)) {
            clan.addReputationScore(FeatureConfig.HERO_POINTS);
            final SystemMessage sm = new SystemMessage(
                    SystemMessageId
                            .CLAN_MEMBER_C1_WAS_NAMED_A_HERO_S2_POINTS_HAVE_BEEN_ADDED_TO_YOUR_CLAN_S_REPUTATION_SCORE);
            sm.addString(CharInfoTable.getInstance().getNameById(player.getObjectId()));
            sm.addInt(FeatureConfig.HERO_POINTS);
            clan.broadcastToOnlineMembers(sm);
        }

        player.setHero(true);
        player.broadcastPacket(new SocialAction(player.getObjectId(), 20016)); // Hero Animation
        player.broadcastUserInfo();

        // Set Gained hero and reload data.
        setHeroGained(player.getObjectId());
        loadFights(player.getObjectId());
        loadDiary(player.getObjectId());
        updateHeroes(false);
        clearHeroMessage(player.getObjectId());
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static Hero getInstance() {
        final Hero current = instance;
        if (current == null) {
            throw new IllegalStateException("Hero is used before the Spring context created it.");
        }
        return current;
    }
}
