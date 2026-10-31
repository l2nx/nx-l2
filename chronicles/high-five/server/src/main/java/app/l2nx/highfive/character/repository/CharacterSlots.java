/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.repository;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;

import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.CharacterSubclassRepository;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.character.model.Faction;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterSlots {
    private final CharacterRepository characters;
    private final CharacterSubclassRepository subclasses;
    private final ItemAugmentationRepository augmentations;

    public List<CharacterRow> findByAccount(UUID accountId) {
        return characters.findByAccount(accountId);
    }

    public CharacterSummary summarize(CharacterRow row) {
        final int objectId = row.objectId();
        final int activeClassId = row.classId();
        final List<CharacterSummary.PaperdollSlot> paperdoll = Arrays.stream(
                        PlayerInventory.restoreVisibleInventory(objectId))
                .map(slot -> CharacterSummary.PaperdollSlot.builder()
                        .objectId(slot[0])
                        .itemId(slot[1])
                        .enchantLevel(slot[2])
                        .build())
                .toList();
        final int weaponObjectId = paperdoll.get(Inventory.PAPERDOLL_RHAND).objectId();
        final CharacterSummary.CharacterSummaryBuilder summary = CharacterSummary.builder()
                .objectId(objectId)
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
                .clanId(row.clanId())
                .classId(activeClassId)
                // Characters created before base classes existed have 0 there; the client needs the class shown.
                .baseClassId(((row.baseClassId() == 0) && (activeClassId > 0)) ? activeClassId : row.baseClassId())
                .x(row.x())
                .y(row.y())
                .z(row.z())
                .good(row.faction() == Faction.GOOD)
                .evil(row.faction() == Faction.EVIL)
                .vitalityPoints(row.vitalityPoints())
                .augmentationId(
                        (weaponObjectId > 0)
                                ? augmentations.find(weaponObjectId).orElse(0)
                                : 0)
                .deleteTimer(epochMillisOrZero(row.deleteAt()))
                .lastAccess(epochMillisOrZero(row.lastAccessAt()))
                .paperdoll(paperdoll);
        if (row.baseClassId() != activeClassId) {
            subclasses
                    .findByClassId(objectId, activeClassId)
                    .ifPresent(
                            active -> summary.exp(active.exp()).sp(active.sp()).level(active.level()));
        }
        return summary.build();
    }
}
