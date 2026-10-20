/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import io.netty.handler.codec.DecoderException;

public final class PacketDecodeException extends DecoderException {
    public PacketDecodeException(String message) {
        super(message);
    }

    public PacketDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
