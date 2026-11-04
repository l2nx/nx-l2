/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.CodeTable;

/** Server opcodes that have a record; the rest are still written by legacy packets. */
public enum GameServerOpcode {
    DIE(0x00),
    REVIVE(0x01),
    DELETE_OBJECT(0x08),
    CHARACTER_SELECTION_INFO(0x09),
    LOGIN_FAIL(0x0A),
    CHARACTER_SELECTED(0x0B),
    NPC_INFO(0x0C),
    CHARACTER_CREATE_SUCCESS(0x0F),
    CHARACTER_CREATE_FAIL(0x10),
    STATUS_UPDATE(0x18),
    CHARACTER_DELETE_SUCCESS(0x1D),
    CHARACTER_DELETE_FAIL(0x1E),
    ACTION_FAIL(0x1F),
    SERVER_CLOSE(0x20),
    TELEPORT_TO_LOCATION(0x22),
    VERSION_CHECK(0x2E),
    MOVE_TO_LOCATION(0x2F),
    CHAR_INFO(0x31),
    USER_INFO(0x32),
    ATTACK(0x33),
    STOP_MOVE(0x47),
    MOVE_TO_PAWN(0x72),
    MAGIC_SKILL_USE(0x48),
    SAY2(0x4A),
    SYSTEM_MESSAGE(0x62),
    RESTART_RESPONSE(0x71),
    VALIDATE_LOCATION(0x79),
    LOG_OUT_OK(0x84),
    MY_TARGET_SELECTED(0xB9);

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
