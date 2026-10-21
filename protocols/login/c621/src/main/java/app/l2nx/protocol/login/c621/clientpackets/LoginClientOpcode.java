/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.clientpackets;

import app.l2nx.protocol.login.c621.CodeTable;

public enum LoginClientOpcode {
    REQUEST_AUTH_LOGIN(0x00),
    REQUEST_SERVER_LOGIN(0x02),
    REQUEST_SERVER_LIST(0x05),
    AUTH_GAME_GUARD(0x07),
    REQUEST_PI_AGREEMENT_CHECK(0x0E),
    REQUEST_PI_AGREEMENT(0x0F);

    private static final CodeTable<LoginClientOpcode> BY_OPCODE = new CodeTable<>(values(), LoginClientOpcode::opcode);

    private final int opcode;

    LoginClientOpcode(int opcode) {
        this.opcode = opcode;
    }

    public int opcode() {
        return opcode;
    }

    /**
     * @return {@code null} when the opcode is unknown
     */
    public static LoginClientOpcode of(int opcode) {
        return BY_OPCODE.of(opcode);
    }
}
