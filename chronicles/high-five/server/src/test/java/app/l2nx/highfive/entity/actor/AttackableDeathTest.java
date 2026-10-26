/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.zone.ZoneRegion;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.attackable.OnAttackableKill;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("PlayerConfig")
class AttackableDeathTest {
    private static final int NPC_ID = 20_545;

    private final List<String> log = new ArrayList<>();
    private int partyRange;

    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
        partyRange = PlayerConfig.ALT_PARTY_RANGE;
        PlayerConfig.ALT_PARTY_RANGE = 1_500;
    }

    @AfterEach
    void restoreConfig() {
        PlayerConfig.ALT_PARTY_RANGE = partyRange;
    }

    @Nested
    class CalculateRewards {
        @Test
        void calculateRewards_shouldDropInTheSideEffectPhase_whenTheCorpseIsDeletedInTheSameStack() {
            final Player killer = player(0x7ffe_0101);

            WorldLoopExtension.runInPass(() -> {
                final RecordingAttackable mob = new RecordingAttackable(log);
                standAt(mob, killer);
                mob.addDamageHate(killer, 100, 1);
                mob.calculateRewards(killer);
                mob.clearAggroList();
                log.add("death stack done");
            });

            assertThat(log).containsExactly("death stack done", "drop:" + 0x7ffe_0101);
        }

        @Test
        void calculateRewards_shouldUseTheDamageAtDeath_whenTheCorpseIsHitInTheSamePass() {
            final Player killer = player(0x7ffe_0102);
            final Player late = player(0x7ffe_0103);

            WorldLoopExtension.runInPass(() -> {
                final RecordingAttackable mob = new RecordingAttackable(log);
                standAt(mob, killer, late);
                mob.addDamageHate(killer, 100, 1);
                mob.calculateRewards(late);
                mob.addDamageHate(late, 10_000, 1);
            });

            assertThat(log).containsExactly("drop:" + 0x7ffe_0102);
        }

        // Template level 70, base max HP 1000 and an experience reward of 4410 make the vitality divider 100.
        @Test
        void calculateRewards_shouldKeepTheVitalityOfTheMaxHpAtDeath_whenAMaxHpBuffStopsInTheSameStack() {
            final Player killer = player(0x7ffe_0105);
            final Party party = mock(Party.class);
            when(killer.getParty()).thenReturn(party);
            when(killer.getLevel()).thenReturn(70);
            when(party.getMembers()).thenReturn(List.of(killer));
            final AtomicReference<RecordingAttackable> dead = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final RecordingAttackable mob = new RecordingAttackable(log);
                dead.set(mob);
                standAt(mob, killer);
                mob.setMustRewardExpSp(true);
                mob.maxHp = 2_000;
                mob.addDamageHate(killer, 1_500, 1);
                mob.calculateRewards(killer);
                mob.maxHp = 1_000;
            });

            final ArgumentCaptor<Integer> level = ArgumentCaptor.forClass(Integer.class);
            final ArgumentCaptor<Long> damage = ArgumentCaptor.forClass(Long.class);
            final ArgumentCaptor<Integer> maxHp = ArgumentCaptor.forClass(Integer.class);
            verify(party)
                    .distributeXpAndSp(
                            anyDouble(),
                            anyDouble(),
                            anyList(),
                            level.capture(),
                            damage.capture(),
                            any(Attackable.class),
                            maxHp.capture(),
                            anyBoolean());
            final float actual = dead.get().getVitalityPoints(level.getValue(), damage.getValue(), maxHp.getValue());

            assertThat(actual).isEqualTo(-15f);
        }
    }

    @Nested
    class DoDie {
        @Test
        void doDie_shouldNotifyTheKillAfterTheDrop_whenTheKillHasNoDelay() {
            final Player killer = player(0x7ffe_0104);
            final ZoneManager zones = mock(ZoneManager.class);
            when(zones.getRegion(any(ILocational.class))).thenReturn(mock(ZoneRegion.class));

            try (MockedStatic<ZoneManager> zoneManager = mockStatic(ZoneManager.class)) {
                zoneManager.when(ZoneManager::getInstance).thenReturn(zones);
                die(killer);
            }
            WorldLoopExtension.passAfter(Duration.ofMillis(1));

            assertThat(log).containsExactly("drop:" + 0x7ffe_0104, "kill");
        }

        private void die(Player killer) {
            WorldLoopExtension.runInPass(() -> {
                final RecordingAttackable mob = new RecordingAttackable(log);
                standAt(mob, killer);
                mob.setOnKillDelay(0);
                mob.addListener(new ConsumerEventListener(
                        mob, EventType.ON_ATTACKABLE_KILL, (OnAttackableKill kill) -> log.add("kill"), this));
                mob.addDamageHate(killer, 100, 1);
                mob.doDie(killer);
            });
        }
    }

    private static Player player(int objectId) {
        final Player player = mock(Player.class);
        when(player.getObjectId()).thenReturn(objectId);
        when(player.isPlayer()).thenReturn(true);
        when(player.isPlayable()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.asPlayer()).thenReturn(player);
        return player;
    }

    private static void standAt(Attackable mob, Player... players) {
        for (Player player : players) {
            when(player.getX()).thenReturn(mob.getX());
            when(player.getY()).thenReturn(mob.getY());
            when(player.getZ()).thenReturn(mob.getZ());
        }
    }

    private static final class RecordingAttackable extends Attackable {
        private final List<String> log;
        private final CreatureAI ai = mock(CreatureAI.class);
        private int maxHp = 1_000;

        private RecordingAttackable(List<String> log) {
            super(template());
            this.log = log;
            setMustRewardExpSp(false);
        }

        private static NpcTemplate template() {
            final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "baseHpMax", 1_000)));
            template.setSkills(Map.of());
            return template;
        }

        @Override
        public CreatureAI getAI() {
            return ai;
        }

        @Override
        public int getMaxHp() {
            return maxHp;
        }

        @Override
        public long getExpReward(int level) {
            return 4_410;
        }

        @Override
        public void doItemDrop(Creature mainDamageDealer) {
            log.add("drop:" + mainDamageDealer.getObjectId());
        }
    }
}
