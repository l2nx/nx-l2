/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.sql;

import app.l2nx.highfive.repository.character.CharacterRepository;
import org.springframework.stereotype.Component;

/**
 * Changes the stored access level of a character that is not in the world; an online character changes it through
 * {@code Player.setAccessLevel}.
 */
@Component
public class CharacterAccessTable {
    private static volatile CharacterAccessTable instance;

    private final CharacterRepository characters;

    public CharacterAccessTable(CharacterRepository characters) {
        this.characters = characters;
        instance = this;
    }

    public boolean setAccessLevel(String characterName, int accessLevel) {
        if (!characters.updateAccessLevelByName(characterName, accessLevel)) {
            return false;
        }

        // CharInfoTable caches access levels: dropping the entry makes its next read load the new level.
        final CharInfoTable characterInfo = CharInfoTable.getInstance();
        final int objectId = characterInfo.getIdByName(characterName);
        if (objectId > 0) {
            characterInfo.removeName(objectId);
        }

        return true;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CharacterAccessTable getInstance() {
        final CharacterAccessTable current = instance;
        if (current == null) {
            throw new IllegalStateException("CharacterAccessTable is used before the Spring context created it.");
        }
        return current;
    }
}
