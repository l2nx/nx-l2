/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets.reason;

import app.l2nx.network.codec.CodeTable;

public enum LoginFailReason {
    NO_TEXT(0),
    SYSTEM_ERROR_LOGIN_LATER(1),
    PASSWORD_DOES_NOT_MATCH_THIS_ACCOUNT(2),
    PASSWORD_DOES_NOT_MATCH_THIS_ACCOUNT2(3),
    ACCESS_FAILED_TRY_LATER(4),
    INCORRECT_ACCOUNT_INFO_CONTACT_CUSTOMER_SUPPORT(5),
    ACCESS_FAILED_TRY_LATER2(6),
    ACOUNT_ALREADY_IN_USE(7),
    ACCESS_FAILED_TRY_LATER3(8),
    ACCESS_FAILED_TRY_LATER4(9),
    ACCESS_FAILED_TRY_LATER5(10);

    private static final CodeTable<LoginFailReason> BY_CODE = new CodeTable<>(values(), LoginFailReason::code);

    private final int code;

    LoginFailReason(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * @return {@code null} when the code is unknown
     */
    public static LoginFailReason of(int code) {
        return BY_CODE.of(code);
    }
}
