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

/**
 * The first hit sits in front of the attacker's position, the others behind it. {@code extraHits} stays apart from
 * {@code hit} on purpose: the usual single hit then copies no list on the combat path.
 */
@Builder(toBuilder = true)
public record Attack(
        int attackerObjectId, Hit hit, int x, int y, int z, List<Hit> extraHits, int targetX, int targetY, int targetZ)
        implements GameServerPacket {
    private static final int HIT_BYTES = 4 + 4 + 1;

    public Attack {
        Objects.requireNonNull(hit, "hit");
        extraHits = List.copyOf(Objects.requireNonNull(extraHits, "extraHits"));
    }

    public static Attack read(ByteBuf body) {
        return Bodies.parse("Attack", body, in -> {
            final AttackBuilder packet = Attack.builder()
                    .attackerObjectId(in.readIntLE())
                    .hit(Hit.read(in))
                    .x(in.readIntLE())
                    .y(in.readIntLE())
                    .z(in.readIntLE());
            final int count = Bodies.count(in.readUnsignedShortLE(), in, HIT_BYTES);
            final List<Hit> extraHits = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                extraHits.add(Hit.read(in));
            }
            return packet.extraHits(extraHits)
                    .targetX(in.readIntLE())
                    .targetY(in.readIntLE())
                    .targetZ(in.readIntLE())
                    .build();
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.ATTACK.opcode());
        out.writeIntLE(attackerObjectId);
        hit.write(out);
        out.writeIntLE(x);
        out.writeIntLE(y);
        out.writeIntLE(z);
        out.writeShortLE(extraHits.size());
        for (Hit extraHit : extraHits) {
            extraHit.write(out);
        }
        out.writeIntLE(targetX);
        out.writeIntLE(targetY);
        out.writeIntLE(targetZ);
    }

    @Builder(toBuilder = true)
    public record Hit(int targetObjectId, int damage, int flags) {
        private static Hit read(ByteBuf in) {
            return Hit.builder()
                    .targetObjectId(in.readIntLE())
                    .damage(in.readIntLE())
                    .flags(in.readUnsignedByte())
                    .build();
        }

        private void write(ByteBuf out) {
            out.writeIntLE(targetObjectId);
            out.writeIntLE(damage);
            out.writeByte(flags);
        }
    }
}
