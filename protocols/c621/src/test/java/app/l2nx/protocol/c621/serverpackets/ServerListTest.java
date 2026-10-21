/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.c621.serverpackets;

import static app.l2nx.protocol.c621.GoldenBytes.read;
import static app.l2nx.protocol.c621.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.c621.serverpackets.ServerList.Characters;
import app.l2nx.protocol.c621.serverpackets.ServerList.Server;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerListTest {
    // 2 servers, last server 1
    private static final String HEADER = "02" + "01";
    // id 1, 192.168.0.10, port 7777, age 18, pvp, 25 of 1000 players, up, type 1, no brackets
    private static final String SERVER_1 =
            "01" + "c0a8000a" + "611e0000" + "12" + "01" + "1900" + "e803" + "01" + "01000000" + "00";
    // id 2, 10.0.0.2, port 7778, age 0, no pvp, 0 of 500 players, down, types 4 and 32, brackets
    private static final String SERVER_2 =
            "02" + "0a000002" + "621e0000" + "00" + "00" + "0000" + "f401" + "00" + "24000000" + "01";
    // unknown short, 2 servers with characters: 3 on server 1 (2 pending deletion: 3600 s, 60 s), 1 on server 2
    private static final String CHARACTERS =
            "0000" + "02" + "01" + "03" + "02" + "100e0000" + "3c000000" + "02" + "01" + "00";
    private static final String BODY = HEADER + SERVER_1 + SERVER_2 + CHARACTERS;

    private static final ServerList PACKET = ServerList.builder()
            .servers(List.of(
                    Server.builder()
                            .id(1)
                            .ip(new byte[] {(byte) 192, (byte) 168, 0, 10})
                            .port(7777)
                            .ageLimit(18)
                            .pvp(true)
                            .currentPlayers(25)
                            .maxPlayers(1000)
                            .up(true)
                            .serverType(1)
                            .brackets(false)
                            .build(),
                    Server.builder()
                            .id(2)
                            .ip(new byte[] {10, 0, 0, 2})
                            .port(7778)
                            .ageLimit(0)
                            .pvp(false)
                            .currentPlayers(0)
                            .maxPlayers(500)
                            .up(false)
                            .serverType(4 | 32)
                            .brackets(true)
                            .build()))
            .lastServer(1)
            .characters(List.of(
                    Characters.builder()
                            .serverId(1)
                            .count(3)
                            .pendingDeletion(new int[] {3600, 60})
                            .build(),
                    Characters.builder()
                            .serverId(2)
                            .count(1)
                            .pendingDeletion(new int[0])
                            .build()))
            .build();

    @Nested
    class Write {
        @Test
        void write_shouldWriteTheServersThenTheCharactersPerServer() {
            assertThat(written(PACKET::write)).isEqualTo("04" + BODY);
        }

        @Test
        void write_shouldWriteTheEmptyList_whenNoServerIsOpenAndNoCharacterArrived() {
            final ServerList empty = ServerList.builder()
                    .servers(List.of())
                    .lastServer(1)
                    .characters(List.of())
                    .build();

            assertThat(written(empty::write)).isEqualTo("04" + "00" + "01" + "0000" + "00");
        }
    }

    @Nested
    class Read {
        @Test
        void read_shouldReadBackWhatWriteWrote() {
            final ServerList actual = read(BODY, ServerList::read);

            assertThat(actual).isEqualTo(PACKET);
        }

        @Test
        void read_shouldFail_whenTheBodyStopsInsideAServer() {
            final String body = HEADER + SERVER_1.substring(0, 10);

            assertThatThrownBy(() -> read(body, ServerList::read)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class Construct {
        @Test
        void server_shouldRefuseAnAddressThatIsNotIpv4() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Server.builder().ip(new byte[16]).build());
        }

        @Test
        void server_shouldKeepItsOwnCopy_whenTheCallerChangesTheAddress() {
            final byte[] ip = {10, 0, 0, 2};
            final Server server = PACKET.servers().get(1).toBuilder().ip(ip).build();

            ip[0] = 1;
            server.ip()[1] = 1;

            assertThat(server)
                    .isEqualTo(PACKET.servers().get(1))
                    .hasSameHashCodeAs(PACKET.servers().get(1));
        }

        @Test
        void characters_shouldKeepItsOwnCopy_whenTheCallerChangesTheDeletionTimes() {
            final int[] pendingDeletion = {3600, 60};
            final Characters characters = PACKET.characters().get(0).toBuilder()
                    .pendingDeletion(pendingDeletion)
                    .build();

            pendingDeletion[0] = 1;
            characters.pendingDeletion()[1] = 1;

            assertThat(characters)
                    .isEqualTo(PACKET.characters().get(0))
                    .hasSameHashCodeAs(PACKET.characters().get(0));
        }

        @Test
        void server_shouldNameTheMissingAddress() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Server.builder().build())
                    .withMessage("ip");
        }

        @Test
        void characters_shouldNameTheMissingDeletionTimes() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Characters.builder().build())
                    .withMessage("pendingDeletion");
        }

        @Test
        void serverList_shouldNameTheMissingList() {
            assertThatNullPointerException()
                    .isThrownBy(() -> PACKET.toBuilder().characters(null).build())
                    .withMessage("characters");
        }

        @Test
        void toString_shouldPrintTheAddressAndTheDeletionTimes() {
            assertThat(PACKET.toString()).contains("ip=192.168.0.10").contains("pendingDeletion=[3600, 60]");
        }
    }
}
