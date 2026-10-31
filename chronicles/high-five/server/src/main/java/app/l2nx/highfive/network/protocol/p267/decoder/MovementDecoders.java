/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import app.l2nx.highfive.movement.MoveInput;
import app.l2nx.highfive.movement.command.MoveBlocked;
import app.l2nx.highfive.movement.command.MoveTo;
import app.l2nx.highfive.movement.command.ReportPosition;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.protocol.game.p267.clientpackets.CannotMoveAnymore;
import app.l2nx.protocol.game.p267.clientpackets.MoveToLocation;
import app.l2nx.protocol.game.p267.clientpackets.ValidatePosition;
import io.netty.buffer.ByteBuf;

public final class MovementDecoders {
    private MovementDecoders() {}

    /** The client sends 0 for the cursor keys; any mode but the mouse is read as the keys. */
    public static Object moveToLocation(ByteBuf frame, GameConnection connection) {
        final MoveToLocation packet = MoveToLocation.decode(frame);
        return MoveTo.builder()
                .targetX(packet.targetX())
                .targetY(packet.targetY())
                .targetZ(packet.targetZ())
                .originX(packet.originX())
                .originY(packet.originY())
                .originZ(packet.originZ())
                .input((packet.movementMode() == MoveToLocation.MOUSE) ? MoveInput.MOUSE : MoveInput.KEYBOARD)
                .build();
    }

    /** The vehicle id is dropped: the server knows which vehicle its player is on. */
    public static Object validatePosition(ByteBuf frame, GameConnection connection) {
        final ValidatePosition packet = ValidatePosition.decode(frame);
        return ReportPosition.builder()
                .x(packet.x())
                .y(packet.y())
                .z(packet.z())
                .heading(packet.heading())
                .build();
    }

    public static Object cannotMoveAnymore(ByteBuf frame, GameConnection connection) {
        final CannotMoveAnymore packet = CannotMoveAnymore.decode(frame);
        return MoveBlocked.builder()
                .x(packet.x())
                .y(packet.y())
                .z(packet.z())
                .heading(packet.heading())
                .build();
    }
}
