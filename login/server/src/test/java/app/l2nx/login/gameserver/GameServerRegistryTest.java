/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gameserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.gameserver.model.GameServerInfo;
import app.l2nx.login.gameserver.model.Registration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.QueryTimeoutException;

class GameServerRegistryTest {
    private static final byte[] HEX_ID = {0x11, 0x22, 0x33, 0x44};
    private static final byte[] OTHER_HEX_ID = {0x09};

    @TempDir
    Path datapackRoot;

    private final GameServerRepository repository = mock(GameServerRepository.class);
    private GameServerTable table;

    @BeforeEach
    void loadTable() throws Exception {
        Files.createDirectories(datapackRoot.resolve("data"));
        Files.writeString(datapackRoot.resolve("data/servername.xml"), """
            <servers_list>
            \t<server id="1" name="Bartz" />
            \t<server id="2" name="Sieghardt" />
            </servers_list>
            """);
        table = new GameServerTable(repository, properties(true));
        table.load();
    }

    @Nested
    class Register {
        @Test
        void register_shouldPutTheServerIntoTheTableOnce() {
            final Registration actual = registry(true).register(1, HEX_ID, false);

            assertThat(actual).isInstanceOf(Registration.Accepted.class);
            verify(repository, times(1)).create(1, HEX_ID);
            assertThat(table.getRegisteredGameServerById(1).getHexId()).isEqualTo(HEX_ID);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.login.gameserver.GameServerTableTest#hexIds")
        void register_shouldPersistTheHexIdByteForByte(String name, byte[] hexId) {
            registry(true).register(1, hexId, false);

            verify(repository).create(1, hexId);
            assertThat(table.getRegisteredGameServerById(1).getHexId()).isEqualTo(hexId);
        }

        @Test
        void register_shouldGiveTheKnownServerBack_andStoreNothing_whenTheHexIdMatches() {
            final GameServerInfo known = new GameServerInfo(1, HEX_ID);
            table.register(1, known);

            final Registration actual = registry(false).register(1, HEX_ID.clone(), false);

            assertThat(actual).isEqualTo(new Registration.Accepted(known));
            verify(repository, never()).create(anyInt(), any());
        }

        @Test
        void register_shouldBeRefused_whenNewServersAreNotAccepted() {
            final Registration actual = registry(false).register(1, HEX_ID, false);

            assertThat(reason(actual)).isEqualTo(Registration.Reason.NOT_ACCEPTED);
            assertThat(table.getRegisteredGameServerById(1)).isNull();
            verify(repository, never()).create(anyInt(), any());
        }

        @Test
        void register_shouldGiveTheNextFreeId_whenTheHexIdDiffersAndAnotherIdIsAccepted() {
            table.register(1, new GameServerInfo(1, OTHER_HEX_ID));

            final Registration actual = registry(true).register(1, HEX_ID, true);

            assertThat(actual)
                    .isInstanceOfSatisfying(
                            Registration.Accepted.class,
                            accepted -> assertThat(accepted.server().getId()).isEqualTo(2));
            verify(repository).create(2, HEX_ID);
        }

        @ParameterizedTest(name = "accept new {0}, accept another id {1}")
        @CsvSource({"true, false", "false, true", "false, false"})
        void register_shouldBeRefused_whenTheHexIdDiffersAndNoOtherIdMayBeGiven(
                boolean acceptNew, boolean acceptAlternativeId) {
            table.register(1, new GameServerInfo(1, OTHER_HEX_ID));

            final Registration actual = registry(acceptNew).register(1, HEX_ID, acceptAlternativeId);

            assertThat(reason(actual)).isEqualTo(Registration.Reason.HEX_ID_MISMATCH);
            verify(repository, never()).create(anyInt(), any());
        }

        @Test
        void register_shouldBeRefused_whenNoFreeIdIsLeft() {
            table.register(1, new GameServerInfo(1, OTHER_HEX_ID));
            table.register(2, new GameServerInfo(2, OTHER_HEX_ID));

            final Registration actual = registry(true).register(1, HEX_ID, true);

            assertThat(reason(actual)).isEqualTo(Registration.Reason.NO_FREE_ID);
        }

        @Test
        void register_shouldGiveTheNextFreeId_whenAnotherServerTakesTheDesiredIdMeanwhile_andAnotherIdIsAccepted() {
            final GameServerInfo winner = new GameServerInfo(1, OTHER_HEX_ID);

            final Registration actual = racingRegistry(winner).register(1, HEX_ID, true);

            assertThat(actual)
                    .isInstanceOfSatisfying(
                            Registration.Accepted.class,
                            accepted -> assertThat(accepted.server().getId()).isEqualTo(2));
            assertThat(table.getRegisteredGameServerById(1)).isSameAs(winner);
            verify(repository).create(2, HEX_ID);
        }

        @Test
        void register_shouldBeRefused_whenAnotherServerTakesTheDesiredIdMeanwhile_andNoOtherIdMayBeGiven() {
            final Registration actual =
                    racingRegistry(new GameServerInfo(1, OTHER_HEX_ID)).register(1, HEX_ID, false);

            assertThat(reason(actual)).isEqualTo(Registration.Reason.ID_TAKEN);
            verify(repository, never()).create(anyInt(), any());
        }

        private GameServerRegistry racingRegistry(GameServerInfo winner) {
            final GameServerTable racing = spy(table);
            doAnswer(invocation -> {
                        table.register(winner.getId(), winner);
                        return invocation.callRealMethod();
                    })
                    .when(racing)
                    .register(eq(winner.getId()), any());
            return new GameServerRegistry(racing, repository, properties(true));
        }

        @Test
        void register_shouldKeepTheServerForThisRun_whenStoringItFails() {
            doThrow(new QueryTimeoutException("statement timeout"))
                    .when(repository)
                    .create(1, HEX_ID);

            final Registration actual = registry(true).register(1, HEX_ID, false);

            assertThat(actual).isInstanceOf(Registration.Accepted.class);
            assertThat(table.getRegisteredGameServerById(1)).isNotNull();
        }
    }

    @Nested
    class StoreChronicle {
        @Test
        void storeChronicle_shouldWriteTheChronicleOfTheServer() {
            registry(true).storeChronicle(1, "high-five");

            verify(repository).updateChronicle(1, "high-five");
        }
    }

    private GameServerRegistry registry(boolean acceptNew) {
        return new GameServerRegistry(table, repository, properties(acceptNew));
    }

    private LoginProperties properties(boolean acceptNew) {
        return TestLoginProperties.create(datapackRoot.toFile()).toBuilder()
                .gameServer(LoginProperties.GameServer.builder()
                        .acceptNew(acceptNew)
                        .build())
                .build();
    }

    private static Registration.Reason reason(Registration registration) {
        assertThat(registration).isInstanceOf(Registration.Refused.class);
        return ((Registration.Refused) registration).reason();
    }
}
