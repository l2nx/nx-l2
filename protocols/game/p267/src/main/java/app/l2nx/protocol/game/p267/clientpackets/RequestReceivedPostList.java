/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import io.netty.buffer.ByteBuf;

public record RequestReceivedPostList() {
    public static final RequestReceivedPostList INSTANCE = new RequestReceivedPostList();

    public static RequestReceivedPostList decode(ByteBuf body) {
        return INSTANCE;
    }

    public void encode(ByteBuf out) {
        GameClientExOpcode.REQUEST_RECEIVED_POST_LIST.write(out);
    }
}
