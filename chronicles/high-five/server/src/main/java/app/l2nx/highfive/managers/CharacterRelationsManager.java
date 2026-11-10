/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.model.CharacterSummaryRow;
import app.l2nx.highfive.repository.social.CharacterContactRepository;
import app.l2nx.highfive.repository.social.CharacterRelationRepository;
import app.l2nx.highfive.repository.social.model.RelationType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CharacterRelationsManager {
    private static volatile CharacterRelationsManager instance;

    private final CharacterRelationRepository relationRepository;
    private final CharacterContactRepository contactRepository;
    private final CharacterRepository characterRepository;

    public CharacterRelationsManager(
            CharacterRelationRepository relationRepository,
            CharacterContactRepository contactRepository,
            CharacterRepository characterRepository) {
        this.relationRepository = relationRepository;
        this.contactRepository = contactRepository;
        this.characterRepository = characterRepository;
        instance = this;
    }

    public List<Integer> findBlockedIds(int characterId) {
        return relationRepository.findTargetIds(characterId, RelationType.BLOCK);
    }

    public void block(int characterId, int targetId) {
        relationRepository.add(characterId, targetId, RelationType.BLOCK);
    }

    public void unblock(int characterId, int targetId) {
        relationRepository.remove(characterId, targetId, RelationType.BLOCK);
    }

    public void addFriendship(int characterId, int friendId) {
        relationRepository.addFriendship(characterId, friendId);
    }

    /**
     * Removes each character from the other's friend list; blocks stay.
     */
    public void removeFriendship(int characterId, int friendId) {
        relationRepository.removeFriendship(characterId, friendId);
    }

    public Optional<CharacterSummaryRow> findFriendSummary(int friendId) {
        return characterRepository.findSummary(friendId);
    }

    public void addContact(int characterId, int contactId) {
        contactRepository.add(characterId, contactId);
    }

    public void removeContact(int characterId, int contactId) {
        contactRepository.remove(characterId, contactId);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CharacterRelationsManager getInstance() {
        final CharacterRelationsManager current = instance;
        if (current == null) {
            throw new IllegalStateException("CharacterRelationsManager is used before the Spring context created it.");
        }
        return current;
    }
}
