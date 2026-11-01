/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class BlockListTest {
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
        when(names.getNameById(anyInt())).thenAnswer(invocation -> "Player" + invocation.getArgument(0));
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
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Player> call) {
            final Player owner = owner(268_200_001, List.of());

            assertThatThrownBy(() -> call.accept(owner))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Player>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("playerLogout", owner -> owner.getBlockList().playerLogout()),
                    Named.of("isInBlockList (id)", owner -> owner.getBlockList().isInBlockList(1)),
                    Named.of(
                            "isInBlockList (player)",
                            owner -> owner.getBlockList().isInBlockList(owner)),
                    Named.of("isBlockAll", owner -> owner.getBlockList().isBlockAll(owner)),
                    Named.of("isBlocked (id)", owner -> BlockList.isBlocked(owner, 1)),
                    Named.of("isBlocked (player)", owner -> BlockList.isBlocked(owner, owner)),
                    Named.of("addToBlockList", owner -> BlockList.addToBlockList(owner, 1)),
                    Named.of("removeFromBlockList", owner -> BlockList.removeFromBlockList(owner, 1)),
                    Named.of("isInBlockList (static)", owner -> BlockList.isInBlockList(owner, owner)),
                    Named.of("setBlockAll", owner -> BlockList.setBlockAll(owner, true)),
                    Named.of("sendListToOwner", BlockList::sendListToOwner),
                    Named.of("isInBlockList (offline)", owner -> BlockList.isInBlockList(1, 2)));
        }
    }

    @Nested
    class Membership {
        @Test
        void isBlocked_shouldAnswerForLoadedAndAddedTargets() {
            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                final Player owner = owner(268_200_002, List.of(7));
                BlockList.addToBlockList(owner, 9);
                return List.of(
                        BlockList.isBlocked(owner, 7), BlockList.isBlocked(owner, 9), BlockList.isBlocked(owner, 8));
            });

            assertThat(actual).containsExactly(true, true, false);
        }

        @Test
        void removeFromBlockList_shouldUnblockTheTarget() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final Player owner = owner(268_200_003, List.of(7));
                BlockList.removeFromBlockList(owner, 7);
                return BlockList.isBlocked(owner, 7);
            });

            assertThat(actual).isFalse();
        }
    }

    @Nested
    class Order {
        @Test
        void sendListToOwner_shouldListTargetsInLoadThenBlockOrder() {
            final Player owner = WorldLoopExtension.callInPass(() -> {
                final Player created = owner(268_200_004, List.of(30, 10));
                BlockList.addToBlockList(created, 20);
                BlockList.sendListToOwner(created);
                return created;
            });

            final InOrder order = inOrder(owner);
            order.verify(owner).sendMessage("1. Player30");
            order.verify(owner).sendMessage("2. Player10");
            order.verify(owner).sendMessage("3. Player20");
        }
    }

    private Player owner(int objectId, List<Integer> blocked) {
        final Player owner = mock(Player.class);
        when(owner.getObjectId()).thenReturn(objectId);
        when(relations.findBlockedIds(objectId)).thenReturn(blocked);
        final BlockList blockList = new BlockList(owner);
        when(owner.getBlockList()).thenReturn(blockList);
        return owner;
    }
}
