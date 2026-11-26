/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.mechanics.skill.Skill;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

@ExtendWith(ClockBridgeExtension.class)
@ResourceLock(ClockBridgeExtension.LOCK)
class DocumentItemSkillTypeTest {
    private static final int SKILL_ID = 3411;
    private static final int SKILL_LEVEL = 1;

    @TempDir
    Path dir;

    private final SkillData skillData = mock(SkillData.class);
    private final Skill skill = mock(Skill.class);
    private MockedStatic<SkillData> skillDataInstance;

    @BeforeEach
    void mockSkillData() {
        skillDataInstance = mockStatic(SkillData.class);
        skillDataInstance.when(SkillData::getInstance).thenReturn(skillData);
        when(skillData.getSkill(SKILL_ID, SKILL_LEVEL)).thenReturn(skill);
    }

    @AfterEach
    void closeSkillData() {
        skillDataInstance.close();
    }

    // <stats> builds the item before its skills: a Weapon or Armor built only at the end of its element has its
    // enchant skill and cast chance reset by its field initializers.
    private ItemTemplate load(String itemClass, String skillAttributes) throws IOException {
        final Path file = dir.resolve("items.xml");
        Files.writeString(file, """
            <?xml version="1.0" encoding="UTF-8"?>
            <list>
                <item id="1" type="%s" name="Item">
                    <stats />
                    <skills>
                        <skill id="%d" level="%d" %s />
                    </skills>
                </item>
            </list>
            """.formatted(itemClass, SKILL_ID, SKILL_LEVEL, skillAttributes));
        final DocumentItem document = new DocumentItem(file.toFile());
        document.parse();
        assertThat(document.getItemList()).hasSize(1);
        return document.getItemList().getFirst();
    }

    @Nested
    class Grant {
        @ParameterizedTest(name = "{0} [{1}]")
        @CsvSource(
                delimiter = '|',
                value = {
                    "Weapon  | ''",
                    "Armor   | ''",
                    "EtcItem | ''",
                    "Weapon  | type=\"EQUIPPED\"",
                    "Armor   | type=\"EQUIPPED\"",
                    "EtcItem | type=\"ON_USE\""
                })
        void parse_shouldAddItemSkill_whenGrantOrUntyped(String itemClass, String attributes) throws IOException {
            final ItemTemplate actual = load(itemClass, attributes);

            assertThat(actual.getSkills())
                    .singleElement()
                    .satisfies(holder -> assertThat(holder.getSkillId()).isEqualTo(SKILL_ID))
                    .satisfies(holder -> assertThat(holder.getSkillLevel()).isEqualTo(SKILL_LEVEL));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Weapon", "Armor"})
        void parse_shouldSetEnchantSkill_whenEquippedEnchant(String itemClass) throws IOException {
            final ItemTemplate actual = load(itemClass, "type=\"EQUIPPED_ENCHANT\"");

            assertThat(actual.getEnchant4Skill()).isSameAs(skill);
            assertThat(actual.getSkills()).isNull();
        }
    }

    @Nested
    class Cast {
        @Test
        void parse_shouldSetUnequipSkill_whenOnUnequip() throws IOException {
            final ItemTemplate actual = load("Armor", "type=\"ON_UNEQUIP\"");

            assertThat(actual.getUnequipSkill()).isSameAs(skill);
            assertThat(actual.getSkills()).isNull();
        }

        @Test
        void parse_shouldSetCriticalHitSkill_whenOnNormalAttackCriticalHit() throws IOException {
            final Weapon actual = (Weapon) load("Weapon", "type=\"ON_NORMAL_ATTACK_CRITICAL_HIT\" type_chance=\"100\"");
            final Creature caster = mock(Creature.class);
            final Creature target = mock(Creature.class);

            actual.castOnCriticalSkill(caster, target);

            verify(skill).checkCondition(caster, target, false);
            assertThat(actual.getSkills()).isNull();
        }

        @Test
        void parse_shouldSetMagicSkillCastSkill_whenOnMagicSkillCast() throws IOException {
            final Weapon actual = (Weapon) load("Weapon", "type=\"ON_MAGIC_SKILL_CAST\" type_chance=\"100\"");

            actual.castOnMagicSkill(mock(Creature.class), mock(Creature.class), mock(Skill.class));

            verify(skillData).getSkill(SKILL_ID, SKILL_LEVEL);
            assertThat(actual.getSkills()).isNull();
        }
    }

    @Nested
    class Reject {
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"ON_PHYSICAL_HIT", "UNKNOWN"})
        void parse_shouldSkipSkill_whenTypeIsNoItemSkillType(String type) throws IOException {
            final Weapon actual = (Weapon) load("Weapon", "type=\"" + type + "\" type_chance=\"100\"");

            actual.castOnCriticalSkill(mock(Creature.class), mock(Creature.class));
            actual.castOnMagicSkill(mock(Creature.class), mock(Creature.class), mock(Skill.class));

            assertThat(actual.getSkills()).isNull();
            assertThat(actual.getEnchant4Skill()).isNull();
            assertThat(actual.getUnequipSkill()).isNull();
            verify(skillData, never()).getSkill(anyInt(), anyInt());
        }
    }
}
