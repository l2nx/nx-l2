/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.CodeTable;

public enum PostState {
    DELETED(0),
    READ(1),
    /** Clears the attachment icon of the mail in the open list. */
    ATTACHMENTS_TAKEN(2);

    private static final CodeTable<PostState> BY_CODE = new CodeTable<>(values(), PostState::code);

    private final int code;

    PostState(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * @return {@code null} when the code is unknown
     */
    public static PostState of(int code) {
        return BY_CODE.of(code);
    }
}
