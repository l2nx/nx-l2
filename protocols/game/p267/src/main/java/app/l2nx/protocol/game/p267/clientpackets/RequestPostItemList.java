/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import io.netty.buffer.ByteBuf;

public record RequestPostItemList() {
    public static final RequestPostItemList INSTANCE = new RequestPostItemList();

    public static RequestPostItemList decode(ByteBuf body) {
        return INSTANCE;
    }

    public void encode(ByteBuf out) {
        GameClientExOpcode.REQUEST_POST_ITEM_LIST.write(out);
    }
}
