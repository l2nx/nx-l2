/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.repository.character.CharacterQuestRepository;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.CharacterShortcutRepository;
import app.l2nx.highfive.repository.character.model.CharacterAccess;
import app.l2nx.highfive.repository.character.model.CharacterQuestVariable;
import app.l2nx.highfive.repository.item.ItemRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class AdminCharacterManagerTest {
    private static final String NAME = "Stuck";
    private static final int OBJECT_ID = 268_435_457;
    private static final String QUEST = "Q00001_Letters";

    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final CharacterShortcutRepository shortcuts = mock(CharacterShortcutRepository.class);
    private final CharacterQuestRepository quests = mock(CharacterQuestRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);

    private AdminCharacterManager manager;

    @BeforeEach
    void createManager() {
        when(transactions.execute(any()))
                .thenAnswer(invocation ->
                        invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        manager = new AdminCharacterManager(characters, shortcuts, quests, items, transactions);
    }

    @Nested
    class RepairCharacter {
        @Test
        void repairCharacter_shouldMoveAndClearShortcutsAndUnequip_whenCharacterExists() {
            when(characters.findAccessByName(NAME))
                    .thenReturn(Optional.of(CharacterAccess.builder()
                            .objectId(OBJECT_ID)
                            .name(NAME)
                            .build()));

            final boolean actual = manager.repairCharacter(NAME, 1, 2, 3);

            assertThat(actual).isTrue();
            verify(characters).updatePositionByName(NAME, 1, 2, 3);
            verify(shortcuts).deleteAll(OBJECT_ID);
            verify(items).unequipAll(OBJECT_ID);
        }

        @Test
        void repairCharacter_shouldWriteNothing_whenCharacterIsMissing() {
            when(characters.findAccessByName(NAME)).thenReturn(Optional.empty());

            final boolean actual = manager.repairCharacter(NAME, 1, 2, 3);

            assertThat(actual).isFalse();
            verify(characters, never()).updatePositionByName(anyString(), anyInt(), anyInt(), anyInt());
            verifyNoInteractions(shortcuts, items);
        }
    }

    @Nested
    class FindQuestVariables {
        @Test
        void findQuestVariables_shouldSkipState_andKeepNullValues() {
            when(quests.findVariables(OBJECT_ID, QUEST))
                    .thenReturn(List.of(variable("<state>", "STARTED"), variable("cond", "2"), variable("note", null)));

            final Map<String, String> actual = manager.findQuestVariables(OBJECT_ID, QUEST);

            assertThat(actual)
                    .containsOnlyKeys("cond", "note")
                    .containsEntry("cond", "2")
                    .containsEntry("note", null);
        }
    }

    private static CharacterQuestVariable variable(String name, String content) {
        return CharacterQuestVariable.builder()
                .questName(QUEST)
                .name(name)
                .content(content)
                .build();
    }
}
