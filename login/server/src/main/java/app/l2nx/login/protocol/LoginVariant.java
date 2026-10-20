/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol;

import java.util.Set;

/**
 * The login server speaks first, so a port names its variant.
 */
public enum LoginVariant {
    C621("c621", Set.of("high-five"));

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
}
