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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.actor.holders.player.Macro;
import app.l2nx.highfive.entity.actor.holders.player.MacroCmd;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

public class SendMacroList extends ServerPacket {
    private final int rev;
    private final int count;
    private final Macro macro;

    public SendMacroList(int rev, int count, Macro macro) {
        this.rev = rev;
        this.count = count;
        this.macro = macro;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.MACRO_LIST.writeId(this, buffer);
        buffer.writeInt(rev); // macro change revision (changes after each macro edition)
        buffer.writeByte(0); // unknown
        buffer.writeByte(count); // count of Macros
        buffer.writeByte(macro != null); // unknown
        if (macro != null) {
            buffer.writeInt(macro.getId()); // Macro ID
            buffer.writeString(macro.getName()); // Macro Name
            buffer.writeString(macro.getDescr()); // Desc
            buffer.writeString(macro.getAcronym()); // acronym
            buffer.writeByte(macro.getIcon()); // icon
            buffer.writeByte(macro.getCommands().size()); // count
            int i = 1;
            for (MacroCmd cmd : macro.getCommands()) {
                buffer.writeByte(i++); // command count
                buffer.writeByte(cmd.getType().ordinal()); // type 1 = skill, 3 = action, 4 = shortcut
                buffer.writeInt(cmd.getD1()); // skill id
                buffer.writeByte(cmd.getD2()); // shortcut id
                buffer.writeString(cmd.getCmd()); // command name
            }
        }
    }
}
