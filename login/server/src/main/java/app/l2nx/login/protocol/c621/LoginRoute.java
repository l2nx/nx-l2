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
package app.l2nx.login.protocol.c621.clientpackets;

import app.l2nx.login.protocol.c621.ConnectionState;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import lombok.Getter;

/**
 * @author Mobius
 */
public enum LoginClientPackets {
    AUTH_GAME_GUARD(0x07, AuthGameGuard::new, ConnectionState.CONNECTED),
    REQUEST_AUTH_LOGIN(0x00, RequestAuthLogin::new, ConnectionState.AUTHED_GG),
    REQUEST_SERVER_LOGIN(0x02, RequestServerLogin::new, ConnectionState.AUTHED_LOGIN),
    REQUEST_SERVER_LIST(0x05, RequestServerList::new, ConnectionState.AUTHED_LOGIN),
    // Sent by some High Five clients; the server never offers the agreement screen, so there is nothing to answer.
    REQUEST_PI_AGREEMENT_CHECK(0x0E, null, ConnectionState.AUTHED_LOGIN),
    REQUEST_PI_AGREEMENT(0x0F, null, ConnectionState.AUTHED_LOGIN);

    public static final LoginClientPackets[] PACKET_ARRAY;

    static {
        final short maxPacketId = (short) Arrays.stream(values())
                .mapToInt(LoginClientPackets::getPacketId)
                .max()
                .orElse(0);
        PACKET_ARRAY = new LoginClientPackets[maxPacketId + 1];
        for (LoginClientPackets packet : values()) {
            PACKET_ARRAY[packet.getPacketId()] = packet;
        }
    }

    private final short packetId;
    private final Supplier<LoginClientPacket> packetSupplier;

    @Getter
    private final Set<ConnectionState> connectionStates;

    LoginClientPackets(int packetId, Supplier<LoginClientPacket> packetSupplier, ConnectionState... connectionStates) {
        if (packetId > 0xFF) {
            throw new IllegalArgumentException("Packet id must not be bigger than 0xFF");
        }

        this.packetId = (short) packetId;
        this.packetSupplier = packetSupplier;

        final EnumSet<ConnectionState> states = EnumSet.noneOf(ConnectionState.class);
        Collections.addAll(states, connectionStates);
        this.connectionStates = Collections.unmodifiableSet(states);
    }

    public int getPacketId() {
        return packetId;
    }

    /** A known opcode without a handler: the packet is dropped under its own reason, not counted as unknown. */
    public boolean isIgnored() {
        return packetSupplier == null;
    }

    public LoginClientPacket newPacket() {
        if (packetSupplier == null) {
            throw new IllegalStateException(name() + " has no handler");
        }
        return packetSupplier.get();
    }
}
