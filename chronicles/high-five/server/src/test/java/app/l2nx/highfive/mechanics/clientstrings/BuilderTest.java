/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.clientstrings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BuilderTest {
    @Nested
    class ToString {
        @Test
        void toString_shouldRenderParameter_whenParameterIsGiven() {
            final String actual = Builder.newBuilder("Leader: $s1, level $s2.").toString("Bob", 5);

            assertThat(actual).isEqualTo("Leader: Bob, level 5.");
        }

        @Test
        void toString_shouldRenderEmptyText_whenParameterIsNull() {
            final String actual = Builder.newBuilder("Leader: $s1.").toString((Object) null);

            assertThat(actual).isEqualTo("Leader: .");
        }

        @Test
        void toString_shouldRenderEmptyText_whenSingleParameterTextGetsNull() {
            final Builder builder = Builder.newBuilder("$s1");

            assertThat(builder.toString((Object) null)).isEmpty();
            assertThat(builder.toString(new Object[] {null})).isEmpty();
        }
    }
}
