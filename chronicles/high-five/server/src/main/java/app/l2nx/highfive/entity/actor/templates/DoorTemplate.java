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
package app.l2nx.highfive.entity.actor.templates;

import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;

/**
 * Doors template.
 * @author JIV
 */
public class DoorTemplate extends CreatureTemplate {
    @Getter
    private final int id;

    @Getter
    private final int[] nodeX;

    @Getter
    private final int[] nodeY;

    @Getter
    private final int nodeZ;

    @Getter
    private final int height;

    @Getter
    private final int x;

    @Getter
    private final int y;

    @Getter
    private final int z;

    @Getter
    private final int emmiter;

    @Getter
    private final int childDoorId;

    @Getter
    private final String name;

    @Getter
    private final String groupName;

    @Getter
    private final boolean showHp;

    @Getter
    private final boolean wall;

    // -1 close, 0 nothing, 1 open
    @Getter
    private final byte masterDoorClose;

    @Getter
    private final byte masterDoorOpen;

    @Getter
    private final boolean targetable;

    @Getter
    private final boolean openByDefault;

    @Getter
    private int openTime;

    @Getter
    private int randomTime;

    @Getter
    private final int closeTime;

    @Getter
    private final int level;

    @Getter
    private final int openType;

    @Getter
    private final boolean checkCollision;

    @Getter
    private final boolean attackable;

    @Getter
    private final int clanHallId;

    @Getter
    private final boolean stealth;

    public DoorTemplate(StatSet set) {
        super(set);
        id = set.getInt("id");
        name = set.getString("name");

        // position
        final String[] pos = set.getString("pos").split(",");
        height = set.getInt("height");
        nodeZ = set.getInt("nodeZ");
        nodeX = new int[4]; // 4 * x
        nodeY = new int[4]; // 4 * y
        for (int i = 0; i < 4; i++) {
            final String[] split = set.getString("node" + (i + 1)).split(",");
            nodeX[i] = Integer.parseInt(split[0]);
            nodeY[i] = Integer.parseInt(split[1]);
        }

        x = Integer.parseInt(pos[0]);
        y = Integer.parseInt(pos[1]);
        z = Math.min(Integer.parseInt(pos[2]), nodeZ);

        // optional
        emmiter = set.getInt("emitter_id", 0);
        showHp = set.getBoolean("hp_showable", true);
        wall = set.getBoolean("is_wall", false);
        groupName = set.getString("group", null);
        childDoorId = set.getInt("child_id_event", -1);

        // true if door is opening
        String masterevent = set.getString("master_close_event", "act_nothing");
        masterDoorClose = (byte) (masterevent.equals("act_open") ? 1 : masterevent.equals("act_close") ? -1 : 0);
        masterevent = set.getString("master_open_event", "act_nothing");
        masterDoorOpen = (byte) (masterevent.equals("act_open") ? 1 : masterevent.equals("act_close") ? -1 : 0);
        targetable = set.getBoolean("targetable", true);
        openByDefault = set.getString("default_status", "close").equals("open");
        closeTime = set.getInt("close_time", -1);
        level = set.getInt("level", 0);
        openType = set.getInt("open_method", 0);
        checkCollision = set.getBoolean("check_collision", true);
        if ((openType & Door.OPEN_BY_TIME) == Door.OPEN_BY_TIME) {
            openTime = set.getInt("open_time");
            randomTime = set.getInt("random_time", -1);
        }

        attackable = set.getBoolean("is_attackable", false);
        clanHallId = set.getInt("clanhall_id", 0);
        stealth = set.getBoolean("stealth", false);
    }
}
