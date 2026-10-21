/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.metrics.model;

public enum DropReason {
    BANNED("banned"),
    CONNECTIONS_PER_IP("connections-per-ip"),
    ACCEPT_RATE("accept-rate"),
    MAX_CONNECTIONS("max-connections"),
    UNKNOWN_OPCODE("unknown-opcode"),
    WRONG_STATE("wrong-state"),
    UNAUTHENTICATED("unauthenticated"),
    /** A known opcode the server deliberately does not handle. */
    IGNORED("ignored"),
    /** A frame sent to a connection that is closing. */
    CLOSED("closed"),
    UNHANDLED("unhandled"),
    NO_SESSION("no-session");

    private final String tag;

    DropReason(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
