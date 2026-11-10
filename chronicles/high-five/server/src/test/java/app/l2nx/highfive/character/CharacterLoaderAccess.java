/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character;

import java.util.UUID;

public final class CharacterLoaderAccess {
    private CharacterLoaderAccess() {}

    public static StoredCharacter readAndMarkOnline(CharacterLoader loader, UUID characterId) {
        return loader.readAndMarkOnline(characterId);
    }

    public static void markOffline(CharacterLoader loader, UUID characterId) {
        loader.markOffline(characterId);
    }
}
