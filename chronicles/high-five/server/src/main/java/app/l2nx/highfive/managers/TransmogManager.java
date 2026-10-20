/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.repository.character.CharacterTransmogRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TransmogManager {
    private static volatile TransmogManager instance;

    private final CharacterTransmogRepository transmogRepository;

    public TransmogManager(CharacterTransmogRepository transmogRepository) {
        this.transmogRepository = transmogRepository;
        instance = this;
    }

    /**
     * @return the item template ids, ascending
     */
    public List<Integer> findByCharacter(int characterId) {
        return transmogRepository.findByCharacter(characterId);
    }

    /**
     * @return the item template ids, ascending
     */
    public List<Integer> findByAccount(UUID accountId) {
        return transmogRepository.findByAccount(accountId);
    }

    /**
     * Stores the unlocks; the ones already stored stay as they are.
     */
    public void addForCharacter(int characterId, Collection<Integer> itemTemplateIds) {
        transmogRepository.addForCharacter(characterId, itemTemplateIds);
    }

    /**
     * Stores the unlocks; the ones already stored stay as they are.
     */
    public void addForAccount(UUID accountId, Collection<Integer> itemTemplateIds) {
        transmogRepository.addForAccount(accountId, itemTemplateIds);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static TransmogManager getInstance() {
        final TransmogManager current = instance;
        if (current == null) {
            throw new IllegalStateException("TransmogManager is used before the Spring context created it.");
        }
        return current;
    }
}
