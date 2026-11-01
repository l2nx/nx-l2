/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.network.codec.CodeTable;
import io.netty.buffer.ByteBuf;

/** Extended client opcodes: the {@link #PREFIX} byte, then the opcode as a little-endian short. */
public enum GameClientExOpcode {
    REQUEST_POST_ITEM_LIST(0x65),
    REQUEST_SEND_POST(0x66),
    REQUEST_RECEIVED_POST_LIST(0x67),
    REQUEST_DELETE_RECEIVED_POST(0x68),
    REQUEST_RECEIVED_POST(0x69),
    REQUEST_POST_ATTACHMENT(0x6A),
    REQUEST_REJECT_POST_ATTACHMENT(0x6B),
    REQUEST_SENT_POST_LIST(0x6C),
    REQUEST_DELETE_SENT_POST(0x6D),
    REQUEST_SENT_POST(0x6E),
    REQUEST_CANCEL_POST_ATTACHMENT(0x6F);

    public static final int PREFIX = 0xD0;

    private static final CodeTable<GameClientExOpcode> BY_OPCODE =
            new CodeTable<>(values(), GameClientExOpcode::opcode);

    private final int opcode;

    GameClientExOpcode(int opcode) {
        this.opcode = opcode;
    }

    public int opcode() {
        return opcode;
    }

    /**
     * @return {@code null} when the opcode has no record
     */
    public static GameClientExOpcode of(int opcode) {
        return BY_OPCODE.of(opcode);
    }

    public void write(ByteBuf out) {
        out.writeByte(PREFIX);
        out.writeShortLE(opcode);
    }
}
