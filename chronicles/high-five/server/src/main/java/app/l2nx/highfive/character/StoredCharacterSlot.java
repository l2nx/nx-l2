/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character;

import app.l2nx.highfive.repository.character.model.CharacterSlotRow;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import app.l2nx.highfive.repository.item.model.StoredItem;
import java.util.List;
import lombok.Builder;

/** @param activeSubclass {@code null} while the base class is active */
@Builder(toBuilder = true)
public record StoredCharacterSlot(
        CharacterSlotRow character, List<StoredItem> paperdoll, CharacterSubclass activeSubclass) {
    public StoredCharacterSlot {
        paperdoll = List.copyOf(paperdoll);
    }
}
