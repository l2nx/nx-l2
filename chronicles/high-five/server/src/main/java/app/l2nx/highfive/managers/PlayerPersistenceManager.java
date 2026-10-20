/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

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
import app.l2nx.highfive.repository.character.model.CharacterHenna;
import app.l2nx.highfive.repository.character.model.CharacterItemReuse;
import app.l2nx.highfive.repository.character.model.CharacterMacro;
import app.l2nx.highfive.repository.character.model.CharacterPremiumItem;
import app.l2nx.highfive.repository.character.model.CharacterQuestVariable;
import app.l2nx.highfive.repository.character.model.CharacterRecipe;
import app.l2nx.highfive.repository.character.model.CharacterRecipeShopItem;
import app.l2nx.highfive.repository.character.model.CharacterRecommendation;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.character.model.CharacterShortcut;
import app.l2nx.highfive.repository.character.model.CharacterSkill;
import app.l2nx.highfive.repository.character.model.CharacterSkillSave;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import app.l2nx.highfive.repository.character.model.CharacterTeleportBookmark;
import app.l2nx.highfive.repository.character.model.OnlineStatus;
import app.l2nx.highfive.repository.item.ItemRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PlayerPersistenceManager {
    private static volatile PlayerPersistenceManager instance;

    private final CharacterRepository characterRepository;
    private final CharacterSubclassRepository subclassRepository;
    private final CharacterSkillRepository skillRepository;
    private final CharacterSkillSaveRepository skillSaveRepository;
    private final CharacterItemReuseRepository itemReuseRepository;
    private final CharacterHennaRepository hennaRepository;
    private final CharacterTeleportBookmarkRepository bookmarkRepository;
    private final CharacterRecipeRepository recipeRepository;
    private final CharacterRecipeShopRepository recipeShopRepository;
    private final CharacterPremiumItemRepository premiumItemRepository;
    private final CharacterRecommendationRepository recommendationRepository;
    private final CharacterMacroRepository macroRepository;
    private final CharacterShortcutRepository shortcutRepository;
    private final CharacterQuestRepository questRepository;
    private final CharacterVariableRepository variableRepository;
    private final CharacterInstanceTimeRepository instanceTimeRepository;
    private final ItemRepository itemRepository;
    private final MailManager mailManager;
    private final IdManager idManager;
    private final TransactionTemplate transactionTemplate;

    public PlayerPersistenceManager(
            CharacterRepository characterRepository,
            CharacterSubclassRepository subclassRepository,
            CharacterSkillRepository skillRepository,
            CharacterSkillSaveRepository skillSaveRepository,
            CharacterItemReuseRepository itemReuseRepository,
            CharacterHennaRepository hennaRepository,
            CharacterTeleportBookmarkRepository bookmarkRepository,
            CharacterRecipeRepository recipeRepository,
            CharacterRecipeShopRepository recipeShopRepository,
            CharacterPremiumItemRepository premiumItemRepository,
            CharacterRecommendationRepository recommendationRepository,
            CharacterMacroRepository macroRepository,
            CharacterShortcutRepository shortcutRepository,
            CharacterQuestRepository questRepository,
            CharacterVariableRepository variableRepository,
            CharacterInstanceTimeRepository instanceTimeRepository,
            ItemRepository itemRepository,
            MailManager mailManager,
            IdManager idManager,
            TransactionTemplate transactionTemplate) {
        this.characterRepository = characterRepository;
        this.subclassRepository = subclassRepository;
        this.skillRepository = skillRepository;
        this.skillSaveRepository = skillSaveRepository;
        this.itemReuseRepository = itemReuseRepository;
        this.hennaRepository = hennaRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.recipeRepository = recipeRepository;
        this.recipeShopRepository = recipeShopRepository;
        this.premiumItemRepository = premiumItemRepository;
        this.recommendationRepository = recommendationRepository;
        this.macroRepository = macroRepository;
        this.shortcutRepository = shortcutRepository;
        this.questRepository = questRepository;
        this.variableRepository = variableRepository;
        this.instanceTimeRepository = instanceTimeRepository;
        this.itemRepository = itemRepository;
        this.mailManager = mailManager;
        this.idManager = idManager;
        this.transactionTemplate = transactionTemplate;
        instance = this;
    }

    /**
     * Must run before any character is loaded.
     */
    public void cleanUpAtStart(Instant now) {
        characterRepository.resetOnlineStatus();
        skillSaveRepository.deleteExpiredReuse(now);
        instanceTimeRepository.deleteExpired(now);
    }

    public void insertCharacter(CharacterRow character) {
        characterRepository.insert(character);
    }

    public void saveCharacter(CharacterRow character, List<CharacterSubclass> subclasses) {
        transactionTemplate.executeWithoutResult(status -> {
            characterRepository.update(character);
            if (!subclasses.isEmpty()) {
                subclassRepository.updateAll(character.objectId(), subclasses);
            }
        });
    }

    public Optional<CharacterRow> findCharacter(int objectId) {
        return characterRepository.findById(objectId);
    }

    /**
     * @return the characters of the account, oldest first
     */
    public List<CharacterRow> findCharacters(UUID accountId) {
        return characterRepository.findByAccount(accountId);
    }

    /**
     * @return names by handle of the other characters of the account, oldest first
     */
    public Map<Integer, String> findOtherCharacterNames(UUID accountId, int objectId) {
        return characterRepository.findOtherNames(accountId, objectId);
    }

    public void updateOnlineStatus(int objectId, OnlineStatus status, long lastAccessMillis) {
        characterRepository.updateOnlineStatus(objectId, status, instantOrNull(lastAccessMillis));
    }

    /**
     * @return the clan handle, {@code 0} for a character without a clan
     */
    public int findClanId(int objectId) {
        return characterRepository.findClanId(objectId);
    }

    /**
     * @param deleteAtMillis when the character is deleted, {@code 0} to cancel a pending deletion
     */
    public void updateDeleteTime(int objectId, long deleteAtMillis) {
        characterRepository.updateDeleteTime(objectId, instantOrNull(deleteAtMillis));
    }

    /**
     * The handles of the character and its items are released only after the commit, so a failed deletion keeps them.
     */
    public void deleteCharacter(int objectId) {
        final List<Integer> itemHandles = transactionTemplate.execute(status -> {
            mailManager.deleteMailOfCharacter(objectId);
            final List<Integer> deleted = itemRepository.deleteByOwner(objectId);
            characterRepository.delete(objectId);
            return deleted;
        });
        itemHandles.forEach(idManager::releaseId);
        idManager.releaseId(objectId);
    }

    public List<CharacterSubclass> findSubclasses(int objectId) {
        return subclassRepository.findAll(objectId);
    }

    public Optional<CharacterSubclass> findSubclass(int objectId, int classId) {
        return subclassRepository.findByClassId(objectId, classId);
    }

    public void insertSubclass(int objectId, CharacterSubclass subclass, List<CharacterSkill> skills) {
        transactionTemplate.executeWithoutResult(status -> {
            subclassRepository.insert(objectId, subclass);
            if (!skills.isEmpty()) {
                skillRepository.saveAll(objectId, subclass.classIndex(), skills);
            }
        });
    }

    /**
     * Removes the subclass with the hennas, shortcuts, stored effects and skills of its class index.
     */
    public void deleteSubclass(int objectId, int classIndex) {
        subclassRepository.delete(objectId, classIndex);
    }

    public List<CharacterSkill> findSkills(int objectId, int classIndex) {
        return skillRepository.findAll(objectId, classIndex);
    }

    public void updateSkillLevel(int objectId, int classIndex, int skillId, int skillLevel) {
        skillRepository.updateLevel(objectId, classIndex, skillId, skillLevel);
    }

    public void saveSkill(int objectId, int classIndex, CharacterSkill skill) {
        skillRepository.save(objectId, classIndex, skill);
    }

    public void saveSkills(int objectId, int classIndex, List<CharacterSkill> skills) {
        skillRepository.saveAll(objectId, classIndex, skills);
    }

    public void deleteSkill(int objectId, int classIndex, int skillId) {
        skillRepository.delete(objectId, classIndex, skillId);
    }

    /**
     * @return the stored effects and reuses of the class, in the order they were saved
     */
    public List<CharacterSkillSave> findSkillSaves(int objectId, int classIndex) {
        return skillSaveRepository.findAll(objectId, classIndex);
    }

    public void replaceSkillSaves(int objectId, int classIndex, List<CharacterSkillSave> saves) {
        skillSaveRepository.replaceAll(objectId, classIndex, saves);
    }

    public void deleteSkillSaves(int objectId, int classIndex) {
        skillSaveRepository.deleteAll(objectId, classIndex);
    }

    public List<CharacterItemReuse> findItemReuses(int objectId) {
        return itemReuseRepository.findAll(objectId);
    }

    public void replaceItemReuses(int objectId, List<CharacterItemReuse> reuses) {
        itemReuseRepository.replaceAll(objectId, reuses);
    }

    public void deleteItemReuses(int objectId) {
        itemReuseRepository.deleteAll(objectId);
    }

    public List<CharacterHenna> findHennas(int objectId, int classIndex) {
        return hennaRepository.findAll(objectId, classIndex);
    }

    public void insertHenna(int objectId, int classIndex, CharacterHenna henna) {
        hennaRepository.insert(objectId, classIndex, henna);
    }

    public void deleteHenna(int objectId, int classIndex, int slot) {
        hennaRepository.delete(objectId, classIndex, slot);
    }

    public List<CharacterTeleportBookmark> findBookmarks(int objectId) {
        return bookmarkRepository.findAll(objectId);
    }

    public void insertBookmark(int objectId, CharacterTeleportBookmark bookmark) {
        bookmarkRepository.insert(objectId, bookmark);
    }

    public void updateBookmark(int objectId, int bookmarkId, int icon, String tag, String name) {
        bookmarkRepository.update(objectId, bookmarkId, icon, tag, name);
    }

    public void deleteBookmark(int objectId, int bookmarkId) {
        bookmarkRepository.delete(objectId, bookmarkId);
    }

    public List<CharacterRecipe> findRecipes(int objectId) {
        return recipeRepository.findAll(objectId);
    }

    public List<Integer> findDwarvenRecipeIds(int objectId, int classIndex) {
        return recipeRepository.findDwarvenRecipeIds(objectId, classIndex);
    }

    /**
     * @param recipe a common recipe carries class index 0
     */
    public void insertRecipe(int objectId, CharacterRecipe recipe) {
        recipeRepository.insert(objectId, recipe);
    }

    public void deleteRecipe(int objectId, int classIndex, int recipeId) {
        recipeRepository.delete(objectId, classIndex, recipeId);
    }

    /**
     * @return the manufacture list in its stored order
     */
    public List<CharacterRecipeShopItem> findRecipeShopItems(int objectId) {
        return recipeShopRepository.findAll(objectId);
    }

    public void replaceRecipeShopItems(int objectId, List<CharacterRecipeShopItem> items) {
        recipeShopRepository.replaceAll(objectId, items);
    }

    public List<CharacterPremiumItem> findPremiumItems(int objectId) {
        return premiumItemRepository.findByCharacter(objectId);
    }

    public void updatePremiumItemCount(int objectId, int itemNum, long count) {
        premiumItemRepository.updateCount(objectId, itemNum, count);
    }

    public void deletePremiumItem(int objectId, int itemNum) {
        premiumItemRepository.delete(objectId, itemNum);
    }

    public Optional<CharacterRecommendation> findRecommendation(int objectId) {
        return recommendationRepository.find(objectId);
    }

    public void saveRecommendation(int objectId, CharacterRecommendation recommendation) {
        recommendationRepository.save(objectId, recommendation);
    }

    public void saveRecommendationCounts(int objectId, int received, int leftToGive) {
        recommendationRepository.saveCounts(objectId, received, leftToGive);
    }

    public List<CharacterMacro> findMacros(int objectId) {
        return macroRepository.findAll(objectId);
    }

    public void saveMacro(int objectId, CharacterMacro macro) {
        macroRepository.save(objectId, macro);
    }

    public void deleteMacro(int objectId, int macroId) {
        macroRepository.delete(objectId, macroId);
    }

    public List<CharacterShortcut> findShortcuts(int objectId, int classIndex) {
        return shortcutRepository.findAll(objectId, classIndex);
    }

    public void saveShortcut(int objectId, int classIndex, CharacterShortcut shortcut) {
        shortcutRepository.save(objectId, classIndex, shortcut);
    }

    public void deleteShortcut(int objectId, int classIndex, int slot, int page) {
        shortcutRepository.delete(objectId, classIndex, slot, page);
    }

    public List<CharacterQuestVariable> findQuestStates(int objectId) {
        return questRepository.findStates(objectId);
    }

    /**
     * @return the variables of every quest of the character except the states
     */
    public List<CharacterQuestVariable> findQuestVariables(int objectId) {
        return questRepository.findVariables(objectId);
    }

    public void saveQuestVariable(int objectId, String questName, String name, String content) {
        questRepository.save(objectId, questName, name, content);
    }

    /**
     * Changes an existing variable; writes nothing when the variable is not stored.
     */
    public void updateQuestVariable(int objectId, String questName, String name, String content) {
        questRepository.update(objectId, questName, name, content);
    }

    public void deleteQuestVariable(int objectId, String questName, String name) {
        questRepository.deleteVariable(objectId, questName, name);
    }

    /**
     * @param keepState keeps the state of the quest, so a non-repeatable quest stays completed
     */
    public void deleteQuest(int objectId, String questName, boolean keepState) {
        if (keepState) {
            questRepository.deleteQuestKeepingState(objectId, questName);
        } else {
            questRepository.deleteQuest(objectId, questName);
        }
    }

    /**
     * @return the variable values by name; a value stored empty is {@code null}
     */
    public Map<String, String> findVariables(int objectId) {
        return variableRepository.findAll(objectId);
    }

    public void saveVariables(int objectId, Collection<String> deletedNames, Map<String, String> values) {
        variableRepository.save(objectId, deletedNames, values);
    }

    public void deleteVariables(int objectId) {
        variableRepository.deleteAll(objectId);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static PlayerPersistenceManager getInstance() {
        final PlayerPersistenceManager current = instance;
        if (current == null) {
            throw new IllegalStateException("PlayerPersistenceManager is used before the Spring context created it.");
        }
        return current;
    }
}
