/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character;

import app.l2nx.highfive.repository.character.model.CharacterHenna;
import app.l2nx.highfive.repository.character.model.CharacterMacro;
import app.l2nx.highfive.repository.character.model.CharacterPremiumItem;
import app.l2nx.highfive.repository.character.model.CharacterQuestVariable;
import app.l2nx.highfive.repository.character.model.CharacterRecipe;
import app.l2nx.highfive.repository.character.model.CharacterRecipeShopItem;
import app.l2nx.highfive.repository.character.model.CharacterRecommendation;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.character.model.CharacterSkill;
import app.l2nx.highfive.repository.character.model.CharacterSkillSave;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import app.l2nx.highfive.repository.character.model.CharacterTeleportBookmark;
import app.l2nx.highfive.repository.character.model.StoredItemReuse;
import app.l2nx.highfive.repository.character.model.StoredShortcut;
import app.l2nx.highfive.repository.item.model.StoredItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record StoredCharacter(
        UUID characterId,
        CharacterRow character,
        List<CharacterSubclass> subclasses,
        int classIndex,
        Map<UUID, String> otherCharacters,
        List<StoredItem> inventory,
        List<StoredItem> warehouse,
        List<StoredItem> freight,
        List<CharacterSkill> skills,
        List<CharacterMacro> macros,
        List<StoredShortcut> shortcuts,
        List<CharacterHenna> hennas,
        List<CharacterTeleportBookmark> bookmarks,
        List<CharacterRecipe> recipes,
        List<CharacterRecipeShopItem> recipeShop,
        List<CharacterPremiumItem> premiumItems,
        boolean petItems,
        List<StoredItemReuse> itemReuses,
        List<UUID> friends,
        List<UUID> blocked,
        List<String> contacts,
        Map<String, String> variables,
        Map<String, String> accountVariables,
        List<Integer> rebirthSkillIds,
        WorldEntry worldEntry) {

    /** {@code recommendation} and {@code premiumEndsAt} are {@code null} when nothing is stored. */
    @Builder(toBuilder = true)
    public record WorldEntry(
            List<CharacterQuestVariable> questStates,
            List<CharacterQuestVariable> questVariables,
            CharacterRecommendation recommendation,
            List<CharacterSkillSave> skillSaves,
            Instant premiumEndsAt,
            List<Integer> transmogItemIds) {}

    public static int activeClassIndex(CharacterRow character, List<CharacterSubclass> subclasses) {
        if (character.classId() == character.baseClassId()) {
            return 0;
        }
        for (CharacterSubclass subclass : subclasses) {
            if (subclass.classId() == character.classId()) {
                return subclass.classIndex();
            }
        }
        return 0;
    }
}
