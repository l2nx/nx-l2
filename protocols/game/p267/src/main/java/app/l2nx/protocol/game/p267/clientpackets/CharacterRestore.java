/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record CharacterRestore(int slot) {
    public static CharacterRestore decode(ByteBuf body) {
        return Bodies.parse("CharacterRestore", body, in -> new CharacterRestore(in.readIntLE()));
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.CHARACTER_RESTORE.opcode());
        out.writeIntLE(slot);
    }
}
