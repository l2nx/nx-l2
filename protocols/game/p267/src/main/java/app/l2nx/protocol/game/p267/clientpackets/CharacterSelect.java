/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

/** The slot of the chosen character; the client follows it with 14 bytes nobody reads, which must be present. */
public record CharacterSelect(int slot) {
    private static final int TRAILER = 14;

    public static CharacterSelect decode(ByteBuf body) {
        return Bodies.parse("CharacterSelect", body, in -> {
            final int slot = in.readIntLE();
            in.skipBytes(TRAILER);
            return new CharacterSelect(slot);
        });
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.CHARACTER_SELECT.opcode());
        out.writeIntLE(slot);
        out.writeZero(TRAILER);
    }
}
