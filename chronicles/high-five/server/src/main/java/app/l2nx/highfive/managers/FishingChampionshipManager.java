/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Folk;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.world.FishingChampionshipRepository;
import app.l2nx.highfive.repository.world.model.FishingChampionshipEntry;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.random.RandomGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * @author n0nam3
 * @date 08/08/2010 15:11
 */
@Slf4j
@Component
public class FishingChampionshipManager {
    private static volatile FishingChampionshipManager instance;

    // TODO: callers receive the bean through their constructor (#41).
    public static FishingChampionshipManager getInstance() {
        final FishingChampionshipManager current = instance;
        if (current == null) {
            throw new IllegalStateException("FishingChampionshipManager is used before the Spring context created it.");
        }
        return current;
    }

    private final FishingChampionshipRepository championshipRepository;

    protected long enddate = 0;
    protected final List<String> playersName = new ArrayList<>();
    protected final List<String> fishLength = new ArrayList<>();
    protected final List<String> winPlayersName = new ArrayList<>();
    protected final List<String> winFishLength = new ArrayList<>();
    protected final List<Fisher> tmpPlayers = new ArrayList<>();
    protected final List<Fisher> winPlayers = new ArrayList<>();
    protected double minFishLength = 0;
    protected boolean needRefresh = true;

    public FishingChampionshipManager(FishingChampionshipRepository championshipRepository) {
        this.championshipRepository = championshipRepository;
        instance = this;
    }

    public void load() {
        restoreData();
        refreshWinResult();
        recalculateMinLength();

        if (enddate <= ClockBridge.wallMillis()) {
            enddate = ClockBridge.wallMillis();
            new finishChamp().run();
        } else {
            ThreadPool.schedule(new finishChamp(), enddate - ClockBridge.wallMillis());
        }
    }

    protected void setEndOfChamp() {
        final Calendar finishtime = ClockBridge.calendar();
        finishtime.setTimeInMillis(enddate);
        finishtime.set(Calendar.MINUTE, 0);
        finishtime.set(Calendar.SECOND, 0);
        finishtime.add(Calendar.DAY_OF_MONTH, 6);
        finishtime.set(Calendar.DAY_OF_WEEK, 3);
        finishtime.set(Calendar.HOUR_OF_DAY, 19);
        enddate = finishtime.getTimeInMillis();
    }

    private void restoreData() {
        enddate = GlobalVariablesManager.getInstance().getLong("fishChampionshipEnd", 0);

        try {
            for (FishingChampionshipEntry entry : championshipRepository.findAll()) {
                final Fisher fisher =
                        new Fisher(entry.objectId(), entry.characterName(), entry.fishLength(), entry.rewardType());
                if (entry.rewardType() == 0) {
                    tmpPlayers.add(fisher);
                } else {
                    winPlayers.add(fisher);
                }
            }
        } catch (DataAccessException e) {
            log.warn("can't restore fishing championship info: {}", e.getMessage(), e);
        }
    }

    public synchronized void newFish(Player pl, int lureId) {
        if (!GeneralConfig.ALT_FISH_CHAMPIONSHIP_ENABLED) {
            return;
        }

        final RandomGenerator random = ClockBridge.random();
        double len = random.nextInt(60, 90) + (random.nextInt(1001) / 1000.);
        if ((lureId >= 8484) && (lureId <= 8486)) {
            len += random.nextInt(3001) / 1000.;
        }

        pl.sendPacket(new SystemMessage(SystemMessageId.YOU_CAUGHT_A_FISH_S1_IN_LENGTH).addString(String.valueOf(len)));

        if (tmpPlayers.size() < 5) {
            for (Fisher fisher : tmpPlayers) {
                if (fisher.getObjectId() == pl.getObjectId()) {
                    if (fisher.getLength() < len) {
                        fisher.setLength(len);
                        pl.sendPacket(
                                SystemMessageId
                                        .BECAUSE_OF_THE_SIZE_OF_FISH_CAUGHT_YOU_WILL_BE_REGISTERED_IN_THE_RANKING);
                        recalculateMinLength();
                    }
                    return;
                }
            }

            tmpPlayers.add(new Fisher(pl.getObjectId(), pl.getName(), len, 0));
            pl.sendPacket(SystemMessageId.BECAUSE_OF_THE_SIZE_OF_FISH_CAUGHT_YOU_WILL_BE_REGISTERED_IN_THE_RANKING);
            recalculateMinLength();
        } else if (minFishLength < len) {
            for (Fisher fisher : tmpPlayers) {
                if (fisher.getObjectId() == pl.getObjectId()) {
                    if (fisher.getLength() < len) {
                        fisher.setLength(len);
                        pl.sendPacket(
                                SystemMessageId
                                        .BECAUSE_OF_THE_SIZE_OF_FISH_CAUGHT_YOU_WILL_BE_REGISTERED_IN_THE_RANKING);
                        recalculateMinLength();
                    }
                    return;
                }
            }

            Fisher minFisher = null;
            double minLen = 99999.;
            for (Fisher fisher : tmpPlayers) {
                if (fisher.getLength() < minLen) {
                    minFisher = fisher;
                    minLen = minFisher.getLength();
                }
            }

            tmpPlayers.remove(minFisher);
            tmpPlayers.add(new Fisher(pl.getObjectId(), pl.getName(), len, 0));
            pl.sendPacket(SystemMessageId.BECAUSE_OF_THE_SIZE_OF_FISH_CAUGHT_YOU_WILL_BE_REGISTERED_IN_THE_RANKING);
            recalculateMinLength();
        }
    }

