/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import java.util.Objects;

public sealed interface ReserveResult<T> {
    record Reserved<T>(T value) implements ReserveResult<T> {
        public Reserved {
            Objects.requireNonNull(value, "value");
        }
    }

    record Refused<T>(MailRefusal reason) implements ReserveResult<T> {
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
