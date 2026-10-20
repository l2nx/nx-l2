/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.ClanHallTemplate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClanHallDataTest {

    private static final ClanHallData DATA = ClanHallData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedRow() {
            assertThat(DATA.getClanHalls()).hasSize(38);
        }

        @Test
        void load_shouldReadHallAttributes() {
            final ClanHallTemplate expected = ClanHallTemplate.builder()
                    .id(31)
                    .name("The Atramental Barracks")
                    .description("Clan hall located in the Town of Dion")
                    .location("Dion")
                    .lease(200000)
                    .grade(1)
                    .auctionStartingBid(8000000L)
                    .build();

            final ClanHallTemplate actual = DATA.getClanHall(31);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void load_shouldGiveEveryHallAnNpcAuction() {
            assertThat(DATA.getClanHalls())
                    .allSatisfy(hall -> assertThat(hall.auctionStartingBid()).isPositive());
        }
    }
}
