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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

public class StatusUpdate extends ServerPacket {
    public static final int LEVEL = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.LEVEL;
    public static final int EXP = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.EXP;
    public static final int STR = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.STR;
    public static final int DEX = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.DEX;
    public static final int CON = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.CON;
    public static final int INT = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.INT;
    public static final int WIT = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.WIT;
    public static final int MEN = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.MEN;
    public static final int CUR_HP = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.CUR_HP;
    public static final int MAX_HP = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.MAX_HP;
    public static final int CUR_MP = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.CUR_MP;
    public static final int MAX_MP = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.MAX_MP;
    public static final int SP = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.SP;
    public static final int CUR_LOAD = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.CUR_LOAD;
    public static final int MAX_LOAD = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.MAX_LOAD;
    public static final int P_ATK = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.P_ATK;
    public static final int ATK_SPD = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.ATK_SPD;
    public static final int P_DEF = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.P_DEF;
    public static final int EVASION = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.EVASION;
    public static final int ACCURACY = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.ACCURACY;
    public static final int CRITICAL = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.CRITICAL;
    public static final int M_ATK = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.M_ATK;
    public static final int CAST_SPD = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.CAST_SPD;
    public static final int M_DEF = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.M_DEF;
    public static final int PVP_FLAG = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.PVP_FLAG;
    public static final int KARMA = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.KARMA;
    public static final int CUR_CP = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.CUR_CP;
    public static final int MAX_CP = app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.MAX_CP;

    private final int objectId;
    private final List<Attribute> attributes = new ArrayList<>();

    private static class Attribute {
        /**
         * id values 09 - current health 0a - max health 0b - current mana 0c - max mana
         */
        public int id;

        public int value;

        Attribute(int pId, int pValue) {
            id = pId;
            value = pValue;
        }
    }

    /**
     * If you have access to object itself use {@link StatusUpdate#StatusUpdate(WorldObject)}.
     * @param objectId
     */
    public StatusUpdate(int objectId) {
        this.objectId = objectId;
    }

    /**
     * Create {@link StatusUpdate} packet for given {@link WorldObject}.
     * @param object
     */
    public StatusUpdate(WorldObject object) {
        objectId = object.getObjectId();
    }

    public void addAttribute(int id, int level) {
        attributes.add(new Attribute(id, level));
    }

    public boolean hasAttributes() {
        return !attributes.isEmpty();
    }

    /**
     * Writes the layout itself, held equal to the wire record by a golden test: a status update is serialized once per
     * listener, and going through the record would copy the attribute list for each of them.
     */
    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.STATUS_UPDATE.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(attributes.size());
        for (Attribute temp : attributes) {
            buffer.writeInt(temp.id);
            buffer.writeInt(temp.value);
        }
    }
}
