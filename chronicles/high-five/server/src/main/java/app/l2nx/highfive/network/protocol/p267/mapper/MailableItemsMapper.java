/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.engine.mail.view.MailableItems;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.ExReplyPostItemList;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.ItemBlock;
import java.util.ArrayList;
import java.util.List;

/** Reads the viewer's live items on the loop that emitted the list; an item gone since is left out. */
public final class MailableItemsMapper implements OutboundMapper<MailableItems> {
    @Override
    public Class<MailableItems> eventType() {
        return MailableItems.class;
    }

    @Override
    public GameServerPacket map(MailableItems event, GameClient viewer) {
        final Player player = viewer.getPlayer();
        if (player == null) {
            return null;
        }
        final List<ItemBlock> items = new ArrayList<>(event.itemHandles().size());
        for (int handle : event.itemHandles()) {
            final Item item = player.getInventory().getItemByObjectId(handle);
            if (item != null) {
                items.add(P267MailWire.item(item));
            }
        }
        return new ExReplyPostItemList(items);
    }

    @Override
    public int sizeHint() {
        return 4_096;
    }
}
