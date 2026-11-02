/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.IntSnapshot;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.model.CharacterClanMember;
import app.l2nx.highfive.repository.clan.ClanNoticeRepository;
import app.l2nx.highfive.repository.clan.ClanRankPrivilegeRepository;
import app.l2nx.highfive.repository.clan.ClanRepository;
import app.l2nx.highfive.repository.clan.ClanSkillRepository;
import app.l2nx.highfive.repository.clan.ClanSubPledgeRepository;
import app.l2nx.highfive.repository.clan.ClanWarRepository;
import app.l2nx.highfive.repository.clan.model.ClanRow;
import app.l2nx.highfive.repository.residence.CastleStateRepository;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class ClanTest {
    private static final int CLAN_ID = 268_435_456;
    private static final int RESTORED_ID = 268_435_470;
    private static final int PRIVILEGES = 0b1011;
    private static final int MAX_NOTICE_LENGTH = 8192;

    private final ClanRepository clanRepository = mock(ClanRepository.class);
    private final ClanNoticeRepository notices = mock(ClanNoticeRepository.class);
    private final ClanRankPrivilegeRepository rankPrivileges = mock(ClanRankPrivilegeRepository.class);
    private final ClanSubPledgeRepository subPledges = mock(ClanSubPledgeRepository.class);
    private final CharacterRepository characters = mock(CharacterRepository.class);

    private MockedStatic<ItemManager> itemManager;
    private Clan clan;

    @BeforeEach
    void bindClanTable() {
        new ClanTable(
                clanRepository,
                notices,
                rankPrivileges,
                mock(ClanSkillRepository.class),
                subPledges,
                mock(ClanWarRepository.class),
                mock(CastleStateRepository.class),
                characters,
                mock(TransactionTemplate.class));
        itemManager = mockStatic(ItemManager.class);
        itemManager.when(ItemManager::getInstance).thenReturn(mock(ItemManager.class));
        clan = new Clan(CLAN_ID, "Clan");
    }

    @AfterEach
    void releaseItemManager() {
        itemManager.close();
    }

    private Clan restoredClan(int leaderId, int... memberIds) {
        final ClanRow row = ClanRow.builder()
                .clanId(RESTORED_ID)
                .name("Restored")
                .level(1)
                .leaderId(leaderId)
                .build();
        when(clanRepository.find(RESTORED_ID)).thenReturn(Optional.of(row));
        when(characters.findClanMembers(RESTORED_ID))
                .thenReturn(
                        IntStream.of(memberIds).mapToObj(ClanTest::memberRow).toList());
        return WorldLoopExtension.callInPass(() -> new Clan(RESTORED_ID));
    }

    private static CharacterClanMember memberRow(int objectId) {
        return CharacterClanMember.builder()
                .objectId(objectId)
                .name("member" + objectId)
                .level(40)
                .classId(0)
                .powerGrade(6)
                .sex(Sex.MALE)
                .race(Race.HUMAN)
                .build();
    }

    private static Player online(Clan clan, int objectId) {
        final Player player = mock(Player.class);
        when(player.getObjectId()).thenReturn(objectId);
        when(player.isOnline()).thenReturn(true);
        WorldLoopExtension.runInPass(() -> clan.getClanMember(objectId).setPlayer(player));
        return player;
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Clan> call) {
            assertThatThrownBy(() -> call.accept(clan))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Clan>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getName (accessor)", Clan::getName),
                    Named.of("setName (accessor)", c -> c.setName("Other")),
                    Named.of("getId (accessor)", Clan::getId),
                    Named.of("getLevel (accessor)", Clan::getLevel),
                    Named.of("setCastleId (accessor)", c -> c.setCastleId(1)),
                    Named.of("getWarehouse (accessor)", Clan::getWarehouse),
                    Named.of("getLeader", Clan::getLeader),
                    Named.of("setLeader", c -> c.setLeader(mock(ClanMember.class))),
                    Named.of("getMembers", Clan::getMembers),
                    Named.of("collectMembers", c -> c.collectMembers(new ArrayList<>())),
                    Named.of("getMembersCount", Clan::getMembersCount),
                    Named.of("getClanMember by id", c -> c.getClanMember(1)),
                    Named.of("getClanMember by name", c -> c.getClanMember("name")),
                    Named.of("isMember", c -> c.isMember(1)),
                    Named.of("getOnlineMembers", c -> c.getOnlineMembers(0)),
                    Named.of("getOnlineMembersCount", Clan::getOnlineMembersCount),
                    Named.of("broadcastToOnlineMembers", c -> c.broadcastToOnlineMembers(null)),
                    Named.of("broadcastClanStatus", Clan::broadcastClanStatus),
                    Named.of("collectWarList", c -> c.collectWarList(new IntArrayList())),
                    Named.of("collectAttackerList", c -> c.collectAttackerList(new IntArrayList())),
                    Named.of("getLeaderName", Clan::getLeaderName),
                    Named.of("getNotice", Clan::getNotice),
                    Named.of("isAtWarWith", c -> c.isAtWarWith(1)),
                    Named.of("setEnemyClan", c -> c.setEnemyClan(1)),
                    Named.of("deleteEnemyClan", c -> c.deleteEnemyClan(1)),
                    Named.of("deleteAttackerClan", c -> c.deleteAttackerClan(1)),
                    Named.of("isAtWar", Clan::isAtWar),
                    Named.of("getAllSkills", Clan::getAllSkills),
                    Named.of("getSkill", c -> c.getSkill(1)),
                    Named.of("addSkill", c -> c.addSkill(null)),
                    Named.of("getAllSubPledges", Clan::getAllSubPledges),
                    Named.of("getSubPledge", c -> c.getSubPledge(Clan.SUBUNIT_ACADEMY)),
                    Named.of("collectRankPrivs", c -> c.collectRankPrivs(new ArrayList<>())),
                    Named.of("getRankPrivs", c -> c.getRankPrivs(1)),
                    Named.of("addReputationScore (former synchronized)", c -> c.addReputationScore(1)),
                    Named.of("takeReputationScore (former synchronized)", c -> c.takeReputationScore(1)),
                    Named.of("setRank (former synchronized)", c -> c.setRank(1)),
                    Named.of("addSiegeKill (former atomic)", Clan::addSiegeKill),
                    Named.of("getSiegeDeaths (former atomic)", Clan::getSiegeDeaths),
                    Named.of("getLeaderSubPledge", c -> c.getLeaderSubPledge(1)));
        }

        @Test
        void subPledge_shouldThrow_whenCalledOffTheLoop() {
            final Clan.SubPledge subPledge = new Clan.SubPledge(Clan.SUBUNIT_ROYAL1, "Guard", 0);

            assertThatThrownBy(subPledge::getSkills)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        @Test
        void rankPrivs_shouldThrow_whenCalledOffTheLoop() {
            final Clan.RankPrivs rankPrivs = new Clan.RankPrivs(1, 0, PRIVILEGES);

            assertThatThrownBy(rankPrivs::getPrivs)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }
    }

    @Nested
    class GetMembers {
        @Test
        void getMembers_shouldKeepRestoreOrder_whenIdsAreNotSorted() {
            final Clan restored = restoredClan(10, 30, 10, 20);

            final List<Integer> actual = WorldLoopExtension.callInPass(() ->
                    restored.getMembers().stream().map(ClanMember::getObjectId).toList());

            assertThat(actual).containsExactly(30, 10, 20);
        }

        @Test
        void getMembers_shouldStayUnchanged_whenMemberIsAddedAfterwards() {
            final Clan restored = restoredClan(10, 10, 20);

            final List<ClanMember> snapshot = WorldLoopExtension.callInPass(() -> {
                final List<ClanMember> members = restored.getMembers();
                restored.setLeader(new ClanMember(restored, memberRow(30)));
                return members;
            });

            final List<String> actual = WorldLoopExtension.callInPass(
                    () -> snapshot.stream().map(ClanMember::getName).toList());
            assertThat(actual).containsExactly("member10", "member20");
            assertThatThrownBy(() -> snapshot.add(snapshot.getFirst()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void collectMembers_shouldAddMembersInOrder_whenTargetIsLease() {
            final Clan restored = restoredClan(10, 30, 10, 20);

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                try (Snapshot<ClanMember> members = ScratchBuffers.empty()) {
                    restored.collectMembers(members);
                    return members.stream().map(ClanMember::getObjectId).toList();
                }
            });

            assertThat(actual).containsExactly(30, 10, 20);
        }

        @Test
        void getLeader_shouldBeRestoredLeader_whenLeaderRowIsAmongMembers() {
            final Clan restored = restoredClan(20, 10, 20);

            final int actual =
                    WorldLoopExtension.callInPass(() -> restored.getLeader().getObjectId());

            assertThat(actual).isEqualTo(20);
        }
    }

    @Nested
    class Lookups {
        @Test
        void getClanMember_shouldFindMemberByIdAndName_whenMemberIsOffline() {
            final Clan restored = restoredClan(10, 10, 20);

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> List.of(
                    restored.getClanMember(20).getObjectId(),
                    restored.getClanMember("member20").getObjectId(),
                    restored.getMembersCount()));

            assertThat(actual).containsExactly(20, 20, 2);
        }

        @Test
        void getClanMember_shouldReturnNull_whenNoMemberMatches() {
            final Clan restored = restoredClan(10, 10);

            final List<Object> actual = WorldLoopExtension.callInPass(() ->
                    Arrays.asList(restored.getClanMember(99), restored.getClanMember("nobody"), restored.isMember(0)));

            assertThat(actual).containsExactly(null, null, false);
        }

        @Test
        void getLeaderName_shouldReturnNull_whenClanHasNoLeader() {
            final Clan restored = restoredClan(20, 20);

            final List<String> actual =
                    WorldLoopExtension.callInPass(() -> Arrays.asList(clan.getLeaderName(), restored.getLeaderName()));

            assertThat(actual).containsExactly(null, "member20");
        }

        @Test
        void getOnlineMembersCount_shouldCountOnlyOnlineMembers() {
            final Clan restored = restoredClan(10, 10, 20, 30);
            online(restored, 20);

            final int actual = WorldLoopExtension.callInPass(restored::getOnlineMembersCount);

            assertThat(actual).isEqualTo(1);
        }

        @Test
        void getOnlineMembers_shouldExcludeGivenMember() {
            final Clan restored = restoredClan(10, 10, 20, 30);
            online(restored, 20);
            final Player third = online(restored, 30);

            final List<Player> actual = WorldLoopExtension.callInPass(() -> restored.getOnlineMembers(20));

            assertThat(actual).containsExactly(third);
        }
    }

    @Nested
    class Broadcast {
        @Test
        void broadcastToOnlineMembers_shouldReachEveryOnlineMemberOnce_whenRecipientAddsMember() {
            final Clan restored = restoredClan(10, 10, 20, 30);
            final Player first = online(restored, 10);
            final Player second = online(restored, 30);
            final ServerPacket packet = mock(ServerPacket.class);
            final ClanMember joining = WorldLoopExtension.callInPass(() -> new ClanMember(restored, memberRow(40)));
            doAnswer(_ -> {
                        restored.setLeader(joining);
                        return null;
                    })
                    .when(first)
                    .sendPacket(packet);

            final int actual = WorldLoopExtension.callInPass(() -> {
                restored.broadcastToOnlineMembers(packet);
                return restored.getMembersCount();
            });

            verify(first, times(1)).sendPacket(packet);
            verify(second, times(1)).sendPacket(packet);
            assertThat(actual).isEqualTo(4);
        }

        @Test
        void broadcastToOtherOnlineMembers_shouldSkipGivenPlayer() {
            final Clan restored = restoredClan(10, 10, 20);
            final Player first = online(restored, 10);
            final Player second = online(restored, 20);
            final ServerPacket packet = mock(ServerPacket.class);

            WorldLoopExtension.runInPass(() -> restored.broadcastToOtherOnlineMembers(packet, first));

            verify(first, never()).sendPacket(packet);
            verify(second).sendPacket(packet);
        }
    }

    @Nested
    class Wars {
        @Test
        void collectWarList_shouldKeepDeclarationOrder_whenTargetIsLease() {
            final List<List<Integer>> actual = WorldLoopExtension.callInPass(() -> {
                clan.setEnemyClan(30);
                clan.setEnemyClan(10);
                clan.setEnemyClan(20);
                clan.setAttackerClan(50);
                clan.setAttackerClan(40);
                try (IntSnapshot wars = ScratchBuffers.intEmpty();
                        IntSnapshot attackers = ScratchBuffers.intEmpty()) {
                    clan.collectWarList(wars);
                    clan.collectAttackerList(attackers);
                    return List.of(List.copyOf(wars), List.copyOf(attackers));
                }
            });

            assertThat(actual).containsExactly(List.of(30, 10, 20), List.of(50, 40));
        }

        @Test
        void deleteEnemyClan_shouldEndWar_whenGivenClanId() {
            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                clan.setEnemyClan(30);
                clan.setAttackerClan(30);
                clan.deleteEnemyClan(30);
                clan.deleteAttackerClan(30);
                return List.of(clan.isAtWarWith(30), clan.isAtWarAttacker(30), clan.isAtWar());
            });

            assertThat(actual).containsExactly(false, false, false);
        }

        @Test
        void isAtWarWith_shouldBeTrue_whenWarIsDeclaredOnClan() {
            final Clan enemy = new Clan(CLAN_ID + 1, "Enemy");

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                clan.setEnemyClan(enemy);
                return List.of(clan.isAtWarWith(enemy), enemy.isAtWarWith(clan), clan.isAtWar());
            });

            assertThat(actual).containsExactly(true, false, true);
        }
    }

    @Nested
    class Skills {
        @Test
        void getSkill_shouldFindAddedSkill() {
            final Skill first = skill(1);

            final Skill actual = WorldLoopExtension.callInPass(() -> {
                clan.addSkill(skill(2));
                clan.addSkill(first);
                return clan.getSkill(1);
            });

            assertThat(actual).isSameAs(first);
        }

        @Test
        void getAllSkills_shouldKeepAddOrder() {
            final Skill second = skill(2);
            final Skill first = skill(1);

            final List<Skill> actual = WorldLoopExtension.callInPass(() -> {
                clan.addSkill(second);
                clan.addSkill(first);
                return clan.getAllSkills();
            });

            assertThat(actual).containsExactly(second, first);
        }

        @Test
        void getSkill_shouldReturnNull_whenClanHasNoSuchSkill() {
            final Skill actual = WorldLoopExtension.callInPass(() -> clan.getSkill(1));

            assertThat(actual).isNull();
        }

        @Test
        void isLearnableSubPledgeSkill_shouldBeFalse_whenClanHasNoSuchSubUnit() {
            final Skill skill = skill(1);
            when(skill.getLevel()).thenReturn(1);

            final boolean actual =
                    WorldLoopExtension.callInPass(() -> clan.isLearnableSubPledgeSkill(skill, Clan.SUBUNIT_ROYAL1));

            assertThat(actual).isFalse();
        }

        private static Skill skill(int id) {
            final Skill skill = mock(Skill.class);
            when(skill.getId()).thenReturn(id);
            return skill;
        }
    }

    @Nested
    class SiegeCounters {
        @Test
        void addSiegeKill_shouldCountUp() {
            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                clan.addSiegeKill();
                final int returned = clan.addSiegeKill();
                return List.of(returned, clan.getSiegeKills());
            });

            assertThat(actual).containsExactly(2, 2);
        }

        @Test
        void clearSiegeKills_shouldResetCount() {
            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                clan.addSiegeKill();
                clan.addSiegeDeath();
                clan.clearSiegeKills();
                return List.of(clan.getSiegeKills(), clan.getSiegeDeaths());
            });

            assertThat(actual).containsExactly(0, 1);
        }
    }

    @Nested
    class SetRankPrivs {
        @ParameterizedTest(name = "rank {0}")
        @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 10, Integer.MAX_VALUE})
        void setRankPrivs_shouldIgnoreRank_whenOutsideOneToNine(int rank) {
            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                clan.setRankPrivs(rank, PRIVILEGES);
                try (Snapshot<Clan.RankPrivs> grades = ScratchBuffers.empty()) {
                    clan.collectRankPrivs(grades);
                    return grades.stream().map(Clan.RankPrivs::getRank).toList();
                }
            });

            verifyNoInteractions(rankPrivileges);
            assertThat(actual).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        }

        @ParameterizedTest(name = "rank {0}")
        @ValueSource(ints = {1, 5, 9})
        void setRankPrivs_shouldStorePrivileges_whenRankIsOneToNine(int rank) {
            final int actual = WorldLoopExtension.callInPass(() -> {
                clan.setRankPrivs(rank, PRIVILEGES);
                return clan.getRankPrivs(rank).getMask();
            });

            verify(rankPrivileges).upsert(CLAN_ID, rank, PRIVILEGES);
            assertThat(actual).isEqualTo(PRIVILEGES);
        }

        @Test
        void setRankPrivs_shouldUpdateOnlyOnlineMembersOfRank() {
            final Clan restored = restoredClan(10, 10, 20);
            final Player ofRank = online(restored, 10);
            final Player otherRank = online(restored, 20);
            final ClanPrivileges ofRankPrivileges = new ClanPrivileges();
            when(ofRank.getPowerGrade()).thenReturn(5);
            when(ofRank.getClanPrivileges()).thenReturn(ofRankPrivileges);
            when(otherRank.getPowerGrade()).thenReturn(6);

            final int actual = WorldLoopExtension.callInPass(() -> {
                restored.setRankPrivs(5, PRIVILEGES);
                return ofRankPrivileges.getMask();
            });

            assertThat(actual).isEqualTo(PRIVILEGES);
            verify(ofRank).updateUserInfo();
            verify(otherRank, never()).updateUserInfo();
        }
    }

    @Nested
    class GetNotice {
        @Test
        void getNotice_shouldStripTags_whenNoticeHasMarkup() {
            final String actual = WorldLoopExtension.callInPass(() -> {
                clan.setNotice("<b>Siege</b> at eight");
                return clan.getNotice();
            });

            assertThat(actual).isEqualTo("Siege at eight");
        }

        @Test
        void getNotice_shouldReturnNull_whenNoticeCarriesBypassAction() {
            final String actual = WorldLoopExtension.callInPass(() -> {
                clan.setNotice("<a action=\"bypass -h admin\">x</a>");
                return clan.getNotice();
            });

            assertThat(actual).isNull();
        }
    }

    @Nested
    class SetNotice {
        @Test
        void setNotice_shouldStoreNull_whenNoticeIsEmpty() {
            final String actual = WorldLoopExtension.callInPass(() -> {
                clan.setNotice("");
                return clan.getNotice();
            });

            verify(notices).upsert(CLAN_ID, null, false);
            assertThat(actual).isNull();
        }

        @Test
        void setNotice_shouldStoreNull_whenNoticeIsNull() {
            WorldLoopExtension.runInPass(() -> clan.setNotice(null));

            verify(notices).upsert(CLAN_ID, null, false);
        }

        @Test
        void setNotice_shouldCutNoticeToLimit_whenNoticeIsLonger() {
            final ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);

            WorldLoopExtension.runInPass(() -> clan.setNotice("a".repeat(MAX_NOTICE_LENGTH + 1)));

            verify(notices).upsert(eq(CLAN_ID), stored.capture(), anyBoolean());
            assertThat(stored.getValue()).hasSize(MAX_NOTICE_LENGTH);
        }

        @Test
        void setNoticeEnabled_shouldKeepNotice_whenNoticeIsStored() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                clan.setNotice("Siege at eight");
                clan.setNoticeEnabled(true);
                return clan.isNoticeEnabled();
            });

            verify(notices).upsert(CLAN_ID, "Siege at eight", true);
            assertThat(actual).isTrue();
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
            WorldLoopExtension.runInPass(() -> clan.setLeader(clanLeader));
        }

        @Test
        void createSubPledge_shouldSendAcademyMessage_whenAcademyAlreadyExists() {
            final Clan.SubPledge actual = WorldLoopExtension.callInPass(() -> {
                clan.createSubPledge(player, Clan.SUBUNIT_ACADEMY, 0, "Academy");
                return clan.createSubPledge(player, Clan.SUBUNIT_ACADEMY, 0, "Second");
            });

            assertThat(actual).isNull();
            verify(player).sendPacket(SystemMessageId.YOUR_CLAN_HAS_ALREADY_ESTABLISHED_A_CLAN_ACADEMY);
            verify(player, never()).sendMessage(anyString());
            verify(subPledges).insert(anyInt(), anyInt(), anyString(), anyInt());
        }

        @Test
        void createSubPledge_shouldKeepAcademyLeaderless_whenLeaderIsRequested() {
            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                final Clan.SubPledge created =
                        clan.createSubPledge(player, Clan.SUBUNIT_ACADEMY, REQUESTED_LEADER_ID, "Academy");
                return List.of(
                        created.getLeaderId(),
                        clan.getSubPledge(Clan.SUBUNIT_ACADEMY).getLeaderId(),
                        clan.getAllSubPledges().size());
            });

            verify(subPledges).insert(CLAN_ID, Clan.SUBUNIT_ACADEMY, "Academy", 0);
            assertThat(actual).containsExactly(0, 0, 1);
        }

        @Test
        void addNewSkill_shouldAddSkillToSubPledgeOnly() {
            final Skill skill = mock(Skill.class);
            when(skill.getId()).thenReturn(7);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                clan.createSubPledge(player, Clan.SUBUNIT_ACADEMY, 0, "Academy");
                clan.addNewSkill(skill, Clan.SUBUNIT_ACADEMY);
                return Arrays.asList(
                        clan.getSubPledge(Clan.SUBUNIT_ACADEMY).getSkill(7),
                        clan.getSubPledge(Clan.SUBUNIT_ACADEMY).getSkills(),
                        clan.getSkill(7));
            });

            assertThat(actual).containsExactly(skill, List.of(skill), null);
            verify(player, never()).sendPacket(any(ServerPacket.class));
        }
    }
}
