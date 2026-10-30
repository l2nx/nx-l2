/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** The form {@code accounts.password} stores: Base64 of the password's SHA-1 digest, as the Mobius accounts carry. */
public final class PasswordHash {
    private PasswordHash() {}

    public static String of(String password) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA").digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-1.", e);
        }
    }
}
