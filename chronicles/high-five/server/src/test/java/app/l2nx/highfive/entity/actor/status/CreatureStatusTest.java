/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.stat.PlayerStat;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
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

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class CreatureStatusTest {
    private static final int NPC_ID = 20_545;

    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<CreatureStatus> call) {
            final CreatureStatus status =
                    WorldLoopExtension.callInPass(() -> creature().getStatus());

            assertThatThrownBy(() -> call.accept(status))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<CreatureStatus>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getActiveChar (AttackableStatus override)", CreatureStatus::getActiveChar),
                    Named.of("getCurrentHp (accessor)", CreatureStatus::getCurrentHp),
                    Named.of("getCurrentMp (accessor)", CreatureStatus::getCurrentMp),
                    Named.of("setCurrentHp", status -> status.setCurrentHp(1)),
                    Named.of("reduceHp (AttackableStatus override)", status -> status.reduceHp(1, null)),
                    Named.of("addStatusListener", status -> status.addStatusListener(null)),
                    Named.of("removeStatusListener", status -> status.removeStatusListener(null)),
                    Named.of("hasStatusListeners", CreatureStatus::hasStatusListeners),
                    Named.of("forEachStatusListener", status -> status.forEachStatusListener(_ -> {})),
                    Named.of("startHpMpRegeneration", CreatureStatus::startHpMpRegeneration),
                    Named.of("stopHpMpRegeneration", CreatureStatus::stopHpMpRegeneration));
        }

        @Test
        void hasStatusListeners_shouldAnswer_whenCalledOnTheLoop() {
            final boolean actual =
                    WorldLoopExtension.callInPass(() -> creature().getStatus().hasStatusListeners());

            assertThat(actual).isFalse();
        }
    }

    @Nested
    class StatusListeners {
        @Test
        void forEachStatusListener_shouldSendInTheOrderTheListenersWereAdded() {
            final List<Creature> received = new ArrayList<>();
            final List<Creature> expected = WorldLoopExtension.callInPass(() -> {
                final CreatureStatus status = creature().getStatus();
                final List<Creature> byObjectId =
                        List.of(new Listener(received), new Listener(received), new Listener(received));
                // Added against the object id order, so a set ordered by id or hash cannot pass.
                final List<Creature> added = byObjectId.reversed();
                added.forEach(status::addStatusListener);
                sendStatus(status);
                return added;
            });

            assertThat(received).containsExactlyElementsOf(expected);
        }

        @Test
        void forEachStatusListener_shouldReachTheOthers_whenAListenerLeavesWhileNotified() {
            final List<Creature> received = new ArrayList<>();
            final List<Creature> expected = WorldLoopExtension.callInPass(() -> {
                final CreatureStatus status = creature().getStatus();
                final Listener leaving = new Listener(received);
                final Listener staying = new Listener(received);
                leaving.onPacket = () -> status.removeStatusListener(leaving);
                status.addStatusListener(leaving);
                status.addStatusListener(staying);
                sendStatus(status);
                sendStatus(status);
                return List.of(leaving, staying, staying);
            });

            assertThat(received).containsExactlyElementsOf(expected);
        }

        @Test
        void forEachStatusListener_shouldReachAListenerFromTheNextSend_whenItIsAddedWhileNotified() {
            final List<Creature> received = new ArrayList<>();
            final List<Creature> expected = WorldLoopExtension.callInPass(() -> {
                final CreatureStatus status = creature().getStatus();
                final Listener first = new Listener(received);
                final Listener joining = new Listener(received);
                first.onPacket = () -> status.addStatusListener(joining);
                status.addStatusListener(first);
                sendStatus(status);
                sendStatus(status);
                return List.of(first, first, joining);
            });

            assertThat(received).containsExactlyElementsOf(expected);
        }

        @Test
        void addStatusListener_shouldKeepOneEntry_whenAddedTwice() {
            final List<Creature> received = new ArrayList<>();
            final Creature expected = WorldLoopExtension.callInPass(() -> {
                final CreatureStatus status = creature().getStatus();
                final Listener listener = new Listener(received);
                status.addStatusListener(listener);
                status.addStatusListener(listener);
                sendStatus(status);
                return listener;
            });

            assertThat(received).containsExactly(expected);
        }

        @Test
        void addStatusListener_shouldIgnoreTheOwner() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                creature.getStatus().addStatusListener(creature);
                return creature.getStatus().hasStatusListeners();
            });

            assertThat(actual).isFalse();
        }

        @Test
        void removeStatusListener_shouldLeaveNoListeners_whenTheLastOneLeaves() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final CreatureStatus status = creature().getStatus();
                final Listener listener = new Listener(new ArrayList<>());
                status.addStatusListener(listener);
                status.removeStatusListener(listener);
                return status.hasStatusListeners();
            });

            assertThat(actual).isFalse();
        }
    }

    @Nested
    class CurrentHp {
        @Test
        void setCurrentHp_shouldCapAtTheMaxHp() {
            final double[] actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                creature.getStatus().setCurrentHp(Double.MAX_VALUE);
                return new double[] {creature.getStatus().getCurrentHp(), creature.getMaxHp()};
            });

            assertThat(actual[0]).isEqualTo(actual[1]);
        }
    }

    @Nested
    class ReduceHp {
        @Test
        void reduceHp_shouldReduceTheCurrentHp_whenTheCreatureIsAnAttackable() {
            final double actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                final CreatureStatus status = creature.getStatus();
                status.setCurrentHp(creature.getMaxHp());
                status.reduceHp(100, creature());
                status.stopHpMpRegeneration();
                return creature.getMaxHp() - status.getCurrentHp();
            });

            assertThat(actual).isEqualTo(100.0);
        }

        @Test
        void reduceHp_shouldRecordTheAttacker_whenTheCreatureIsAnAttackable() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                final Creature attacker = creature();
                final CreatureStatus status = creature.getStatus();
                status.setCurrentHp(creature.getMaxHp());
                status.reduceHp(100, attacker);
                status.stopHpMpRegeneration();
                return creature.getAttackByList().contains(attacker);
            });

            assertThat(actual).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("overloads")
        void reduceHp_shouldIgnoreTheDamage_whenTheCreatureIsFolk(Consumer<CreatureStatus> overload) {
            final Npc folk = mock(Npc.class);
            final FolkStatus status = new FolkStatus(folk);

            WorldLoopExtension.runInPass(() -> overload.accept(status));

            verifyNoInteractions(folk);
        }

        static Stream<Named<Consumer<CreatureStatus>>> overloads() {
            final Creature attacker = mock(Creature.class);
            return Stream.of(
                    Named.of("reduceHp(value, attacker)", status -> status.reduceHp(100, attacker)),
                    Named.of(
                            "reduceHp(value, attacker, isHpConsumption)",
                            status -> status.reduceHp(100, attacker, false)),
                    Named.of(
                            "reduceHp(value, attacker, awake, isDOT, isHpConsumption)",
                            status -> status.reduceHp(100, attacker, true, false, false)));
        }

        @Test
        void reduceHp_shouldWakeThePlayerAndLetCpAbsorbTheDamage_whenCalledWithTwoArguments() throws Exception {
            final Player player = mock(Player.class);
            final PlayerStat stat = mock(PlayerStat.class);
            when(player.getStat()).thenReturn(stat);
            when(stat.getMaxCp()).thenReturn(2_000);
            when(player.getCurrentHp()).thenReturn(1_000.0);
            final Creature attacker = mock(Creature.class);
            when(attacker.isPlayable()).thenReturn(true);
            final PlayerStatus status = new PlayerStatus(player);
            final Field currentCp = PlayerStatus.class.getDeclaredField("currentCp");
            currentCp.setAccessible(true);
            currentCp.setDouble(status, 1_000);

            final double actual = WorldLoopExtension.callInPass(() -> {
                status.reduceHp(300, attacker);
                status.stopHpMpRegeneration();
                return status.getCurrentCp();
            });

            assertThat(actual).isEqualTo(700.0);
            verify(player).stopEffectsOnDamage(true);
        }
    }

    private static Creature creature() {
        return new Attackable(template());
    }

    private static void sendStatus(CreatureStatus status) {
        status.forEachStatusListener(listener -> listener.sendPacket(ActionFailed.STATIC_PACKET));
    }

    private static NpcTemplate template() {
        final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        return template;
    }

    private static final class Listener extends Attackable {
        private final List<Creature> received;
        private Runnable onPacket = () -> {};

        Listener(List<Creature> received) {
            super(template());
            this.received = received;
        }

        @Override
        public void sendPacket(ServerPacket packet) {
            received.add(this);
            onPacket.run();
        }
    }
}
