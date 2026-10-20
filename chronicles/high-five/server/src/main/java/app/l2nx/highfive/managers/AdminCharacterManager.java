/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.repository.character.CharacterQuestRepository;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.CharacterShortcutRepository;
import app.l2nx.highfive.repository.character.model.CharacterAccess;
import app.l2nx.highfive.repository.character.model.CharacterQuestVariable;
import app.l2nx.highfive.repository.item.ItemRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The boolean results tell whether a character of that name exists.
 */
@Component
public class AdminCharacterManager {
    private static final String QUEST_STATE_VARIABLE = "<state>";

    private static volatile AdminCharacterManager instance;

    private final CharacterRepository characterRepository;
    private final CharacterShortcutRepository shortcutRepository;
    private final CharacterQuestRepository questRepository;
    private final ItemRepository itemRepository;
    private final TransactionTemplate transactionTemplate;

    public AdminCharacterManager(
            CharacterRepository characterRepository,
            CharacterShortcutRepository shortcutRepository,
            CharacterQuestRepository questRepository,
            ItemRepository itemRepository,
            TransactionTemplate transactionTemplate) {
        this.characterRepository = characterRepository;
        this.shortcutRepository = shortcutRepository;
        this.questRepository = questRepository;
        this.itemRepository = itemRepository;
        this.transactionTemplate = transactionTemplate;
        instance = this;
    }

    public boolean moveCharacter(String name, int x, int y, int z) {
        return characterRepository.updatePositionByName(name, x, y, z);
    }

    public boolean clearClanJoinPenalty(String name) {
        return characterRepository.clearClanJoinPenaltyByName(name);
    }

    public boolean clearClanCreatePenalty(String name) {
        return characterRepository.clearClanCreatePenaltyByName(name);
    }

    /**
     * Moves a stuck character to the given location, drops its shortcuts and takes off its equipment.
     */
    public boolean repairCharacter(String name, int x, int y, int z) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            final Optional<CharacterAccess> character = characterRepository.findAccessByName(name);
            if (character.isEmpty()) {
                return false;
            }

            final int objectId = character.get().objectId();
            characterRepository.updatePositionByName(name, x, y, z);
            shortcutRepository.deleteAll(objectId);
            itemRepository.unequipAll(objectId);
            return true;
        }));
    }

    /**
     * @return the names of the stored quests of the character, in name order
     */
    public List<String> findQuestNames(int objectId) {
        return questRepository.findStates(objectId).stream()
                .map(CharacterQuestVariable::questName)
                .toList();
    }

    public List<String> findQuestNames(int objectId, String state) {
        return questRepository.findStates(objectId, state).stream()
                .map(CharacterQuestVariable::questName)
                .toList();
    }

    /**
     * @return the stored variables of the quest by name, its state excluded; a value may be {@code null}
     */
    public Map<String, String> findQuestVariables(int objectId, String questName) {
        final Map<String, String> variables = new LinkedHashMap<>();
        for (CharacterQuestVariable variable : questRepository.findVariables(objectId, questName)) {
            if (!QUEST_STATE_VARIABLE.equals(variable.name())) {
                variables.put(variable.name(), variable.content());
            }
        }
        return variables;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static AdminCharacterManager getInstance() {
        final AdminCharacterManager current = instance;
        if (current == null) {
            throw new IllegalStateException("AdminCharacterManager is used before the Spring context created it.");
        }
        return current;
    }
}
