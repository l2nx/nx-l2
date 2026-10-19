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
package app.l2nx.network.packet;

import app.l2nx.network.Client;
import app.l2nx.network.Connection;
import app.l2nx.network.buffer.ReadBuffer;
import java.nio.charset.StandardCharsets;
import lombok.Getter;

/**
 * Base class for all packets received from a client.<br>
 * Subclasses implement {@link #read()} to parse fields from the buffer and then override {@link #run()} (inherited from {@link Runnable}) to act on those fields.<br>
 * <br>
 * The lifecycle managed by the network layer is:
 * <ol>
 * <li>{@link PacketHandler} creates the concrete subclass instance.</li>
 * <li>{@link #init(Client, ReadBuffer)} binds the client and buffer.</li>
 * <li>{@link #read()} is called on the I/O thread to parse the payload.</li>
 * <li>If {@link #read()} returns {@code true}, the packet is submitted to a {@link PacketExecutor} which calls {@link #run()} on a worker thread.</li>
 * </ol>
 * @param <T> the client type associated with this packet
 * @author JoeAlisson, Mobius
 */
public abstract class ReadablePacket<T extends Client<Connection<T>>> implements Runnable {
    private ReadBuffer buffer;

    @Getter
    private T client;

    protected ReadablePacket() {}

    /**
     * Binds this packet to the given client and buffer before parsing begins.
     * @param client the client that sent this packet
     * @param buffer the decrypted payload buffer
     */
    public void init(T client, ReadBuffer buffer) {
        this.client = client;
        this.buffer = buffer;
    }

    // -------------------------------------------------------------------------
    // Delegating read helpers - all delegate to the bound buffer.
    // -------------------------------------------------------------------------

    /**
     * Reads a little-endian {@code char} (2 bytes).
     * @return the char value
     */
    protected char readChar() {
        return buffer.readChar();
    }

    /**
     * Reads a single byte.
     * @return the byte value
     */
    protected byte readByte() {
        return buffer.readByte();
    }

    /**
     * Reads a single byte as an unsigned int (0–255).
     * @return the unsigned byte value
     */
    protected int readUnsignedByte() {
        return Byte.toUnsignedInt(buffer.readByte());
    }

    /**
     * Reads a byte and returns {@code true} if it is non-zero.
     * @return {@code true} if the byte is non-zero
     */
    protected boolean readBoolean() {
        return buffer.readByte() != 0;
    }

    /**
     * Reads {@code length} bytes into a new array.
     * @param length the number of bytes to read
     * @return the byte array
     */
    protected byte[] readBytes(int length) {
        return buffer.readBytes(length);
    }

    /**
     * Reads {@code dst.length} bytes into {@code dst}.
     * @param dst the destination array
     */
    protected void readBytes(byte[] dst) {
        buffer.readBytes(dst, 0, dst.length);
    }

    /**
     * Reads {@code length} bytes into {@code dst} starting at {@code offset}.
     * @param dst the destination array
     * @param offset the starting offset within {@code dst}
     * @param length the number of bytes to read
     */
    protected void readBytes(byte[] dst, int offset, int length) {
        buffer.readBytes(dst, offset, length);
    }

    /**
     * Reads a little-endian {@code short} (2 bytes).
     * @return the short value
     */
    protected short readShort() {
        return buffer.readShort();
    }

    /**
     * Reads a little-endian {@code int} (4 bytes).
     * @return the int value
     */
    protected int readInt() {
        return buffer.readInt();
    }

    /**
     * Reads a little-endian {@code long} (8 bytes).
     * @return the long value
     */
    protected long readLong() {
        return buffer.readLong();
    }

    /**
     * Reads a little-endian {@code float} (4 bytes).
     * @return the float value
     */
    protected float readFloat() {
        return buffer.readFloat();
    }

    /**
     * Reads a little-endian {@code double} (8 bytes).
     * @return the double value
     */
    protected double readDouble() {
        return buffer.readDouble();
    }

    /**
     * Reads a null-terminated UTF-16LE string from the buffer.<br>
     * Reads {@code short} values until a zero short is encountered.
     * @return the decoded string (never {@code null})
     */
    protected String readString() {
        final StringBuilder result = new StringBuilder();
        try {
            int charId;
            while ((charId = readShort()) != 0) {
                result.append((char) charId);
            }
        } catch (Exception ignored) {
        }

        return result.toString();
    }

    /**
     * Reads a length-prefixed UTF-16LE string.<br>
     * The first {@code short} is the character count; the following bytes are the UTF-16LE data.
     * @return the decoded string (never {@code null})
     */
    protected String readSizedString() {
        try {
            final int charCount = readShort();
            if (charCount > 0) {
                return new String(readBytes(charCount * 2), StandardCharsets.UTF_16LE);
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    /**
     * Returns the number of unread bytes remaining in the buffer.
     * @return remaining byte count
     */
    protected int remaining() {
        return buffer.remaining();
    }

    /**
     * Parses the packet's fields from the bound buffer.<br>
     * Called on the I/O thread; must not block or perform heavy work.
     * @return {@code true} if parsing succeeded and the packet should be executed; {@code false} to discard
     */
    public abstract boolean read();
}
