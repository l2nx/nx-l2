/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigReaderTest {
    @TempDir
    private Path dir;

    private ConfigReader reader(String content) throws IOException {
        final Path file = dir.resolve("Test.ini");
        Files.writeString(file, content);
        return new ConfigReader(file.toString());
    }

    @Nested
    class GetPositiveInt {
        @Test
        void getPositiveInt_shouldReturnValue_whenPositive() throws IOException {
            final int actual = reader("Period = 300").getPositiveInt("Period", 1);

            assertThat(actual).isEqualTo(300);
        }

        @Test
        void getPositiveInt_shouldReturnDefault_whenKeyIsAbsent() throws IOException {
            final int actual = reader("").getPositiveInt("Period", 5);

            assertThat(actual).isEqualTo(5);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"0", "-1"})
        void getPositiveInt_shouldThrowNamingKeyAndFile_whenNotPositive(String value) throws IOException {
            final ConfigReader reader = reader("Period = " + value);

            assertThatThrownBy(() -> reader.getPositiveInt("Period", 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Period")
                    .hasMessageContaining("Test.ini")
                    .hasMessageContaining(value);
        }
    }

    @Nested
    class GetPositiveLong {
        @Test
        void getPositiveLong_shouldReturnValue_whenPositive() throws IOException {
            final long actual = reader("Period = 604800000").getPositiveLong("Period", 1);

            assertThat(actual).isEqualTo(604_800_000L);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"0", "-1"})
        void getPositiveLong_shouldThrow_whenNotPositive(String value) throws IOException {
            final ConfigReader reader = reader("Period = " + value);

            assertThatThrownBy(() -> reader.getPositiveLong("Period", 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Period");
        }
    }
}
