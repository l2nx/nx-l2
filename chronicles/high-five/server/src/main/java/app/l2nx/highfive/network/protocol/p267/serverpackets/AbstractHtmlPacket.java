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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.enums.HtmlActionScope;
import app.l2nx.highfive.util.HtmlUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author HorridoJoho
 */
@Slf4j
public abstract class AbstractHtmlPacket extends ServerPacket {
    public static final char VAR_PARAM_START_CHAR = '$';

    @Getter
    private final int npcObjId;

    @Getter
    private String html = null;

    private boolean disabledValidation = false;

    protected AbstractHtmlPacket() {
        npcObjId = 0;
    }

    protected AbstractHtmlPacket(int npcObjId) {
        if (npcObjId < 0) {
            throw new IllegalArgumentException();
        }

        this.npcObjId = npcObjId;
    }

    protected AbstractHtmlPacket(String html) {
        npcObjId = 0;
        setHtml(html);
    }

    protected AbstractHtmlPacket(int npcObjId, String html) {
        if (npcObjId < 0) {
            throw new IllegalArgumentException();
        }

        this.npcObjId = npcObjId;
        setHtml(html);
    }

    public void disableValidation() {
        disabledValidation = true;
    }

    public void setHtml(String html) {
        if (html.length() > 17200) {
            log.warn(
                    "{}: Html is too long! this will crash the client!",
                    getClass().getSimpleName());
            this.html = html.substring(0, 17200);
        } else {
            if (!html.contains("<html") && !html.startsWith("..\\L2")) {
                this.html = "<html><body>" + html + "</body></html>";
            } else {
                this.html = html;
            }
        }
    }

    public boolean setFile(Player player, String path) {
        final String content = HtmCache.getInstance().getHtm(player, path);
        if (content == null) {
            setHtml("<html><body>My Text is missing:<br>" + path + "</body></html>");
            log.warn("{}: Missing html page {}", getClass().getSimpleName(), path);
            return false;
        }

        setHtml(content);
        return true;
    }

    /** A {@code null} value renders as empty text. */
    public void replace(String pattern, String value) {
        final String text = value == null ? "" : value;
        html = html.replaceAll(pattern, text.replaceAll("\\$", "\\\\\\$"));
    }

    public void replace(String pattern, CharSequence value) {
        replace(pattern, value == null ? null : value.toString());
    }

    public void replace(String pattern, boolean value) {
        replace(pattern, String.valueOf(value));
    }

    public void replace(String pattern, int value) {
        replace(pattern, String.valueOf(value));
    }

    public void replace(String pattern, long value) {
        replace(pattern, String.valueOf(value));
    }

    public void replace(String pattern, double value) {
        replace(pattern, String.valueOf(value));
    }

    @Override
    public void runImpl(Player player) {
        if (player != null) {
            player.clearHtmlActions(getScope());
        }

        if (disabledValidation) {
            return;
        }

        if (player != null) {
            HtmlUtil.buildHtmlActionCache(player, getScope(), npcObjId, html);
        }
    }

    public abstract HtmlActionScope getScope();
}
