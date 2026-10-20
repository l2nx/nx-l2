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
package app.l2nx.highfive.mechanics.announce;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.managers.IdManager;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * @author UnAfraid, Mobius
 */
public class EventAnnouncement implements IAnnouncement {
    @Getter
    private final int id;

    private Date startDate = null;
    private Date endDate = null;

    @Getter
    @Setter
    private String content;

    public EventAnnouncement(Date startDate, Date endDate, String content) {
        id = IdManager.getInstance().getNextId();
        this.startDate = startDate;
        this.endDate = endDate;
        this.content = content;
    }

    @Override
    public AnnouncementType getType() {
        return AnnouncementType.EVENT;
    }

    @Override
    public void setType(AnnouncementType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValid() {
        final Date now = new Date(ClockBridge.wallMillis());
        return (startDate != null) && (endDate != null) && !now.before(startDate) && !now.after(endDate);
    }

    @Override
    public String getAuthor() {
        return "N/A";
    }

    @Override
    public void setAuthor(String author) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteMe() {
        IdManager.getInstance().releaseId(id);
        return true;
    }

    @Override
    public boolean storeMe() {
        return true;
    }

    @Override
    public boolean updateMe() {
        throw new UnsupportedOperationException();
    }
}
