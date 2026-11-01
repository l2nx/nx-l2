/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

/** Who holds a value on one side of a ledger row; {@code holder_id} is a character for the first two, a mail for the last. */
public enum HolderType {
    INVENTORY,
    WAREHOUSE,
    MAIL
}
