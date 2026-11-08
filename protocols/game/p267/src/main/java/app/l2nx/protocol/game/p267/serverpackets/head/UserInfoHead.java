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
 * Read-only head of {@code UserInfo} up to the movement multiplier; a golden test in the server module fails when it
 * drifts from the legacy packet.
 *
 * @param baseRunSpeed as written: the run speed divided by {@code moveMultiplier}, rounded
 */
@Builder(toBuilder = true)
public record UserInfoHead(
        int x, int y, int z, int vehicleId, int objectId, String name, int baseRunSpeed, double moveMultiplier) {
    private static final int AFTER_NAME_TO_RUN_SPEED = 4 * 4 + 8 + 8 + 14 * 4 + 3 * 26 * 4 + 2 * 4 + 12 * 4;
    private static final int RUN_SPEED_TO_MULTIPLIER = 7 * 4;

    public static UserInfoHead read(ByteBuf body) {
        return Bodies.parse("UserInfoHead", body, in -> {
            final UserInfoHead.UserInfoHeadBuilder head = UserInfoHead.builder()
                    .x(in.readIntLE())
                    .y(in.readIntLE())
                    .z(in.readIntLE())
                    .vehicleId(in.readIntLE())
                    .objectId(in.readIntLE())
                    .name(Wire.readString(in));
            in.skipBytes(AFTER_NAME_TO_RUN_SPEED);
            head.baseRunSpeed(in.readIntLE());
            in.skipBytes(RUN_SPEED_TO_MULTIPLIER);
            return head.moveMultiplier(in.readDoubleLE()).build();
        });
    }

    /** Game units per second, as the client moves the character. */
    public double runSpeed() {
        return baseRunSpeed * moveMultiplier;
    }
}
