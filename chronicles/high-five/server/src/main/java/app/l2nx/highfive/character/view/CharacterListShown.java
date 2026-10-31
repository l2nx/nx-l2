/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record CharacterListShown(String login, int playOk1, List<CharacterSummary> characters, int activeSlot)
        implements ViewEvent {
    public static final int NO_SLOT = -1;

    public CharacterListShown {
        characters = List.copyOf(characters);
    }
}
