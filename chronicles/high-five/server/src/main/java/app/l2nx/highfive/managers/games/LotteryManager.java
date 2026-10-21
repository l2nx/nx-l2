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
package app.l2nx.highfive.managers.games;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.item.model.LotteryTicket;
import app.l2nx.highfive.repository.minigame.LotteryRepository;
import app.l2nx.highfive.repository.minigame.model.Lottery;
import app.l2nx.highfive.repository.minigame.model.LotteryResult;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.MathUtil;
import java.util.Calendar;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LotteryManager {
    public static final long SECOND = 1000;
    public static final long MINUTE = 60000;

    private static final int TICKET_ITEM_ID = 4442;

    private static volatile LotteryManager instance;

    private final LotteryRepository lotteryRepository;
    private final ItemManager itemManager;

    @Getter
    protected int id;

    @Getter
    protected long prize;

    @Getter
    protected boolean sellableTickets;

    @Getter
    protected boolean started;

    @Getter
    protected long endDate;

    public LotteryManager(LotteryRepository lotteryRepository, ItemManager itemManager) {
        this.lotteryRepository = lotteryRepository;
        this.itemManager = itemManager;
        instance = this;
    }

    public void load() {
        id = 1;
        prize = GeneralConfig.ALT_LOTTERY_PRIZE;
        sellableTickets = false;
        started = false;
        endDate = ClockBridge.wallMillis();
        if (GeneralConfig.ALLOW_LOTTERY) {
            (new startLottery()).run();
        }
    }

    public void increasePrize(long count) {
        prize += count;
        try {
            lotteryRepository.updatePrize(id, prize, prize);
        } catch (DataAccessException e) {
            log.warn("Lottery: Could not increase current lottery prize: {}", e.getMessage(), e);
        }
    }

    private class startLottery implements Runnable {
        protected startLottery() {
            // Do nothing.
        }

        @Override
        public void run() {
            try {
                final Optional<Lottery> last = lotteryRepository.findLast();
                if (last.isPresent()) {
                    final Lottery row = last.get();
                    if (row.finished()) {
                        prize = row.newPrize();
                    } else {
                        id = row.id();
                        prize = row.prize();
                        LotteryManager.this.endDate = epochMillisOrZero(row.endDate());
                        if (LotteryManager.this.endDate <= (ClockBridge.wallMillis() + (2 * MINUTE))) {
                            (new finishLottery()).run();
                            return;
                        }

                        if (LotteryManager.this.endDate > ClockBridge.wallMillis()) {
                            started = true;
                            ThreadPool.schedule(
                                    new finishLottery(), LotteryManager.this.endDate - ClockBridge.wallMillis());
                            if (LotteryManager.this.endDate > (ClockBridge.wallMillis() + (12 * MINUTE))) {
                                sellableTickets = true;
                                ThreadPool.schedule(
                                        new stopSellingTickets(),
                                        LotteryManager.this.endDate - ClockBridge.wallMillis() - (10 * MINUTE));
                            }
                            return;
                        }
                    }
                }
            } catch (DataAccessException e) {
                log.warn("Lottery: Could not restore lottery data: {}", e.getMessage(), e);
            }

            final Calendar finishtime = ClockBridge.calendar();
            finishtime.setTimeInMillis(LotteryManager.this.endDate);
            finishtime.set(Calendar.MINUTE, 0);
            finishtime.set(Calendar.SECOND, 0);
            if (finishtime.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                finishtime.set(Calendar.HOUR_OF_DAY, 19);
                LotteryManager.this.endDate = finishtime.getTimeInMillis();
                LotteryManager.this.endDate += 604800000;
            } else {
                finishtime.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                finishtime.set(Calendar.HOUR_OF_DAY, 19);
                LotteryManager.this.endDate = finishtime.getTimeInMillis();
            }

            // The database numbers the round; the number is announced and stored on its tickets.
            try {
                id = lotteryRepository.create(instantOrNull(LotteryManager.this.endDate), prize, prize);
            } catch (DataAccessException e) {
                log.warn("Lottery: Could not store new lottery data: {}", e.getMessage(), e);
            }

            sellableTickets = true;
            started = true;
            World.broadcastToAllOnlinePlayers("Lottery tickets are now available for Lucky Lottery #" + id + ".");

            final long endDate = LotteryManager.this.endDate - ClockBridge.wallMillis();
            ThreadPool.schedule(new stopSellingTickets(), Math.max(endDate - (10 * MINUTE), 0));
            ThreadPool.schedule(new finishLottery(), Math.max(endDate, 0));
        }
    }

    private class stopSellingTickets implements Runnable {
        protected stopSellingTickets() {
            // Do nothing.
        }

        @Override
        public void run() {
            sellableTickets = false;
            World.broadcastToAllOnlinePlayers(
                    new SystemMessage(SystemMessageId.LOTTERY_TICKET_SALES_HAVE_BEEN_TEMPORARILY_SUSPENDED));
        }
    }

    private class finishLottery implements Runnable {
        protected finishLottery() {
            // Do nothing.
        }

        @Override
        public void run() {
            final int[] luckynums = new int[5];
            int luckynum = 0;
            for (int i = 0; i < 5; i++) {
                boolean found = true;

                while (found) {
                    luckynum = ClockBridge.random().nextInt(20) + 1;
                    found = false;
                    for (int j = 0; j < i; j++) {
                        if (luckynums[j] == luckynum) {
                            found = true;
                        }
                    }
                }

                luckynums[i] = luckynum;
            }

            int enchant = 0;
            int type2 = 0;
            for (int i = 0; i < 5; i++) {
                if (luckynums[i] < 17) {
                    enchant += MathUtil.pow(2, luckynums[i] - 1);
                } else {
                    type2 += MathUtil.pow(2, luckynums[i] - 17);
                }
            }

            int count1 = 0;
            int count2 = 0;
            int count3 = 0;
            int count4 = 0;

            try {
                for (LotteryTicket ticket : itemManager.findLotteryTickets(TICKET_ITEM_ID, id)) {
                    int curenchant = ticket.enchantLevel() & enchant;
                    int curtype2 = ticket.customType2() & type2;
                    if ((curenchant == 0) && (curtype2 == 0)) {
                        continue;
                    }

                    int count = 0;
                    for (int i = 1; i <= 16; i++) {
                        final int val = curenchant / 2;
                        if (val != Math.round((double) curenchant / 2)) {
                            count++;
                        }

                        final int val2 = curtype2 / 2;
                        if (val2 != ((double) curtype2 / 2)) {
                            count++;
                        }

                        curenchant = val;
                        curtype2 = val2;
                    }

                    if (count == 5) {
                        count1++;
                    } else if (count == 4) {
                        count2++;
                    } else if (count == 3) {
                        count3++;
                    } else if (count > 0) {
                        count4++;
                    }
                }
            } catch (DataAccessException e) {
                log.warn("Lottery: Could restore lottery data: {}", e.getMessage(), e);
            }

            final long prize4 = count4 * GeneralConfig.ALT_LOTTERY_2_AND_1_NUMBER_PRIZE;
            long prize1 = 0;
            long prize2 = 0;
            long prize3 = 0;
            if (count1 > 0) {
                prize1 = (long) (((prize - prize4) * GeneralConfig.ALT_LOTTERY_5_NUMBER_RATE) / count1);
            }

            if (count2 > 0) {
                prize2 = (long) (((prize - prize4) * GeneralConfig.ALT_LOTTERY_4_NUMBER_RATE) / count2);
            }

            if (count3 > 0) {
                prize3 = (long) (((prize - prize4) * GeneralConfig.ALT_LOTTERY_3_NUMBER_RATE) / count3);
            }

            final long newprize = prize - (prize1 + prize2 + prize3 + prize4);
            SystemMessage sm;
            if (count1 > 0) {
                // There are winners.
                sm = new SystemMessage(
                        SystemMessageId
                                .THE_PRIZE_AMOUNT_FOR_THE_WINNER_OF_LOTTERY_S1_IS_S2_ADENA_WE_HAVE_S3_FIRST_PRIZE_WINNERS);
                sm.addInt(id);
                sm.addLong(prize);
                sm.addLong(count1);
            } else {
                // There are no winners.
                sm = new SystemMessage(
                        SystemMessageId
                                .THE_PRIZE_AMOUNT_FOR_LUCKY_LOTTERY_S1_IS_S2_ADENA_THERE_WAS_NO_FIRST_PRIZE_WINNER_IN_THIS_DRAWING_THEREFORE_THE_JACKPOT_WILL_BE_ADDED_TO_THE_NEXT_DRAWING);
                sm.addInt(id);
                sm.addLong(prize);
            }

            World.broadcastToAllOnlinePlayers(sm);

            try {
                lotteryRepository.finish(
                        id,
                        prize,
                        newprize,
                        LotteryResult.builder()
                                .number1(enchant)
                                .number2(type2)
                                .prize1(prize1)
                                .prize2(prize2)
                                .prize3(prize3)
                                .build());
            } catch (DataAccessException e) {
                log.warn("Lottery: Could not store finished lottery data: {}", e.getMessage(), e);
            }

            ThreadPool.schedule(new startLottery(), MINUTE);

            started = false;
        }
    }

    public int[] decodeNumbers(int enchantValue, int type2Value) {
        final int[] res = new int[5];
        int id = 0;
        int nr = 1;

        int enchant = enchantValue;
        while (enchant > 0) {
            final int val = enchant / 2;
            if (val != ((double) enchant / 2)) {
                res[id] = nr;
                id++;
            }

            enchant /= 2;
            nr++;
        }

        nr = 17;

        int type2 = type2Value;
        while (type2 > 0) {
            final int val = type2 / 2;
            if (val != ((double) type2 / 2)) {
                res[id] = nr;
                id++;
            }

            type2 /= 2;
            nr++;
        }

        return res;
    }

    public long[] checkTicket(Item item) {
        return checkTicket(item.getCustomType1(), item.getEnchantLevel(), item.getCustomType2());
    }

    public long[] checkTicket(int id, int enchant, int type2) {
        final long[] res = {0, 0};
        try {
            final Optional<LotteryResult> result = lotteryRepository.findResult(id);
            if (result.isPresent()) {
                final LotteryResult draw = result.get();
                int curenchant = draw.number1() & enchant;
                int curtype2 = draw.number2() & type2;
                if ((curenchant == 0) && (curtype2 == 0)) {
                    return res;
                }

                int count = 0;
                for (int i = 1; i <= 16; i++) {
                    final int val = curenchant / 2;
                    if (val != Math.round((double) curenchant / 2)) {
                        count++;
                    }

                    final int val2 = curtype2 / 2;
                    if (val2 != ((double) curtype2 / 2)) {
                        count++;
                    }

                    curenchant = val;
                    curtype2 = val2;
                }

                switch (count) {
                    case 0: {
                        break;
                    }
                    case 5: {
                        res[0] = 1;
                        res[1] = draw.prize1();
                        break;
                    }
                    case 4: {
                        res[0] = 2;
                        res[1] = draw.prize2();
                        break;
                    }
                    case 3: {
                        res[0] = 3;
                        res[1] = draw.prize3();
                        break;
                    }
                    default: {
                        res[0] = 4;
                        res[1] = GeneralConfig.ALT_LOTTERY_2_AND_1_NUMBER_PRIZE;
                    }
                }
            }
        } catch (DataAccessException e) {
            log.warn("Lottery: Could not check lottery ticket #{}: {}", id, e.getMessage(), e);
        }

        return res;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static LotteryManager getInstance() {
        final LotteryManager current = instance;
        if (current == null) {
            throw new IllegalStateException("LotteryManager is used before the Spring context created it.");
        }
        return current;
    }
}
