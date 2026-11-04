/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

@Builder(toBuilder = true)
public record RequestMagicSkillUse(int skillId, boolean ctrlPressed, boolean shiftPressed) {
    public static RequestMagicSkillUse decode(ByteBuf body) {
        return Bodies.parse(
                "RequestMagicSkillUse",
                body,
                in -> RequestMagicSkillUse.builder()
                        .skillId(in.readIntLE())
                        .ctrlPressed(in.readIntLE() != 0)
                        .shiftPressed(in.readByte() != 0)
                        .build());
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.REQUEST_MAGIC_SKILL_USE.opcode());
        out.writeIntLE(skillId);
        out.writeIntLE(ctrlPressed ? 1 : 0);
        out.writeByte(shiftPressed ? 1 : 0);
    }
}
