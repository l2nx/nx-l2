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
package app.l2nx.highfive.mechanics.announce;

import app.l2nx.highfive.data.sql.AnnouncementsTable;
import app.l2nx.highfive.repository.world.model.AnnouncementRow;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author UnAfraid
 */
@Slf4j
public class Announcement implements IAnnouncement {

    @Getter
    protected int id;

    @Getter
    @Setter
    private AnnouncementType type;

    @Getter
    @Setter
    private String content;

    @Getter
    @Setter
    private String author;

    public Announcement(AnnouncementType type, String content, String author) {
        this.type = type;
        this.content = content;
        this.author = author;
    }

    public Announcement(AnnouncementRow row) {
        id = row.id();
        type = row.type();
        content = row.content();
        author = row.author();
    }

    protected AnnouncementRow toRow() {
        return AnnouncementRow.builder()
                .id(id)
                .type(type)
                .content(content)
                .author(author)
                .repeat(-1)
                .build();
    }

    /**
     * A plain announcement has no schedule, so it cannot be stored with an {@code AUTO_*} type.
     */
    protected boolean isStorable() {
        if ((type == AnnouncementType.AUTO_NORMAL) || (type == AnnouncementType.AUTO_CRITICAL)) {
            log.warn("Announcement {} has the type {} but no schedule; it is not stored.", id, type);
            return false;
        }

        return true;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean storeMe() {
        if (!isStorable()) {
            return false;
        }

        try {
            id = AnnouncementsTable.getInstance().insert(toRow());
        } catch (DataAccessException e) {
            log.warn("Couldn't store announcement: ", e);
            return false;
        }

        return true;
    }

    @Override
    public boolean updateMe() {
        if (!isStorable()) {
            return false;
        }

        try {
            AnnouncementsTable.getInstance().update(toRow());
        } catch (DataAccessException e) {
            log.warn("Couldn't store announcement: ", e);
            return false;
        }

        return true;
    }

    @Override
    public boolean deleteMe() {
        try {
            AnnouncementsTable.getInstance().delete(id);
        } catch (DataAccessException e) {
            log.warn("Couldn't remove announcement: ", e);
            return false;
        }

        return true;
    }
}
