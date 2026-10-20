/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import io.netty.buffer.ByteBuf;

public final class PacketWriter {
    private final ByteBuf buffer;

    public PacketWriter(ByteBuf buffer) {
        this.buffer = buffer;
    }

    public ByteBuf buffer() {
        return buffer;
    }

    public void writeByte(byte value) {
        buffer.writeByte(value);
    }

    public void writeByte(int value) {
        buffer.writeByte(value);
    }

    public void writeByte(boolean value) {
        buffer.writeByte(value ? 1 : 0);
    }

    public void writeBytes(byte... value) {
        if (value != null) {
            buffer.writeBytes(value);
        }
    }

    public void writeShort(short value) {
        buffer.writeShortLE(value);
    }

    public void writeShort(int value) {
        buffer.writeShortLE(value);
    }

    public void writeShort(boolean value) {
        buffer.writeShortLE(value ? 1 : 0);
    }

    public void writeInt(int value) {
        buffer.writeIntLE(value);
    }

    public void writeInt(boolean value) {
        buffer.writeIntLE(value ? 1 : 0);
    }

    public void writeLong(long value) {
        buffer.writeLongLE(value);
    }

    public void writeFloat(float value) {
        buffer.writeFloatLE(value);
    }

    public void writeDouble(double value) {
        buffer.writeDoubleLE(value);
    }

    public void writeChar(char value) {
        buffer.writeShortLE(value);
    }

    public void writeString(CharSequence text) {
        Wire.writeString(buffer, text);
    }

    public void writeSizedString(CharSequence text) {
        Wire.writeSizedString(buffer, text);
    }
}
