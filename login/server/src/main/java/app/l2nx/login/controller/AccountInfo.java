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
package app.l2nx.login.controller;

import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Immutable snapshot of login account information used by the login server.<br>
 * Encapsulates the account login name, password hash, access level and last selected server.
 * <ul>
 * <li>Normalizes the login name to lower case.</li>
 * <li>Enforces non-null and non-empty login and password hash.</li>
 * </ul>
 * @author BazookaRpm
 */
public class AccountInfo {
    @Getter
    private final UUID id;

    // Account login name (normalized to lower case).
    @Getter
    private final String login;

    // Encoded password hash associated with this account.
    private final String passwordHash;

    // Access level granted to this account.
    @Getter
    private final int accessLevel;

    // Identifier of the last game server used by this account.
    @Getter
    private final int lastServer;

    /**
     * Creates a new immutable account information instance.
     * @param login the account login name
     * @param passwordHash the encoded password hash
     * @param accessLevel the account access level
     * @param lastServer the last server identifier used by this account
     * @throws NullPointerException if {@code login} or {@code passwordHash} is {@code null}
     * @throws IllegalArgumentException if {@code login} or {@code passwordHash} is empty
     */
    public AccountInfo(UUID id, String login, String passwordHash, int accessLevel, int lastServer) {
        this.id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(passwordHash, "passwordHash");

        this.login = requireNonEmpty(login, "login").toLowerCase();
        this.passwordHash = requireNonEmpty(passwordHash, "passwordHash");
        this.accessLevel = accessLevel;
        this.lastServer = lastServer;
    }

    /**
     * Ensures that the provided string is not empty.
     * @param value the value to validate
     * @param argumentName the argument name used in exception messages
     * @return the validated value
     * @throws IllegalArgumentException if {@code value} is empty
     */
    private static String requireNonEmpty(String value, String argumentName) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(argumentName);
        }
        return value;
    }

    /**
     * Verifies that the provided password hash matches the stored hash.
     * @param passwordHash the password hash to verify
     * @return {@code true} if the hash matches, {@code false} otherwise
     */
    public boolean checkPassHash(String passwordHash) {
        return this.passwordHash.equals(passwordHash);
    }

    /**
     * Returns a string representation of this account without exposing the password hash.
     * @return a string representation of this account info
     */
    @Override
    public String toString() {
        return "AccountInfo[login=" + login + ", accessLevel=" + accessLevel + ", lastServer=" + lastServer + "]";
    }
}
