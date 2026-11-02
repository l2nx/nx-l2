/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.appearance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;
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
class PlayerAppearanceTest {
    private final Player owner = mock(Player.class);

    private PlayerAppearance appearance() {
        when(owner.getName()).thenReturn("Owner");
        when(owner.getTitle()).thenReturn("Title");
        return WorldLoopExtension.callInPass(() -> {
            final PlayerAppearance appearance = new PlayerAppearance((byte) 1, (byte) 2, (byte) 3, true);
            appearance.setOwner(owner);
            return appearance;
        });
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<PlayerAppearance> call) {
            final PlayerAppearance appearance = new PlayerAppearance((byte) 1, (byte) 2, (byte) 3, true);

            assertThatThrownBy(() -> call.accept(appearance))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<PlayerAppearance>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getOwner (accessor)", PlayerAppearance::getOwner),
                    Named.of("setOwner", a -> a.setOwner(null)),
                    Named.of("getFace (accessor)", PlayerAppearance::getFace),
                    Named.of("getHairColor (accessor)", PlayerAppearance::getHairColor),
                    Named.of("getHairStyle (accessor)", PlayerAppearance::getHairStyle),
                    Named.of("isFemale (accessor)", PlayerAppearance::isFemale),
                    Named.of("getVisibleName", PlayerAppearance::getVisibleName),
                    Named.of("setVisibleName", a -> a.setVisibleName("Name")),
                    Named.of("getVisibleTitle", PlayerAppearance::getVisibleTitle),
                    Named.of("setVisibleTitle", a -> a.setVisibleTitle("Title")),
                    Named.of("getNameColor (accessor)", PlayerAppearance::getNameColor),
                    Named.of("getTitleColor (accessor)", PlayerAppearance::getTitleColor),
                    Named.of("setFace", a -> a.setFace(4)),
                    Named.of("setHairColor", a -> a.setHairColor(4)),
                    Named.of("setHairStyle", a -> a.setHairStyle(4)),
                    Named.of("setFemale", PlayerAppearance::setFemale),
                    Named.of("setMale", PlayerAppearance::setMale),
                    Named.of("setNameColor", a -> a.setNameColor(0x00FF00)),
                    Named.of("setNameColor(r, g, b)", a -> a.setNameColor(1, 2, 3)),
                    Named.of("setTitleColor", a -> a.setTitleColor(0x00FF00)),
                    Named.of("setTitleColor(r, g, b)", a -> a.setTitleColor(1, 2, 3)));
        }
    }

    @Nested
    class VisibleName {
        @Test
        void getVisibleName_shouldFallBackToTheRealNameAndTitle_whenNoneIsSet() {
            final PlayerAppearance appearance = appearance();

            final List<String> actual = WorldLoopExtension.callInPass(
                    () -> List.of(appearance.getVisibleName(), appearance.getVisibleTitle()));

            assertThat(actual).containsExactly("Owner", "Title");
        }

        @Test
        void getVisibleName_shouldAnswerTheVisibleNameAndTitle_whenSet() {
            final PlayerAppearance appearance = appearance();

            final List<String> actual = WorldLoopExtension.callInPass(() -> {
                appearance.setVisibleName("Disguise");
                appearance.setVisibleTitle("Hidden");
                return List.of(appearance.getVisibleName(), appearance.getVisibleTitle());
            });

            assertThat(actual).containsExactly("Disguise", "Hidden");
        }
    }

    @Nested
    class Colors {
        @Test
        void setNameColor_shouldPackRedGreenBlueLowByteFirst() {
            final PlayerAppearance appearance = appearance();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                appearance.setNameColor(0x11, 0x22, 0x33);
                appearance.setTitleColor(0x44, 0x55, 0x66);
                return List.of(appearance.getNameColor(), appearance.getTitleColor());
            });

            assertThat(actual).containsExactly(0x332211, 0x665544);
        }

        @Test
        void setNameColor_shouldKeepTheColor_whenTheValueIsNegative() {
            final PlayerAppearance appearance = appearance();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                appearance.setNameColor(-1);
                appearance.setTitleColor(-1);
                return List.of(appearance.getNameColor(), appearance.getTitleColor());
            });

            assertThat(actual).containsExactly(0xFFFFFF, PlayerAppearance.DEFAULT_TITLE_COLOR);
        }
    }
}
