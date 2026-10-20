/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

public enum CloseReason {
    IDLE("idle"),
    TIMEOUT("timeout"),
    FLOOD("flood"),
    SLOW("slow"),
    OUTBOUND_LIMIT("outbound-limit"),
    ERROR("error");

    private final String tag;

    CloseReason(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
