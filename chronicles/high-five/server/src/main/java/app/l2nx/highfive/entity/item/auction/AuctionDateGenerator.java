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
package app.l2nx.highfive.entity.item.auction;

import app.l2nx.highfive.util.StatSet;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * @author Forsaiken
 */
public class AuctionDateGenerator {
    public static final String FIELD_INTERVAL = "interval";
    public static final String FIELD_DAY_OF_WEEK = "day_of_week";
    public static final String FIELD_HOUR_OF_DAY = "hour_of_day";
    public static final String FIELD_MINUTE_OF_HOUR = "minute_of_hour";

    private static final long MILLIS_IN_WEEK = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);

    private final Calendar calendar;

    private final int interval;
    private int day_of_week;
    private int hour_of_day;
    private int minute_of_hour;

    public AuctionDateGenerator(StatSet config) {
        calendar = Calendar.getInstance();
        interval = config.getInt(FIELD_INTERVAL, -1);

        // NC week start in Monday.
        final int fixedDayWeek = config.getInt(FIELD_DAY_OF_WEEK, -1) + 1;
        day_of_week = (fixedDayWeek > 7) ? 1 : fixedDayWeek;
        hour_of_day = config.getInt(FIELD_HOUR_OF_DAY, -1);
        minute_of_hour = config.getInt(FIELD_MINUTE_OF_HOUR, -1);
        checkDayOfWeek(-1);
        checkHourOfDay(-1);
        checkMinuteOfHour(0);
    }

    public synchronized long nextDate(long date) {
        calendar.setTimeInMillis(date);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, minute_of_hour);
        calendar.set(Calendar.HOUR_OF_DAY, hour_of_day);
        if (day_of_week > 0) {
            calendar.set(Calendar.DAY_OF_WEEK, day_of_week);
            return calcDestTime(calendar.getTimeInMillis(), date, MILLIS_IN_WEEK);
        }

        return calcDestTime(calendar.getTimeInMillis(), date, TimeUnit.MILLISECONDS.convert(interval, TimeUnit.DAYS));
    }

    private long calcDestTime(long timeValue, long date, long add) {
        long time = timeValue;
        if (time < date) {
            time += ((date - time) / add) * add;
            if (time < date) {
                time += add;
            }
        }

        return time;
    }

    private void checkDayOfWeek(int defaultValue) {
        if ((day_of_week < 1) || (day_of_week > 7)) {
            if ((defaultValue == -1) && (interval < 1)) {
                throw new IllegalArgumentException("Illegal params for '" + FIELD_DAY_OF_WEEK + "': "
                        + (day_of_week == -1 ? "not found" : day_of_week));
            }

            day_of_week = defaultValue;
        } else if (interval > 1) {
            throw new IllegalArgumentException("Illegal params for '" + FIELD_INTERVAL + "' and '" + FIELD_DAY_OF_WEEK
                    + "': you can use only one, not both");
        }
    }

    private void checkHourOfDay(int defaultValue) {
        if ((hour_of_day < 0) || (hour_of_day > 23)) {
            if (defaultValue == -1) {
                throw new IllegalArgumentException("Illegal params for '" + FIELD_HOUR_OF_DAY + "': "
                        + (hour_of_day == -1 ? "not found" : hour_of_day));
            }

            hour_of_day = defaultValue;
        }
    }

    private void checkMinuteOfHour(int defaultValue) {
        if ((minute_of_hour < 0) || (minute_of_hour > 59)) {
            if (defaultValue == -1) {
                throw new IllegalArgumentException("Illegal params for '" + FIELD_MINUTE_OF_HOUR + "': "
                        + (minute_of_hour == -1 ? "not found" : minute_of_hour));
            }

            minute_of_hour = defaultValue;
        }
    }
}
