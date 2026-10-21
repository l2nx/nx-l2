/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol;

import app.l2nx.highfive.network.GameConnection;
import io.netty.buffer.ByteBuf;

/**
 * Parses the rest of a frame (after the opcode) into a command, a legacy packet or an {@link OpcodeTable} marker.
 * Runs on the IO thread: copies every value it keeps and never retains the frame.
 */
@FunctionalInterface
public interface InboundDecoder {
    Object decode(ByteBuf frame, GameConnection connection);
}
