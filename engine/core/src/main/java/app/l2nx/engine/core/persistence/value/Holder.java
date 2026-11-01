/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/** A place values move to or from: the inventory or warehouse of a character, or a mail. */
@Builder(toBuilder = true)
public record Holder(HolderType type, UUID id) {
    public Holder {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public static Holder inventory(UUID characterId) {
        return new Holder(HolderType.INVENTORY, characterId);
    }

    public static Holder warehouse(UUID characterId) {
        return new Holder(HolderType.WAREHOUSE, characterId);
    }

    public static Holder mail(UUID mailId) {
        return new Holder(HolderType.MAIL, mailId);
    }
}
