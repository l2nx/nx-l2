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
package app.l2nx.highfive.mechanics.html.styles;

import app.l2nx.highfive.mechanics.html.IHtmlStyle;

/**
 * @author UnAfraid
 */
public class ButtonsStyle implements IHtmlStyle {
    private static final String DEFAULT_PAGE_LINK_FORMAT =
            "<td><button action=\"%s\" value=\"%s\" width=\"%d\" height=\"%d\" back=\"%s\" fore=\"%s\"></td>";
    private static final String DEFAULT_PAGE_TEXT_FORMAT = "<td>%s</td>";
    private static final String DEFAULT_PAGER_SEPARATOR = "<td align=center> | </td>";

    public static final ButtonsStyle INSTANCE =
            new ButtonsStyle(40, 15, "L2UI_CT1.Button_DF_Down", "L2UI_CT1.Button_DF");

    private final int width;
    private final int height;
    private final String back;
    private final String fore;

    public ButtonsStyle(int width, int height, String back, String fore) {
        this.width = width;
        this.height = height;
        this.back = back;
        this.fore = fore;
    }

    @Override
    public String applyBypass(String bypass, String name, boolean isEnabled) {
        if (isEnabled) {
            return String.format(DEFAULT_PAGE_TEXT_FORMAT, name);
        }

        return String.format(DEFAULT_PAGE_LINK_FORMAT, bypass, name, width, height, back, fore);
    }

    @Override
    public String applySeparator() {
        return DEFAULT_PAGER_SEPARATOR;
    }
}
