/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ShortcutsTest {
    private static final int SKILL_ID = 1177;

    private final Player owner = mock(Player.class);
    private final PlayerPersistenceManager persistence = mock(PlayerPersistenceManager.class);
    private final Shortcuts shortcuts = new Shortcuts(owner);
    private MockedStatic<PlayerPersistenceManager> persistenceInstance;

    @BeforeEach
    void bindPersistence() {
        persistenceInstance = mockStatic(PlayerPersistenceManager.class);
        persistenceInstance.when(PlayerPersistenceManager::getInstance).thenReturn(persistence);
    }

    @AfterEach
    void unbindPersistence() {
        persistenceInstance.close();
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Shortcuts> call) {
            assertThatThrownBy(() -> call.accept(shortcuts))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Shortcuts>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getAllShortcuts", Shortcuts::getAllShortcuts),
                    Named.of("getShortcut", shortcuts -> shortcuts.getShortcut(0, 0)),
                    Named.of("registerShortcut", shortcuts -> shortcuts.registerShortcut(skill(0, 0))),
                    Named.of("deleteShortcut", shortcuts -> shortcuts.deleteShortcut(0, 0)),
                    Named.of("deleteShortcutByObjectId", shortcuts -> shortcuts.deleteShortcutByObjectId(1)),
                    Named.of("restoreMe", Shortcuts::restoreMe),
                    Named.of("updateShortcuts", shortcuts -> shortcuts.updateShortcuts(SKILL_ID, 2)));
        }
    }

    @Nested
    class Snapshots {
        @Test
        void getAllShortcuts_shouldBeReadOnly() {
            final Collection<Shortcut> actual = WorldLoopExtension.callInPass(() -> {
                shortcuts.registerShortcut(skill(0, 0));
                return shortcuts.getAllShortcuts();
            });

            assertThatThrownBy(actual::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getAllShortcuts_shouldKeepItsEntries_whenAShortcutIsDeleted() {
            final Shortcut expected = skill(4, 0);

            final Collection<Shortcut> actual = WorldLoopExtension.callInPass(() -> {
                shortcuts.registerShortcut(expected);
                final Collection<Shortcut> snapshot = shortcuts.getAllShortcuts();
                shortcuts.deleteShortcut(4, 0);
                return snapshot;
            });

            assertThat(actual).containsExactly(expected);
        }
    }

    @Nested
    class Order {
        @Test
        void getAllShortcuts_shouldKeepRegistrationOrder() {
            final Shortcut first = skill(5, 0);
            final Shortcut second = skill(1, 0);
            final Shortcut third = skill(3, 1);

            final Collection<Shortcut> actual = WorldLoopExtension.callInPass(() -> {
                shortcuts.registerShortcut(first);
                shortcuts.registerShortcut(second);
                shortcuts.registerShortcut(third);
                return shortcuts.getAllShortcuts();
            });

            assertThat(actual).containsExactly(first, second, third);
        }
    }

    @Nested
    class Lookup {
        @Test
        void getShortcut_shouldAnswerTheShortcutAtSlotAndPage() {
            final Shortcut expected = skill(2, 3);

            final Shortcut actual = WorldLoopExtension.callInPass(() -> {
                shortcuts.registerShortcut(skill(2, 0));
                shortcuts.registerShortcut(expected);
                return shortcuts.getShortcut(2, 3);
            });

            assertThat(actual).isSameAs(expected);
        }

        @Test
        void updateShortcuts_shouldRegisterTheSkillAtItsNewLevel() {
            final ArgumentCaptor<Shortcut> registered = ArgumentCaptor.forClass(Shortcut.class);

            WorldLoopExtension.runInPass(() -> {
                shortcuts.registerShortcut(skill(6, 0));
                shortcuts.updateShortcuts(SKILL_ID, 2);
            });

            verify(owner).registerShortcut(registered.capture());
            final List<Integer> actual = WorldLoopExtension.callInPass(() -> List.of(
                    registered.getValue().getSlot(),
                    registered.getValue().getId(),
                    registered.getValue().getLevel()));
            assertThat(actual).containsExactly(6, SKILL_ID, 2);
        }
    }

    private static Shortcut skill(int slot, int page) {
        return new Shortcut(slot, page, ShortcutType.SKILL, SKILL_ID, 1, 1);
    }
}
