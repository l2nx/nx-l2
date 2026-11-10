/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.repository;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.handleOrZero;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.character.StoredCharacterSlot;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.config.custom.TransmogConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.mechanics.variables.ItemVariables;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.CharacterSubclassRepository;
import app.l2nx.highfive.repository.character.model.CharacterSlotRow;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import app.l2nx.highfive.repository.character.model.Faction;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.item.model.StoredItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterSlots {
    private static final CharacterSummary.PaperdollSlot EMPTY = CharacterSummary.PaperdollSlot.builder()
            .objectId(0)
            .itemId(0)
            .enchantLevel(0)
            .build();

    private final CharacterRepository characters;
    private final CharacterSubclassRepository subclasses;
    private final ItemRepository items;
    private final ItemAugmentationRepository augmentations;
    private final ItemVariableRepository itemVariables;
    private final IdentityRegistry registry;

    public List<StoredCharacterSlot> findByAccount(UUID accountId) {
        return characters.findSlotsByAccount(accountId).stream().map(this::read).toList();
    }

    private StoredCharacterSlot read(CharacterSlotRow row) {
        final UUID characterId = row.id();
        return StoredCharacterSlot.builder()
                .character(row)
                .paperdoll(paperdoll(characterId))
                .activeSubclass(
                        row.baseClassId() == row.classId()
                                ? null
                                : subclasses.findAll(characterId).stream()
                                        .filter(subclass -> subclass.classId() == row.classId())
                                        .findFirst()
                                        .orElse(null))
                .build();
    }

    private List<StoredItem> paperdoll(UUID characterId) {
        final List<StoredItem> worn = items.findStored(characterId, ItemLocation.PAPERDOLL);
        if (worn.isEmpty()) {
            return worn;
        }
        final Map<UUID, Integer> augmentationIds = augmentations.findByOwner(characterId);
        final Map<UUID, Map<String, String>> variables =
                TransmogConfig.ENABLE_TRANSMOG ? itemVariables.findByOwner(characterId) : Map.of();
        return worn.stream()
                .map(item -> item.toBuilder()
                        .augmentationId(augmentationIds.get(item.id()))
                        .variables(variables.getOrDefault(item.id(), Map.of()))
                        .build())
                .toList();
    }

    public CharacterSummary summarize(StoredCharacterSlot slot) {
        WorldPartition.checkOwner();
        final CharacterSlotRow row = slot.character();
        final int activeClassId = row.classId();
        final List<CharacterSummary.PaperdollSlot> paperdoll =
                new ArrayList<>(Collections.nCopies(Inventory.PAPERDOLL_TOTALSLOTS, EMPTY));
        int augmentationId = 0;
        for (StoredItem item : slot.paperdoll()) {
            final int objectId = registry.handleOf(item.id());
            paperdoll.set(
                    item.locationSlot(),
                    CharacterSummary.PaperdollSlot.builder()
                            .objectId(objectId)
                            .itemId(displayId(objectId, item))
                            .enchantLevel(item.enchantLevel())
                            .build());
            if ((item.locationSlot() == Inventory.PAPERDOLL_RHAND) && (item.augmentationId() != null)) {
                augmentationId = item.augmentationId();
            }
        }
        final CharacterSummary.CharacterSummaryBuilder summary = CharacterSummary.builder()
                .objectId(registry.handleOf(row.id()))
                .name(row.name())
                .accessLevel(row.accessLevel())
                .level(row.level())
                .exp(row.exp())
                .sp(row.sp())
                .maxHp(row.maxHp())
                .currentHp(row.currentHp())
                .maxMp(row.maxMp())
                .currentMp(row.currentMp())
                .karma(row.karma())
                .pkKills(row.pkKills())
                .pvpKills(row.pvpKills())
                .face(row.face())
                .hairStyle(row.hairStyle())
                .hairColor(row.hairColor())
                .sex(row.sex())
                .race(row.race())
                .clanId(handleOrZero(registry, row.clanId()))
                .classId(activeClassId)
                // Characters created before base classes existed have 0 there; the client needs the class shown.
                .baseClassId(((row.baseClassId() == 0) && (activeClassId > 0)) ? activeClassId : row.baseClassId())
                .x(row.x())
                .y(row.y())
                .z(row.z())
                .good(row.faction() == Faction.GOOD)
                .evil(row.faction() == Faction.EVIL)
                .vitalityPoints(row.vitalityPoints())
                .augmentationId(augmentationId)
                .deleteTimer(epochMillisOrZero(row.deleteAt()))
                .lastAccess(epochMillisOrZero(row.lastAccessAt()))
                .paperdoll(paperdoll);
        final CharacterSubclass active = slot.activeSubclass();
        if (active != null) {
            summary.exp(active.exp()).sp(active.sp()).level(active.level());
        }
        return summary.build();
    }

    private static int displayId(int objectId, StoredItem item) {
        if (TransmogConfig.ENABLE_TRANSMOG) {
            return new ItemVariables(objectId, item.variables())
                    .getInt(ItemVariables.TRANSMOG_ID, item.itemTemplateId());
        }
        final ItemTemplate template = ItemData.getInstance().getTemplate(item.itemTemplateId());
        return template == null ? item.itemTemplateId() : template.getDisplayId();
    }
}
