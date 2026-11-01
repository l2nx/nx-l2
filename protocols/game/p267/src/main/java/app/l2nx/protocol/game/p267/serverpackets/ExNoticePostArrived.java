/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record ExNoticePostArrived(boolean animate) implements GameServerPacket {
    public static ExNoticePostArrived read(ByteBuf body) {
        return Bodies.parse("ExNoticePostArrived", body, in -> new ExNoticePostArrived(in.readIntLE() != 0));
    }

    @Override
    public void write(ByteBuf out) {
        GameServerExOpcode.EX_NOTICE_POST_ARRIVED.write(out);
        out.writeIntLE(animate ? 1 : 0);
    }
}
