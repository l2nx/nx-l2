/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets.reason;

import app.l2nx.network.codec.CodeTable;

public enum CharCreateFailReason {
    /** "Your character creation has failed." */
    REASON_CREATION_FAILED(0x00),
    /** "You cannot create another character. Please delete the existing character and try again." */
    REASON_TOO_MANY_CHARACTERS(0x01),
    /** "This name already exists." */
    REASON_NAME_ALREADY_EXISTS(0x02),
    /** "Your title cannot exceed 16 characters in length. Please try again." */
    REASON_16_ENG_CHARS(0x03),
    /** "Incorrect name. Please try again." */
    REASON_INCORRECT_NAME(0x04),
    /** "Characters cannot be created from this server." */
    REASON_CREATE_NOT_ALLOWED(0x05),
    /** "Unable to create character. You are unable to create a new character on the selected server. ..." */
    REASON_CHOOSE_ANOTHER_SVR(0x06);

    private static final CodeTable<CharCreateFailReason> BY_CODE =
            new CodeTable<>(values(), CharCreateFailReason::code);

    private final int code;

    CharCreateFailReason(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * @return {@code null} when the code is unknown
     */
    public static CharCreateFailReason of(int code) {
        return BY_CODE.of(code);
    }
}
