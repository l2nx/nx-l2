/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.chat.view.ChatMessage;
import app.l2nx.highfive.entity.actor.Player;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ChatServiceTest {
    private static final ChatMessage MESSAGE = ChatMessage.builder()
            .senderHandle(1)
            .type(ChatType.SHOUT)
            .senderName("A")
            .text("b")
            .build();

    private final ChatService service = new ChatService();

    @Nested
    class Message {
        @Test
        void message_shouldNameTheSenderByItsName_whenNoShownNameIsGiven() {
            final ChatMessage actual = ChatService.message(sender(), ChatType.SHOUT, "hi");

            assertThat(actual).isEqualTo(line("Alice"));
        }

        @Test
        void message_shouldNameTheSenderByTheShownName_whenOneIsGiven() {
            final ChatMessage actual = ChatService.message(sender(), "Masked", ChatType.SHOUT, "hi");

            assertThat(actual).isEqualTo(line("Masked"));
        }

        private static Player sender() {
            final Player sender = mock(Player.class);
            when(sender.getObjectId()).thenReturn(7);
            when(sender.getName()).thenReturn("Alice");
            return sender;
        }

        private static ChatMessage line(String senderName) {
            return ChatMessage.builder()
                    .senderHandle(7)
                    .type(ChatType.SHOUT)
                    .senderName(senderName)
                    .text("hi")
                    .build();
        }
    }

    @Nested
    class DeliverAll {
        @Test
        void deliverAll_shouldEmitAndSnoopForEachReceiverInTurn() {
            final Player first = mock(Player.class);
            final Player second = mock(Player.class);
            final ClientPort firstPort = mock(ClientPort.class);
            final ClientPort secondPort = mock(ClientPort.class);
            when(first.getPort()).thenReturn(firstPort);
            when(second.getPort()).thenReturn(secondPort);

            service.deliverAll(List.of(first, second), MESSAGE);

            final InOrder order = inOrder(firstPort, first, secondPort, second);
            order.verify(firstPort).emit(MESSAGE);
            order.verify(first).broadcastSnoop(MESSAGE);
            order.verify(secondPort).emit(MESSAGE);
            order.verify(second).broadcastSnoop(MESSAGE);
        }
    }
}
