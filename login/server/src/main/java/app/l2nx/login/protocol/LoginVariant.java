/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol;

import app.l2nx.protocol.login.c621.crypt.Credentials;
import java.util.Set;

/**
 * The login server speaks first, so a port names its variant.
 */
public enum LoginVariant {
    C621("c621", Set.of("high-five")) {
        @Override
        public boolean carriesPassword(String password) {
            return Credentials.fitsPassword(password);
        }
    };

    private final String id;
    private final Set<String> chronicles;

    LoginVariant(String id, Set<String> chronicles) {
        this.id = id;
        this.chronicles = chronicles;
    }

    public String id() {
        return id;
    }

    public Set<String> chronicles() {
        return chronicles;
    }

    /** Whether a client of this variant can log in with the password: its credentials block carries it intact. */
    public abstract boolean carriesPassword(String password);
}
