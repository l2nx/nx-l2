/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.network.gameserverpackets.receive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.repository.AccountRepository;
import java.io.File;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.TaskScheduler;

// The packet reaches the controller through its static bridge, which every controller construction rebinds.
@Isolated
class RequestTempBanTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("0190a6b2-0000-7000-8000-000000000001");
    private static final String ADDRESS = "10.1.2.3";

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final GameServerLink server = mock(GameServerLink.class);

    private LoginController controller;

    @BeforeEach
    void createController() {
        controller =
                new LoginController(accounts, TestLoginProperties.create(new File(".")), mock(TaskScheduler.class));
        when(server.hasAccountOnGameServer(ACCOUNT_ID)).thenReturn(true);
    }

    @Nested
    class Receive {
        @Test
        void requestTempBan_shouldBanAccountAndAddress_whenLengthIsPositive() throws Exception {
            new RequestTempBan(packet(30), server);

            verify(accounts).updateBannedUntil(eq(ACCOUNT_ID), any());
            assertThat(controller.isBannedAddress(InetAddress.getByName(ADDRESS)))
                    .isTrue();
        }

        @ParameterizedTest(name = "{0} minutes")
        @ValueSource(longs = {0, -1})
        void requestTempBan_shouldBanNothing_whenLengthIsNotPositive(long minutes) throws Exception {
            new RequestTempBan(packet(minutes), server);

            verify(accounts, never()).updateBannedUntil(any(), any());
            assertThat(controller.isBannedAddress(InetAddress.getByName(ADDRESS)))
                    .isFalse();
        }
    }

    private static byte[] packet(long minutes) {
        final ByteBuffer buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0x0A);
        buffer.putLong(ACCOUNT_ID.getMostSignificantBits());
        buffer.putLong(ACCOUNT_ID.getLeastSignificantBits());
        for (char c : ADDRESS.toCharArray()) {
            buffer.putShort((short) c);
        }
        buffer.putShort((short) 0);
        buffer.putLong(minutes);
        buffer.put((byte) 0);
        return buffer.array();
    }
}
