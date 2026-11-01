/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.mail.ItemCount;
import app.l2nx.engine.mail.command.CancelMail;
import app.l2nx.engine.mail.command.DeleteReceivedMail;
import app.l2nx.engine.mail.command.DeleteSentMail;
import app.l2nx.engine.mail.command.ListMailableItems;
import app.l2nx.engine.mail.command.ListReceivedMail;
import app.l2nx.engine.mail.command.ListSentMail;
import app.l2nx.engine.mail.command.OpenReceivedMail;
import app.l2nx.engine.mail.command.OpenSentMail;
import app.l2nx.engine.mail.command.ReceiveAttachments;
import app.l2nx.engine.mail.command.ReturnMail;
import app.l2nx.engine.mail.command.SendMail;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.InboundDecoder;
import app.l2nx.highfive.network.protocol.p267.P267MailNumbers;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.clientpackets.RequestCancelPostAttachment;
import app.l2nx.protocol.game.p267.clientpackets.RequestDeleteReceivedPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestDeleteSentPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestPostAttachment;
import app.l2nx.protocol.game.p267.clientpackets.RequestPostItemList;
import app.l2nx.protocol.game.p267.clientpackets.RequestReceivedPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestReceivedPostList;
import app.l2nx.protocol.game.p267.clientpackets.RequestRejectPostAttachment;
import app.l2nx.protocol.game.p267.clientpackets.RequestSendPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestSentPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestSentPostList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MailDecodersTest {
    // The extended prefix and the 2-byte sub-opcode the table reads before the decoder runs.
    private static final int EX_HEADER = 3;
    private static final UUID RECEIVED = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final UUID SENT = UUID.fromString("018f0000-0000-7000-8000-000000000002");
    private static final int RECEIVED_NUMBER = 1;
    private static final int SENT_NUMBER = 2;
    private static final int UNKNOWN = 99;

    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final GameConnection connection = mock(GameConnection.class);

    @BeforeEach
    void showBoxes() {
        when(connection.channel()).thenReturn(channel);
        P267MailNumbers.of(connection).show(MailBox.RECEIVED, List.of(RECEIVED));
        P267MailNumbers.of(connection).show(MailBox.SENT, List.of(SENT));
    }

    @AfterEach
    void close() {
        channel.finishAndReleaseAll();
    }

    @Nested
    class Lists {
        @Test
        void postItemList_shouldListTheMailableItems() {
            assertThat(decode(RequestPostItemList.INSTANCE::encode, MailDecoders::postItemList))
                    .isEqualTo(new ListMailableItems());
        }

        @Test
        void receivedPostList_shouldListTheReceivedBox() {
            assertThat(decode(RequestReceivedPostList.INSTANCE::encode, MailDecoders::receivedPostList))
                    .isEqualTo(new ListReceivedMail());
        }

        @Test
        void sentPostList_shouldListTheSentBox() {
            assertThat(decode(RequestSentPostList.INSTANCE::encode, MailDecoders::sentPostList))
                    .isEqualTo(new ListSentMail());
        }
    }

    @Nested
    class SingleMail {
        @ParameterizedTest(name = "{0}")
        @MethodSource("requests")
        void decode_shouldResolveTheNumberToTheMail(
                String packet, Consumer<ByteBuf> request, InboundDecoder decoder, Object expected) {
            assertThat(decode(request, decoder)).isEqualTo(expected);
        }

        static Stream<Arguments> requests() {
            return Stream.of(
                    Arguments.of(
                            "RequestReceivedPost",
                            (Consumer<ByteBuf>) new RequestReceivedPost(RECEIVED_NUMBER)::encode,
                            (InboundDecoder) MailDecoders::receivedPost,
                            new OpenReceivedMail(RECEIVED)),
                    Arguments.of(
                            "RequestPostAttachment",
                            (Consumer<ByteBuf>) new RequestPostAttachment(RECEIVED_NUMBER)::encode,
                            (InboundDecoder) MailDecoders::postAttachment,
                            new ReceiveAttachments(RECEIVED)),
                    Arguments.of(
                            "RequestRejectPostAttachment",
                            (Consumer<ByteBuf>) new RequestRejectPostAttachment(RECEIVED_NUMBER)::encode,
                            (InboundDecoder) MailDecoders::rejectPostAttachment,
                            new ReturnMail(RECEIVED)),
                    Arguments.of(
                            "RequestSentPost",
                            (Consumer<ByteBuf>) new RequestSentPost(SENT_NUMBER)::encode,
                            (InboundDecoder) MailDecoders::sentPost,
                            new OpenSentMail(SENT)),
                    Arguments.of(
                            "RequestCancelPostAttachment",
                            (Consumer<ByteBuf>) new RequestCancelPostAttachment(SENT_NUMBER)::encode,
                            (InboundDecoder) MailDecoders::cancelPostAttachment,
                            new CancelMail(SENT)),
                    Arguments.of(
                            "RequestDeleteReceivedPost",
                            (Consumer<ByteBuf>) new RequestDeleteReceivedPost(List.of(RECEIVED_NUMBER))::encode,
                            (InboundDecoder) MailDecoders::deleteReceivedPost,
                            new DeleteReceivedMail(List.of(RECEIVED))),
                    Arguments.of(
                            "RequestDeleteSentPost",
                            (Consumer<ByteBuf>) new RequestDeleteSentPost(List.of(SENT_NUMBER))::encode,
                            (InboundDecoder) MailDecoders::deleteSentPost,
                            new DeleteSentMail(List.of(SENT))));
        }

        @Test
        void receivedPost_shouldBeMalformed_whenTheNumberWasNeverGiven() {
            assertThatThrownBy(() -> decode(new RequestReceivedPost(UNKNOWN)::encode, MailDecoders::receivedPost))
                    .isInstanceOf(PacketDecodeException.class)
                    .hasMessageContaining(String.valueOf(UNKNOWN));
        }

        @Test
        void deleteReceivedPost_shouldBeMalformed_whenOneNumberWasNeverGiven() {
            final RequestDeleteReceivedPost packet = new RequestDeleteReceivedPost(List.of(RECEIVED_NUMBER, UNKNOWN));

            assertThatThrownBy(() -> decode(packet::encode, MailDecoders::deleteReceivedPost))
                    .isInstanceOf(PacketDecodeException.class);
        }

        @Test
        void sentPost_shouldBeMalformed_whenTheBoxWasShownAgainWithoutTheMail() {
            P267MailNumbers.of(connection).show(MailBox.SENT, List.of());

            assertThatThrownBy(() -> decode(new RequestSentPost(SENT_NUMBER)::encode, MailDecoders::sentPost))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class SendPost {
        private static final RequestSendPost POST = RequestSendPost.builder()
                .receiverName("Bob")
                .subject("Hi")
                .body("Text")
                .attachments(List.of(new RequestSendPost.Attachment(0x10000005, 3)))
                .build();

        @Test
        void sendPost_shouldPassTheItemHandlesThrough() {
            assertThat(decode(POST::encode, MailDecoders::sendPost))
                    .isEqualTo(SendMail.builder()
                            .receiverName("Bob")
                            .subject("Hi")
                            .body("Text")
                            .items(List.of(new ItemCount(0x10000005, 3)))
                            .build());
        }

        @Test
        void sendPost_shouldTakeTheAmount_whenTheMailIsCashOnDelivery() {
            final RequestSendPost packet =
                    POST.toBuilder().cashOnDelivery(true).codAdena(500).build();

            assertThat(decode(packet::encode, MailDecoders::sendPost))
                    .extracting(command -> ((SendMail) command).codAdena())
                    .isEqualTo(500L);
        }

        @Test
        void sendPost_shouldKeepAZeroAmountForTheHandlerToAnswer_whenTheMailIsCashOnDelivery() {
            final RequestSendPost packet = POST.toBuilder().cashOnDelivery(true).build();

            assertThat(decode(packet::encode, MailDecoders::sendPost))
                    .extracting(command -> ((SendMail) command).codAdena())
                    .isEqualTo(0L);
        }

        @Test
        void sendPost_shouldIgnoreTheAmount_whenTheMailIsNotCashOnDelivery() {
            final RequestSendPost packet = POST.toBuilder().codAdena(500).build();

            assertThat(decode(packet::encode, MailDecoders::sendPost))
                    .extracting(command -> ((SendMail) command).codAdena())
                    .isNull();
        }

        @Test
        void sendPost_shouldLeaveTheBodyNull_whenItIsBlank() {
            final RequestSendPost packet = POST.toBuilder().body(" ").build();

            assertThat(decode(packet::encode, MailDecoders::sendPost))
                    .extracting(command -> ((SendMail) command).body())
                    .isNull();
        }
    }

    private Object decode(Consumer<ByteBuf> packet, InboundDecoder decoder) {
        final ByteBuf frame = Unpooled.buffer();
        try {
            packet.accept(frame);
            frame.skipBytes(EX_HEADER);
            return decoder.decode(frame, connection);
        } finally {
            frame.release();
        }
    }
}
