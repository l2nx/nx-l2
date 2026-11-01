/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.DuelResult;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.partition.WorldLoopExtension;
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
import org.mockito.InOrder;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class DuelTest {
    private final Player playerA = mock(Player.class, "playerA");
    private final Player playerB = mock(Player.class, "playerB");

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Duel> call) {
            final Duel duel = WorldLoopExtension.callInPass(() -> new Duel(playerA, playerB, 0, 1));

            assertThatThrownBy(() -> call.accept(duel))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Duel>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId", Duel::getId),
                    Named.of("getPlayerA", Duel::getPlayerA),
                    Named.of("getPlayerB", Duel::getPlayerB),
                    Named.of("isPartyDuel", Duel::isPartyDuel),
                    Named.of("setFinished", duel -> duel.setFinished(true)),
                    Named.of("getFinished", Duel::getFinished),
                    Named.of("getDueldInstanceId", Duel::getDueldInstanceId),
                    Named.of("isDuelistInPvp", duel -> duel.isDuelistInPvp(false)),
                    Named.of("startDuel", Duel::startDuel),
                    Named.of("savePlayerConditions", Duel::savePlayerConditions),
                    Named.of("restorePlayerConditions", duel -> duel.restorePlayerConditions(false)),
                    Named.of("getRemainingTime", Duel::getRemainingTime),
                    Named.of("teleportPlayers", Duel::teleportPlayers),
                    Named.of("broadcastToTeam1", duel -> duel.broadcastToTeam1(ActionFailed.STATIC_PACKET)),
                    Named.of("broadcastToTeam2", duel -> duel.broadcastToTeam2(ActionFailed.STATIC_PACKET)),
                    Named.of("getWinner", Duel::getWinner),
                    Named.of("getLooser", Duel::getLooser),
                    Named.of("playKneelAnimation", Duel::playKneelAnimation),
                    Named.of("countdown", Duel::countdown),
                    Named.of("endDuel", duel -> duel.endDuel(DuelResult.CANCELED)),
                    Named.of("checkEndDuelCondition", Duel::checkEndDuelCondition),
                    Named.of("doSurrender", duel -> duel.doSurrender(null)),
                    Named.of("onPlayerDefeat", duel -> duel.onPlayerDefeat(null)),
                    Named.of("onRemoveFromParty", duel -> duel.onRemoveFromParty(null)),
                    Named.of("onBuff", duel -> duel.onBuff(null, null)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("conditionCalls")
        void playerConditionCall_shouldThrow_whenCalledOffTheLoop(Consumer<Duel.PlayerCondition> call) {
            final Duel.PlayerCondition condition = new Duel.PlayerCondition(null, false);

            assertThatThrownBy(() -> call.accept(condition))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Duel.PlayerCondition>>> conditionCalls() {
            return Stream.of(
                    Named.of("getPlayer", Duel.PlayerCondition::getPlayer),
                    Named.of("restoreCondition", Duel.PlayerCondition::restoreCondition),
                    Named.of("registerDebuff", condition -> condition.registerDebuff(null)),
                    Named.of("teleportBack", Duel.PlayerCondition::teleportBack));
        }
    }

    @Nested
    class EndCondition {
        @Test
        void checkEndDuelCondition_shouldCancel_whenADuelistLeft() {
            when(playerA.isOnline()).thenReturn(false);
            when(playerB.isOnline()).thenReturn(true);

            final DuelResult actual =
                    WorldLoopExtension.callInPass(() -> new Duel(playerA, playerB, 0, 1).checkEndDuelCondition());

            assertThat(actual).isEqualTo(DuelResult.CANCELED);
        }

        @Test
        void getWinner_shouldAnswerNull_whenTheDuelIsNotFinished() {
            final Player actual = WorldLoopExtension.callInPass(() -> new Duel(playerA, playerB, 0, 1).getWinner());

            assertThat(actual).isNull();
        }
    }

    @Nested
    class Conditions {
        @Test
        void restoreCondition_shouldRemoveTheDebuffsInTheOrderTheyLanded() {
            final Skill first = skill(1002);
            final Skill second = skill(1001);

            WorldLoopExtension.runInPass(() -> {
                final Duel.PlayerCondition condition = new Duel.PlayerCondition(playerA, false);
                condition.registerDebuff(first);
                condition.registerDebuff(second);
                condition.registerDebuff(first);
                condition.restoreCondition();
            });

            final InOrder order = inOrder(playerA);
            order.verify(playerA).stopSkillEffects(SkillFinishType.REMOVED, 1002);
            order.verify(playerA).stopSkillEffects(SkillFinishType.REMOVED, 1001);
            order.verifyNoMoreInteractions();
        }
    }

    private static Skill skill(int id) {
        final Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        return skill;
    }
}
