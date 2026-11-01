/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.partition.WorldLoopExtension;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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
class AutoUseSettingsHolderTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<AutoUseSettingsHolder> call) {
            final AutoUseSettingsHolder settings = new AutoUseSettingsHolder();

            assertThatThrownBy(() -> call.accept(settings))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<AutoUseSettingsHolder>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getAutoSupplyItems", AutoUseSettingsHolder::getAutoSupplyItems),
                    Named.of("collectAutoSupplyItems", settings -> settings.collectAutoSupplyItems(new IntArrayList())),
                    Named.of("isAutoSupplyItem", settings -> settings.isAutoSupplyItem(1)),
                    Named.of("addAutoSupplyItem", settings -> settings.addAutoSupplyItem(1)),
                    Named.of("removeAutoSupplyItem", settings -> settings.removeAutoSupplyItem(1)),
                    Named.of("getAutoActions", AutoUseSettingsHolder::getAutoActions),
                    Named.of("isAutoAction", settings -> settings.isAutoAction(1)),
                    Named.of("addAutoAction", settings -> settings.addAutoAction(1)),
                    Named.of("removeAutoAction", settings -> settings.removeAutoAction(1)),
                    Named.of("getAutoBuffs", AutoUseSettingsHolder::getAutoBuffs),
                    Named.of("collectAutoBuffs", settings -> settings.collectAutoBuffs(new IntArrayList())),
                    Named.of("isAutoBuff", settings -> settings.isAutoBuff(1)),
                    Named.of("addAutoBuff", settings -> settings.addAutoBuff(1)),
                    Named.of("removeAutoBuff", settings -> settings.removeAutoBuff(1)),
                    Named.of("clearAutoBuffs", AutoUseSettingsHolder::clearAutoBuffs),
                    Named.of("getAutoSkills", AutoUseSettingsHolder::getAutoSkills),
                    Named.of("getAutoSkillCount", AutoUseSettingsHolder::getAutoSkillCount),
                    Named.of("isAutoSkill", settings -> settings.isAutoSkill(1)),
                    Named.of("addAutoSkill", settings -> settings.addAutoSkill(1)),
                    Named.of("removeAutoSkill", settings -> settings.removeAutoSkill(1)),
                    Named.of("clearAutoSkills", AutoUseSettingsHolder::clearAutoSkills),
                    Named.of("getAutoPotionItem", AutoUseSettingsHolder::getAutoPotionItem),
                    Named.of("setAutoPotionItem", settings -> settings.setAutoPotionItem(1)),
                    Named.of("getNextSkillId", AutoUseSettingsHolder::getNextSkillId),
                    Named.of("incrementSkillOrder", AutoUseSettingsHolder::incrementSkillOrder),
                    Named.of("resetSkillOrder", AutoUseSettingsHolder::resetSkillOrder),
                    Named.of("isEmpty", AutoUseSettingsHolder::isEmpty));
        }
    }

    @Nested
    class Snapshots {
        @Test
        void getAutoBuffs_shouldBeReadOnly() {
            final AutoUseSettingsHolder settings = new AutoUseSettingsHolder();

            final IntList actual = WorldLoopExtension.callInPass(() -> {
                settings.addAutoBuff(1204);
                return settings.getAutoBuffs();
            });

            assertThatThrownBy(() -> actual.add(1)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getAutoSupplyItems_shouldKeepItsEntries_whenAnItemIsRemoved() {
            final AutoUseSettingsHolder settings = new AutoUseSettingsHolder();

            final IntList actual = WorldLoopExtension.callInPass(() -> {
                settings.addAutoSupplyItem(1539);
                final IntList snapshot = settings.getAutoSupplyItems();
                settings.removeAutoSupplyItem(1539);
                return snapshot;
            });

            assertThat(actual.toIntArray()).containsExactly(1539);
        }
    }

    @Nested
    class Order {
        @Test
        void collectAutoSupplyItems_shouldKeepInsertionOrder() {
            final AutoUseSettingsHolder settings = new AutoUseSettingsHolder();

            final IntList actual = WorldLoopExtension.callInPass(() -> {
                settings.addAutoSupplyItem(5592);
                settings.addAutoSupplyItem(1539);
                settings.addAutoSupplyItem(728);
                final IntList collected = new IntArrayList();
                settings.collectAutoSupplyItems(collected);
                return collected;
            });

            assertThat(actual.toIntArray()).containsExactly(5592, 1539, 728);
        }

        @Test
        void getNextSkillId_shouldRotateInInsertionOrder() {
            final AutoUseSettingsHolder settings = new AutoUseSettingsHolder();

            final IntList actual = WorldLoopExtension.callInPass(() -> {
                settings.addAutoSkill(56);
                settings.addAutoSkill(19);
                final IntList picks = new IntArrayList();
                for (int i = 0; i < 3; i++) {
                    picks.add(settings.getNextSkillId());
                    settings.incrementSkillOrder();
                }
                return picks;
            });

            assertThat(actual.toIntArray()).containsExactly(56, 19, 56);
        }
    }

    @Nested
    class Skills {
        @Test
        void getNextSkillId_shouldAnswerMinValue_whenNoSkillIsSet() {
            final AutoUseSettingsHolder settings = new AutoUseSettingsHolder();

            final int actual = WorldLoopExtension.callInPass(settings::getNextSkillId);

            assertThat(actual).isEqualTo(Integer.MIN_VALUE);
        }

        @Test
        void removeAutoSkill_shouldRemoveByValue_whenTheIdIsAlsoAnIndex() {
            final AutoUseSettingsHolder settings = new AutoUseSettingsHolder();

            final IntList actual = WorldLoopExtension.callInPass(() -> {
                settings.addAutoSkill(1);
                settings.addAutoSkill(0);
                settings.removeAutoSkill(0);
                return settings.getAutoSkills();
            });

            assertThat(actual.toIntArray()).containsExactly(1);
        }

        @Test
        void isAutoSkill_shouldNotAnswerForABuff() {
            final AutoUseSettingsHolder settings = new AutoUseSettingsHolder();

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                settings.addAutoBuff(1204);
                return settings.isAutoSkill(1204);
            });

            assertThat(actual).isFalse();
        }
    }
}
