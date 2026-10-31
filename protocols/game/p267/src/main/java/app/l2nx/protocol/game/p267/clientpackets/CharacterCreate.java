/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import lombok.Builder;

/**
 * @param name as sent, bounded only by the frame: the server answers a name of any length with the client's own
 *     failure reason, which a decode error would never reach
 */
@Builder(toBuilder = true)
public record CharacterCreate(
        String name,
        int race,
        boolean female,
        int classId,
        int intelligence,
        int strength,
        int constitution,
        int mentality,
        int dexterity,
        int wit,
        int hairStyle,
        int hairColor,
        int face) {
    public CharacterCreate {
        Objects.requireNonNull(name, "name");
    }

    public static CharacterCreate decode(ByteBuf body) {
        return Bodies.parse(
                "CharacterCreate",
                body,
                in -> CharacterCreate.builder()
                        .name(Wire.readString(in))
                        .race(in.readIntLE())
                        .female(in.readIntLE() != 0)
                        .classId(in.readIntLE())
                        .intelligence(in.readIntLE())
                        .strength(in.readIntLE())
                        .constitution(in.readIntLE())
                        .mentality(in.readIntLE())
                        .dexterity(in.readIntLE())
                        .wit(in.readIntLE())
                        .hairStyle(in.readIntLE())
                        .hairColor(in.readIntLE())
                        .face(in.readIntLE())
                        .build());
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.CHARACTER_CREATE.opcode());
        Wire.writeString(out, name);
        out.writeIntLE(race);
        out.writeIntLE(female ? 1 : 0);
        out.writeIntLE(classId);
        out.writeIntLE(intelligence);
        out.writeIntLE(strength);
        out.writeIntLE(constitution);
        out.writeIntLE(mentality);
        out.writeIntLE(dexterity);
        out.writeIntLE(wit);
        out.writeIntLE(hairStyle);
        out.writeIntLE(hairColor);
        out.writeIntLE(face);
    }
}
