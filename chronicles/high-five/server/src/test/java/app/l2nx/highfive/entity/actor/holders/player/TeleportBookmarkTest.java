/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.entity.Location;
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
class TeleportBookmarkTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<TeleportBookmark> call) {
            final TeleportBookmark bookmark = bookmark();

            assertThatThrownBy(() -> call.accept(bookmark))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<TeleportBookmark>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId", TeleportBookmark::getId),
                    Named.of("getIcon", TeleportBookmark::getIcon),
                    Named.of("setIcon", bookmark -> bookmark.setIcon(2)),
                    Named.of("getName", TeleportBookmark::getName),
                    Named.of("setName", bookmark -> bookmark.setName("Giran")),
                    Named.of("getTag", TeleportBookmark::getTag),
                    Named.of("setTag", bookmark -> bookmark.setTag("GI")),
                    Named.of("setXYZ(int, int, int) (Location)", bookmark -> bookmark.setXYZ(1, 2, 3)),
                    Named.of("setXYZ(ILocational) (Location)", bookmark -> bookmark.setXYZ(new Location(1, 2, 3))),
                    Named.of("setHeading (Location)", bookmark -> bookmark.setHeading(100)),
                    Named.of("setInstanceId (Location)", bookmark -> bookmark.setInstanceId(7)),
                    Named.of("setLocation (Location)", bookmark -> bookmark.setLocation(new Location(1, 2, 3))),
                    Named.of("getX (Location)", bookmark -> bookmark.getX()),
                    Named.of("getY (Location)", bookmark -> bookmark.getY()),
                    Named.of("getZ (Location)", bookmark -> bookmark.getZ()),
                    Named.of("getHeading (Location)", bookmark -> bookmark.getHeading()),
                    Named.of("getInstanceId (Location)", bookmark -> bookmark.getInstanceId()),
                    Named.of("getLocation (Location)", bookmark -> bookmark.getLocation()),
                    Named.of("clone (Location)", bookmark -> bookmark.clone()));
        }
    }

    @Nested
    class Edit {
        @Test
        void setters_shouldReplaceIconNameAndTag() {
            final TeleportBookmark bookmark = bookmark();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                bookmark.setIcon(2);
                bookmark.setName("Giran");
                bookmark.setTag("GI");
                return List.of(bookmark.getId(), bookmark.getIcon(), bookmark.getName(), bookmark.getTag());
            });

            assertThat(actual).containsExactly(1, 2, "Giran", "GI");
        }

        @Test
        void locationSetters_shouldMoveTheBookmark_whenCalledOnTheLoop() {
            final TeleportBookmark bookmark = bookmark();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                bookmark.setXYZ(new Location(1, 2, 3));
                bookmark.setHeading(4);
                bookmark.setInstanceId(5);
                return List.of(
                        bookmark.getX(),
                        bookmark.getY(),
                        bookmark.getZ(),
                        bookmark.getHeading(),
                        bookmark.getInstanceId());
            });

            assertThat(actual).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void setLocation_shouldCopyCoordinatesHeadingAndInstance() {
            final TeleportBookmark bookmark = bookmark();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                bookmark.setLocation(new Location(1, 2, 3, 4, 5));
                return List.of(
                        bookmark.getX(),
                        bookmark.getY(),
                        bookmark.getZ(),
                        bookmark.getHeading(),
                        bookmark.getInstanceId());
            });

            assertThat(actual).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void equals_shouldReadFieldsOnly_whenCalledOffTheLoop() {
            assertThat(bookmark()).isEqualTo(bookmark()).hasSameHashCodeAs(bookmark());
        }
    }

    private static TeleportBookmark bookmark() {
        return new TeleportBookmark(1, 83_400, 147_900, -3_400, 1, "AD", "Aden");
    }
}
