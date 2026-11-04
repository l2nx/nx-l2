/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import io.netty.buffer.ByteBuf;

public record Appearing() {
    public static final Appearing INSTANCE = new Appearing();

    public static Appearing decode(ByteBuf body) {
        return INSTANCE;
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.APPEARING.opcode());
    }
}