    private void recalculateMinLength() {
        double minLen = 99999.;
        for (Fisher fisher : tmpPlayers) {
            if (fisher.getLength() < minLen) {
                minLen = fisher.getLength();
            }
        }

        minFishLength = minLen;
    }

    public long getTimeRemaining() {
        return (enddate - ClockBridge.wallMillis()) / 60000;
    }

    public String getWinnerName(int par) {
        return winPlayersName.size() >= par ? winPlayersName.get(par - 1) : "None";
    }

    public String getCurrentName(int par) {
        return playersName.size() >= par ? playersName.get(par - 1) : "None";
    }

    public String getFishLength(int par) {
        return winFishLength.size() >= par ? winFishLength.get(par - 1) : "0";
    }

    public String getCurrentFishLength(int par) {
        return fishLength.size() >= par ? fishLength.get(par - 1) : "0";
    }

    public boolean isWinner(int objectId) {
        for (Fisher fisher : winPlayers) {
            if (fisher.getObjectId() == objectId) {
                return true;
            }
        }

        return false;
    }

    public void getReward(Player pl) {
        for (Fisher fisher : winPlayers) {
            if ((fisher.getObjectId() == pl.getObjectId()) && (fisher.getRewardType() != 2)) {
                int rewardCnt = 0;
                for (int x = 0; x < winPlayers.size(); x++) {
                    if (winPlayers.get(x).getObjectId() == pl.getObjectId()) {
                        switch (x) {
                            case 0: {
                                rewardCnt = GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_1;
                                break;
                            }
                            case 1: {
                                rewardCnt = GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_2;
                                break;
                            }
                            case 2: {
                                rewardCnt = GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_3;
                                break;
                            }
                            case 3: {
                                rewardCnt = GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_4;
                                break;
                            }
                            case 4: {
                                rewardCnt = GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_5;
                                break;
                            }
                        }
                    }
                }

                fisher.setRewardType(2);
                if (rewardCnt > 0) {
                    pl.addItem(
                            ItemProcessType.REWARD,
                            GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_ITEM,
                            rewardCnt,
                            null,
                            true);

                    final NpcHtmlMessage html = new NpcHtmlMessage();
                    html.setFile(pl, "data/html/fisherman/championship/fish_event_reward001.htm");
                    pl.sendPacket(html);
                }
            }
        }
    }

