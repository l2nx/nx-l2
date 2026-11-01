/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ExReplyPostItemList(List<ItemBlock> items) implements GameServerPacket {
    public ExReplyPostItemList {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public static ExReplyPostItemList read(ByteBuf body) {
        return Bodies.parse("ExReplyPostItemList", body, in -> {
            final int count = Bodies.count(in.readIntLE(), in, ItemBlock.BYTES);
            final List<ItemBlock> items = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                items.add(ItemBlock.read(in));
            }
            return new ExReplyPostItemList(items);
        });
    }

    @Override
    public void write(ByteBuf out) {
        GameServerExOpcode.EX_REPLY_POST_ITEM_LIST.write(out);
        out.writeIntLE(items.size());
        for (ItemBlock item : items) {
            item.write(out);
        }
    }
}
