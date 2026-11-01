/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.ParticipantState;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.holders.AccessLevel;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.BlockList;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.network.holders.TradeList;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("GeneralConfig")
class WorldMailParticipantsTest {
    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final WorldMailParticipants participants = new WorldMailParticipants(registry);
    private final UUID characterId = EntityIds.next();
    private final int handle = registry.handleOf(characterId);
    private final Player player = mock(Player.class);
    private final AccessLevel accessLevel = mock(AccessLevel.class);
    private final AttackStanceTaskManager stances = mock(AttackStanceTaskManager.class);
    private MockedStatic<World> world;
    private MockedStatic<AttackStanceTaskManager> stance;
    private boolean jailDisableTransaction;
    private boolean jailDisableChat;

    @BeforeEach
    void setUp() {
        when(player.getObjectId()).thenReturn(handle);
        when(player.getName()).thenReturn("Alice");
        when(player.getAccessLevel()).thenReturn(accessLevel);
        when(accessLevel.allowTransaction()).thenReturn(true);
        world = mockStatic(World.class);
        world.when(() -> World.getPlayer(handle)).thenReturn(player);
        stance = mockStatic(AttackStanceTaskManager.class);
        stance.when(AttackStanceTaskManager::getInstance).thenReturn(stances);
        jailDisableTransaction = GeneralConfig.JAIL_DISABLE_TRANSACTION;
        jailDisableChat = GeneralConfig.JAIL_DISABLE_CHAT;
    }

    @AfterEach
    void tearDown() {
        world.close();
        stance.close();
        GeneralConfig.JAIL_DISABLE_TRANSACTION = jailDisableTransaction;
        GeneralConfig.JAIL_DISABLE_CHAT = jailDisableChat;
    }

    @Nested
    class State {
        @Test
        void state_shouldAllowEverything_forAnIdlePlayer() {
            assertThat(participants.state(handle))
                    .isEqualTo(ParticipantState.builder().tradeAllowed(true).build());
        }

        @Test
        void state_shouldReadThePeaceZoneDeathGmAndAccessLevel() {
            when(player.isInsideZone(ZoneId.PEACE)).thenReturn(true);
            when(player.isAlikeDead()).thenReturn(true);
            when(player.isGM()).thenReturn(true);
            when(accessLevel.allowTransaction()).thenReturn(false);

            assertThat(participants.state(handle))
                    .isEqualTo(ParticipantState.builder()
                            .peaceZone(true)
                            .dead(true)
                            .gm(true)
                            .build());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Combat.class)
        void state_shouldMarkCombat(Combat combat) {
            combat.apply(WorldMailParticipantsTest.this);

            assertThat(participants.state(handle).inCombat()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Busy.class)
        void state_shouldMarkBusy(Busy busy) {
            busy.apply(player);

            assertThat(participants.state(handle).busy()).isTrue();
        }

        @ParameterizedTest(name = "jailed {0}, disable transaction {1}, disable chat {2}")
        @CsvSource({
            "true, true, false, false, true",
            "true, false, true, true, false",
            "true, true, true, true, true",
            "false, true, true, false, false"
        })
        void state_shouldFollowTheLegacyJailRules(
                boolean jailed,
                boolean disableTransaction,
                boolean disableChat,
                boolean mailBlocked,
                boolean transactionsBlocked) {
            when(player.isJailed()).thenReturn(jailed);
            GeneralConfig.JAIL_DISABLE_TRANSACTION = disableTransaction;
            GeneralConfig.JAIL_DISABLE_CHAT = disableChat;

            final ParticipantState actual = participants.state(handle);

            assertThat(actual.mailBlocked()).isEqualTo(mailBlocked);
            assertThat(actual.transactionsBlocked()).isEqualTo(transactionsBlocked);
        }
    }

    @Nested
    class Online {
        @Test
        void online_shouldFindTheWorldPlayer_byItsPersistentId() {
            final ClientPort port = mock(ClientPort.class);
            when(player.getPort()).thenReturn(port);

            assertThat(participants.online(characterId))
                    .contains(MailActor.builder()
                            .characterHandle(handle)
                            .characterId(characterId)
                            .name("Alice")
                            .port(port)
                            .build());
        }

