/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.item.holders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.AbstractFunction;
import app.l2nx.highfive.mechanics.stats.functions.FuncTemplate;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ElementalsTest {
    private final Player player = mock(Player.class);

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Elementals> call) {
            final Elementals elementals = new Elementals(Elementals.FIRE, 150);

            assertThatThrownBy(() -> call.accept(elementals))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Elementals>>> ownerCheckedCalls() {
            final Player player = mock(Player.class);
            return Stream.of(
                    Named.of("getElement (accessor)", Elementals::getElement),
                    Named.of("getValue (accessor)", Elementals::getValue),
                    Named.of("setElement", e -> e.setElement(Elementals.WATER)),
                    Named.of("setValue", e -> e.setValue(10)),
                    Named.of("applyBonus", e -> e.applyBonus(player, false)),
                    Named.of("removeBonus", e -> e.removeBonus(player)),
                    Named.of("updateBonus", e -> e.updateBonus(player, false)),
                    Named.of("getElementName", e -> Elementals.getElementName(Elementals.FIRE)),
                    Named.of("getOppositeElement", e -> Elementals.getOppositeElement(Elementals.FIRE)),
                    Named.of("weaponValue", e -> Elementals.weaponValue(1)),
                    Named.of("armorValue", e -> Elementals.armorValue(1)));
        }

        @Test
        void toString_shouldNotCheckTheOwner() {
            final String actual = new Elementals(Elementals.FIRE, 150).toString();

            assertThat(actual).isEqualTo("Fire +150");
        }
    }

    @Nested
    class MaxValues {
        @ParameterizedTest(name = "level {0} -> weapon {1}, armor {2}")
        @CsvSource({"0, 0, 0", "3, 150, 60", "6, 300, 120", "12, 600, 240"})
        void weaponValue_shouldAnswerTheValueOfTheLevel(int level, int weapon, int armor) {
            final List<Integer> actual = WorldLoopExtension.callInPass(
                    () -> List.of(Elementals.weaponValue(level), Elementals.armorValue(level)));

            assertThat(actual).containsExactly(weapon, armor);
        }
    }

    @Nested
    class Bonus {
        @ParameterizedTest(name = "element {0}, armor {1} -> {2}")
        @CsvSource({"0, false, FIRE_POWER", "0, true, FIRE_RES", "5, false, DARK_POWER", "4, true, HOLY_RES"})
        void applyBonus_shouldAddTheStatOfTheElement(byte element, boolean armor, Stat expected) {
            final Elementals elementals = new Elementals(element, 150);
            final ArgumentCaptor<AbstractFunction> added = ArgumentCaptor.forClass(AbstractFunction.class);

            WorldLoopExtension.runInPass(() -> elementals.applyBonus(player, armor));

            verify(player).addStatFunc(added.capture());
            assertThat(added.getValue().getStat()).isEqualTo(expected);
            assertThat(added.getValue().getValue()).isEqualTo(150);
            assertThat(added.getValue().getFuncOwner()).isSameAs(elementals);
        }

        @Test
        void applyBonus_shouldAddTheBonusOnce_whenAppliedTwice() {
            final Elementals elementals = new Elementals(Elementals.FIRE, 150);

            WorldLoopExtension.runInPass(() -> {
                elementals.applyBonus(player, false);
                elementals.applyBonus(player, false);
            });

            verify(player, times(1)).addStatFunc(any(AbstractFunction.class));
        }

        @Test
        void removeBonus_shouldDoNothing_whenNoBonusIsApplied() {
            final Elementals elementals = new Elementals(Elementals.FIRE, 150);

            WorldLoopExtension.runInPass(() -> elementals.removeBonus(player));

            verify(player, never()).removeStatsOwner(any());
        }

        @Test
        void updateBonus_shouldApplyTheNewValue() {
            final Elementals elementals = new Elementals(Elementals.FIRE, 150);
            final ArgumentCaptor<AbstractFunction> added = ArgumentCaptor.forClass(AbstractFunction.class);

            WorldLoopExtension.runInPass(() -> {
                elementals.applyBonus(player, false);
                elementals.setValue(175);
                elementals.updateBonus(player, false);
            });

            verify(player).removeStatsOwner(elementals);
            verify(player, times(2)).addStatFunc(added.capture());
            assertThat(added.getAllValues())
                    .extracting(AbstractFunction::getValue)
                    .containsExactly(150.0, 175.0);
        }
    }

    @Nested
    class TemplateAttributes {
        // The item parse pool attaches functions to templates off the loop, so it may not read an Elementals.
        @Test
        void attach_shouldKeepTheLastValueOfAnElement_whenCalledOffTheLoop() {
            final EtcItem template = new EtcItem(new StatSet(Map.of("item_id", 57, "name", "x")));

            template.attach(new FuncTemplate(null, null, "add", 0x40, Stat.FIRE_RES, 10));
            template.attach(new FuncTemplate(null, null, "add", 0x40, Stat.WATER_RES, 20));
            template.attach(new FuncTemplate(null, null, "add", 0x40, Stat.FIRE_RES, 30));

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> List.of(
                    template.getElementals().length,
                    template.getElemental(Elementals.FIRE).getValue(),
                    template.getElemental(Elementals.WATER).getValue()));

            assertThat(actual).containsExactly(2, 30, 20);
        }
    }
}
