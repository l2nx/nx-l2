/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.data.sql;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.holders.PetData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.actor.instance.Servitor;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.StoredEffect;
import app.l2nx.highfive.network.serverpackets.PetItemList;
import app.l2nx.highfive.repository.pet.CharacterSummonRepository;
import app.l2nx.highfive.repository.pet.PetRepository;
import app.l2nx.highfive.repository.pet.PetSkillSaveRepository;
import app.l2nx.highfive.repository.pet.SummonSkillSaveRepository;
import app.l2nx.highfive.repository.pet.model.CharacterSummon;
import app.l2nx.highfive.repository.pet.model.PetRow;
import app.l2nx.highfive.repository.pet.model.SummonEffectRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * @author Nyaran
 */
@Slf4j
@Component
public class CharSummonTable {
    private static volatile CharSummonTable instance;

    private final Map<Integer, Integer> pets = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> servitors = new ConcurrentHashMap<>();

    private final PetRepository petRepository;
    private final PetSkillSaveRepository petSkillSaveRepository;
    private final CharacterSummonRepository summonRepository;
    private final SummonSkillSaveRepository summonSkillSaveRepository;

    public CharSummonTable(
            PetRepository petRepository,
            PetSkillSaveRepository petSkillSaveRepository,
            CharacterSummonRepository summonRepository,
            SummonSkillSaveRepository summonSkillSaveRepository) {
        this.petRepository = petRepository;
        this.petSkillSaveRepository = petSkillSaveRepository;
        this.summonRepository = summonRepository;
        this.summonSkillSaveRepository = summonSkillSaveRepository;
        instance = this;
    }

    public Map<Integer, Integer> getPets() {
        return pets;
    }

    public Map<Integer, Integer> getServitors() {
        return servitors;
    }

    public void init() {
        if (PlayerConfig.RESTORE_SERVITOR_ON_RECONNECT) {
            servitors.putAll(summonRepository.findSummonSkills());
        }

        if (PlayerConfig.RESTORE_PET_ON_RECONNECT) {
            pets.putAll(petRepository.findRestorable());
        }
    }

    public void removeServitor(Player player) {
        servitors.remove(player.getObjectId());
        try {
            summonRepository.deleteByOwner(player.getObjectId());
        } catch (DataAccessException e) {
            log.warn("Summon cannot be removed", e);
        }
    }

    public void restorePet(Player player) {
        final Item item = player.getInventory().getItemByObjectId(pets.get(player.getObjectId()));
        if (item == null) {
            log.warn("Null pet summoning item for: {}", player);
            return;
        }

        final PetData petData = PetDataTable.getInstance().getPetDataByItemId(item.getId());
        if (petData == null) {
            log.warn("Null pet data for: {} and summoning item: {}", player, item);
            return;
        }

        final NpcTemplate npcTemplate = NpcData.getInstance().getTemplate(petData.getNpcId());
        if (npcTemplate == null) {
            log.warn("Null pet NPC template for: {} and pet Id:{}", player, petData.getNpcId());
            return;
        }

        final Pet pet = Pet.spawnPet(npcTemplate, player, item);
        if (pet == null) {
            log.warn("Null pet instance for: {} and pet NPC template:{}", player, npcTemplate);
            return;
        }

        pet.setShowSummonAnimation(true);
        pet.setTitle(player.getName());

        if (!pet.isRespawned()) {
            pet.setCurrentHp(pet.getMaxHp());
            pet.setCurrentMp(pet.getMaxMp());
            pet.getStat().setExp(pet.getExpForThisLevel());
            pet.setCurrentFed(pet.getMaxFed());
        }

        pet.setRunning();

        if (!pet.isRespawned()) {
            pet.storeMe();
        }

        item.setEnchantLevel(pet.getLevel());
        player.setPet(pet);
        pet.spawnMe(player.getX() + 50, player.getY() + 100, player.getZ());
        pet.startFeed();
        pet.setFollowStatus(true);
        pet.getOwner().sendPacket(new PetItemList(pet.getInventory().getItems()));
        pet.broadcastStatusUpdate();
    }

