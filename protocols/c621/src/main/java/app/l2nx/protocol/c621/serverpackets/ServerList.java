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
package app.l2nx.protocol.c621.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * @param characters the character counts of the account, per server that reported them
 */
@Builder(toBuilder = true)
public record ServerList(List<Server> servers, int lastServer, List<Characters> characters)
        implements LoginServerPacket {
    private static final int IP_SIZE = 4;

    public ServerList {
        servers = List.copyOf(Objects.requireNonNull(servers, "servers"));
        characters = List.copyOf(Objects.requireNonNull(characters, "characters"));
    }

    /**
     * @param ip the IPv4 address in network order
     * @param serverType flags: 1 normal, 2 relax, 4 public test, 8 no label, 16 character creation restricted, 32
     *     event, 64 free
     */
    @Builder(toBuilder = true)
    public record Server(
            int id,
            byte[] ip,
            int port,
            int ageLimit,
            boolean pvp,
            int currentPlayers,
            int maxPlayers,
            boolean up,
            int serverType,
            boolean brackets) {
        public Server {
            Objects.requireNonNull(ip, "ip");
            if (ip.length != IP_SIZE) {
                throw new IllegalArgumentException("An IPv4 address has " + IP_SIZE + " bytes, not " + ip.length);
            }
            ip = ip.clone();
        }

        @Override
        public byte[] ip() {
            return ip.clone();
        }

        @Override
        public boolean equals(Object other) {
            return (other instanceof Server that)
                    && (id == that.id)
                    && Arrays.equals(ip, that.ip)
                    && (port == that.port)
                    && (ageLimit == that.ageLimit)
                    && (pvp == that.pvp)
                    && (currentPlayers == that.currentPlayers)
                    && (maxPlayers == that.maxPlayers)
                    && (up == that.up)
                    && (serverType == that.serverType)
                    && (brackets == that.brackets);
        }

        @Override
        public int hashCode() {
            return (31 * Objects.hash(id, port, ageLimit, pvp, currentPlayers, maxPlayers, up, serverType, brackets))
                    + Arrays.hashCode(ip);
        }

        @Override
        public String toString() {
            return "Server[id=" + id + ", ip=" + (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "."
                    + (ip[3] & 0xFF) + ", port=" + port + ", ageLimit=" + ageLimit + ", pvp=" + pvp
                    + ", currentPlayers=" + currentPlayers + ", maxPlayers=" + maxPlayers + ", up=" + up
                    + ", serverType=" + serverType + ", brackets=" + brackets + "]";
        }
    }

    /**
     * @param pendingDeletion the seconds left until each character of the server pending deletion is deleted
     */
    @Builder(toBuilder = true)
    public record Characters(int serverId, int count, int[] pendingDeletion) {
        public Characters {
            pendingDeletion =
                    Objects.requireNonNull(pendingDeletion, "pendingDeletion").clone();
        }

        @Override
        public int[] pendingDeletion() {
            return pendingDeletion.clone();
        }

        @Override
        public boolean equals(Object other) {
            return (other instanceof Characters that)
                    && (serverId == that.serverId)
                    && (count == that.count)
                    && Arrays.equals(pendingDeletion, that.pendingDeletion);
        }

        @Override
        public int hashCode() {
            return (31 * ((31 * Integer.hashCode(serverId)) + Integer.hashCode(count)))
                    + Arrays.hashCode(pendingDeletion);
        }

        @Override
        public String toString() {
            return "Characters[serverId=" + serverId + ", count=" + count + ", pendingDeletion="
                    + Arrays.toString(pendingDeletion) + "]";
        }
    }

    /**
     * @param body the packet after its opcode
     */
    public static ServerList read(ByteBuf body) {
        try {
            final int serverCount = body.readUnsignedByte();
            final int lastServer = body.readUnsignedByte();
            final List<Server> servers = new ArrayList<>(serverCount);
            for (int i = 0; i < serverCount; i++) {
                servers.add(readServer(body));
            }
            body.skipBytes(2);
            final int characterCount = body.readUnsignedByte();
            final List<Characters> characters = new ArrayList<>(characterCount);
            for (int i = 0; i < characterCount; i++) {
                final int serverId = body.readUnsignedByte();
                final int count = body.readUnsignedByte();
                final int[] pendingDeletion = new int[body.readUnsignedByte()];
                for (int j = 0; j < pendingDeletion.length; j++) {
                    pendingDeletion[j] = body.readIntLE();
                }
                characters.add(Characters.builder()
                        .serverId(serverId)
                        .count(count)
                        .pendingDeletion(pendingDeletion)
                        .build());
            }
            return ServerList.builder()
                    .servers(servers)
                    .lastServer(lastServer)
                    .characters(characters)
                    .build();
        } catch (IndexOutOfBoundsException e) {
            throw new PacketDecodeException("ServerList read past the end of its frame", e);
        }
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(LoginServerOpcode.SERVER_LIST.opcode());
        out.writeByte(servers.size());
        out.writeByte(lastServer);
        for (Server server : servers) {
            out.writeByte(server.id());
            out.writeBytes(server.ip);
            out.writeIntLE(server.port());
            out.writeByte(server.ageLimit());
            out.writeByte(server.pvp() ? 1 : 0);
            out.writeShortLE(server.currentPlayers());
            out.writeShortLE(server.maxPlayers());
            out.writeByte(server.up() ? 1 : 0);
            out.writeIntLE(server.serverType());
            out.writeByte(server.brackets() ? 1 : 0);
        }
        out.writeShortLE(0);
        out.writeByte(characters.size());
        for (Characters entry : characters) {
            out.writeByte(entry.serverId());
            out.writeByte(entry.count());
            out.writeByte(entry.pendingDeletion.length);
            for (int seconds : entry.pendingDeletion) {
                out.writeIntLE(seconds);
            }
        }
    }

    private static Server readServer(ByteBuf body) {
        final int id = body.readUnsignedByte();
        final byte[] ip = new byte[IP_SIZE];
        body.readBytes(ip);
        return Server.builder()
                .id(id)
                .ip(ip)
                .port(body.readIntLE())
                .ageLimit(body.readUnsignedByte())
                .pvp(body.readByte() != 0)
                .currentPlayers(body.readUnsignedShortLE())
                .maxPlayers(body.readUnsignedShortLE())
                .up(body.readByte() != 0)
                .serverType(body.readIntLE())
                .brackets(body.readByte() != 0)
                .build();
    }
}
