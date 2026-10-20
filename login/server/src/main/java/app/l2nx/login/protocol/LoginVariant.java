/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol;

/**
 * The login server speaks first, so a port names its variant; the client or launcher points at the port of its
 * variant.
 */
public enum LoginVariant {
    /** High Five login protocol, revision 0xc621 in {@code Init}. */
    C621("c621");

    private final String id;

    LoginVariant(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
