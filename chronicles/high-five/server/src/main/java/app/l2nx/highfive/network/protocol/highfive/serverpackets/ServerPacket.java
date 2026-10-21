/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.engine.core.session.SessionState;
import app.l2nx.engine.core.session.outbound.LegacyPacket;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.codec.OutgoingPacket;
import app.l2nx.network.codec.PacketWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Mobius
 */
@Slf4j
public abstract class ServerPacket extends OutgoingPacket<GameClient> implements LegacyPacket {
    private static final int[] PAPERDOLL_ORDER = {
        Inventory.PAPERDOLL_UNDER,
        Inventory.PAPERDOLL_REAR,
        Inventory.PAPERDOLL_LEAR,
        Inventory.PAPERDOLL_NECK,
        Inventory.PAPERDOLL_RFINGER,
        Inventory.PAPERDOLL_LFINGER,
        Inventory.PAPERDOLL_HEAD,
        Inventory.PAPERDOLL_RHAND,
        Inventory.PAPERDOLL_LHAND,
        Inventory.PAPERDOLL_GLOVES,
        Inventory.PAPERDOLL_CHEST,
        Inventory.PAPERDOLL_LEGS,
        Inventory.PAPERDOLL_FEET,
        Inventory.PAPERDOLL_CLOAK,
        Inventory.PAPERDOLL_RHAND,
        Inventory.PAPERDOLL_HAIR,
        Inventory.PAPERDOLL_HAIR2,
        Inventory.PAPERDOLL_RBRACELET,
        Inventory.PAPERDOLL_LBRACELET,
        Inventory.PAPERDOLL_DECO1,
        Inventory.PAPERDOLL_DECO2,
        Inventory.PAPERDOLL_DECO3,
        Inventory.PAPERDOLL_DECO4,
        Inventory.PAPERDOLL_DECO5,
        Inventory.PAPERDOLL_DECO6,
        Inventory.PAPERDOLL_BELT
    };

    protected int[] getPaperdollOrder() {
        return PAPERDOLL_ORDER;
    }

    private volatile boolean broadcast;
    private ByteBuf broadcastFrame;

    /**
     * A broadcast serializes for its first recipient and hands every recipient {@link Frames#share}; the broadcaster
     * releases the shared frame with {@link #releaseBroadcast()} after the fan-out.
     */
    @Override
    public ByteBuf serialize(GameClient client, ByteBufAllocator alloc) {
        if (!broadcast) {
            return super.serialize(client, alloc);
        }
        synchronized (this) {
            // A sender that read the flag just before the broadcaster released must not cache a frame nobody frees.
            if (!broadcast) {
                return super.serialize(client, alloc);
            }
            if (broadcastFrame == null) {
                broadcastFrame = super.serialize(client, alloc);
                if (broadcastFrame == null) {
                    return null;
                }
            }
            return Frames.share(broadcastFrame);
        }
    }

    public void sendInBroadcast() {
        broadcast = true;
    }

    public synchronized void releaseBroadcast() {
        broadcast = false;
        if (broadcastFrame != null) {
            broadcastFrame.release();
            broadcastFrame = null;
        }
    }

    @Override
    protected boolean write(GameClient client, PacketWriter writer) {
        if ((client == null) || client.isDetached() || (client.getConnectionState() == SessionState.DISCONNECTED)) {
            return false;
        }
        try {
            writeImpl(client, writer);
            return true;
        } catch (Exception e) {
            log.warn("Error writing packet {} to client {}: {}", this, client, e.getMessage(), e);
            return false;
        }
    }

    public void runImpl(Player player) {}

    protected abstract void writeImpl(GameClient client, PacketWriter buffer) throws Exception;
}
