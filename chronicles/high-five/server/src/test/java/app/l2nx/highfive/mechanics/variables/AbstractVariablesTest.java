/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.variables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class AbstractVariablesTest {
    private static final class RecordingVariables extends AbstractVariables {
        final List<Map<String, String>> persisted = new ArrayList<>();
        boolean failing;

        @Override
        protected void persist(Set<String> deleted, Map<String, String> changed) {
            if (failing) {
                throw new DataAccessResourceFailureException("save failed");
            }

            persisted.add(changed);
        }

        @Override
        protected String variablesKey() {
            return "test";
        }
    }

    private static RecordingVariables variables() {
        return new RecordingVariables();
    }

    private static AbstractVariables saved(Map<String, String> stored) {
        final AbstractVariables variables = variables();
        WorldLoopExtension.runInPass(() -> {
            stored.forEach((name, value) -> variables.set(name, value, false));
            variables.markSaved();
        });
        return variables;
    }

    @Nested
    class SaveNow {
        @Test
        void saveNow_shouldPersistTheChangesOnce_whenTheSaveSucceeds() {
            final RecordingVariables variables = variables();

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                variables.set("a", 1);
                final boolean first = variables.saveNow();
                return first && !variables.saveNow();
            });

            assertThat(actual).isTrue();
            assertThat(variables.persisted).containsExactly(Map.of("a", "1"));
        }

        @Test
        void saveNow_shouldKeepTheChangesPending_whenThePersistFails() {
            final RecordingVariables variables = variables();
            variables.failing = true;

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                variables.set("a", 1);
                return variables.saveNow();
            });
            variables.failing = false;
            final boolean retried = WorldLoopExtension.callInPass(variables::saveNow);

            assertThat(actual).isFalse();
            assertThat(retried).isTrue();
            assertThat(variables.persisted).containsExactly(Map.of("a", "1"));
        }
    }

    @Nested
    class StoreMe {
        @Test
        void storeMe_shouldPersistNothingAtOnce_whenASaveIsAlreadyScheduled() {
            final RecordingVariables variables = variables();

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                variables.set("a", 1);
                variables.storeMe();
                variables.set("b", 2);
                return variables.storeMe();
            });

            assertThat(actual).isTrue();
            assertThat(variables.persisted).isEmpty();
        }

        @Test
        void storeMe_shouldPersistTheLaterChangesWithTheScheduledSave_whenASaveIsAlreadyScheduled() {
            final RecordingVariables variables = variables();
            WorldLoopExtension.runInPass(() -> {
                variables.set("a", 1);
                variables.storeMe();
                variables.set("b", 2);
                variables.storeMe();
            });

            WorldLoopExtension.passAfter(Duration.ofMinutes(1));

            assertThat(variables.persisted).containsExactly(Map.of("a", "1", "b", "2"));
        }
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<AbstractVariables> call) {
            final AbstractVariables variables = variables();

            assertThatThrownBy(() -> call.accept(variables))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<AbstractVariables>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getBoolean", v -> v.getBoolean("A", false)),
                    Named.of("getInt", v -> v.getInt("A", 0)),
                    Named.of("getLong", v -> v.getLong("A", 0)),
                    Named.of("getString", v -> v.getString("A", null)),
                    Named.of("getByteArray", v -> v.getByteArray("A", ",")),
                    Named.of("getIntegerList", v -> v.getIntegerList("A")),
                    Named.of("set int", v -> v.set("A", 1)),
                    Named.of("set String", v -> v.set("A", "1")),
                    Named.of("set Object", v -> v.set("A", (Object) List.of())),
                    Named.of("set unmarked", v -> v.set("A", "1", false)),
                    Named.of("setIntegerList", v -> v.setIntegerList("A", List.of(1))),
                    Named.of("hasVariable", v -> v.hasVariable("A")),
                    Named.of("hasChanges", AbstractVariables::hasChanges),
                    Named.of("remove", v -> v.remove("A")));
        }

        @Test
        void hasVariable_shouldAnswer_whenCalledOnTheLoop() {
            final AbstractVariables variables = variables();

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                variables.set("A", 1);
                return variables.hasVariable("A");
            });

            assertThat(actual).isTrue();
        }
    }

    @Nested
    class ChangeTracking {
        @Test
        void set_shouldMarkTheVariableAdded_whenItIsNew() {
            final AbstractVariables variables = saved(Map.of());

            WorldLoopExtension.runInPass(() -> variables.set("NEW", 1));

            assertThat(WorldLoopExtension.callInPass(variables::hasChanges)).isTrue();
            assertThat(WorldLoopExtension.callInPass(variables::changedValues)).containsExactly(Map.entry("NEW", "1"));
            assertThat(WorldLoopExtension.callInPass(variables::deletedNames)).isEmpty();
        }

        @Test
        void set_shouldMarkTheVariableModified_whenItIsStored() {
            final AbstractVariables variables = saved(Map.of("KEPT", "1", "OLD", "2"));

            WorldLoopExtension.runInPass(() -> variables.set("OLD", 3));

            assertThat(WorldLoopExtension.callInPass(variables::changedValues)).containsExactly(Map.entry("OLD", "3"));
        }

        @Test
        void set_shouldNotMarkAChange_whenTheValueIsRestored() {
            final AbstractVariables variables = saved(Map.of("OLD", "1"));

            assertThat(WorldLoopExtension.callInPass(variables::hasChanges)).isFalse();
            assertThat(WorldLoopExtension.callInPass(variables::changedValues)).isEmpty();
        }

        @Test
        void remove_shouldMarkTheVariableDeleted_whenItIsStored() {
            final AbstractVariables variables = saved(Map.of("OLD", "1"));

            WorldLoopExtension.runInPass(() -> {
                variables.set("OLD", 2);
                variables.remove("OLD");
            });

            assertThat(WorldLoopExtension.callInPass(variables::deletedNames)).containsExactly("OLD");
            assertThat(WorldLoopExtension.callInPass(variables::changedValues)).isEmpty();
            assertThat(WorldLoopExtension.callInPass(() -> variables.hasVariable("OLD")))
                    .isFalse();
        }

        @Test
        void remove_shouldLeaveNothingToSave_whenTheVariableWasAddedSinceTheSave() {
            final AbstractVariables variables = saved(Map.of());

            WorldLoopExtension.runInPass(() -> {
                variables.set("NEW", 1);
                variables.remove("NEW");
            });

            assertThat(WorldLoopExtension.callInPass(variables::deletedNames)).isEmpty();
            assertThat(WorldLoopExtension.callInPass(variables::changedValues)).isEmpty();
        }

        @Test
        void markSaved_shouldClearTheChanges() {
            final AbstractVariables variables = saved(Map.of("OLD", "1"));

            WorldLoopExtension.runInPass(() -> {
                variables.set("NEW", 1);
                variables.remove("OLD");
                variables.markSaved();
            });

            assertThat(WorldLoopExtension.callInPass(variables::hasChanges)).isFalse();
            assertThat(WorldLoopExtension.callInPass(variables::changedValues)).isEmpty();
            assertThat(WorldLoopExtension.callInPass(variables::deletedNames)).isEmpty();
        }

        @Test
        void changedValues_shouldKeepTheOrderTheVariablesWereAddedIn() {
            final AbstractVariables variables = saved(Map.of());

            WorldLoopExtension.runInPass(() -> {
                variables.set("C", 1);
                variables.set("A", 2);
                variables.set("B", 3);
            });

            assertThat(WorldLoopExtension.callInPass(variables::changedValues))
                    .containsExactly(Map.entry("C", "1"), Map.entry("A", "2"), Map.entry("B", "3"));
            assertThat(WorldLoopExtension.callInPass(variables::allValues).keySet())
                    .containsExactly("C", "A", "B");
        }
    }

    @Nested
    class TypedGetters {
        @ParameterizedTest(name = "{0}")
        @MethodSource("readsOfStoredValues")
        void get_shouldParseTheStoredValue(
                String name, Object stored, Function<AbstractVariables, Object> read, Object expected) {
            final AbstractVariables variables = variables();

            final Object actual = WorldLoopExtension.callInPass(() -> {
                variables.set("A", stored);
                return read.apply(variables);
            });

            assertThat(actual).isEqualTo(expected);
        }

        static Stream<Arguments> readsOfStoredValues() {
            return Stream.of(
                    Arguments.of("int as long", 5, read(v -> v.getLong("A")), 5L),
                    Arguments.of("int as String", 5, read(v -> v.getString("A")), "5"),
                    Arguments.of("String as int", "7", read(v -> v.getInt("A")), 7),
                    Arguments.of("String as long", "7", read(v -> v.getLong("A", 0)), 7L),
                    Arguments.of("String as boolean", "true", read(v -> v.getBoolean("A")), true),
                    Arguments.of("long as int", 9L, read(v -> v.getInt("A", 0)), 9),
                    Arguments.of(
                            "String as integer list", "1,2,3", read(v -> v.getIntegerList("A")), List.of(1, 2, 3)));
        }

        private static Function<AbstractVariables, Object> read(Function<AbstractVariables, Object> read) {
            return read;
        }

        @Test
        void getInt_shouldAnswerTheDefault_whenTheVariableIsMissing() {
            final AbstractVariables variables = variables();

            final int actual = WorldLoopExtension.callInPass(() -> variables.getInt("A", 4));

            assertThat(actual).isEqualTo(4);
        }

        @Test
        void getInt_shouldThrow_whenTheVariableIsMissingAndHasNoDefault() {
            final AbstractVariables variables = variables();

            assertThatThrownBy(() -> WorldLoopExtension.callInPass(() -> variables.getInt("A")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class SetIntegerList {
        @Test
        void setIntegerList_shouldStoreCommaSeparatedValues() {
            final AbstractVariables variables = variables();

            final String actual = WorldLoopExtension.callInPass(() -> {
                variables.setIntegerList("A", List.of(1, 2, 3));
                return variables.getString("A");
            });

            assertThat(actual).isEqualTo("1,2,3");
        }

        @Test
        void setIntegerList_shouldDeleteTheVariable_whenTheListIsEmpty() {
            final AbstractVariables variables = saved(Map.of("A", "1,2"));

            WorldLoopExtension.runInPass(() -> variables.setIntegerList("A", List.of()));

            assertThat(WorldLoopExtension.callInPass(() -> variables.hasVariable("A")))
                    .isFalse();
            assertThat(WorldLoopExtension.callInPass(variables::deletedNames)).containsExactly("A");
        }
    }
}
