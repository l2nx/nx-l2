/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.partition.WorldLoopExtension;
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
class WalkInfoTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<WalkInfo> call) {
            final WalkInfo walk = new WalkInfo("route");

            assertThatThrownBy(() -> call.accept(walk))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<WalkInfo>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getWalkCheckTask (accessor)", WalkInfo::getWalkCheckTask),
                    Named.of("setWalkCheckTask (accessor)", walk -> walk.setWalkCheckTask(null)),
                    Named.of("getCurrentNodeId (accessor)", WalkInfo::getCurrentNodeId),
                    Named.of("getLastAction (accessor)", WalkInfo::getLastAction),
                    Named.of("setLastAction (accessor)", walk -> walk.setLastAction(1L)),
                    Named.of("isBlocked", WalkInfo::isBlocked),
                    Named.of("setBlocked", walk -> walk.setBlocked(true)),
                    Named.of("isSuspended", WalkInfo::isSuspended),
                    Named.of("setSuspended", walk -> walk.setSuspended(true)),
                    Named.of("isStoppedByAttack", WalkInfo::isStoppedByAttack),
                    Named.of("setStoppedByAttack", walk -> walk.setStoppedByAttack(true)),
                    Named.of("getRoute", WalkInfo::getRoute),
                    Named.of("getCurrentNode", WalkInfo::getCurrentNode),
                    Named.of("calculateNextNode (was synchronized)", walk -> walk.calculateNextNode(null)));
        }
    }

    @Nested
    class StateFlags {
        @Test
        void setters_shouldToggleEachFlagOnItsOwn() {
            final WalkInfo walk = new WalkInfo("route");

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                walk.setBlocked(true);
                walk.setStoppedByAttack(true);
                walk.setBlocked(false);
                return List.of(walk.isBlocked(), walk.isSuspended(), walk.isStoppedByAttack());
            });

            assertThat(actual).containsExactly(false, false, true);
        }
    }

    @Nested
    class ToString {
        @Test
        void toString_shouldReadFieldsOnly_whenCalledOffTheLoop() {
            final WalkInfo walk = new WalkInfo("route");

            final String actual = walk.toString();

            assertThat(actual).startsWith("WalkInfo [routeName=route").contains("blocked=false");
        }
    }
}
