/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.CodeTable;

/** Server opcodes that have a record; the rest are still written by legacy packets. */
public enum GameServerOpcode {
    CHARACTER_SELECTION_INFO(0x09),
    LOGIN_FAIL(0x0A),
    CHARACTER_SELECTED(0x0B),
    CHARACTER_CREATE_SUCCESS(0x0F),
    CHARACTER_CREATE_FAIL(0x10),
    CHARACTER_DELETE_SUCCESS(0x1D),
    CHARACTER_DELETE_FAIL(0x1E),
    ACTION_FAIL(0x1F),
    SERVER_CLOSE(0x20),
    VERSION_CHECK(0x2E),
    SAY2(0x4A),
    SYSTEM_MESSAGE(0x62),
    RESTART_RESPONSE(0x71),
    VALIDATE_LOCATION(0x79),
    LOG_OUT_OK(0x84);

    private static final CodeTable<GameServerOpcode> BY_OPCODE = new CodeTable<>(values(), GameServerOpcode::opcode);

    private final int opcode;

    GameServerOpcode(int opcode) {
        this.opcode = opcode;
    }

    public int opcode() {
        return opcode;
    }

    /**
     * @return {@code null} when the opcode has no record
     */
    public static GameServerOpcode of(int opcode) {
        return BY_OPCODE.of(opcode);
    }
}
