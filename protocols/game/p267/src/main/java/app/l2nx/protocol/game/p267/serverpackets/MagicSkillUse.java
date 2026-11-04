/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

@Builder(toBuilder = true)
public record MagicSkillUse(
        int objectId,
        int targetObjectId,
        int skillId,
        int skillLevel,
        int hitTime,
        int reuseDelay,
        int x,
        int y,
        int z,
        List<GroundLocation> groundLocations,
        int targetX,
        int targetY,
        int targetZ)
        implements GameServerPacket {
    private static final int GROUND_LOCATION_BYTES = 4 + 4 + 4;

    public MagicSkillUse {
        groundLocations = List.copyOf(Objects.requireNonNull(groundLocations, "groundLocations"));
    }

    public static MagicSkillUse read(ByteBuf body) {
        return Bodies.parse("MagicSkillUse", body, in -> {
            final MagicSkillUseBuilder packet = MagicSkillUse.builder()
                    .objectId(in.readIntLE())
                    .targetObjectId(in.readIntLE())
                    .skillId(in.readIntLE())
                    .skillLevel(in.readIntLE())
                    .hitTime(in.readIntLE())
                    .reuseDelay(in.readIntLE())
                    .x(in.readIntLE())
                    .y(in.readIntLE())
                    .z(in.readIntLE());
            in.skipBytes(2);
            final int count = Bodies.count(in.readUnsignedShortLE(), in, GROUND_LOCATION_BYTES);
            final List<GroundLocation> groundLocations = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                groundLocations.add(GroundLocation.builder()
                        .x(in.readIntLE())
                        .y(in.readIntLE())
                        .z(in.readIntLE())
                        .build());
            }
            return packet.groundLocations(groundLocations)
                    .targetX(in.readIntLE())
                    .targetY(in.readIntLE())
                    .targetZ(in.readIntLE())
                    .build();
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.MAGIC_SKILL_USE.opcode());
        out.writeIntLE(objectId);
        out.writeIntLE(targetObjectId);
        out.writeIntLE(skillId);
        out.writeIntLE(skillLevel);
        out.writeIntLE(hitTime);
        out.writeIntLE(reuseDelay);
        out.writeIntLE(x);
        out.writeIntLE(y);
        out.writeIntLE(z);
        out.writeShortLE(0);
        out.writeShortLE(groundLocations.size());
        for (GroundLocation location : groundLocations) {
            out.writeIntLE(location.x());
            out.writeIntLE(location.y());
            out.writeIntLE(location.z());
        }
        out.writeIntLE(targetX);
        out.writeIntLE(targetY);
        out.writeIntLE(targetZ);
    }

    @Builder(toBuilder = true)
    public record GroundLocation(int x, int y, int z) {}
}
