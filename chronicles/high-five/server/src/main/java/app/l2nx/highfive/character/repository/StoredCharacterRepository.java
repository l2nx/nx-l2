/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.repository;

import app.l2nx.highfive.character.StoredCharacter;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.config.custom.RebirthConfig;
import app.l2nx.highfive.config.custom.TransmogConfig;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.repository.account.AccountPremiumRepository;
import app.l2nx.highfive.repository.account.AccountVariableRepository;
import app.l2nx.highfive.repository.character.CharacterHennaRepository;
import app.l2nx.highfive.repository.character.CharacterItemReuseRepository;
import app.l2nx.highfive.repository.character.CharacterMacroRepository;
import app.l2nx.highfive.repository.character.CharacterPremiumItemRepository;
import app.l2nx.highfive.repository.character.CharacterPremiumRepository;
import app.l2nx.highfive.repository.character.CharacterQuestRepository;
import app.l2nx.highfive.repository.character.CharacterRebirthRepository;
import app.l2nx.highfive.repository.character.CharacterRecipeRepository;
import app.l2nx.highfive.repository.character.CharacterRecipeShopRepository;
import app.l2nx.highfive.repository.character.CharacterRecommendationRepository;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.CharacterShortcutRepository;
import app.l2nx.highfive.repository.character.CharacterSkillRepository;
import app.l2nx.highfive.repository.character.CharacterSkillSaveRepository;
import app.l2nx.highfive.repository.character.CharacterSubclassRepository;
import app.l2nx.highfive.repository.character.CharacterTeleportBookmarkRepository;
import app.l2nx.highfive.repository.character.CharacterTransmogRepository;
import app.l2nx.highfive.repository.character.CharacterVariableRepository;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import app.l2nx.highfive.repository.character.model.OnlineStatus;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import app.l2nx.highfive.repository.item.model.StoredItem;
import app.l2nx.highfive.repository.social.CharacterContactRepository;
import app.l2nx.highfive.repository.social.CharacterRelationRepository;
import app.l2nx.highfive.repository.social.model.RelationType;
import app.l2nx.highfive.security.SecondaryPasswordAuth;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StoredCharacterRepository {
    private final CharacterRepository characters;
    private final CharacterSubclassRepository subclasses;
    private final CharacterSkillRepository skills;
    private final CharacterMacroRepository macros;
    private final CharacterShortcutRepository shortcuts;
    private final CharacterHennaRepository hennas;
    private final CharacterTeleportBookmarkRepository bookmarks;
    private final CharacterRecipeRepository recipes;
    private final CharacterRecipeShopRepository recipeShop;
    private final CharacterPremiumItemRepository premiumItems;
    private final CharacterItemReuseRepository itemReuses;
    private final CharacterVariableRepository variables;
    private final CharacterQuestRepository quests;
    private final CharacterRecommendationRepository recommendations;
    private final CharacterSkillSaveRepository skillSaves;
    private final CharacterPremiumRepository characterPremiums;
    private final AccountPremiumRepository accountPremiums;
    private final AccountVariableRepository accountVariables;
    private final CharacterTransmogRepository transmogs;
    private final CharacterRebirthRepository rebirths;
    private final CharacterContactRepository contacts;
    private final CharacterRelationRepository relations;
    private final ItemRepository items;
    private final ItemAugmentationRepository augmentations;
    private final ItemElementalRepository elementals;
    private final ItemVariableRepository itemVariables;

    public Optional<StoredCharacter> find(UUID characterId) {
        return characters.findById(characterId).map(character -> read(characterId, character));
    }

    public void updateOnlineStatus(UUID characterId, OnlineStatus status, Instant at) {
        characters.updateOnlineStatus(characterId, status, at);
    }

    private StoredCharacter read(UUID characterId, CharacterRow character) {
        final UUID accountId = character.accountId();
        final List<CharacterSubclass> classes = subclasses.findAll(characterId);
        final int classIndex = StoredCharacter.activeClassIndex(character, classes);
        final UnaryOperator<StoredItem> attributes = attributes(characterId);
        return StoredCharacter.builder()
                .characterId(characterId)
                .character(character)
                .subclasses(classes)
                .classIndex(classIndex)
                .otherCharacters(characters.findOtherNames(accountId, characterId))
                .inventory(withAttributes(
                        items.findStored(characterId, ItemLocation.INVENTORY, ItemLocation.PAPERDOLL), attributes))
                .warehouse(withAttributes(items.findStored(characterId, ItemLocation.WAREHOUSE), attributes))
                .freight(withAttributes(items.findStored(characterId, ItemLocation.FREIGHT), attributes))
                .skills(skills.findAll(characterId, classIndex))
                .macros(macros.findAll(characterId))
                .shortcuts(shortcuts.findStored(characterId, classIndex))
                .hennas(hennas.findAll(characterId, classIndex))
                .bookmarks(bookmarks.findAll(characterId))
                .recipes(recipes.findAll(characterId))
                .recipeShop(PlayerConfig.STORE_RECIPE_SHOPLIST ? recipeShop.findAll(characterId) : List.of())
                .premiumItems(premiumItems.findByCharacter(characterId))
                .petItems(items.hasPetItems(characterId))
                .itemReuses(itemReuses.findStored(characterId))
                .friends(relations.findTargets(characterId, RelationType.FRIEND))
                .blocked(relations.findTargets(characterId, RelationType.BLOCK))
                .contacts(contacts.findContactNames(characterId))
                .variables(variables.findAll(characterId))
                .accountVariables(
                        accountVariables.findAllWithoutPrefix(accountId, SecondaryPasswordAuth.VARIABLE_PREFIX))
                .rebirthSkillIds(
                        RebirthConfig.REBIRTH_ALLOW_REBIRTH ? rebirths.findSelectedSkillIds(characterId) : List.of())
                .worldEntry(StoredCharacter.WorldEntry.builder()
                        .questStates(quests.findStates(characterId))
                        .questVariables(quests.findVariables(characterId))
                        .recommendation(recommendations.find(characterId).orElse(null))
                        .skillSaves(
                                PlayerConfig.STORE_SKILL_COOLTIME
                                        ? skillSaves.findAll(characterId, classIndex)
                                        : List.of())
                        .premiumEndsAt(premiumEndsAt(characterId, accountId))
                        .transmogItemIds(transmogItemIds(characterId, accountId))
                        .build())
                .build();
    }

    // Item variables are read whatever the config: an item built from a StoredItem trusts them to be the stored ones.
    private UnaryOperator<StoredItem> attributes(UUID characterId) {
        final Map<UUID, Integer> augmentationIds = augmentations.findByOwner(characterId);
        final Map<UUID, List<ItemElemental>> elementalRows = elementals.findByOwner(characterId);
        final Map<UUID, Map<String, String>> variableRows = itemVariables.findByOwner(characterId);
        return item -> item.toBuilder()
                .augmentationId(augmentationIds.get(item.id()))
                .elementals(elementalRows.getOrDefault(item.id(), List.of()))
                .variables(variableRows.getOrDefault(item.id(), Map.of()))
                .build();
    }

    private static List<StoredItem> withAttributes(List<StoredItem> rows, UnaryOperator<StoredItem> attributes) {
        return rows.stream().map(attributes).toList();
    }

    private Instant premiumEndsAt(UUID characterId, UUID accountId) {
        if (!PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED) {
            return null;
        }
        return (PremiumSystemConfig.ACCOUNT_WIDE_PREMIUM
                        ? accountPremiums.findEndsAt(accountId)
                        : characterPremiums.findEndsAt(characterId))
                .orElse(null);
    }

    private List<Integer> transmogItemIds(UUID characterId, UUID accountId) {
        if (!TransmogConfig.ENABLE_TRANSMOG) {
            return List.of();
        }
        return TransmogConfig.TRANSMOG_SHARE_ACCOUNT
                ? transmogs.findByAccount(accountId)
                : transmogs.findByCharacter(characterId);
    }
}
