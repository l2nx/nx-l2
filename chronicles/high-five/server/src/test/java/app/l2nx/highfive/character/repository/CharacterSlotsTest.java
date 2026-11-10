/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.character.StoredCharacterSlot;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.CharacterSubclassRepository;
import app.l2nx.highfive.repository.character.model.CharacterSlotRow;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.item.model.StoredItem;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class CharacterSlotsTest {
    private static final int SWORD = 2;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final CharacterSubclassRepository subclasses = mock(CharacterSubclassRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final ItemAugmentationRepository augmentations = mock(ItemAugmentationRepository.class);
    private final CharacterSlots slots = new CharacterSlots(
            characters, subclasses, items, augmentations, mock(ItemVariableRepository.class), registry);
    private final UUID accountId = EntityIds.next();
    private final UUID characterId = EntityIds.next();
    private final UUID clanId = EntityIds.next();
    private final UUID swordId = EntityIds.next();
    private final StoredItem sword = StoredItem.builder()
            .id(swordId)
            .itemTemplateId(SWORD)
            .location(ItemLocation.PAPERDOLL)
            .locationSlot(Inventory.PAPERDOLL_RHAND)
            .enchantLevel(7)
            .build();

    @Nested
    class FindByAccount {
        @Test
        void findByAccount_shouldReadTheWornItemsWithTheirAugmentation_andTheActiveSubclass() {
            final CharacterSubclass active = new CharacterSubclass(1, 12, 500, 50, 40);
            when(characters.findSlotsByAccount(accountId))
                    .thenReturn(List.of(CharacterSlotRow.builder()
                            .id(characterId)
                            .classId(12)
                            .baseClassId(10)
                            .build()));
            when(items.findStored(characterId, ItemLocation.PAPERDOLL)).thenReturn(List.of(sword));
            when(augmentations.findByOwner(characterId)).thenReturn(Map.of(swordId, 77));
            when(subclasses.findAll(characterId)).thenReturn(List.of(new CharacterSubclass(2, 30, 1, 1, 1), active));

            final List<StoredCharacterSlot> actual = slots.findByAccount(accountId);

            assertThat(actual).singleElement().satisfies(slot -> {
                assertThat(slot.activeSubclass()).isEqualTo(active);
                assertThat(slot.paperdoll())
                        .singleElement()
                        .extracting(StoredItem::augmentationId)
                        .isEqualTo(77);
            });
            assertThat(registry.findHandle(characterId))
                    .as("no handle off the loop")
                    .isEqualTo(OptionalInt.empty());
            assertThat(registry.findHandle(swordId)).isEqualTo(OptionalInt.empty());
        }

        @Test
        void findByAccount_shouldReadNoSubclass_whenTheBaseClassIsActive() {
            when(characters.findSlotsByAccount(accountId))
                    .thenReturn(List.of(CharacterSlotRow.builder()
                            .id(characterId)
                            .classId(10)
                            .baseClassId(10)
                            .build()));

            final List<StoredCharacterSlot> actual = slots.findByAccount(accountId);

            assertThat(actual)
                    .singleElement()
                    .extracting(StoredCharacterSlot::activeSubclass)
                    .isNull();
            verify(subclasses, never()).findAll(characterId);
            verify(augmentations, never()).findByOwner(characterId);
        }
    }

    @Nested
    class Summarize {
        @Test
        void summarize_shouldAllocateTheHandlesAndShowTheActiveSubclass() {
            final StoredCharacterSlot slot = StoredCharacterSlot.builder()
                    .character(CharacterSlotRow.builder()
                            .id(characterId)
                            .clanId(clanId)
                            .classId(12)
                            .baseClassId(10)
                            .level(80)
                            .build())
                    .paperdoll(List.of(sword.toBuilder().augmentationId(77).build()))
                    .activeSubclass(new CharacterSubclass(1, 12, 500, 50, 40))
                    .build();

            final CharacterSummary actual;
            try (MockedStatic<ItemData> itemData = mockStatic(ItemData.class)) {
                itemData.when(ItemData::getInstance).thenReturn(mock(ItemData.class));
                actual = WorldLoopExtension.callInPass(() -> slots.summarize(slot));
            }

            assertThat(actual.objectId()).isEqualTo(registry.handleOf(characterId));
            assertThat(actual.clanId()).isEqualTo(registry.handleOf(clanId));
            assertThat(actual.level()).isEqualTo(40);
            assertThat(actual.exp()).isEqualTo(500);
            assertThat(actual.augmentationId()).isEqualTo(77);
            assertThat(actual.paperdoll()).hasSize(Inventory.PAPERDOLL_TOTALSLOTS);
            assertThat(actual.paperdoll().get(Inventory.PAPERDOLL_RHAND))
                    .isEqualTo(CharacterSummary.PaperdollSlot.builder()
                            .objectId(registry.handleOf(swordId))
                            .itemId(SWORD)
                            .enchantLevel(7)
                            .build());
            assertThat(actual.paperdollItemId(Inventory.PAPERDOLL_HEAD)).isZero();
        }

        @Test
        void summarize_shouldShowNoClan_whenTheCharacterHasNone() {
            final StoredCharacterSlot slot = StoredCharacterSlot.builder()
                    .character(CharacterSlotRow.builder().id(characterId).build())
                    .paperdoll(List.of())
                    .build();

            final CharacterSummary actual = WorldLoopExtension.callInPass(() -> slots.summarize(slot));

            assertThat(actual.clanId()).isZero();
            assertThat(actual.augmentationId()).isZero();
        }
    }
}
