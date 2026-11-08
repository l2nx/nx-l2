/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import app.l2nx.highfive.taskmanagers.CreatureFollowTaskManager;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PlayerAITest {
    private static final Location CLICK = new Location(10, 20, 30);

    private final Player player = mock(Player.class);
    private MockedStatic<CreatureFollowTaskManager> follow;
    private MockedStatic<AttackStanceTaskManager> stance;
    private MockedStatic<GameTimeTaskManager> gameTime;
    private MockedStatic<ClockBridge> clock;
    private PlayerAI ai;

    @BeforeEach
    void setUp() {
        follow = mockStatic(CreatureFollowTaskManager.class);
        follow.when(CreatureFollowTaskManager::getInstance).thenReturn(mock(CreatureFollowTaskManager.class));
        stance = mockStatic(AttackStanceTaskManager.class);
        stance.when(AttackStanceTaskManager::getInstance).thenReturn(mock(AttackStanceTaskManager.class));
        gameTime = mockStatic(GameTimeTaskManager.class);
        gameTime.when(GameTimeTaskManager::getInstance).thenReturn(mock(GameTimeTaskManager.class));
        clock = mockStatic(ClockBridge.class);
        when(player.isPlayer()).thenReturn(true);
        when(player.asPlayer()).thenReturn(player);
        when(player.isSpawned()).thenReturn(true);
        when(player.hasAI()).thenReturn(true);
        ai = new PlayerAI(player);
    }

    @AfterEach
    void tearDown() {
        clock.close();
        gameTime.close();
        stance.close();
        follow.close();
    }

    private void clickMoveDuringSwing() {
        when(player.isAttackingNow()).thenReturn(true);
        ai.setIntentionMoveTo(CLICK);
        assertThat(ai.getNextIntention()).isEqualTo(Intention.MOVE_TO);
    }

    @Nested
    class NotifyActionCancel {
        @Test
        void notifyActionCancel_shouldReissueTheSavedMove_whenTheSwingIsOver() {
            clickMoveDuringSwing();
            when(player.isAttackingNow()).thenReturn(false);

            ai.notifyActionCancel();

            verify(player).moveToLocation(10, 20, 30, 0);
            assertThat(ai.getIntention()).isEqualTo(Intention.MOVE_TO);
            assertThat(ai.getNextIntention()).isNull();
        }

        @Test
        void notifyActionCancel_shouldKeepTheMoveSavedWithoutAnAnswer_whenTheSwingStillRuns() {
            clickMoveDuringSwing();

            ai.notifyActionCancel();

            verify(player, never()).moveToLocation(anyInt(), anyInt(), anyInt(), anyInt());
            verify(player, times(1)).sendPacket(ActionFailed.STATIC_PACKET);
            assertThat(ai.getNextIntention()).isEqualTo(Intention.MOVE_TO);
        }

        @Test
        void notifyActionReadyToAct_shouldReplayTheMoveKeptThroughTheCancel_whenTheSwingEnds() {
            clickMoveDuringSwing();
            ai.notifyActionCancel();
            when(player.isAttackingNow()).thenReturn(false);

            ai.notifyActionReadyToAct();

            verify(player).moveToLocation(10, 20, 30, 0);
            assertThat(ai.getIntention()).isEqualTo(Intention.MOVE_TO);
            assertThat(ai.getNextIntention()).isNull();
        }

        @Test
        void notifyActionCancel_shouldNotReplayTheSavedMove_whenTheActorIsDead() {
            clickMoveDuringSwing();
            when(player.isAttackingNow()).thenReturn(false);
            when(player.isAlikeDead()).thenReturn(true);

            ai.notifyActionCancel();

            verify(player, never()).moveToLocation(anyInt(), anyInt(), anyInt(), anyInt());
            assertThat(ai.getIntention()).isNotEqualTo(Intention.MOVE_TO);
            assertThat(ai.getNextIntention()).isNull();
        }

        @Test
        void notifyActionCancel_shouldNotReplayTheSavedMove_whenTheActorIsNotSpawned() {
            clickMoveDuringSwing();
            when(player.isAttackingNow()).thenReturn(false);
            when(player.isSpawned()).thenReturn(false);

            ai.notifyActionCancel();

            verify(player, never()).moveToLocation(anyInt(), anyInt(), anyInt(), anyInt());
            assertThat(ai.getNextIntention()).isNull();
        }

        @Test
        void notifyActionCancel_shouldAnswerActionFailed_whenMovementIsDisabled() {
            clickMoveDuringSwing();
            when(player.isAttackingNow()).thenReturn(false);
            when(player.isMovementDisabled()).thenReturn(true);

            ai.notifyActionCancel();

            verify(player, never()).moveToLocation(anyInt(), anyInt(), anyInt(), anyInt());
            verify(player, times(2)).sendPacket(ActionFailed.STATIC_PACKET);
            assertThat(ai.getNextIntention()).isNull();
        }

        @Test
        void notifyActionCancel_shouldDropTheSavedIntention_whenItIsNotAMove() {
            ai.intention = Intention.ATTACK;
            ai.setIntentionCast(mock(Skill.class), null);
            assertThat(ai.getNextIntention()).isEqualTo(Intention.ATTACK);

            ai.notifyActionCancel();

            assertThat(ai.getNextIntention()).isNull();
            verify(player, never()).moveToLocation(anyInt(), anyInt(), anyInt(), anyInt());
        }
    }
}
