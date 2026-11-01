/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.mail.WorldMail;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private static final UUID CHARACTER_ID = EntityIds.next();

    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final CharacterSubclassRepository subclasses = mock(CharacterSubclassRepository.class);
    private final CharacterSkillRepository skills = mock(CharacterSkillRepository.class);
    private final CharacterSkillSaveRepository skillSaves = mock(CharacterSkillSaveRepository.class);
    private final CharacterInstanceTimeRepository instanceTimes = mock(CharacterInstanceTimeRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final WorldMail mail = mock(WorldMail.class);
    private final IdManager ids = mock(IdManager.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);

    private final List<Boolean> answers = new ArrayList<>();

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
        when(ids.findId(OBJECT_ID)).thenReturn(Optional.of(CHARACTER_ID));
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
        void deleteCharacter_shouldSettleMailThenDeleteRowsThenReleaseHandles() {
            final int itemHandle = OBJECT_ID + 1;
            when(items.deleteByOwner(OBJECT_ID)).thenReturn(List.of(itemHandle));
            settleMail(true);

            manager.deleteCharacter(OBJECT_ID, answers::add);

            final InOrder order = inOrder(mail, items, characters, ids);
            order.verify(mail).onCharacterDeleted(eq(CHARACTER_ID), any());
            order.verify(items).deleteByOwner(OBJECT_ID);
            order.verify(characters).delete(OBJECT_ID);
            order.verify(ids).releaseId(itemHandle);
            order.verify(ids).releaseId(OBJECT_ID);
            assertThat(answers).containsExactly(true);
        }

        @Test
        void deleteCharacter_shouldKeepCharacter_whenMailIsNotSettled() {
            settleMail(false);

            manager.deleteCharacter(OBJECT_ID, answers::add);

            verify(characters, never()).delete(anyInt());
            verify(ids, never()).releaseId(anyInt());
            assertThat(answers).containsExactly(false);
        }

        @Test
        void deleteCharacter_shouldKeepHandles_whenRowDeletionFails() {
            settleMail(true);
            doThrow(new DataIntegrityViolationException("character"))
                    .when(characters)
                    .delete(OBJECT_ID);

            manager.deleteCharacter(OBJECT_ID, answers::add);

            verify(ids, never()).releaseId(anyInt());
            assertThat(answers).containsExactly(false);
        }

        @Test
        void deleteCharacter_shouldSettleMailOnce_whenDeletionIsUnderWay() {
            manager.deleteCharacter(OBJECT_ID, answers::add);
            manager.deleteCharacter(OBJECT_ID, answers::add);

            verify(mail, times(1)).onCharacterDeleted(eq(CHARACTER_ID), any());
            assertThat(answers).isEmpty();
        }

        @Test
        void deleteCharacter_shouldRetry_whenPreviousAttemptWasRefused() {
            settleMail(false);
            manager.deleteCharacter(OBJECT_ID, answers::add);
            settleMail(true);

            manager.deleteCharacter(OBJECT_ID, answers::add);

            verify(characters).delete(OBJECT_ID);
            assertThat(answers).containsExactly(false, true);
        }

        @Test
        void deleteCharacter_shouldAnswerFalse_whenCharacterWasNeverStored() {
            when(ids.findId(OBJECT_ID)).thenReturn(Optional.empty());

            manager.deleteCharacter(OBJECT_ID, answers::add);

            verify(mail, never()).onCharacterDeleted(any(), any());
            assertThat(answers).containsExactly(false);
        }

        private void settleMail(boolean settled) {
            doAnswer(invocation -> {
                        invocation.<Consumer<Boolean>>getArgument(1).accept(settled);
                        return null;
                    })
                    .when(mail)
                    .onCharacterDeleted(eq(CHARACTER_ID), any());
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
