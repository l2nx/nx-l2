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
package app.l2nx.highfive.data.holders;

import java.time.DayOfWeek;
import lombok.Getter;

/**
 * Simple class for storing Reenter Data for Instances.
 * @author FallenAngel
 */
public class InstanceReenterTimeHolder {
    @Getter
    private final DayOfWeek day;

    @Getter
    private final int hour;

    @Getter
    private final int minute;

    @Getter
    private final long time;

    public InstanceReenterTimeHolder(long time) {
        this.time = time;
        day = null;
        hour = -1;
        minute = -1;
    }

    public InstanceReenterTimeHolder(DayOfWeek day, int hour, int minute) {
        time = -1;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
    }
}
