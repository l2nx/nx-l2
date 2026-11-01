/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.util.Objects;

/** A failed condition: everything written is rolled back and the caller gets {@link ValueOutcome.Refused}. */
public final class ValueRefusal extends Exception {
    private final Enum<?> reason;
    private final String param;

    public ValueRefusal(Enum<?> reason) {
        this(reason, null);
    }

    public ValueRefusal(Enum<?> reason, String param) {
        super(reason.name(), null, false, false);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.param = param;
    }

    public Enum<?> reason() {
        return reason;
    }

    public String param() {
        return param;
    }
}