        @Test
        void online_shouldBeEmpty_whenTheCharacterHasNoHandle() {
            assertThat(participants.online(EntityIds.next())).isEmpty();
        }

        @Test
        void online_shouldBeEmpty_whenTheCharacterIsNotInTheWorld() {
            world.when(() -> World.getPlayer(handle)).thenReturn(null);

            assertThat(participants.online(characterId)).isEmpty();
        }

        @Test
        void online_shouldFindALoadedCharacter_beforeItEntersTheWorld() {
            world.when(() -> World.getPlayer(handle)).thenReturn(null);
            WorldPartition.loadedCharacters().register(characterId, player);
            try {
                assertThat(participants.online(characterId))
                        .map(MailActor::characterHandle)
                        .contains(handle);
            } finally {
                WorldPartition.loadedCharacters().release(characterId, player);
            }
        }
    }

    @Nested
    class RefusesMailFrom {
        private final UUID senderId = EntityIds.next();

        @ParameterizedTest(name = "blocked {0}")
        @CsvSource({"true", "false"})
        void refusesMailFrom_shouldAskTheBlockListOfTheReceiverInTheWorld(boolean blocked) {
            world.when(() -> World.getPlayer("Alice")).thenReturn(player);
            try (MockedStatic<BlockList> lists = mockStatic(BlockList.class)) {
                lists.when(() -> BlockList.isBlocked(player, registry.handleOf(senderId)))
                        .thenReturn(blocked);

                assertThat(participants.refusesMailFrom("Alice", senderId)).isEqualTo(blocked);
            }
        }

        @Test
        void refusesMailFrom_shouldRefuseNothing_whenTheReceiverIsNotInTheWorld() {
            assertThat(participants.refusesMailFrom("Bob", senderId)).isFalse();
        }
    }

    @Nested
    class Transactions {
        @Test
        void endTransaction_shouldReleaseTheWorkWaitingForTheCharacter() {
            final AtomicBoolean saved = new AtomicBoolean();

            WorldLoopExtension.runInPass(() -> {
                participants.beginTransaction(characterId);
                participants.beginTransaction(characterId);
                WorldPartition.inFlightCharacters().whenIdle(characterId, () -> saved.set(true));
                participants.endTransaction(characterId);
            });
            assertThat(saved).isFalse();

            WorldLoopExtension.runInPass(() -> participants.endTransaction(characterId));
            assertThat(saved).isTrue();
        }

        @Test
        void loop_shouldDeliverOnTheWorldLoop() {
            final AtomicBoolean ran = new AtomicBoolean();

            WorldLoopExtension.runInPass(() -> participants.loop().execute(() -> ran.set(true)));
            WorldLoopExtension.runInPass(() -> {});

            assertThat(ran).isTrue();
        }
    }

    enum Combat {
        ATTACKING(test -> when(test.player.isInCombat()).thenReturn(true)),
        ATTACK_STANCE(
                test -> when(test.stances.hasAttackStanceTask(test.player)).thenReturn(true));

        private final Consumer<WorldMailParticipantsTest> setup;

        Combat(Consumer<WorldMailParticipantsTest> setup) {
            this.setup = setup;
        }

        void apply(WorldMailParticipantsTest test) {
            setup.accept(test);
        }
    }

    enum Busy {
        TRADING(player -> when(player.getActiveTradeList()).thenReturn(mock(TradeList.class))),
        STORE(player -> when(player.isInStoreMode()).thenReturn(true)),
        ENCHANTING(player -> when(player.isEnchanting()).thenReturn(true)),
        INVENTORY_DISABLED(player -> when(player.isInventoryDisabled()).thenReturn(true));

        private final Consumer<Player> setup;

        Busy(Consumer<Player> setup) {
            this.setup = setup;
        }

        void apply(Player player) {
            setup.accept(player);
        }
    }
}
