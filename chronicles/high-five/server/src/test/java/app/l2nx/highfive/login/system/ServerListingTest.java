/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.system;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.ListingAttribute;
import app.l2nx.highfive.login.ListingStatus;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ResourceLock("ServerConfig")
class ServerListingTest {
    private final ServerListing listing = new ServerListing();

    private boolean bracket;
    private int type;
    private int age;
    private int maximumOnline;
    private boolean gmOnly;

    @BeforeEach
    void saveConfiguration() {
        bracket = ServerConfig.SERVER_LIST_BRACKET;
        type = ServerConfig.SERVER_LIST_TYPE;
        age = ServerConfig.SERVER_LIST_AGE;
        maximumOnline = ServerConfig.MAXIMUM_ONLINE_USERS;
        gmOnly = GeneralConfig.SERVER_GMONLY;
    }

    @AfterEach
    void restoreConfiguration() {
        ServerConfig.SERVER_LIST_BRACKET = bracket;
        ServerConfig.SERVER_LIST_TYPE = type;
        ServerConfig.SERVER_LIST_AGE = age;
        ServerConfig.MAXIMUM_ONLINE_USERS = maximumOnline;
        GeneralConfig.SERVER_GMONLY = gmOnly;
    }

    @Nested
    class Full {
        @Test
        void full_shouldDescribeTheWholeEntry_fromTheConfiguration() {
            ServerConfig.SERVER_LIST_BRACKET = true;
            ServerConfig.SERVER_LIST_TYPE = 0x05;
            ServerConfig.SERVER_LIST_AGE = 18;
            ServerConfig.MAXIMUM_ONLINE_USERS = 500;
            GeneralConfig.SERVER_GMONLY = true;

            final Map<ListingAttribute, Integer> actual = listing.full();

            assertThat(actual)
                    .isEqualTo(Map.of(
                            ListingAttribute.SQUARE_BRACKET, 1,
                            ListingAttribute.TYPE, 0x05,
                            ListingAttribute.STATUS, ListingStatus.GM_ONLY.code(),
                            ListingAttribute.AGE, 18,
                            ListingAttribute.MAX_PLAYERS, 500));
        }

        @Test
        void full_shouldCarryTheRuntimeStatus_whenTheServerSetIt() {
            GeneralConfig.SERVER_GMONLY = false;
            listing.setStatus(ListingStatus.DOWN);

            final Map<ListingAttribute, Integer> actual = listing.full();

            assertThat(actual).containsEntry(ListingAttribute.STATUS, ListingStatus.DOWN.code());
        }
    }

    @Nested
    class MaxPlayers {
        @Test
        void getMaxPlayers_shouldFollowTheConfiguration_untilSet() {
            ServerConfig.MAXIMUM_ONLINE_USERS = 500;
            assertThat(listing.getMaxPlayers()).isEqualTo(500);

            listing.setMaxPlayers(10);

            assertThat(listing.getMaxPlayers()).isEqualTo(10);
        }
    }

    @Nested
    class AgeCode {
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"0, 0", "15, 15", "18, 18", "12, 0"})
        void ageCode_shouldKeepOnlyTheAgesTheListKnows(int age, int expected) {
            assertThat(ServerListing.ageCode(age)).isEqualTo(expected);
        }
    }
}
