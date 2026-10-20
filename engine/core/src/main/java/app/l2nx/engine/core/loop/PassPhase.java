/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import java.util.Locale;

public enum PassPhase {
    INBOX,
    DEADLINES,
    SYSTEMS,
    SIDE_EFFECTS,
    REPLICATION,
    FLUSH;

    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
