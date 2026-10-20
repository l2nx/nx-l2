/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.clan.ClanNoticeRepository;
import app.l2nx.highfive.repository.clan.ClanRankPrivilegeRepository;
import app.l2nx.highfive.repository.clan.ClanRepository;
import app.l2nx.highfive.repository.clan.ClanSkillRepository;
import app.l2nx.highfive.repository.clan.ClanSubPledgeRepository;
import app.l2nx.highfive.repository.clan.ClanWarRepository;
import app.l2nx.highfive.repository.residence.CastleStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

// The clan reaches its persistence through the static ClanTable bridge, which each test rebinds.
@Execution(ExecutionMode.SAME_THREAD)
class ClanTest {
    private static final int CLAN_ID = 268_435_456;
    private static final int PRIVILEGES = 0b1011;
    private static final int MAX_NOTICE_LENGTH = 8192;

    private final ClanNoticeRepository notices = mock(ClanNoticeRepository.class);
    private final ClanRankPrivilegeRepository rankPrivileges = mock(ClanRankPrivilegeRepository.class);

    private Clan clan;

    @BeforeEach
    void bindClanTable() {
        new ClanTable(
                mock(ClanRepository.class),
                notices,
                rankPrivileges,
                mock(ClanSkillRepository.class),
                mock(ClanSubPledgeRepository.class),
                mock(ClanWarRepository.class),
                mock(CastleStateRepository.class),
                mock(CharacterRepository.class),
                mock(TransactionTemplate.class));
        clan = new Clan(CLAN_ID, "Clan");
    }

    @Nested
    class SetRankPrivs {
        @ParameterizedTest(name = "rank {0}")
        @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 10, Integer.MAX_VALUE})
        void setRankPrivs_shouldIgnoreRank_whenOutsideOneToNine(int rank) {
            clan.setRankPrivs(rank, PRIVILEGES);

            verifyNoInteractions(rankPrivileges);
            assertThat(clan.getAllRankPrivs())
                    .extracting(Clan.RankPrivs::getRank)
                    .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9);
        }

        @ParameterizedTest(name = "rank {0}")
        @ValueSource(ints = {1, 5, 9})
        void setRankPrivs_shouldStorePrivileges_whenRankIsOneToNine(int rank) {
            clan.setRankPrivs(rank, PRIVILEGES);

            verify(rankPrivileges).upsert(CLAN_ID, rank, PRIVILEGES);
            assertThat(clan.getRankPrivs(rank).getMask()).isEqualTo(PRIVILEGES);
        }
    }

    @Nested
    class SetNotice {
        @Test
        void setNotice_shouldStoreNull_whenNoticeIsEmpty() {
            clan.setNotice("");

            verify(notices).upsert(CLAN_ID, null, false);
            assertThat(clan.getNotice()).isEmpty();
        }

        @Test
        void setNotice_shouldStoreNull_whenNoticeIsNull() {
            clan.setNotice(null);

            verify(notices).upsert(CLAN_ID, null, false);
        }

        @Test
        void setNotice_shouldCutNoticeToLimit_whenNoticeIsLonger() {
            final ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);

            clan.setNotice("a".repeat(MAX_NOTICE_LENGTH + 1));

            verify(notices).upsert(eq(CLAN_ID), stored.capture(), anyBoolean());
            assertThat(stored.getValue()).hasSize(MAX_NOTICE_LENGTH);
        }

        @Test
        void setNoticeEnabled_shouldKeepNotice_whenNoticeIsStored() {
            clan.setNotice("Siege at eight");

            clan.setNoticeEnabled(true);

            verify(notices).upsert(CLAN_ID, "Siege at eight", true);
            assertThat(clan.isNoticeEnabled()).isTrue();
        }
    }
}
