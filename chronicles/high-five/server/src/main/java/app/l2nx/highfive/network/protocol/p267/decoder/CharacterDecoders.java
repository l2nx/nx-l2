/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import app.l2nx.highfive.character.command.CreateCharacter;
import app.l2nx.highfive.character.command.DeleteCharacter;
import app.l2nx.highfive.character.command.RestoreCharacter;
import app.l2nx.highfive.character.command.SelectCharacter;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.protocol.game.p267.clientpackets.CharacterCreate;
import app.l2nx.protocol.game.p267.clientpackets.CharacterDelete;
import app.l2nx.protocol.game.p267.clientpackets.CharacterRestore;
import app.l2nx.protocol.game.p267.clientpackets.CharacterSelect;
import io.netty.buffer.ByteBuf;

/** Character packets of the selection screen to the commands of the {@code character} slice. */
public final class CharacterDecoders {
    private CharacterDecoders() {}

    public static Object characterSelect(ByteBuf frame, GameConnection connection) {
        return new SelectCharacter(CharacterSelect.decode(frame).slot());
    }

    /**
     * Race and base stats follow from the class and are not taken from the client. The name is kept as sent, even
     * empty: the handler answers a name outside 1 to 16 characters, as the client expects, instead of dropping the
     * packet as a decode error.
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
}
