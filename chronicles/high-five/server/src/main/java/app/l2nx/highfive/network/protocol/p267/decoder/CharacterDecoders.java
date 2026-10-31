/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import app.l2nx.highfive.character.command.CreateCharacter;
import app.l2nx.highfive.character.command.DeleteCharacter;
import app.l2nx.highfive.character.command.Restart;
import app.l2nx.highfive.character.command.RestoreCharacter;
import app.l2nx.highfive.character.command.SelectCharacter;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.protocol.game.p267.clientpackets.CharacterCreate;
import app.l2nx.protocol.game.p267.clientpackets.CharacterDelete;
import app.l2nx.protocol.game.p267.clientpackets.CharacterRestore;
import app.l2nx.protocol.game.p267.clientpackets.CharacterSelect;
import app.l2nx.protocol.game.p267.clientpackets.EnterWorld;
import app.l2nx.protocol.game.p267.clientpackets.RequestRestart;
import io.netty.buffer.ByteBuf;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public final class CharacterDecoders {
    private CharacterDecoders() {}

    public static Object characterSelect(ByteBuf frame, GameConnection connection) {
        return new SelectCharacter(CharacterSelect.decode(frame).slot());
    }

    /**
     * Race and base stats follow from the class and are not taken from the client. The name is kept as sent, even
     * empty: the handler answers an invalid length as the client expects, where a decode error would drop the packet.
     */
    public static Object characterCreate(ByteBuf frame, GameConnection connection) {
        final CharacterCreate packet = CharacterCreate.decode(frame);
        return CreateCharacter.builder()
                .name(packet.name())
                .female(packet.female())
                .classId(packet.classId())
                .hairStyle(packet.hairStyle())
                .hairColor(packet.hairColor())
                .face(packet.face())
                .build();
    }

    public static Object characterDelete(ByteBuf frame, GameConnection connection) {
        return new DeleteCharacter(CharacterDelete.decode(frame).slot());
    }

    public static Object characterRestore(ByteBuf frame, GameConnection connection) {
        return new RestoreCharacter(CharacterRestore.decode(frame).slot());
    }

    public static Object restart(ByteBuf frame, GameConnection connection) {
        RequestRestart.decode(frame);
        return Restart.INSTANCE;
    }

    public static Object enterWorld(ByteBuf frame, GameConnection connection) {
        final EnterWorld packet = EnterWorld.decode(frame);
        final List<Inet4Address> route = new ArrayList<>(EnterWorld.HOPS);
        for (int hop = 0; hop < EnterWorld.HOPS; hop++) {
            final byte[] octets = new byte[EnterWorld.OCTETS];
            for (int octet = 0; octet < EnterWorld.OCTETS; octet++) {
                octets[octet] = (byte) packet.octet(hop, octet);
            }
            try {
                route.add((Inet4Address) InetAddress.getByAddress(octets));
            } catch (UnknownHostException e) {
                // getByAddress only rejects an address that is not 4 or 16 bytes long, and this one is 4.
                throw new IllegalStateException("A four-byte address was rejected", e);
            }
        }
        return new app.l2nx.highfive.character.command.EnterWorld(route);
    }
}
