/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.HashSet;
import java.util.Set;
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
class WorldObjectTest {

    static WorldObject objectWithId(int objectId) {
        return new WorldObject(objectId) {
            @Override
            public boolean isAutoAttackable(Creature attacker) {
                return false;
            }

            @Override
            public void sendInfo(Player player) {}
        };
    }

    @Nested
    class HashCodeContract {

        @Test
        void hashCode_shouldMatch_whenObjectIdsEqual() {
            WorldObject first = objectWithId(268_435_456);
            WorldObject second = objectWithId(268_435_456);

            assertThat(first).isEqualTo(second);
            assertThat(first.hashCode()).isEqualTo(second.hashCode());
        }

        @Test
        void contains_shouldFindEqualInstance_whenHashSetHoldsOther() {
            Set<WorldObject> objects = new HashSet<>();
            objects.add(objectWithId(268_435_457));

            assertThat(objects.contains(objectWithId(268_435_457))).isTrue();
        }

        @Test
        void equals_shouldBeFalse_whenObjectIdsDiffer() {
            assertThat(objectWithId(1)).isNotEqualTo(objectWithId(2));
        }
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<WorldObject> call) {
            final WorldObject object = objectWithId(268_435_458);

            assertThatThrownBy(() -> call.accept(object))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<WorldObject>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("setXYZ", object -> object.setXYZ(1, 2, 3)),
                    Named.of("spawnMe", WorldObject::spawnMe),
                    Named.of("spawnMe at a position", object -> object.spawnMe(1, 2, 3)),
                    Named.of("addScript", object -> object.addScript("script")),
                    Named.of("getScript", object -> object.getScript(String.class)),
                    Named.of("getX", WorldObject::getX),
                    Named.of("getObjectId (accessor)", WorldObject::getObjectId),
                    Named.of("setName (accessor)", object -> object.setName("name")),
                    Named.of("isPlayer (type query)", WorldObject::isPlayer));
        }

        @Test
        void toString_shouldNameTheObject_whenCalledOffTheLoop() {
            final WorldObject object = objectWithId(268_435_459);

            assertThat(object.toString()).contains("268435459");
        }
    }

    @Nested
    class Scripts {
        @Test
        void getScript_shouldReturnTheAddedScript_whenCalledOnTheLoop() {
            final WorldObject object = objectWithId(268_435_460);
            final String expected = "script";

            final String actual = WorldLoopExtension.callInPass(() -> {
                object.addScript(expected);
                return object.getScript(String.class);
            });

            assertThat(actual).isSameAs(expected);
        }

        @Test
        void getScript_shouldBeNull_whenNoScriptWasAdded() {
            final WorldObject object = objectWithId(268_435_461);

            final Object actual = WorldLoopExtension.callInPass(() -> object.getScript(String.class));

            assertThat(actual).isNull();
        }

        @Test
        void removeScript_shouldDropTheScript_whenCalledOnTheLoop() {
            final WorldObject object = objectWithId(268_435_462);

            final Object actual = WorldLoopExtension.callInPass(() -> {
                object.addScript("script");
                object.removeScript(String.class);
                return object.getScript(String.class);
            });

            assertThat(actual).isNull();
        }
    }
}
