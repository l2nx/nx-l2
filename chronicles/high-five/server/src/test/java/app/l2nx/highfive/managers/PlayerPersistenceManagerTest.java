/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.repository.character.CharacterHennaRepository;
import app.l2nx.highfive.repository.character.CharacterInstanceTimeRepository;
import app.l2nx.highfive.repository.character.CharacterItemReuseRepository;
import app.l2nx.highfive.repository.character.CharacterMacroRepository;
import app.l2nx.highfive.repository.character.CharacterPremiumItemRepository;
import app.l2nx.highfive.repository.character.CharacterQuestRepository;
import app.l2nx.highfive.repository.character.CharacterRecipeRepository;
import app.l2nx.highfive.repository.character.CharacterRecipeShopRepository;
import app.l2nx.highfive.repository.character.CharacterRecommendationRepository;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.CharacterShortcutRepository;
import app.l2nx.highfive.repository.character.CharacterSkillRepository;
import app.l2nx.highfive.repository.character.CharacterSkillSaveRepository;
import app.l2nx.highfive.repository.character.CharacterSubclassRepository;
import app.l2nx.highfive.repository.character.CharacterTeleportBookmarkRepository;
import app.l2nx.highfive.repository.character.CharacterVariableRepository;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.character.model.CharacterSkill;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import app.l2nx.highfive.repository.item.ItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class PlayerPersistenceManagerTest {
    private static final int OBJECT_ID = 268_435_457;

    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final CharacterSubclassRepository subclasses = mock(CharacterSubclassRepository.class);
    private final CharacterSkillRepository skills = mock(CharacterSkillRepository.class);
    private final CharacterSkillSaveRepository skillSaves = mock(CharacterSkillSaveRepository.class);
    private final CharacterInstanceTimeRepository instanceTimes = mock(CharacterInstanceTimeRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final MailManager mail = mock(MailManager.class);
    private final IdManager ids = mock(IdManager.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);

    private PlayerPersistenceManager manager;

    @BeforeEach
    void createManager() {
        doAnswer(invocation -> {
                    invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
                    return null;
                })
                .when(transactions)
                .executeWithoutResult(any());
        doAnswer(invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null))
                .when(transactions)
                .execute(any());
        manager = new PlayerPersistenceManager(
                characters,
                subclasses,
                skills,
                skillSaves,
                mock(CharacterItemReuseRepository.class),
                mock(CharacterHennaRepository.class),
                mock(CharacterTeleportBookmarkRepository.class),
                mock(CharacterRecipeRepository.class),
                mock(CharacterRecipeShopRepository.class),
                mock(CharacterPremiumItemRepository.class),
                mock(CharacterRecommendationRepository.class),
                mock(CharacterMacroRepository.class),
                mock(CharacterShortcutRepository.class),
                mock(CharacterQuestRepository.class),
                mock(CharacterVariableRepository.class),
                instanceTimes,
                items,
                mail,
                ids,
                transactions);
    }

    @Nested
    class DeleteCharacter {
        @Test
        void deleteCharacter_shouldReturnMailThenDeleteRowsThenReleaseHandles() {
            final int itemHandle = OBJECT_ID + 1;
            when(items.deleteByOwner(OBJECT_ID)).thenReturn(List.of(itemHandle));

            manager.deleteCharacter(OBJECT_ID);

            final InOrder order = inOrder(mail, items, characters, ids);
            order.verify(mail).deleteMailOfCharacter(OBJECT_ID);
            order.verify(items).deleteByOwner(OBJECT_ID);
            order.verify(characters).delete(OBJECT_ID);
            order.verify(ids).releaseId(itemHandle);
            order.verify(ids).releaseId(OBJECT_ID);
        }

        @Test
        void deleteCharacter_shouldKeepHandle_whenDeletionFails() {
            doThrow(new DataIntegrityViolationException("mail")).when(mail).deleteMailOfCharacter(OBJECT_ID);

            assertThatThrownBy(() -> manager.deleteCharacter(OBJECT_ID))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(characters, never()).delete(anyInt());
            verify(ids, never()).releaseId(anyInt());
        }
    }

    @Nested
    class CleanUpAtStart {
        @Test
        void cleanUpAtStart_shouldResetOnlineStatusAndDropExpiredTimes() {
            final Instant now = Instant.parse("2026-10-20T10:00:00Z");

            manager.cleanUpAtStart(now);

            verify(characters).resetOnlineStatus();
            verify(skillSaves).deleteExpiredReuse(now);
            verify(instanceTimes).deleteExpired(now);
        }
    }

    @Nested
    class SaveCharacter {
        @Test
        void saveCharacter_shouldStoreRowOnly_whenCharacterHasNoSubclasses() {
            final CharacterRow row = CharacterRow.builder().objectId(OBJECT_ID).build();

            manager.saveCharacter(row, List.of());

            verify(characters).update(row);
            verify(subclasses, never()).updateAll(anyInt(), anyList());
        }

        @Test
        void saveCharacter_shouldStoreRowAndSubclasses_whenCharacterHasSubclasses() {
            final CharacterRow row = CharacterRow.builder().objectId(OBJECT_ID).build();
            final List<CharacterSubclass> stored = List.of(CharacterSubclass.builder()
                    .classIndex(1)
                    .classId(2)
                    .level(40)
                    .build());

            manager.saveCharacter(row, stored);

            verify(characters).update(row);
            verify(subclasses).updateAll(OBJECT_ID, stored);
        }
    }

    @Nested
    class InsertSubclass {
        @Test
        void insertSubclass_shouldStoreSubclassBeforeItsSkills() {
            final CharacterSubclass subclass = CharacterSubclass.builder()
                    .classIndex(1)
                    .classId(2)
                    .level(40)
                    .build();
            final List<CharacterSkill> starting =
                    List.of(CharacterSkill.builder().skillId(3).skillLevel(1).build());

            manager.insertSubclass(OBJECT_ID, subclass, starting);

            final InOrder order = inOrder(subclasses, skills);
            order.verify(subclasses).insert(OBJECT_ID, subclass);
            order.verify(skills).saveAll(OBJECT_ID, 1, starting);
        }
    }
}
