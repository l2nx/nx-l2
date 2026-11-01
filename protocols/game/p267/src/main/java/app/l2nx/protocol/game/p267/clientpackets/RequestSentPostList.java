/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import io.netty.buffer.ByteBuf;

public record RequestSentPostList() {
    public static final RequestSentPostList INSTANCE = new RequestSentPostList();

    public static RequestSentPostList decode(ByteBuf body) {
        return INSTANCE;
    }

    public void encode(ByteBuf out) {
        GameClientExOpcode.REQUEST_SENT_POST_LIST.write(out);
    }
}
