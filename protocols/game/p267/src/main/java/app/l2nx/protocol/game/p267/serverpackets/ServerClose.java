/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import io.netty.buffer.ByteBuf;

public record ServerClose() implements GameServerPacket {
    public static final ServerClose INSTANCE = new ServerClose();

    public static ServerClose read(ByteBuf body) {
        return INSTANCE;
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.SERVER_CLOSE.opcode());
    }
}
