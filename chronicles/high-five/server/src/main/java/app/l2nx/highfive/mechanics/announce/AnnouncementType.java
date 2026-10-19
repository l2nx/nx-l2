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
package app.l2nx.highfive.mechanics.announce;

import lombok.extern.slf4j.Slf4j;

/**
 * @author UnAfraid
 */
@Slf4j
public enum AnnouncementType {
    NORMAL,
    CRITICAL,
    EVENT,
    AUTO_NORMAL,
    AUTO_CRITICAL;

    public static AnnouncementType findById(int id) {
        for (AnnouncementType type : values()) {
            if (type.ordinal() == id) {
                return type;
            }
        }

        log.warn("Unexistent id specified: {}!", id, new IllegalStateException());
        return NORMAL;
    }

    public static AnnouncementType findByName(String name) {
        for (AnnouncementType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }

        log.warn("Unexistent name specified: {}!", name, new IllegalStateException());
        return NORMAL;
    }
}
