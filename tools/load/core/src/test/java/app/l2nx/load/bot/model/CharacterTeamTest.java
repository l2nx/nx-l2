/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class CharacterTeamTest {
    @Nested
    class OfName {
        @ParameterizedTest(name = "{0}")
        @CsvSource({"load0, 1", "load1, 2", "load10, 1", "load499, 2", "Watcher, 0"})
        void ofName_shouldGoByTheParityOfTheIndex(String name, int expected) {
            assertThat(CharacterTeam.ofName(name)).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void ofName_shouldBeNoTeam_whenThereIsNoName(String name) {
            assertThat(CharacterTeam.ofName(name)).isEqualTo(CharacterTeam.NONE);
        }

        @Test
        void ofName_shouldMatchTheTeamOfTheIndex() {
            assertThat(CharacterTeam.ofName("load7")).isEqualTo(CharacterTeam.ofIndex(7));
        }
    }
}
