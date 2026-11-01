/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.holders.SellBuffHolder;
import app.l2nx.highfive.entity.actor.holders.player.SubClassHolder;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.ActionType;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.handler.IItemHandler;
import app.l2nx.highfive.handler.ItemHandler;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.partition.WorldLoopExtension;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

// A real Player needs the static data and the database; a mock running the real methods has its constructor and field
// initializers skipped, so each test installs the collections it reads.
@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class PlayerTest {
    private final Player player = mock(Player.class, CALLS_REAL_METHODS);

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Player> call) {
            assertThatThrownBy(() -> call.accept(player))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Player>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("load (static)", _ -> Player.load(1)),
                    Named.of("create (static)", _ -> Player.create(null, UUID.randomUUID(), "name", null)),
                    Named.of("getClient (accessor)", Player::getClient),
                    Named.of("isOnline (accessor)", Player::isOnline),
                    Named.of("setTeleMode (former public field)", p -> p.setTeleMode(1)),
                    Named.of("isPlayer", Player::isPlayer),
                    Named.of("sendPacket", p -> p.sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2)),
                    Named.of("getPort", Player::getPort),
                    Named.of("store (former synchronized)", p -> p.store(false)),
                    Named.of("addExpAndSp (former synchronized)", p -> p.addExpAndSp(1, 1)),
                    Named.of("getFriendList", Player::getFriendList),
                    Named.of("isFriend", p -> p.isFriend(1)),
                    Named.of("addFriend", p -> p.addFriend(1)),
                    Named.of("getSubClasses", Player::getSubClasses),
                    Named.of("getSubClass", p -> p.getSubClass(1)),
                    Named.of("getAutoSoulShot", Player::getAutoSoulShot),
                    Named.of("hasAutoSoulShot", p -> p.hasAutoSoulShot(1835)),
                    Named.of("getCubicIds", Player::getCubicIds),
                    Named.of("getSellingBuffs", Player::getSellingBuffs),
                    Named.of("getCharges", Player::getCharges));
        }

        @Test
        void isPlayer_shouldAnswer_whenCalledOnTheLoop() {
            final boolean actual = WorldLoopExtension.callInPass(player::isPlayer);

            assertThat(actual).isTrue();
        }
    }

    @Nested
    class FriendList {
        @Test
        void getFriendList_shouldBeAReadOnlySnapshotInAddingOrder() {
            install("friendList", new IntLinkedOpenHashSet());

            final Set<Integer> actual = WorldLoopExtension.callInPass(() -> {
                player.addFriend(30);
                player.addFriend(10);
                player.addFriend(20);
                final Set<Integer> snapshot = player.getFriendList();
                player.removeFriend(10);
                return snapshot;
            });

            assertThat(actual).containsExactly(30, 10, 20);
            assertThatThrownBy(() -> actual.add(40)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void isFriend_shouldAnswerFromTheLiveList() {
            install("friendList", new IntLinkedOpenHashSet());

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                player.addFriend(7);
                final boolean added = player.isFriend(7);
                player.removeFriend(7);
                return List.of(added, player.isFriend(7));
            });

            assertThat(actual).containsExactly(true, false);
        }
    }

    @Nested
    class AutoSoulShots {
        @Test
        void getAutoSoulShot_shouldBeAReadOnlySnapshot() {
            install("autoSoulShot", new IntLinkedOpenHashSet());

            final Set<Integer> actual = WorldLoopExtension.callInPass(() -> {
                player.addAutoSoulShot(1835);
                final Set<Integer> snapshot = player.getAutoSoulShot();
                player.removeAutoSoulShot(1835);
                return snapshot;
            });

            assertThat(actual).containsExactly(1835);
            assertThatThrownBy(() -> actual.add(2509)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void hasAutoSoulShot_shouldAnswerFromTheLiveSet() {
            install("autoSoulShot", new IntLinkedOpenHashSet());

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                final boolean before = player.hasAutoSoulShots();
                player.addAutoSoulShot(1835);
                return List.of(before, player.hasAutoSoulShots(), player.hasAutoSoulShot(1835));
            });

            assertThat(actual).containsExactly(false, true, true);
        }
    }

    @Nested
    class ChargeAutoShots {
        @Test
        void chargeAutoShots_shouldUseOnlyTheShotsOfTheGivenActions() {
            install("autoSoulShot", new IntLinkedOpenHashSet());
            final PlayerInventory inventory = mock(PlayerInventory.class);
            install("inventory", inventory);
            final Item soulshot = shot(ActionType.SOULSHOT);
            final Item spiritshot = shot(ActionType.SPIRITSHOT);
            final Item summonSoulshot = shot(ActionType.SUMMON_SOULSHOT);
            when(inventory.getItemByItemId(1835)).thenReturn(soulshot);
            when(inventory.getItemByItemId(2509)).thenReturn(spiritshot);
            when(inventory.getItemByItemId(6645)).thenReturn(summonSoulshot);
            final RecordingShotHandler handler = new RecordingShotHandler();
            ItemHandler.getInstance().registerHandler(handler);

            try {
                WorldLoopExtension.runInPass(() -> {
                    player.addAutoSoulShot(1835);
                    player.addAutoSoulShot(2509);
                    player.addAutoSoulShot(6645);
                    player.chargeAutoShots(ActionType.SOULSHOT, null);
                });
            } finally {
                ItemHandler.getInstance().removeHandler(handler);
            }

            assertThat(handler.used).containsExactly(soulshot);
        }

        @Test
        void chargeAutoShots_shouldUseNoShot_whenTheKindOfItsActionIsNotCharged() {
            install("autoSoulShot", new IntLinkedOpenHashSet());
            final PlayerInventory inventory = mock(PlayerInventory.class);
            install("inventory", inventory);
            // No default action stubbed, so a null kind would match it if null were compared as a kind.
            final Item withoutAction = shot(null);
            when(inventory.getItemByItemId(1835)).thenReturn(withoutAction);
            final RecordingShotHandler handler = new RecordingShotHandler();
            ItemHandler.getInstance().registerHandler(handler);

            try {
                WorldLoopExtension.runInPass(() -> {
                    player.addAutoSoulShot(1835);
                    player.chargeAutoShots(null, null);
                });
            } finally {
                ItemHandler.getInstance().removeHandler(handler);
            }

            assertThat(handler.used).isEmpty();
        }

        @Test
        void chargeAutoShots_shouldDropAShot_whenItIsNoLongerInTheInventory() {
            install("autoSoulShot", new IntLinkedOpenHashSet());
            install("inventory", mock(PlayerInventory.class));

            final Set<Integer> actual = WorldLoopExtension.callInPass(() -> {
                player.addAutoSoulShot(1835);
                player.chargeAutoShots(ActionType.SOULSHOT, ActionType.SPIRITSHOT);
                return player.getAutoSoulShot();
            });

            assertThat(actual).isEmpty();
        }

        private static Item shot(ActionType action) {
            final EtcItem template = mock(EtcItem.class);
            when(template.getDefaultAction()).thenReturn(action);
            when(template.getHandlerName()).thenReturn(RecordingShotHandler.class.getSimpleName());
            final Item item = mock(Item.class);
            when(item.getTemplate()).thenReturn(template);
            when(item.getEtcItem()).thenReturn(template);
            return item;
        }
    }

    @Nested
    class DisableAutoShots {
        @Test
        void disableAutoShots_shouldOfferEveryShotInAddingOrder() {
            install("autoSoulShot", new IntLinkedOpenHashSet());
            final List<Integer> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                player.addAutoSoulShot(2509);
                player.addAutoSoulShot(1835);
                player.disableAutoShots(itemId -> {
                    actual.add(itemId);
                    return false;
                });
            });

            assertThat(actual).containsExactly(2509, 1835);
        }

        @Test
        void disableAutoShots_shouldKeepTheShots_whenTheFilterRejectsThem() {
            install("autoSoulShot", new IntLinkedOpenHashSet());

            final Set<Integer> actual = WorldLoopExtension.callInPass(() -> {
                player.addAutoSoulShot(2509);
                player.addAutoSoulShot(1835);
                player.disableAutoShots(itemId -> false);
                return player.getAutoSoulShot();
            });

            assertThat(actual).containsExactly(2509, 1835);
        }
    }

    @Nested
    class SubClasses {
        @Test
        void getSubClasses_shouldBeAReadOnlySnapshotByClassIndex() {
            final Int2ObjectRBTreeMap<SubClassHolder> subClasses = new Int2ObjectRBTreeMap<>();
            install("subClasses", subClasses);

            final Map<Integer, SubClassHolder> actual = WorldLoopExtension.callInPass(() -> {
                subClasses.put(3, new SubClassHolder());
                subClasses.put(1, new SubClassHolder());
                subClasses.put(2, new SubClassHolder());
                return player.getSubClasses();
            });

            assertThat(actual.keySet()).containsExactly(1, 2, 3);
            assertThatThrownBy(() -> actual.remove(1)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getSubClass_shouldBeTheHolderOfTheClassIndex() {
            final Int2ObjectRBTreeMap<SubClassHolder> subClasses = new Int2ObjectRBTreeMap<>();
            install("subClasses", subClasses);
            final SubClassHolder expected = WorldLoopExtension.callInPass(SubClassHolder::new);
            subClasses.put(2, expected);

            final SubClassHolder actual = WorldLoopExtension.callInPass(() -> player.getSubClass(2));

            assertThat(actual).isSameAs(expected);
        }
    }

    @Nested
    class SellingBuffs {
        @Test
        void getSellingBuffs_shouldBeAReadOnlySnapshot() {
            install("sellBuffs", new ObjectArrayList<SellBuffHolder>());
            final SellBuffHolder holder = new SellBuffHolder(1204, 5000);

            final List<SellBuffHolder> actual = WorldLoopExtension.callInPass(() -> {
                player.addSellingBuff(holder);
                final List<SellBuffHolder> snapshot = player.getSellingBuffs();
                player.removeSellingBuff(holder);
                return snapshot;
            });

            assertThat(actual).containsExactly(holder);
            assertThatThrownBy(() -> actual.add(holder)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getSellingBuff_shouldFindTheHolderBySkillId() {
            install("sellBuffs", new ObjectArrayList<SellBuffHolder>());
            final SellBuffHolder expected = new SellBuffHolder(1204, 5000);

            final List<SellBuffHolder> actual = WorldLoopExtension.callInPass(() -> {
                player.addSellingBuff(new SellBuffHolder(1040, 100));
                player.addSellingBuff(expected);
                return Arrays.asList(player.getSellingBuff(1204), player.getSellingBuff(1068));
            });

            assertThat(actual).containsExactly(expected, null);
        }
    }

    private static final class RecordingShotHandler implements IItemHandler {
        private final List<Item> used = new ArrayList<>();

        @Override
        public boolean onItemUse(Playable playable, Item item, boolean forceUse) {
            used.add(item);
            return true;
        }
    }

    private void install(String name, Object value) {
        try {
            final Field field = Player.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(player, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