    public void restoreServitor(Player player) {
        final int skillId = servitors.get(player.getObjectId());
        final Optional<CharacterSummon> stored;
        try {
            stored = summonRepository.find(player.getObjectId(), skillId);
        } catch (DataAccessException e) {
            log.warn("Servitor cannot be restored", e);
            return;
        }

        if (stored.isEmpty()) {
            return;
        }

        final Skill skill = SkillData.getInstance().getSkill(skillId, player.getSkillLevel(skillId));
        if (skill == null) {
            removeServitor(player);
            return;
        }

        skill.applyEffects(player, player);

        if (player.hasServitor()) {
            final CharacterSummon row = stored.get();
            final Servitor summon = player.getSummon().asServitor();
            summon.setCurrentHp(row.currentHp());
            summon.setCurrentMp(row.currentMp());
            summon.setLifeTimeRemaining((int) row.lifeRemainingMs());
        }
    }

    public void saveSummon(Servitor summon) {
        if ((summon == null) || (summon.getLifeTimeRemaining() <= 0)) {
            return;
        }

        servitors.put(summon.getOwner().getObjectId(), summon.getReferenceSkill());

        try {
            summonRepository.save(CharacterSummon.builder()
                    .ownerId(summon.getOwner().getObjectId())
                    .summonSkillId(summon.getReferenceSkill())
                    .currentHp((int) Math.round(summon.getCurrentHp()))
                    .currentMp((int) Math.round(summon.getCurrentMp()))
                    .lifeRemainingMs(summon.getLifeTimeRemaining())
                    .build());
        } catch (DataAccessException e) {
            log.warn("Failed to store summon: {} from {}, error", summon, summon.getOwner(), e);
        }
    }

    public Optional<PetRow> findPet(int controlItemId) {
        return petRepository.find(controlItemId);
    }

    /**
     * Inserts a new pet or updates a stored one; its control item must already be stored.
     */
    public void savePet(PetRow pet) {
        petRepository.save(pet);
    }

    public void updatePetFed(int controlItemId, int fed) {
        petRepository.updateFed(controlItemId, fed);
    }

    public List<SummonEffectRow> findPetEffects(int controlItemId) {
        return petSkillSaveRepository.findAll(controlItemId);
    }

    /**
     * Replaces the stored effects of a pet; the list order is the order they are applied in.
     */
    public void savePetEffects(int controlItemId, List<SummonEffectRow> effects) {
        petSkillSaveRepository.replaceAll(controlItemId, effects);
    }

    public void deletePetEffects(int controlItemId) {
        petSkillSaveRepository.deleteAll(controlItemId);
    }

    public List<SummonEffectRow> findServitorEffects(int ownerId, int classIndex, int summonSkillId) {
        return summonSkillSaveRepository.findAll(ownerId, classIndex, summonSkillId);
    }

    public void saveServitorEffects(int ownerId, int classIndex, int summonSkillId, List<SummonEffectRow> effects) {
        summonSkillSaveRepository.replaceAll(ownerId, classIndex, summonSkillId, effects);
    }

    public void deleteServitorEffects(int ownerId, int classIndex, int summonSkillId) {
        summonSkillSaveRepository.deleteAll(ownerId, classIndex, summonSkillId);
    }

    /**
     * Skips with a warning a row that breaks the {@link StoredEffect} invariants, so one corrupt row does not abort
     * the restore of the other effects.
     *
     * @param ownerId the character, or the control item of a pet
     */
    public static Optional<StoredEffect> toStoredEffect(SummonEffectRow row, int ownerId) {
        return StoredEffectRows.read(row.remainingMillis(), row.tickPhaseMillis(), ownerId, row.skillId());
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CharSummonTable getInstance() {
        final CharSummonTable current = instance;
        if (current == null) {
            throw new IllegalStateException("CharSummonTable is used before the Spring context created it.");
        }
        return current;
    }
}
