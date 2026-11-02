/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.appearance.PlayerAppearance;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.character.model.CharacterClanMember;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ClanMemberTest {
    private static final int OBJECT_ID = 268_480_000;
    private static final int APPRENTICE_ID = 268_480_001;

    private final Clan clan = new Clan(268_435_456, "Clan");
    private final ClanMember member = new ClanMember(clan, row(OBJECT_ID, "Stored", 0));

    private static CharacterClanMember row(int objectId, String name, int apprenticeId) {
        return CharacterClanMember.builder()
                .objectId(objectId)
                .name(name)
                .level(40)
                .classId(PlayerClass.WARRIOR.getId())
                .title("Stored title")
                .powerGrade(6)
                .apprenticeId(apprenticeId)
                .sex(Sex.FEMALE)
                .race(Race.ELF)
                .build();
    }

    private static Player player(String name, int level) {
        final Player player = mock(Player.class);
        final PlayerAppearance appearance = mock(PlayerAppearance.class);
        when(player.getObjectId()).thenReturn(OBJECT_ID);
        when(player.getName()).thenReturn(name);
        when(player.getLevel()).thenReturn(level);
        when(player.getPlayerClass()).thenReturn(PlayerClass.KNIGHT);
        when(player.getRace()).thenReturn(Race.HUMAN);
        when(player.getAppearance()).thenReturn(appearance);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ClanMember> call) {
            assertThatThrownBy(() -> call.accept(member))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ClanMember>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getClan (accessor)", ClanMember::getClan),
                    Named.of("getPlayer (accessor)", ClanMember::getPlayer),
                    Named.of("setPlayer", m -> m.setPlayer(null)),
                    Named.of("isOnline", ClanMember::isOnline),
                    Named.of("getName", ClanMember::getName),
                    Named.of("getObjectId", ClanMember::getObjectId),
                    Named.of("getLevel", ClanMember::getLevel),
                    Named.of("getPledgeType", ClanMember::getPledgeType),
                    Named.of("setPowerGrade", m -> m.setPowerGrade(1)),
                    Named.of("setApprenticeAndSponsor", m -> m.setApprenticeAndSponsor(0, 0)),
                    Named.of("getApprenticeOrSponsorName", ClanMember::getApprenticeOrSponsorName),
                    Named.of("calculatePledgeClass (static)", _ -> ClanMember.calculatePledgeClass(null)));
        }
    }

    @Nested
    class Getters {
        @Test
        void getName_shouldReturnStoredData_whenMemberIsOffline() {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> List.of(
                    member.getName(),
                    member.getLevel(),
                    member.getClassId(),
                    member.getTitle(),
                    member.getSex(),
                    member.isOnline()));

            assertThat(actual).containsExactly("Stored", 40, PlayerClass.WARRIOR.getId(), "Stored title", true, false);
        }

        @Test
        void getName_shouldReturnPlayerData_whenMemberIsOnline() {
            final Player player = player("Renamed", 52);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                member.setPlayer(player);
                return List.of(member.getName(), member.getLevel(), member.getClassId(), member.isOnline());
            });

            assertThat(actual).containsExactly("Renamed", 52, PlayerClass.KNIGHT.getId(), true);
        }

        @Test
        void setPlayer_shouldKeepPlayerData_whenPlayerLogsOff() {
            final Player player = player("Renamed", 52);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                member.setPlayer(player);
                member.setPlayer(null);
                return List.of(member.getName(), member.getLevel(), member.getRaceOrdinal(), member.isOnline());
            });

            assertThat(actual).containsExactly("Renamed", 52, Race.HUMAN.ordinal(), false);
        }
    }

    @Nested
    class GetApprenticeOrSponsorName {
        @Test
        void getApprenticeOrSponsorName_shouldNameApprentice_whenApprenticeIsMember() {
            final String actual = WorldLoopExtension.callInPass(() -> {
                final ClanMember sponsor = new ClanMember(clan, row(OBJECT_ID + 10, "Sponsor", APPRENTICE_ID));
                clan.setLeader(new ClanMember(clan, row(APPRENTICE_ID, "Apprentice", 0)));
                return sponsor.getApprenticeOrSponsorName();
            });

            assertThat(actual).isEqualTo("Apprentice");
        }

        @Test
        void getApprenticeOrSponsorName_shouldReturnError_whenApprenticeLeftTheClan() {
            final String actual = WorldLoopExtension.callInPass(() ->
                    new ClanMember(clan, row(OBJECT_ID + 10, "Sponsor", APPRENTICE_ID)).getApprenticeOrSponsorName());

            assertThat(actual).isEqualTo("Error");
        }

        @Test
        void getApprenticeOrSponsorName_shouldReturnNull_whenMemberHasNeither() {
            final String actual = WorldLoopExtension.callInPass(member::getApprenticeOrSponsorName);

            assertThat(actual).isNull();
        }
    }
}
