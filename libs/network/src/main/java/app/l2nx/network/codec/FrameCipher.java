/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import io.netty.buffer.ByteBuf;

/**
 * Per-channel cipher, called only on the channel's event loop, so implementations keep rolling state without
 * synchronization. Indexes are absolute and never include the length header.
 */
public interface FrameCipher {
    FrameCipher NONE = new FrameCipher() {
        @Override
        public int encryptedLength(int payloadLength) {
            return payloadLength;
        }

        @Override
        public void encrypt(ByteBuf buf, int index, int payloadLength) {}

        @Override
        public boolean decrypt(ByteBuf buf, int index, int length) {
            return true;
        }
    };

    /** Length of the encrypted region for a payload, padding and checksum included. */
    int encryptedLength(int payloadLength);

    /**
     * The region {@code [index, index + encryptedLength(payloadLength))} is writable; the bytes after the payload are
     * scratch the cipher fills (padding, checksum).
     */
    void encrypt(ByteBuf buf, int index, int payloadLength);

    /** {@code false} rejects the frame (bad checksum, bad block size); the connection closes. */
    boolean decrypt(ByteBuf buf, int index, int length);
}
