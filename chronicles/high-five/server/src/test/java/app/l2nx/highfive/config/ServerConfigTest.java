/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@ResourceLock("ServerConfig")
class ServerConfigTest {
    @TempDir
    private Path dir;

    private int savedServerId;
    private byte[] savedHexId;

    @BeforeEach
    void keepConfig() {
        savedServerId = ServerConfig.SERVER_ID;
        savedHexId = ServerConfig.HEX_ID;
    }

    @AfterEach
    void restoreConfig() {
        ServerConfig.SERVER_ID = savedServerId;
        ServerConfig.HEX_ID = savedHexId;
    }

    // A leading zero byte and a high first byte are what a signed-number conversion mangles.
    static Stream<Arguments> hexIds() {
        final byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        return Stream.of(
                Arguments.of("leading zero byte", new byte[] {0x00, 0x11}),
                Arguments.of("high first byte", new byte[] {(byte) 0xFF, (byte) 0x80}),
                Arguments.of("random 16 bytes", random));
    }

    @Nested
    class SaveAndLoad {
        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.highfive.config.ServerConfigTest#hexIds")
        void loadHexid_shouldReturnTheSavedHexIdByteForByte(String name, byte[] hexId) {
            final String file = dir.resolve("hexid.txt").toString();

            ServerConfig.saveHexid(5, HexFormat.of().formatHex(hexId), file);
            ServerConfig.loadHexid(file);

            assertThat(ServerConfig.SERVER_ID).isEqualTo(5);
            assertThat(ServerConfig.HEX_ID).isEqualTo(hexId);
        }

        @Test
        void saveHexid_shouldUseTheRegisteredIdAtOnce_withoutARestart() {
            final String file = dir.resolve("hexid.txt").toString();
            ServerConfig.loadHexid(file);

            ServerConfig.saveHexid(7, "00ff", file);

            assertThat(ServerConfig.SERVER_ID).isEqualTo(7);
            assertThat(ServerConfig.HEX_ID).isEqualTo(new byte[] {0x00, (byte) 0xff});
        }

        @Test
        void saveHexid_shouldReplaceTheStoredId_whenLoginRegisteredAnotherOne() {
            final String file = dir.resolve("hexid.txt").toString();
            ServerConfig.saveHexid(1, "0a0b", file);

            ServerConfig.saveHexid(2, "0c0d", file);
            ServerConfig.loadHexid(file);

            assertThat(ServerConfig.SERVER_ID).isEqualTo(2);
            assertThat(ServerConfig.HEX_ID).isEqualTo(new byte[] {0x0c, 0x0d});
        }
    }

    @Nested
    class LoadHexid {
        @Test
        void loadHexid_shouldLeaveTheHexIdUnset_whenTheFileIsMissing() {
            ServerConfig.HEX_ID = new byte[] {1};

            ServerConfig.loadHexid(dir.resolve("hexid.txt").toString());

            assertThat(ServerConfig.HEX_ID).isNull();
        }

        @ParameterizedTest(name = "HexID={0}")
        @ValueSource(strings = {"-80", "abc", "zz", ""})
        void loadHexid_shouldLeaveTheHexIdUnset_whenTheHexIdIsNotEvenLengthHex(String hexId) throws IOException {
            final Path file = dir.resolve("hexid.txt");
            Files.writeString(file, "ServerID=1\nHexID=" + hexId + "\n");
            ServerConfig.HEX_ID = new byte[] {1};

            ServerConfig.loadHexid(file.toString());

            assertThat(ServerConfig.HEX_ID).isNull();
        }

        @Test
        void loadHexid_shouldLeaveTheHexIdUnset_whenTheServerIdIsMissing() throws IOException {
            final Path file = dir.resolve("hexid.txt");
            Files.writeString(file, "HexID=0a0b\n");

            ServerConfig.loadHexid(file.toString());

            assertThat(ServerConfig.HEX_ID).isNull();
        }
    }
}
