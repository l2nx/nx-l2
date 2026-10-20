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
package app.l2nx.login.network.gameserverpackets.receive;

import app.l2nx.login.controller.LoginController;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.network.packet.SimpleReadablePacket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Handles password change requests coming from game servers.<br>
 * Performs credential verification and persists the updated password hash in the login database.
 * <ul>
 * <li>Resolves the session owner GameServerLink for the requested account.</li>
 * <li>Validates the provided current password hash against the stored value.</li>
 * <li>Updates the stored hash and replies with the result.</li>
 * </ul>
 * @author BazookaRpm
 */
@Slf4j
public class ChangePassword extends SimpleReadablePacket {

    // Password Hashing.
    private static final String PASSWORD_DIGEST_ALGORITHM = "SHA";

    // Response Messages.
    private static final String MSG_INVALID_PASSWORD_DATA = "Invalid password data! Try again.";
    private static final String MSG_PASSWORD_CHANGED = "You have successfully changed your password!";
    private static final String MSG_PASSWORD_CHANGE_FAILED = "The password change was unsuccessful!";
    private static final String MSG_CURRENT_PASSWORD_MISMATCH =
            "The typed current password doesn't match with your current one.";

    /**
     * Reads the password change request and updates the account password when validation succeeds.<br>
     * Sends the result back to the owning game server thread.
     * @param decrypt
     */
    public ChangePassword(byte[] decrypt, GameServerLink server) {
        super(decrypt);

        final int packetId = readByte();
        final UUID accountId = new UUID(readLong(), readLong());
        final String playerName = readString();
        final String currentPassword = readString();
        final String newPassword = readString();

        // A game server may only change the password of an account it holds.
        final GameServerLink ownerThread = server.hasAccountOnGameServer(accountId) ? server : null;

        if (ownerThread == null) {
            if (log.isTraceEnabled()) {
                log.trace(
                        "Ignored ChangePassword packetId={} for account '{}' requested by player '{}'.",
                        packetId,
                        accountId,
                        playerName);
            }
            return;
        }

        if (StringUtils.isEmpty(currentPassword) || StringUtils.isEmpty(newPassword)) {
            ownerThread.changePasswordResponse(playerName, MSG_INVALID_PASSWORD_DATA);
            return;
        }

        try {
            final MessageDigest digest = MessageDigest.getInstance(PASSWORD_DIGEST_ALGORITHM);
            final Encoder encoder = Base64.getEncoder();

            final String currentHash =
                    encoder.encodeToString(digest.digest(currentPassword.getBytes(StandardCharsets.UTF_8)));
            final String newHash = encoder.encodeToString(digest.digest(newPassword.getBytes(StandardCharsets.UTF_8)));

            final LoginController loginController = LoginController.getInstance();
            if (loginController.changePassword(accountId, currentHash, newHash)) {
                ownerThread.changePasswordResponse(playerName, MSG_PASSWORD_CHANGED);
                return;
            }

            if (currentHash.equals(loginController.getPasswordHash(accountId))) {
                log.warn(
                        "Password change failed for account '{}' requested by player '{}' on GS {} (0 rows updated).",
                        accountId,
                        playerName,
                        server.getServerId());
                ownerThread.changePasswordResponse(playerName, MSG_PASSWORD_CHANGE_FAILED);
            } else {
                ownerThread.changePasswordResponse(playerName, MSG_CURRENT_PASSWORD_MISMATCH);
            }
        } catch (Exception e) {
            log.warn(
                    "Error while changing password for account '{}' requested by player '{}' on GS {} (packetId={}).",
                    accountId,
                    playerName,
                    server.getServerId(),
                    packetId,
                    e);
        }
    }
}
