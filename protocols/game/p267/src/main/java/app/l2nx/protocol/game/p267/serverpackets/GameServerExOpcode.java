/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.CodeTable;
import io.netty.buffer.ByteBuf;

/** Extended server opcodes that have a record. */
public enum GameServerExOpcode {
    EX_NOTICE_POST_ARRIVED(0xA9),
    EX_SHOW_RECEIVED_POST_LIST(0xAA),
    EX_REPLY_RECEIVED_POST(0xAB),
    EX_SHOW_SENT_POST_LIST(0xAC),
    EX_REPLY_SENT_POST(0xAD),
    EX_REPLY_POST_ITEM_LIST(0xB2),
    EX_CHANGE_POST_STATE(0xB3),
    EX_NOTICE_POST_SENT(0xB4);

    public static final int PREFIX = 0xFE;

    private static final CodeTable<GameServerExOpcode> BY_OPCODE =
            new CodeTable<>(values(), GameServerExOpcode::opcode);

    private final int opcode;

    GameServerExOpcode(int opcode) {
        this.opcode = opcode;
    }

    public int opcode() {
        return opcode;
    }

    /**
     * @return {@code null} when the opcode has no record
     */
    public static GameServerExOpcode of(int opcode) {
        return BY_OPCODE.of(opcode);
    }

    public void write(ByteBuf out) {
        out.writeByte(PREFIX);
        out.writeShortLE(opcode);
    }
}
