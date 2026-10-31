/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gameserver.model;

import lombok.Builder;

public sealed interface Registration {
    record Accepted(GameServerInfo server) implements Registration {}

    @Builder(toBuilder = true)
    record Refused(Reason reason, String detail) implements Registration {}

    enum Reason {
        NOT_ACCEPTED,
        ID_TAKEN,
        HEX_ID_MISMATCH,
        NO_FREE_ID
    }
}
