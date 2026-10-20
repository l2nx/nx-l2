/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.loginserverpackets.send;

import app.l2nx.network.packet.SimpleWritablePacket;
import java.util.Objects;
import java.util.UUID;

/**
 * Requests an account password change at the login server.
 * <ul>
 * <li>Opcode: 0x0B.</li>
 * <li>Payload: account id (2 longs: msb, lsb), character (String), oldPass (String), newPass (String).</li>
 * </ul>
 * @author BazookaRpm
 */
public class ChangePassword extends SimpleWritablePacket {
    // Opcode.
    private static final int OPCODE = 0x0B;

    // Data.
    private final UUID accountId;
    private final String characterName;
    private final String oldPass;
    private final String newPass;

    /**
     * @param accountId account
     * @param characterName character
     * @param oldPass old password
     * @param newPass new password
     */
    public ChangePassword(UUID accountId, String characterName, String oldPass, String newPass) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.characterName = characterName;
        this.oldPass = oldPass;
        this.newPass = newPass;
    }

    /**
     * Serializes opcode, account and all string fields.
     */
    @Override
    public void write() {
        writeByte(OPCODE);
        writeLong(accountId.getMostSignificantBits());
        writeLong(accountId.getLeastSignificantBits());
        writeString(characterName);
        writeString(oldPass);
        writeString(newPass);
    }
}
