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
package app.l2nx.login.account;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * @param accessLevel -1 for a banned account
 * @author BazookaRpm
 */
@Builder(toBuilder = true)
public record AccountInfo(UUID id, String login, String passwordHash, int accessLevel, int lastServer) {
    public AccountInfo {
        Objects.requireNonNull(id, "id");
        login = requireNonEmpty(login, "login").toLowerCase(Locale.ROOT);
        passwordHash = requireNonEmpty(passwordHash, "passwordHash");
    }

    private static String requireNonEmpty(String value, String argumentName) {
        if (Objects.requireNonNull(value, argumentName).isEmpty()) {
            throw new IllegalArgumentException(argumentName);
        }
        return value;
    }

    @Override
    public String toString() {
        return "AccountInfo[id=" + id + ", login=" + login + ", accessLevel=" + accessLevel + ", lastServer="
                + lastServer + "]";
    }
}
