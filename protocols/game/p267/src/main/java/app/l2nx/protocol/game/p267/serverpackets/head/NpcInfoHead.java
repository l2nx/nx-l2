/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets.head;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

/**
 * Read-only head of {@code NpcInfo}; a golden test in the server module fails when it drifts from the legacy packet.
 * Summons and traps share the opcode and the head.
 *
 * @param attackable the receiver may attack it without forcing; true for monsters and anything hostile to the receiver
 */
@Builder(toBuilder = true)
public record NpcInfoHead(int objectId, int npcId, boolean attackable, int x, int y, int z, boolean dead) {
    private static final int NPC_ID_OFFSET = 1_000_000;

    /** Heading to weapons: 12 ints, 4 doubles and 3 ints, then three flags. */
    private static final int POSITION_TO_DEAD = 95;

    public static NpcInfoHead read(ByteBuf body) {
        return Bodies.parse("NpcInfoHead", body, in -> {
            final NpcInfoHeadBuilder head = NpcInfoHead.builder()
                    .objectId(in.readIntLE())
                    .npcId(in.readIntLE() - NPC_ID_OFFSET)
                    .attackable(in.readIntLE() != 0)
                    .x(in.readIntLE())
                    .y(in.readIntLE())
                    .z(in.readIntLE());
            in.skipBytes(POSITION_TO_DEAD);
            return head.dead(in.readByte() != 0).build();
        });
    }
}
