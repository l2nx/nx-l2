/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets.reason;

import app.l2nx.network.codec.CodeTable;

public enum CharDeleteFailReason {
    REASON_DELETION_FAILED(1),
    REASON_YOU_MAY_NOT_DELETE_CLAN_MEMBER(2),
    REASON_CLAN_LEADERS_MAY_NOT_BE_DELETED(3);

    private static final CodeTable<CharDeleteFailReason> BY_CODE =
            new CodeTable<>(values(), CharDeleteFailReason::code);

    private final int code;

    CharDeleteFailReason(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * @return {@code null} when the code is unknown
     */
    public static CharDeleteFailReason of(int code) {
        return BY_CODE.of(code);
    }
}
