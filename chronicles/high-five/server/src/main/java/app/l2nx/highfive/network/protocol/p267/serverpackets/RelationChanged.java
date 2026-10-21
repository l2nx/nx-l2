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

import app.l2nx.highfive.entity.actor.Playable;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Luca Baldi
 */
public class RelationChanged extends ServerPacket {
    public static final int RELATION_PARTY1 = 0x00001; // party member
    public static final int RELATION_PARTY2 = 0x00002; // party member
    public static final int RELATION_PARTY3 = 0x00004; // party member
    public static final int RELATION_PARTY4 = 0x00008; // party member (for information, see Player.getRelation())
    public static final int RELATION_PARTYLEADER = 0x00010; // true if is party leader
    public static final int RELATION_HAS_PARTY = 0x00020; // true if is in party
    public static final int RELATION_CLAN_MEMBER = 0x00040; // true if is in clan
    public static final int RELATION_LEADER = 0x00080; // true if is clan leader
    public static final int RELATION_CLAN_MATE = 0x00100; // true if is in same clan
    public static final int RELATION_INSIEGE = 0x00200; // true if in siege
    public static final int RELATION_ATTACKER = 0x00400; // true when attacker
    public static final int RELATION_ALLY = 0x00800; // blue siege icon, cannot have if red
    public static final int RELATION_ENEMY = 0x01000; // true when red icon, doesn't matter with blue
    public static final int RELATION_MUTUAL_WAR = 0x04000; // double fist
    public static final int RELATION_1SIDED_WAR = 0x08000; // single fist
    public static final int RELATION_ALLY_MEMBER = 0x10000; // clan is in alliance
    public static final int RELATION_TERRITORY_WAR = 0x80000; // show Territory War icon

    protected static class Relation {
        int objId;
        int relation;
        boolean autoAttackable;
        int karma;
        int pvpFlag;
    }

    private Relation singled;
    private List<Relation> multi;

    public RelationChanged(Playable activeChar, int relation, boolean autoattackable) {
        singled = new Relation();
        singled.objId = activeChar.getObjectId();
        singled.relation = relation;
        singled.autoAttackable = autoattackable;
        singled.karma = activeChar.getKarma();
        singled.pvpFlag = activeChar.getPvpFlag();
    }

    public RelationChanged() {
        multi = new ArrayList<>();
    }

    public void addRelation(Playable activeChar, int relation, boolean autoattackable) {
        if (activeChar.isInvisible()) {
            return;
        }

        final Relation r = new Relation();
        r.objId = activeChar.getObjectId();
        r.relation = relation;
        r.autoAttackable = autoattackable;
        r.karma = activeChar.getKarma();
        r.pvpFlag = activeChar.getPvpFlag();
        multi.add(r);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.RELATION_CHANGED.writeId(this, buffer);
        if (multi == null) {
            buffer.writeInt(1);
            writeRelation(singled, buffer);
        } else {
            buffer.writeInt(multi.size());
            for (Relation relation : multi) {
                writeRelation(relation, buffer);
            }
        }
    }

    private void writeRelation(Relation relation, PacketWriter buffer) {
        buffer.writeInt(relation.objId);
        buffer.writeInt(relation.relation);
        buffer.writeInt(relation.autoAttackable);
        buffer.writeInt(relation.karma);
        buffer.writeInt(relation.pvpFlag);
    }
}
