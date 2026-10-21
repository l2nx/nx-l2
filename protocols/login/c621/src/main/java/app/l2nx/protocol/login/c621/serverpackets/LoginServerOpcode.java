/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.serverpackets;

import app.l2nx.protocol.login.c621.CodeTable;

public enum LoginServerOpcode {
    INIT(0x00),
    LOGIN_FAIL(0x01),
    ACCOUNT_KICKED(0x02),
    LOGIN_OK(0x03),
    SERVER_LIST(0x04),
    PLAY_FAIL(0x06),
    PLAY_OK(0x07),
    GG_AUTH(0x0B);

    private static final CodeTable<LoginServerOpcode> BY_OPCODE = new CodeTable<>(values(), LoginServerOpcode::opcode);

    private final int opcode;

    LoginServerOpcode(int opcode) {
        this.opcode = opcode;
    }

    public int opcode() {
        return opcode;
    }

    /**
     * @return {@code null} when the opcode is unknown
     */
    public static LoginServerOpcode of(int opcode) {
        return BY_OPCODE.of(opcode);
    }
}
