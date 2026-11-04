/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.network.codec.Wire;
import io.netty.buffer.ByteBuf;
import java.util.Objects;

/** Sent instead of {@code Say2} for a chat line starting with {@code //}; {@code command} excludes those two characters. */
public record SendBypassBuildCmd(String command) {
    public SendBypassBuildCmd {
        Objects.requireNonNull(command, "command");
    }

    /** Lenient like the legacy packet: a command without its terminator still runs. */
    public static SendBypassBuildCmd decode(ByteBuf body) {
        return new SendBypassBuildCmd(Wire.readStringToEnd(body));
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.SEND_BYPASS_BUILD_CMD.opcode());
        Wire.writeString(out, command);
    }
}
