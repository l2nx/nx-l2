/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @param mailIds the mail numbers the server showed in this session, as sent: an empty list is the handler's to
 *     answer, not a decode error
 */
public record RequestDeleteSentPost(List<Integer> mailIds) {
    public RequestDeleteSentPost {
        mailIds = List.copyOf(Objects.requireNonNull(mailIds, "mailIds"));
    }

    public static RequestDeleteSentPost decode(ByteBuf body) {
        return Bodies.parse("RequestDeleteSentPost", body, in -> {
            final int count = Bodies.count(in.readIntLE(), in, Integer.BYTES);
            final List<Integer> mailIds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                mailIds.add(in.readIntLE());
            }
            return new RequestDeleteSentPost(mailIds);
        });
    }

    public void encode(ByteBuf out) {
        GameClientExOpcode.REQUEST_DELETE_SENT_POST.write(out);
        out.writeIntLE(mailIds.size());
        for (int mailId : mailIds) {
            out.writeIntLE(mailId);
        }
    }
}
