/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.network.codec.CodeTable;

public enum GameClientOpcode {
    CHARACTER_CREATE(0x0C),
    CHARACTER_DELETE(0x0D),
    PROTOCOL_VERSION(0x0E),
    MOVE_TO_LOCATION(0x0F),
    ENTER_WORLD(0x11),
    CHARACTER_SELECT(0x12),
    ACTION(0x1F),
    AUTH_LOGIN(0x2B),
    ATTACK_REQUEST(0x32),
    REQUEST_MAGIC_SKILL_USE(0x39),
    APPEARING(0x3A),
    CANNOT_MOVE_ANYMORE(0x47),
    SAY2(0x49),
    REQUEST_RESTART(0x57),
    VALIDATE_POSITION(0x59),
    SEND_BYPASS_BUILD_CMD(0x74),
    CHARACTER_RESTORE(0x7B),
    REQUEST_RESTART_POINT(0x7D);

    private static final CodeTable<GameClientOpcode> BY_OPCODE = new CodeTable<>(values(), GameClientOpcode::opcode);

    private final int opcode;

    GameClientOpcode(int opcode) {
        this.opcode = opcode;
    }

    public int opcode() {
        return opcode;
    }

    public static GameClientOpcode of(int opcode) {
        return BY_OPCODE.of(opcode);
    }
}
