/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.character.repository.StoredCharacterRepository;
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
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.social.CharacterContactRepository;
import app.l2nx.highfive.repository.social.CharacterRelationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

final class StoredCharacterRepositories {
    private StoredCharacterRepositories() {}

    static StoredCharacterRepository of(JdbcClient jdbc, JdbcTemplate template, IdentityRegistry registry) {
        final CharacterSkillRepository skills = new CharacterSkillRepository(jdbc, template, registry);
        final CharacterSkillSaveRepository skillSaves = new CharacterSkillSaveRepository(jdbc, template, registry);
        final CharacterShortcutRepository shortcuts = new CharacterShortcutRepository(jdbc, registry);
        final CharacterHennaRepository hennas = new CharacterHennaRepository(jdbc, registry);
        return new StoredCharacterRepository(
                new CharacterRepository(jdbc, registry),
                new CharacterSubclassRepository(jdbc, template, registry, skills, skillSaves, shortcuts, hennas),
                skills,
                new CharacterMacroRepository(jdbc, registry),
                shortcuts,
                hennas,
                new CharacterTeleportBookmarkRepository(jdbc, registry),
                new CharacterRecipeRepository(jdbc, registry),
                new CharacterRecipeShopRepository(jdbc, template, registry),
                new CharacterPremiumItemRepository(jdbc, registry),
                new CharacterItemReuseRepository(jdbc, template, registry),
                new CharacterVariableRepository(jdbc, template, registry),
                new CharacterQuestRepository(jdbc, registry),
                new CharacterRecommendationRepository(jdbc, registry),
                skillSaves,
                new CharacterPremiumRepository(jdbc, registry),
                new AccountPremiumRepository(jdbc),
                new AccountVariableRepository(jdbc, template),
                new CharacterTransmogRepository(jdbc, template, registry),
                new CharacterRebirthRepository(jdbc, registry),
                new CharacterContactRepository(jdbc, registry),
                new CharacterRelationRepository(jdbc, registry),
                new ItemRepository(jdbc, registry),
                new ItemAugmentationRepository(jdbc, registry),
                new ItemElementalRepository(jdbc, template, registry),
                new ItemVariableRepository(jdbc, template, registry));
    }
}
