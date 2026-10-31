/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.chat.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.action.view.ActionCancelled;
import app.l2nx.highfive.character.CharacterService;
import app.l2nx.highfive.character.view.WorldLeft;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.ChatHandler;
import app.l2nx.highfive.handler.IChatHandler;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.session.GameContexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class SendChatHandlerTest {
    // Tests run in parallel, and the chat handler registry is one per JVM.
    private static final String CHAT_HANDLERS = "chat-handlers";

    private final Player player = mock(Player.class);
    private final ClientPort port = mock(ClientPort.class);
    private final SendChatHandler handler = new SendChatHandler();

    @BeforeEach
    void setUp() {
        when(player.getPort()).thenReturn(port);
    }

    @Nested
    class Context {
        @Test
        void context_shouldBeThePlayerInTheWorld() {
            assertThat(handler.context()).isSameAs(GameContexts.WORLD);
        }
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldShowTheSpamMessage_whenAPlayerSendsMoreThan105Characters() {
            handler.handle(
                    player,
                    SendChat.builder()
                            .text("a".repeat(106))
                            .type(ChatType.GENERAL)
                            .build());

            verify(port).emit(MessageShown.of(MessageKey.CHAT_INPUT_SPAM_BAN));
        }

        @ParameterizedTest(name = "{0} characters, spam answered: {1}")
        @CsvSource({"2048,false", "2049,true"})
        void handle_shouldShowTheSpamMessageOnlyPastTheGmCap_whenAGmSpeaks(int length, boolean spam) {
            when(player.isGM()).thenReturn(true);

            handler.handle(
                    player,
                    SendChat.builder()
                            .text("a".repeat(length))
                            .type(ChatType.GENERAL)
                            .build());

            verify(port, spam ? times(1) : never()).emit(MessageShown.of(MessageKey.CHAT_INPUT_SPAM_BAN));
        }

        @Test
        @ResourceLock(CHAT_HANDLERS)
        void handle_shouldAnswerAsToANameNobodyHasAndReachNoChannel_whenTheWhisperTargetIsLongerThanAnyName() {
            final IChatHandler whispers = whispers();
            try {
                handler.handle(player, whisper("a".repeat(CharacterService.MAX_NAME_LENGTH + 1)));
            } finally {
                ChatHandler.getInstance().removeHandler(whispers);
            }

            verify(port).emit(MessageShown.of(MessageKey.PLAYER_NOT_ONLINE));
            verify(whispers, never()).onChat(any(), any(), any(), any());
        }

        @Test
        @ResourceLock(CHAT_HANDLERS)
        void handle_shouldPassTheWhisperToItsChannel_whenTheTargetHasTheLongestNameLength() {
            final String target = "a".repeat(CharacterService.MAX_NAME_LENGTH);
            final IChatHandler whispers = whispers();
            try {
                handler.handle(player, whisper(target));
            } finally {
                ChatHandler.getInstance().removeHandler(whispers);
            }

            verify(whispers).onChat(ChatType.WHISPER, player, target, "hi");
            verify(port, never()).emit(MessageShown.of(MessageKey.PLAYER_NOT_ONLINE));
        }

        @Test
        void handle_shouldAllow500Characters_whenTheTextLinksAnItem() {
            // A link without "ID=" stops in the item publishing, after the length check passed and before any channel.
            handler.handle(
                    player,
                    SendChat.builder()
                            .text("\b" + "a".repeat(400))
                            .type(ChatType.GENERAL)
                            .build());

            verify(port, never()).emit(MessageShown.of(MessageKey.CHAT_INPUT_SPAM_BAN));
        }

        @ParameterizedTest(name = "link \"{0}\"")
        @ValueSource(strings = {"ID=", "ID=123", "ID=99999999999", "ID=x"})
        void handle_shouldRefuseTheLine_whenAnItemLinkHasNoValidObjectId(String text) {
            // The object lookup belongs to the world loop's owner.
            assertThatCode(() -> WorldLoopExtension.runInPass(() -> handler.handle(
                            player,
                            SendChat.builder().text(text).type(ChatType.GENERAL).build())))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "text \"{0}\" on channel {1}")
        @CsvSource({"x,", "'',GENERAL"})
        void handle_shouldCancelAndLeaveTheWorld_whenThePacketIsForged(String text, ChatType type) {
            final Disconnection disconnection = mock(Disconnection.class);
            try (MockedStatic<Disconnection> disconnections = mockStatic(Disconnection.class)) {
                disconnections.when(() -> Disconnection.of(player)).thenReturn(disconnection);

                handler.handle(player, SendChat.builder().text(text).type(type).build());
            }

            verify(port).emit(ActionCancelled.INSTANCE);
            verify(disconnection).storeAndCloseWith(WorldLeft.INSTANCE);
        }
    }

    private static SendChat whisper(String target) {
        return SendChat.builder()
                .text("hi")
                .type(ChatType.WHISPER)
                .target(target)
                .build();
    }

    private static IChatHandler whispers() {
        final IChatHandler whispers = mock(IChatHandler.class);
        when(whispers.getChatTypeList()).thenReturn(new ChatType[] {ChatType.WHISPER});
        ChatHandler.getInstance().registerHandler(whispers);
        return whispers;
    }
}
