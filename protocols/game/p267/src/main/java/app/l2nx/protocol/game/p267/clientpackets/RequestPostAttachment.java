/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record RequestPostAttachment(int mailId) {
    public static RequestPostAttachment decode(ByteBuf body) {
        return Bodies.parse("RequestPostAttachment", body, in -> new RequestPostAttachment(in.readIntLE()));
    }

    public void encode(ByteBuf out) {
        GameClientExOpcode.REQUEST_POST_ATTACHMENT.write(out);
        out.writeIntLE(mailId);
    }
}
