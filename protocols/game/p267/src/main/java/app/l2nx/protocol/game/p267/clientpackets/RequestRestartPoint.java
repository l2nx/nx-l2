/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record RequestRestartPoint(int pointType) {
    public static final int TO_RESTART_POINT = 0;
    public static final int IN_PLACE = 5;

    public static RequestRestartPoint decode(ByteBuf body) {
        return Bodies.parse("RequestRestartPoint", body, in -> new RequestRestartPoint(in.readIntLE()));
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.REQUEST_RESTART_POINT.opcode());
        out.writeIntLE(pointType);
    }
}
