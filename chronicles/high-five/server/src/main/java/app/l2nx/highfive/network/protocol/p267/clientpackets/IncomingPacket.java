/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import io.netty.buffer.ByteBuf;
import lombok.Getter;

/**
 * Decoded on the IO thread, run later on another; the frame is reachable only during {@link #decode}, so a queued
 * packet holds no buffer.
 */
public abstract class IncomingPacket<C> implements Runnable {
    @Getter
    private C client;

    private ByteBuf frame;

    public final boolean decode(C client, ByteBuf frame) {
        this.client = client;
        this.frame = frame;
        try {
            return read();
        } catch (IndexOutOfBoundsException e) {
            throw new PacketDecodeException(getClass().getSimpleName() + " read past the end of its frame", e);
        } finally {
            this.frame = null;
        }
    }

    /** Exceptions are decode errors; {@code false} drops the packet without an error. */
    protected abstract boolean read();

    protected byte readByte() {
        return frame().readByte();
    }

    protected int readUnsignedByte() {
        return frame().readUnsignedByte();
    }

    protected boolean readBoolean() {
        return frame().readByte() != 0;
    }

    protected byte[] readBytes(int length) {
        final ByteBuf buf = frame();
        if ((length < 0) || (length > buf.readableBytes())) {
            throw new PacketDecodeException(length + " bytes requested, " + buf.readableBytes() + " in the frame");
        }
        final byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }

    protected void readBytes(byte[] dst) {
        frame().readBytes(dst);
    }

    protected void readBytes(byte[] dst, int offset, int length) {
        frame().readBytes(dst, offset, length);
    }

    protected short readShort() {
        return frame().readShortLE();
    }

    protected int readInt() {
        return frame().readIntLE();
    }

    protected long readLong() {
        return frame().readLongLE();
    }

    protected float readFloat() {
        return frame().readFloatLE();
    }

    /** Lenient, as the imported packets expect; new decoders use the strict {@link Wire#readString}. */
    protected String readString() {
        return Wire.readStringToEnd(frame());
    }

    protected int remaining() {
        return frame().readableBytes();
    }

    private ByteBuf frame() {
        if (frame == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " read outside decode");
        }
        return frame;
    }
}
