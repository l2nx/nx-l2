/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.item;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.util.StatSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WeaponTest {

    private static Weapon weapon(String damageRange) {
        final Map<String, Object> values = new HashMap<>();
        values.put("item_id", 1);
        values.put("name", "Test Sword");
        if (damageRange != null) {
            values.put("damage_range", damageRange);
        }
        return new Weapon(new StatSet(values));
    }

    @Nested
    class Set {

        @ParameterizedTest(name = "damage_range={0}")
        @ValueSource(strings = {"0", "0;0", "0;0;120", "0;0;120;"})
        void set_shouldKeepDefaultAttackShape_whenDamageRangeIsIncomplete(String damageRange) {
            final Weapon weapon = weapon(damageRange);

            assertThat(weapon.getBaseAttackRadius()).isEqualTo(40);
            assertThat(weapon.getBaseAttackAngle()).isZero();
        }

        @Test
        void set_shouldKeepDefaultAttackShape_whenDamageRangeIsMissing() {
            final Weapon weapon = weapon(null);

            assertThat(weapon.getBaseAttackRadius()).isEqualTo(40);
            assertThat(weapon.getBaseAttackAngle()).isZero();
        }

        @Test
        void set_shouldReadRadiusAndAngle_whenDamageRangeHasFourParts() {
            final Weapon weapon = weapon("0;0;120;90");

            assertThat(weapon.getBaseAttackRadius()).isEqualTo(120);
            assertThat(weapon.getBaseAttackAngle()).isEqualTo(90);
        }
    }
}
