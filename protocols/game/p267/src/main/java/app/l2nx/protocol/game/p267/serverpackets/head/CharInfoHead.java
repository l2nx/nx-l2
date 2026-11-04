/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets.head;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

/**
 * Read-only head of {@code CharInfo}; a golden test in the server module fails when it drifts from the legacy packet.
 * Inside a vehicle the position is relative to it.
 */
@Builder(toBuilder = true)
public record CharInfoHead(int x, int y, int z, int vehicleId, int objectId, String name, boolean dead) {
    /** Race to hair: 60 ints with the 2 x 21 paperdoll slots, 4 doubles and 3 more ints. */
    private static final int NAME_TO_TITLE = 284;

    /** Clan and ally ids and crests, then three flags. */
    private static final int TITLE_TO_DEAD = 19;

    public static CharInfoHead read(ByteBuf body) {
        return Bodies.parse("CharInfoHead", body, in -> {
            final CharInfoHeadBuilder head = CharInfoHead.builder()
                    .x(in.readIntLE())
                    .y(in.readIntLE())
                    .z(in.readIntLE())
                    .vehicleId(in.readIntLE())
                    .objectId(in.readIntLE())
                    .name(Wire.readString(in));
            in.skipBytes(NAME_TO_TITLE);
            Wire.readString(in);
            in.skipBytes(TITLE_TO_DEAD);
            return head.dead(in.readByte() != 0).build();
        });
    }
}
