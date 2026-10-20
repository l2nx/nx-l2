/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PunishmentManagerTest {

    @Nested
    class NormalizeKey {

        @Test
        void normalizeKey_shouldKeepAccountId_whenAffectIsAccount() {
            final UUID expected = UUID.randomUUID();

            final Object actual = PunishmentManager.normalizeKey(expected, PunishmentAffect.ACCOUNT);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void normalizeKey_shouldParseAccountId_whenAccountIdIsText() {
            final UUID expected = UUID.randomUUID();

            final Object actual = PunishmentManager.normalizeKey(expected.toString(), PunishmentAffect.ACCOUNT);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void normalizeKey_shouldReject_whenAccountKeyIsAccountName() {
            assertThatThrownBy(() -> PunishmentManager.normalizeKey("someLogin", PunishmentAffect.ACCOUNT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void normalizeKey_shouldParseObjectId_whenCharacterKeyIsText() {
            final Object actual = PunishmentManager.normalizeKey("268435457", PunishmentAffect.CHARACTER);

            assertThat(actual).isEqualTo(268435457);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = PunishmentAffect.class,
                names = {"IP", "HWID"})
        void normalizeKey_shouldKeepText_whenAffectIsAddress(PunishmentAffect affect) {
            final Object actual = PunishmentManager.normalizeKey("10.0.0.1", affect);

            assertThat(actual).isEqualTo("10.0.0.1");
        }
    }
}
