/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record RequestSentPost(int mailId) {
    public static RequestSentPost decode(ByteBuf body) {
        return Bodies.parse("RequestSentPost", body, in -> new RequestSentPost(in.readIntLE()));
    }

    public void encode(ByteBuf out) {
        GameClientExOpcode.REQUEST_SENT_POST.write(out);
        out.writeIntLE(mailId);
    }
}
