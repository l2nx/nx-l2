/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.login.protocol.c621.model;

import app.l2nx.login.network.LoginClient;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.login.c621.clientpackets.LoginClientOpcode;
import io.netty.buffer.ByteBuf;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Builder;

/**
 * @param decoder {@code null} for a known opcode the login server ignores
 * @author Mobius
 */
@Builder(toBuilder = true)
public record LoginRoute(
        LoginClientOpcode opcode, Function<ByteBuf, Consumer<LoginClient>> decoder, Set<ConnectionState> accepted) {
    public LoginRoute {
        accepted = Set.copyOf(accepted);
    }

    /** A known opcode without a handler: the packet is dropped under its own reason, not counted as unknown. */
    public boolean isIgnored() {
        return decoder == null;
    }

    /**
     * Decodes whatever the connection state; the state is checked on the lane ({@code LanePacket}).
     *
     * @throws PacketDecodeException when the body is too short for the record
     */
    public Consumer<LoginClient> decode(ByteBuf body) {
        if (decoder == null) {
            throw new IllegalStateException(opcode + " has no handler");
        }
        return decoder.apply(body);
    }
}
