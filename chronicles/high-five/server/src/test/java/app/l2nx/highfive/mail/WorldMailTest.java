/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailService;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@Execution(ExecutionMode.SAME_THREAD)
class WorldMailTest {
    private static final Executor LOOP = Runnable::run;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final MailService mailService = mock(MailService.class);
    private final WorldMailParticipants participants = mock(WorldMailParticipants.class);
    private final UUID characterId = EntityIds.next();
    private final int handle = registry.handleOf(characterId);
    private final Player player = mock(Player.class);
    private final List<Boolean> answers = new ArrayList<>();
    private MockedStatic<World> world;

    @BeforeEach
    void setUp() {
        when(participants.loop()).thenReturn(LOOP);
        when(player.getObjectId()).thenReturn(handle);
        world = mockStatic(World.class);
        world.when(() -> World.getPlayer(handle)).thenReturn(player);
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private WorldMail worldMail(MailSettings settings) {
        return new WorldMail(mailService, settings, participants, registry);
    }

    private WorldMail worldMail() {
        return worldMail(MailSettings.defaults());
    }

    @Nested
    class Enabled {
        @ParameterizedTest(name = "l2nx.mail.enabled={0}")
        @ValueSource(booleans = {true, false})
        void enabled_shouldFollowTheSettings(boolean enabled) {
            final WorldMail actual = worldMail(
                    MailSettings.defaults().toBuilder().enabled(enabled).build());

            assertThat(actual.enabled()).isEqualTo(enabled);
        }
    }

    @Nested
    class OnWorldEntered {
        @Test
        void onWorldEntered_shouldTellMailTheCharacterAsActor() {
            final MailActor actor = MailActor.builder()
                    .characterHandle(handle)
                    .characterId(characterId)
                    .name("Alice")
                    .port(mock(ClientPort.class))
                    .build();
            when(participants.actor(player)).thenReturn(actor);

            worldMail().onWorldEntered(player);

            verify(mailService).onWorldEntered(actor);
        }
    }

    @Nested
    class HasAttached {
        @Test
        void hasAttached_shouldAskForTheCharacterAndItemsOnTheWorldLoop() {
            answerAttached(true);

            worldMail().hasAttached(player, new int[] {13539, 57}, answers::add);

            final ArgumentCaptor<IntSet> templates = ArgumentCaptor.forClass(IntSet.class);
            verify(mailService).hasAttached(eq(characterId), templates.capture(), eq(LOOP), any());
            assertThat(templates.getValue()).containsExactlyInAnyOrder(13539, 57);
            assertThat(answers).containsExactly(true);
        }

        @Test
        void hasAttached_shouldDropTheAnswer_whenThePlayerLeftTheWorld() {
            doAnswer(invocation -> {
                        world.when(() -> World.getPlayer(handle)).thenReturn(null);
                        invocation.<Consumer<Boolean>>getArgument(3).accept(false);
                        return null;
                    })
                    .when(mailService)
                    .hasAttached(eq(characterId), any(), eq(LOOP), any());

            worldMail().hasAttached(player, new int[] {13539}, answers::add);

            assertThat(answers).isEmpty();
        }

        private void answerAttached(boolean attached) {
            doAnswer(invocation -> {
                        invocation.<Consumer<Boolean>>getArgument(3).accept(attached);
                        return null;
                    })
                    .when(mailService)
                    .hasAttached(eq(characterId), any(), eq(LOOP), any());
        }
    }

    @Nested
    class OnCharacterDeleted {
        @Test
        void onCharacterDeleted_shouldUseOneKeyPerCharacterOnTheWorldLoop() {
            final UUID otherCharacter = EntityIds.next();
            final Consumer<Boolean> done = answers::add;

            worldMail().onCharacterDeleted(characterId, done);
            worldMail().onCharacterDeleted(otherCharacter, done);

            verify(mailService).onCharacterDeleted(characterId, WorldMail.deletionKey(characterId), LOOP, done);
            verify(mailService).onCharacterDeleted(otherCharacter, WorldMail.deletionKey(otherCharacter), LOOP, done);
            assertThat(WorldMail.deletionKey(characterId))
                    .isEqualTo(WorldMail.deletionKey(characterId))
                    .isNotEqualTo(WorldMail.deletionKey(otherCharacter));
        }
    }
}
