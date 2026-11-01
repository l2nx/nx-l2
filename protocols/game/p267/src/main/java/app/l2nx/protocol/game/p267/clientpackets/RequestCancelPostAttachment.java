/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record RequestCancelPostAttachment(int mailId) {
    public static RequestCancelPostAttachment decode(ByteBuf body) {
        return Bodies.parse("RequestCancelPostAttachment", body, in -> new RequestCancelPostAttachment(in.readIntLE()));
    }

    public void encode(ByteBuf out) {
        GameClientExOpcode.REQUEST_CANCEL_POST_ATTACHMENT.write(out);
        out.writeIntLE(mailId);
    }
}
