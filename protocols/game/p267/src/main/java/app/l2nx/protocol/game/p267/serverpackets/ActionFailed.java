/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import io.netty.buffer.ByteBuf;

public record ActionFailed() implements GameServerPacket {
    public static final ActionFailed INSTANCE = new ActionFailed();

    public static ActionFailed read(ByteBuf body) {
        return INSTANCE;
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.ACTION_FAIL.opcode());
    }
}
