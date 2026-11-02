/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.undergroundColiseum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.highfive.config.UndergroundColiseumConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.mechanics.variables.PlayerVariables;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExShowScreenMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("UndergroundColiseumConfig")
class UCArenaTest {
    private static final int CURATOR = 32377;
    private static final int TOWER = 32378;
    private static final int PARTY_SIZE = 7;

    private final Player member = mock(Player.class);
    private final List<Long> shownAt = new ArrayList<>();
    private final List<ExShowScreenMessage> shown = new ArrayList<>();
    private MockedStatic<SpawnTable> spawnTables;
    private int partySize;
    private UCArena arena;
    private long start;

    @BeforeEach
    void createArena() {
        partySize = UndergroundColiseumConfig.UC_PARTY_SIZE;
        UndergroundColiseumConfig.UC_PARTY_SIZE = PARTY_SIZE;
        spawnTables = mockStatic(SpawnTable.class);
        spawnTables.when(SpawnTable::getInstance).thenReturn(mock(SpawnTable.class));
        stubVariables(member);
        doAnswer(invocation -> {
                    if (invocation.getArgument(0) instanceof ExShowScreenMessage message) {
                        shownAt.add(WorldPartition.loop().gameNow() - start);
                        shown.add(message);
                    }
                    return null;
                })
                .when(member)
                .sendPacket(any(ServerPacket.class));

        arena = new UCArena(1, CURATOR, 1, 85);
        final List<Player> leaders = List.of(member, player());
        for (int i = 0; i < 2; i++) {
            final UCTeam team = new UCTeam(i, arena, 0, 0, 0, TOWER);
            team.setParty(party(leaders.get(i)));
            arena.setUCTeam(i, team);
        }
        for (int i = 0; i < 4; i++) {
            arena.setUCPoint(i, new UCPoint(List.of(), new Location(0, 0, 0)));
        }
    }

    @AfterEach
    void restore() {
        WorldLoopExtension.runInPass(() -> arena.switchStatus(false));
        spawnTables.close();
        UndergroundColiseumConfig.UC_PARTY_SIZE = partySize;
    }

    @Nested
    class PrepareStart {
        @Test
        void prepareStart_shouldCountDownOnLoopTimers_whenTheMatchIsAboutToBegin() {
            prepare();

            for (int second = 1; second <= 60; second++) {
                WorldLoopExtension.passAfter(Duration.ofSeconds(1));
            }

            final int remaining = NpcStringId.S1_SECOND_S_REMAINING.getId();
            assertThat(shownAt)
                    .containsExactly(0L, 30_000L, 50_000L, 55_000L, 56_000L, 57_000L, 58_000L, 59_000L, 60_000L);
            assertThat(shown)
                    .extracting("npcString", "time", "parameters")
                    .containsExactly(
                            tuple(
                                    NpcStringId.MATCH_BEGINS_IN_S1_MINUTE_S_PLEASE_GATHER_AROUND_THE_ADMINISTRATOR
                                            .getId(),
                                    5000,
                                    List.of("1")),
                            tuple(remaining, 5000, List.of("30")),
                            tuple(remaining, 3000, List.of("10")),
                            tuple(remaining, 1000, List.of("5")),
                            tuple(remaining, 1000, List.of("4")),
                            tuple(remaining, 1000, List.of("3")),
                            tuple(remaining, 1000, List.of("2")),
                            tuple(remaining, 1000, List.of("1")),
                            tuple(
                                    NpcStringId
                                            .THE_MATCH_IS_AUTOMATICALLY_CANCELED_BECAUSE_YOU_ARE_TOO_FAR_FROM_THE_ADMISSION_MANAGER
                                            .getId(),
                                    5000,
                                    List.of()));
            assertThat(arena.isBattleNow()).isFalse();
        }

        @Test
        void prepareStart_shouldStopTheCountdown_whenTheArenaStops(Loop world) {
            final long idleTimers = world.timers(TimeBase.GAME).size();
            prepare();
            WorldLoopExtension.passAfter(Duration.ofSeconds(30));

            WorldLoopExtension.runInPass(() -> arena.switchStatus(false));
            for (int second = 1; second <= 60; second++) {
                WorldLoopExtension.passAfter(Duration.ofSeconds(1));
            }

            assertThat(shownAt).containsExactly(0L, 30_000L);
            assertThat(world.timers(TimeBase.GAME).size()).isEqualTo(idleTimers);
            assertThat(arena.isBattleNow()).isFalse();
        }
    }

    @Nested
    class RemoveTeams {
        @Test
        void removeTeams_shouldReturnTheMemberToTheSavedSpotAndForgetIt_whenARestoreLocationIsSet() {
            final PlayerVariables variables = member.getVariables();
            when(variables.getString(PlayerVariables.RESTORE_LOCATION, null)).thenReturn("10;20;30");

            WorldLoopExtension.runInPass(arena::removeTeams);

            verify(member).teleToLocation(10, 20, 30, true);
            verify(variables).remove(PlayerVariables.RESTORE_LOCATION);
        }

        @Test
        void removeTeams_shouldSendTheMemberToTown_whenNoRestoreLocationIsSet() {
            WorldLoopExtension.runInPass(arena::removeTeams);

            verify(member).teleToLocation(TeleportWhereType.TOWN);
            verify(member.getVariables(), never()).remove(PlayerVariables.RESTORE_LOCATION);
        }
    }

    private void prepare() {
        WorldLoopExtension.runInPass(() -> {
            start = WorldPartition.loop().gameNow();
            arena.prepareStart();
        });
    }

    private static Party party(Player player) {
        final Party party = mock(Party.class);
        doAnswer(call -> {
                    final List<Player> target = call.getArgument(0);
                    target.add(player);
                    return null;
                })
                .when(party)
                .collectMembers(any());
        return party;
    }

    private static Player player() {
        final Player player = mock(Player.class);
        stubVariables(player);
        return player;
    }

    private static void stubVariables(Player player) {
        final PlayerVariables variables = mock(PlayerVariables.class);
        when(player.getVariables()).thenReturn(variables);
    }
}
