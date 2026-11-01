/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record RequestReceivedPost(int mailId) {
    public static RequestReceivedPost decode(ByteBuf body) {
        return Bodies.parse("RequestReceivedPost", body, in -> new RequestReceivedPost(in.readIntLE()));
    }

    public void encode(ByteBuf out) {
        GameClientExOpcode.REQUEST_RECEIVED_POST.write(out);
        out.writeIntLE(mailId);
    }
}
