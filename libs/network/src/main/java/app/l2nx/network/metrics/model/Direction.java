/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.metrics.model;

public enum Direction {
    IN("in"),
    OUT("out");

    private final String tag;

    Direction(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
