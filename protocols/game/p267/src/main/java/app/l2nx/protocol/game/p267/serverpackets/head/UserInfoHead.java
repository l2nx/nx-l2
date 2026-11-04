/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets.head;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

/** Read-only head of {@code UserInfo}; a golden test in the server module fails when it drifts from the legacy packet. */
@Builder(toBuilder = true)
public record UserInfoHead(int x, int y, int z, int vehicleId, int objectId, String name) {
    public static UserInfoHead read(ByteBuf body) {
        return Bodies.parse(
                "UserInfoHead",
                body,
                in -> UserInfoHead.builder()
                        .x(in.readIntLE())
                        .y(in.readIntLE())
                        .z(in.readIntLE())
                        .vehicleId(in.readIntLE())
                        .objectId(in.readIntLE())
                        .name(Wire.readString(in))
                        .build());
    }
}
