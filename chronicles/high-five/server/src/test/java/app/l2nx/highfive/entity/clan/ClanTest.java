/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
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
    private final ClanSubPledgeRepository subPledges = mock(ClanSubPledgeRepository.class);

    private Clan clan;

    @BeforeEach
    void bindClanTable() {
        new ClanTable(
                mock(ClanRepository.class),
                notices,
                rankPrivileges,
                mock(ClanSkillRepository.class),
                subPledges,
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

    @Nested
    class CreateSubPledge {
        private static final int CLAN_LEADER_ID = 268_435_457;
        private static final int REQUESTED_LEADER_ID = 268_435_458;

        private final Player player = mock(Player.class);

        @BeforeEach
        void setClanLeader() {
            final ClanMember clanLeader = mock(ClanMember.class);
            when(clanLeader.getObjectId()).thenReturn(CLAN_LEADER_ID);
            when(clanLeader.getClan()).thenReturn(clan);
            clan.setLeader(clanLeader);
        }

        @Test
        void createSubPledge_shouldSendAcademyMessage_whenAcademyAlreadyExists() {
            clan.createSubPledge(player, Clan.SUBUNIT_ACADEMY, 0, "Academy");

            final Clan.SubPledge actual = clan.createSubPledge(player, Clan.SUBUNIT_ACADEMY, 0, "Second");

            assertThat(actual).isNull();
            verify(player).sendPacket(SystemMessageId.YOUR_CLAN_HAS_ALREADY_ESTABLISHED_A_CLAN_ACADEMY);
            verify(player, never()).sendMessage(anyString());
            verify(subPledges).insert(anyInt(), anyInt(), anyString(), anyInt());
        }

        @Test
        void createSubPledge_shouldKeepAcademyLeaderless_whenLeaderIsRequested() {
            final Clan.SubPledge actual =
                    clan.createSubPledge(player, Clan.SUBUNIT_ACADEMY, REQUESTED_LEADER_ID, "Academy");

            verify(subPledges).insert(CLAN_ID, Clan.SUBUNIT_ACADEMY, "Academy", 0);
            assertThat(actual.getLeaderId()).isZero();
            assertThat(clan.getSubPledge(Clan.SUBUNIT_ACADEMY).getLeaderId()).isZero();
        }
    }
}
