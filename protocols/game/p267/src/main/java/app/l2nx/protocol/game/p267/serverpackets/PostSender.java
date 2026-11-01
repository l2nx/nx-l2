/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.CodeTable;
import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;

/**
 * Whom the client shows as the sender of a mail. For every value but {@link #PLAYER} it shows its own sender string
 * and hides Return and Reply.
 */
public enum PostSender {
    PLAYER(0),
    /** The system sender, or the returned-mail sender when the mail is marked returned. */
    SYSTEM(1),
    BIRTHDAY(3);

    private static final CodeTable<PostSender> BY_CODE = new CodeTable<>(values(), PostSender::code);

    private final int code;

    PostSender(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * @return {@code null} when the code is unknown
     */
    public static PostSender of(int code) {
        return BY_CODE.of(code);
    }

    static PostSender read(ByteBuf in) {
        final int code = in.readIntLE();
        final PostSender sender = of(code);
        if (sender == null) {
            throw new PacketDecodeException("Unknown mail sender " + code);
        }
        return sender;
    }
}
