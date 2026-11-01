/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SendMacroList;
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
import org.mockito.InOrder;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class MacroListTest {
    private final Player owner = mock(Player.class);
    private final PlayerPersistenceManager persistence = mock(PlayerPersistenceManager.class);
    private final MacroList macros = new MacroList(owner);
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
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<MacroList> call) {
            assertThatThrownBy(() -> call.accept(macros))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<MacroList>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getRevision", MacroList::getRevision),
                    Named.of("getMacroCount", MacroList::getMacroCount),
                    Named.of("isNameTaken", macros -> macros.isNameTaken("Buff", 0)),
                    Named.of("registerMacro", macros -> macros.registerMacro(macro(0, "Buff"))),
                    Named.of("deleteMacro", macros -> macros.deleteMacro(1000)),
                    Named.of("sendUpdate", MacroList::sendUpdate),
                    Named.of("restoreMe", MacroList::restoreMe));
        }
    }

    @Nested
    class Register {
        @Test
        void registerMacro_shouldAssignTheNextFreeId_whenTheMacroHasNone() {
            final Macro unsaved = macro(0, "Buff");

            WorldLoopExtension.runInPass(() -> {
                macros.registerMacro(macro(1000, "Heal"));
                macros.registerMacro(unsaved);
            });

            final InOrder order = inOrder(persistence);
            order.verify(persistence).saveMacro(anyInt(), argThat(saved -> saved.macroId() == 1000));
            order.verify(persistence).saveMacro(anyInt(), argThat(saved -> saved.macroId() == 1001));
            assertThat(unsaved.getId()).isZero();
        }

        @Test
        void sendUpdate_shouldSendOnePacketPerMacro() {
            WorldLoopExtension.runInPass(() -> {
                macros.registerMacro(macro(1000, "Heal"));
                macros.registerMacro(macro(1001, "Buff"));
            });
            clearInvocations(owner);

            WorldLoopExtension.runInPass(macros::sendUpdate);

            verify(owner, times(2)).sendPacket(any(SendMacroList.class));
        }
    }

    @Nested
    class Lookups {
        @Test
        void isNameTaken_shouldIgnoreCaseAndTheMacroItself() {
            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                macros.registerMacro(macro(1000, "Heal"));
                return List.of(macros.isNameTaken("HEAL", 1001), macros.isNameTaken("heal", 1000));
            });

            assertThat(actual).containsExactly(true, false);
        }

        @Test
        void getMacroCount_shouldCountTheRemainingMacros_whenOneIsDeleted() {
            final int actual = WorldLoopExtension.callInPass(() -> {
                macros.registerMacro(macro(1000, "Heal"));
                macros.registerMacro(macro(1001, "Buff"));
                macros.deleteMacro(1000);
                return macros.getMacroCount();
            });

            assertThat(actual).isEqualTo(1);
        }
    }

    private static Macro macro(int id, String name) {
        return new Macro(id, 1, name, "description", "AC", List.of());
    }
}
