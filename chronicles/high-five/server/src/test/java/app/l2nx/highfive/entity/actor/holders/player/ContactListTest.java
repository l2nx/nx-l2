/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.CharacterRelationsManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
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
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ContactListTest {
    private static final int OWNER_ID = 268_100_001;
    private static final List<String> STORED = List.of("Charlie", "Alice", "Bob");

    private final Player player = mock(Player.class);
    private final CharacterRelationsManager relations = mock(CharacterRelationsManager.class);
    private final CharInfoTable names = mock(CharInfoTable.class);
    private MockedStatic<CharacterRelationsManager> relationsInstance;
    private MockedStatic<CharInfoTable> namesInstance;

    @BeforeEach
    void bindManagers() {
        relationsInstance = mockStatic(CharacterRelationsManager.class);
        relationsInstance.when(CharacterRelationsManager::getInstance).thenReturn(relations);
        namesInstance = mockStatic(CharInfoTable.class);
        namesInstance.when(CharInfoTable::getInstance).thenReturn(names);
        when(player.getObjectId()).thenReturn(OWNER_ID);
        when(player.getName()).thenReturn("Owner");
        when(names.getIdByName("Dave")).thenReturn(4);
    }

    @AfterEach
    void unbindManagers() {
        namesInstance.close();
        relationsInstance.close();
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ContactList> call) {
            final ContactList contacts = WorldLoopExtension.callInPass(() -> new ContactList(player));

            assertThatThrownBy(() -> call.accept(contacts))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ContactList>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getAllContacts", ContactList::getAllContacts),
                    Named.of("restore", contacts -> contacts.restore(List.of())),
                    Named.of("add", contacts -> contacts.add("Dave")),
                    Named.of("remove", contacts -> contacts.remove("Alice")));
        }
    }

    @Nested
    class Snapshots {
        @Test
        void getAllContacts_shouldBeReadOnly() {
            final List<String> actual = WorldLoopExtension.callInPass(() -> new ContactList(player).getAllContacts());

            assertThatThrownBy(actual::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getAllContacts_shouldKeepTheStoredOrderThenAddedNames() {
            final List<String> actual = WorldLoopExtension.callInPass(() -> {
                final ContactList contacts = new ContactList(player);
                contacts.restore(STORED);
                contacts.add("Dave");
                return contacts.getAllContacts();
            });

            assertThat(actual).containsExactly("Charlie", "Alice", "Bob", "Dave");
        }
    }

    @Nested
    class Restore {
        @Test
        void restore_shouldKeepTheStoredOrder_withoutTheOwnersName() {
            final List<String> actual = WorldLoopExtension.callInPass(() -> {
                final ContactList contacts = new ContactList(player);
                contacts.restore(List.of("Bob", "Owner", "Alice"));
                return contacts.getAllContacts();
            });

            assertThat(actual).containsExactly("Bob", "Alice");
        }

        @Test
        void restore_shouldReplaceTheContacts() {
            final List<String> actual = WorldLoopExtension.callInPass(() -> {
                final ContactList contacts = new ContactList(player);
                contacts.restore(STORED);
                contacts.restore(List.of("Bob"));
                return contacts.getAllContacts();
            });

            assertThat(actual).containsExactly("Bob");
        }
    }

    @Nested
    class Add {
        @Test
        void add_shouldRefuse_whenTheNameDiffersOnlyInCase() {
            when(names.getIdByName("ALICE")).thenReturn(1);

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final ContactList contacts = new ContactList(player);
                contacts.restore(STORED);
                return contacts.add("ALICE");
            });

            assertThat(actual).isFalse();
        }
    }
}
