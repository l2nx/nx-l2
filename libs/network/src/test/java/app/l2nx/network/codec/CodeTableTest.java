/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CodeTableTest {
    private enum Sparse {
        LOW(0x01),
        HIGH(0x7F);

        private final int code;

        Sparse(int code) {
            this.code = code;
        }
    }

    private enum Shared {
        FIRST(0x02),
        SECOND(0x02);

        private final int code;

        Shared(int code) {
            this.code = code;
        }
    }

    private enum Negative {
        BELOW(-1);

        private final int code;

        Negative(int code) {
            this.code = code;
        }
    }

    @Nested
    class Of {
        private final CodeTable<Sparse> table = new CodeTable<>(Sparse.values(), value -> value.code);

        @ParameterizedTest(name = "{0}")
        @EnumSource(Sparse.class)
        void of_shouldFindTheConstantUpToTheLargestCode(Sparse value) {
            assertThat(table.of(value.code)).isEqualTo(value);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {Integer.MIN_VALUE, -1, 0x00, 0x02, 0x80, Integer.MAX_VALUE})
        void of_shouldBeNull_whenTheCodeIsUnknown(int code) {
            assertThat(table.of(code)).isNull();
        }
    }

    @Nested
    class Constructor {
        @Test
        void constructor_shouldRefuse_whenTwoConstantsShareACode() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new CodeTable<>(Shared.values(), value -> value.code))
                    .withMessageContaining("FIRST")
                    .withMessageContaining("SECOND");
        }

        @Test
        void constructor_shouldRefuse_whenACodeIsNegative() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new CodeTable<>(Negative.values(), value -> value.code))
                    .withMessageContaining("BELOW");
        }
    }
}