    public void showMidResult(Player pl) {
        final NpcHtmlMessage html = new NpcHtmlMessage();

        if (needRefresh) {
            html.setFile(pl, "data/html/fisherman/championship/fish_event003.htm");
            pl.sendPacket(html);

            refreshResult();
            ThreadPool.schedule(new needRefresh(), 60000);
            return;
        }

        html.setFile(pl, "data/html/fisherman/championship/fish_event002.htm");

        String str = "";
        for (int x = 1; x <= 5; x++) {
            str += "<tr><td width=70 align=center>" + x + "</td>";
            str += "<td width=110 align=center>" + getCurrentName(x) + "</td>";
            str += "<td width=80 align=center>" + getCurrentFishLength(x) + "</td></tr>";
        }

        html.replace("%TABLE%", str);
        html.replace(
                "%prizeItem%",
                ItemData.getInstance()
                        .getTemplate(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_ITEM)
                        .getName());
        html.replace("%prizeFirst%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_1));
        html.replace("%prizeTwo%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_2));
        html.replace("%prizeThree%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_3));
        html.replace("%prizeFour%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_4));
        html.replace("%prizeFive%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_5));
        pl.sendPacket(html);
    }

    public void showChampScreen(Player pl, Folk npc) {
        final NpcHtmlMessage html = new NpcHtmlMessage();
        html.setFile(pl, "data/html/fisherman/championship/fish_event001.htm");

        String str = "";
        for (int x = 1; x <= 5; x++) {
            str += "<tr><td width=70 align=center>" + x + "</td>";
            str += "<td width=110 align=center>" + getWinnerName(x) + "</td>";
            str += "<td width=80 align=center>" + getFishLength(x) + "</td></tr>";
        }

        html.replace("%TABLE%", str);
        html.replace(
                "%prizeItem%",
                ItemData.getInstance()
                        .getTemplate(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_ITEM)
                        .getName());
        html.replace("%prizeFirst%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_1));
        html.replace("%prizeTwo%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_2));
        html.replace("%prizeThree%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_3));
        html.replace("%prizeFour%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_4));
        html.replace("%prizeFive%", String.valueOf(GeneralConfig.ALT_FISH_CHAMPIONSHIP_REWARD_5));
        html.replace("%refresh%", String.valueOf(getTimeRemaining()));
        html.replace("%objectId%", String.valueOf(npc.getObjectId()));
        pl.sendPacket(html);
    }

    public void shutdown() {
        GlobalVariablesManager.getInstance().set("fishChampionshipEnd", enddate);

        final List<FishingChampionshipEntry> entries = new ArrayList<>();
        for (Fisher fisher : winPlayers) {
            entries.add(fisher.toEntry());
        }

        for (Fisher fisher : tmpPlayers) {
            entries.add(fisher.toEntry().toBuilder().rewardType(0).build());
        }

        try {
            championshipRepository.replaceAll(entries);
        } catch (DataAccessException e) {
            log.warn("can't update infos: {}", e.getMessage(), e);
        }
    }

    private synchronized void refreshResult() {
        needRefresh = false;

        playersName.clear();
        fishLength.clear();

        Fisher fisher1;
        Fisher fisher2;

        for (int x = 0; x <= (tmpPlayers.size() - 1); x++) {
            for (int y = 0; y <= (tmpPlayers.size() - 2); y++) {
                fisher1 = tmpPlayers.get(y);
                fisher2 = tmpPlayers.get(y + 1);
                if (fisher1.getLength() < fisher2.getLength()) {
                    tmpPlayers.set(y, fisher2);
                    tmpPlayers.set(y + 1, fisher1);
                }
            }
        }

        for (int x = 0; x <= (tmpPlayers.size() - 1); x++) {
            playersName.add(tmpPlayers.get(x).getName());
            fishLength.add(String.valueOf(tmpPlayers.get(x).getLength()));
        }
    }

    protected void refreshWinResult() {
        winPlayersName.clear();
        winFishLength.clear();

        Fisher fisher1;
        Fisher fisher2;

        for (int x = 0; x <= (winPlayers.size() - 1); x++) {
            for (int y = 0; y <= (winPlayers.size() - 2); y++) {
                fisher1 = winPlayers.get(y);
                fisher2 = winPlayers.get(y + 1);
                if (fisher1.getLength() < fisher2.getLength()) {
                    winPlayers.set(y, fisher2);
                    winPlayers.set(y + 1, fisher1);
                }
            }
        }

        for (int x = 0; x <= (winPlayers.size() - 1); x++) {
            winPlayersName.add(winPlayers.get(x).getName());
            winFishLength.add(String.valueOf(winPlayers.get(x).getLength()));
        }
    }

    private class finishChamp implements Runnable {
        protected finishChamp() {
            // Do nothing.
        }

        @Override
        public void run() {
            winPlayers.clear();
            for (Fisher fisher : tmpPlayers) {
                fisher.setRewardType(1);
                winPlayers.add(fisher);
            }

            tmpPlayers.clear();

            refreshWinResult();
            setEndOfChamp();
            shutdown();

            log.info("FishingChampionshipManager : new event period start.");
            ThreadPool.schedule(new finishChamp(), enddate - ClockBridge.wallMillis());
        }
    }

    private class needRefresh implements Runnable {
        protected needRefresh() {
            // Do nothing.
        }

        @Override
        public void run() {
            needRefresh = true;
        }
    }

    private class Fisher {
        @Getter
        private final int objectId;

        @Getter
        @Setter
        private double length;

        @Getter
        private final String name;

        @Getter
        @Setter
        private int rewardType;

        public Fisher(int objectId, String name, double length, int rewardType) {
            this.objectId = objectId;
            this.name = name;
            this.length = length;
            this.rewardType = rewardType;
        }

        FishingChampionshipEntry toEntry() {
            return FishingChampionshipEntry.builder()
                    .objectId(objectId)
                    .fishLength(length)
                    .rewardType(rewardType)
                    .build();
        }
    }
}
