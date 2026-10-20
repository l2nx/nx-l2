/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.GameClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

// The listing reads ServerConfig statics other tests write.
@ResourceLock("ServerConfig")
class RegistrationResultHandlerTest {
    private static final RegistrationResult RESULT = RegistrationResult.builder()
            .serverId(3)
            .serverName("Bartz")
            .hexId("1f")
            .build();

    private final FakeLoginPort port = new FakeLoginPort();
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final ServerListing listing = new ServerListing();
    private final RegistrationResultHandler handler = new RegistrationResultHandler(port, accounts, listing);

    @Test
    void accept_shouldSaveTheHexId_publishTheListing_andReportEveryHeldAccount() {
        final UUID accountId = UUID.randomUUID();
        accounts.enter(accountId, mock(GameClient.class));

        // saveHexid writes config/hexid.txt into the working directory, the datapack.
        try (MockedStatic<ServerConfig> config = mockStatic(ServerConfig.class)) {
            handler.accept(RESULT);

            config.verify(() -> ServerConfig.saveHexid(3, "1f"));
        }

        assertThat(listing.getServerName()).isEqualTo("Bartz");
        assertThat(port.getCalls())
                .containsExactly(
                        Call.of("publishListing", listing.full()), Call.of("accountsEntered", List.of(accountId)));
    }

    @Test
    void accept_shouldOnlyPublishTheListing_whenNoAccountIsHeld() {
        try (MockedStatic<ServerConfig> config = mockStatic(ServerConfig.class)) {
            handler.accept(RESULT);
        }

        assertThat(port.getCalls()).containsExactly(Call.of("publishListing", listing.full()));
    }
}
