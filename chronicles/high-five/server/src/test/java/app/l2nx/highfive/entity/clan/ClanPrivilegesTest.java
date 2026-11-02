/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.clan;

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
class ClanPrivilegesTest {
    private final ClanPrivileges privileges = new ClanPrivileges();

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ClanPrivileges> call) {
            assertThatThrownBy(() -> call.accept(privileges))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ClanPrivileges>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getMask (accessor)", ClanPrivileges::getMask),
                    Named.of("setMask (accessor)", p -> p.setMask(1)),
                    Named.of("enableAll", ClanPrivileges::enableAll),
                    Named.of("disableAll", ClanPrivileges::disableAll),
                    Named.of("setPrivileges", p -> p.setPrivileges(ClanAccess.INVITE_MEMBER)),
                    Named.of("hasMinimumPrivileges", p -> p.hasMinimumPrivileges(ClanAccess.INVITE_MEMBER)),
                    Named.of("clone", ClanPrivileges::clone),
                    Named.of("getCompleteMask (static)", _ -> ClanPrivileges.getCompleteMask()));
        }
    }

    @Nested
    class EnableAll {
        @Test
        void enableAll_shouldGrantEveryAccess() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                privileges.enableAll();
                final ClanAccess[] accesses = ClanAccess.values();
                return privileges.getMask() == ClanPrivileges.getCompleteMask()
                        && privileges.hasMinimumPrivileges(accesses[0], accesses);
            });

            assertThat(actual).isTrue();
        }
    }

    @Nested
    class SetPrivileges {
        @Test
        void setPrivileges_shouldReplacePreviousAccess() {
            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                privileges.enableAll();
                privileges.setPrivileges(ClanAccess.INVITE_MEMBER);
                return List.of(
                        privileges.hasMinimumPrivileges(ClanAccess.INVITE_MEMBER),
                        privileges.hasMinimumPrivileges(ClanAccess.INVITE_MEMBER, ClanAccess.values()));
            });

            assertThat(actual).containsExactly(true, false);
        }

        @Test
        void removeMinimumPrivileges_shouldKeepOtherAccess() {
            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                privileges.setMinimumPrivileges(ClanAccess.INVITE_MEMBER, ClanAccess.values());
                privileges.removeMinimumPrivileges(ClanAccess.INVITE_MEMBER);
                final ClanAccess other = ClanAccess.values()[ClanAccess.values().length - 1];
                return List.of(
                        privileges.hasMinimumPrivileges(ClanAccess.INVITE_MEMBER),
                        privileges.hasMinimumPrivileges(other));
            });

            assertThat(actual).containsExactly(false, true);
        }
    }

    @Nested
    class Clone {
        @Test
        void clone_shouldCopyMaskIndependently() {
            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                privileges.setMask(0b101);
                final ClanPrivileges copy = privileges.clone();
                privileges.disableAll();
                return List.of(copy.getMask(), privileges.getMask());
            });

            assertThat(actual).containsExactly(0b101, 0);
        }
    }
}
