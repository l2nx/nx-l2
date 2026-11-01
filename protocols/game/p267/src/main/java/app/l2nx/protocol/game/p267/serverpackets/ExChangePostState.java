/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * @param receivedBox {@code true} for mails of the received box, {@code false} for the sent box
 */
@Builder(toBuilder = true)
public record ExChangePostState(boolean receivedBox, List<Change> changes) implements GameServerPacket {
    private static final int CHANGE_BYTES = 4 + 4;

    public ExChangePostState {
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }

    @Builder(toBuilder = true)
    public record Change(int mailId, PostState state) {
        public Change {
            Objects.requireNonNull(state, "state");
        }
    }

    public static ExChangePostState read(ByteBuf body) {
        return Bodies.parse("ExChangePostState", body, in -> {
            final boolean receivedBox = in.readIntLE() != 0;
            final int count = Bodies.count(in.readIntLE(), in, CHANGE_BYTES);
            final List<Change> changes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                final int mailId = in.readIntLE();
                final int code = in.readIntLE();
                final PostState state = PostState.of(code);
                if (state == null) {
                    throw new PacketDecodeException("Unknown ExChangePostState state " + code);
                }
                changes.add(new Change(mailId, state));
            }
            return new ExChangePostState(receivedBox, changes);
        });
    }

    @Override
    public void write(ByteBuf out) {
        GameServerExOpcode.EX_CHANGE_POST_STATE.write(out);
        out.writeIntLE(receivedBox ? 1 : 0);
        out.writeIntLE(changes.size());
        for (Change change : changes) {
            out.writeIntLE(change.mailId());
            out.writeIntLE(change.state().code());
        }
    }
}
