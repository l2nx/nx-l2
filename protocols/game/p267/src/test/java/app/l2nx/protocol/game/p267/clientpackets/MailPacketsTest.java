/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.c;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static app.l2nx.protocol.game.p267.WireHex.h;
import static app.l2nx.protocol.game.p267.WireHex.q;
import static app.l2nx.protocol.game.p267.WireHex.s;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MailPacketsTest {
    private static String ex(GameClientExOpcode opcode) {
        return c(GameClientExOpcode.PREFIX) + h(opcode.opcode());
    }

    @Nested
    class EmptyPackets {
        static Stream<Arguments> packets() {
            return Stream.of(
                    arguments(
                            GameClientExOpcode.REQUEST_POST_ITEM_LIST,
                            RequestPostItemList.INSTANCE,
                            (Function<ByteBuf, Object>) RequestPostItemList::decode,
                            (Consumer<ByteBuf>) RequestPostItemList.INSTANCE::encode),
                    arguments(
                            GameClientExOpcode.REQUEST_RECEIVED_POST_LIST,
                            RequestReceivedPostList.INSTANCE,
                            (Function<ByteBuf, Object>) RequestReceivedPostList::decode,
                            (Consumer<ByteBuf>) RequestReceivedPostList.INSTANCE::encode),
                    arguments(
                            GameClientExOpcode.REQUEST_SENT_POST_LIST,
                            RequestSentPostList.INSTANCE,
                            (Function<ByteBuf, Object>) RequestSentPostList::decode,
                            (Consumer<ByteBuf>) RequestSentPostList.INSTANCE::encode));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("packets")
        void decode_shouldBeTheInstance(
                GameClientExOpcode opcode,
                Object instance,
                Function<ByteBuf, Object> decode,
                Consumer<ByteBuf> encode) {
            assertThat(read("", decode)).isSameAs(instance);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("packets")
        void encode_shouldWriteTheExOpcodeOnly(
                GameClientExOpcode opcode,
                Object instance,
                Function<ByteBuf, Object> decode,
                Consumer<ByteBuf> encode) {
            assertThat(written(encode)).isEqualTo(ex(opcode));
        }
    }

    @Nested
    class MailIdPackets {
        static Stream<Arguments> packets() {
            return Stream.of(
                    packet(
                            GameClientExOpcode.REQUEST_RECEIVED_POST,
                            RequestReceivedPost::decode,
                            RequestReceivedPost::new,
                            id -> new RequestReceivedPost(id)::encode),
                    packet(
                            GameClientExOpcode.REQUEST_POST_ATTACHMENT,
                            RequestPostAttachment::decode,
                            RequestPostAttachment::new,
                            id -> new RequestPostAttachment(id)::encode),
                    packet(
                            GameClientExOpcode.REQUEST_REJECT_POST_ATTACHMENT,
                            RequestRejectPostAttachment::decode,
                            RequestRejectPostAttachment::new,
                            id -> new RequestRejectPostAttachment(id)::encode),
                    packet(
                            GameClientExOpcode.REQUEST_SENT_POST,
                            RequestSentPost::decode,
                            RequestSentPost::new,
                            id -> new RequestSentPost(id)::encode),
                    packet(
                            GameClientExOpcode.REQUEST_CANCEL_POST_ATTACHMENT,
                            RequestCancelPostAttachment::decode,
                            RequestCancelPostAttachment::new,
                            id -> new RequestCancelPostAttachment(id)::encode));
        }

        private static Arguments packet(
                GameClientExOpcode opcode,
                Function<ByteBuf, Object> decode,
                IntFunction<Object> create,
                IntFunction<Consumer<ByteBuf>> encoder) {
            return arguments(opcode, decode, create, encoder);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("packets")
        void decode_shouldReadTheMailId(
                GameClientExOpcode opcode,
                Function<ByteBuf, Object> decode,
                IntFunction<Object> create,
                IntFunction<Consumer<ByteBuf>> encoder) {
            assertThat(read(d(42), decode)).isEqualTo(create.apply(42));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("packets")
        void encode_shouldWriteTheExOpcodeAndTheMailId(
                GameClientExOpcode opcode,
                Function<ByteBuf, Object> decode,
                IntFunction<Object> create,
                IntFunction<Consumer<ByteBuf>> encoder) {
            assertThat(written(encoder.apply(42))).isEqualTo(ex(opcode) + d(42));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("packets")
        void decode_shouldFail_whenTheMailIdIsMissing(
                GameClientExOpcode opcode,
                Function<ByteBuf, Object> decode,
                IntFunction<Object> create,
                IntFunction<Consumer<ByteBuf>> encoder) {
            assertThatThrownBy(() -> read("2a00", decode)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class DeletePackets {
        static Stream<Arguments> packets() {
            return Stream.of(
                    arguments(
                            GameClientExOpcode.REQUEST_DELETE_RECEIVED_POST,
                            (Function<ByteBuf, Object>) RequestDeleteReceivedPost::decode,
                            (Function<List<Integer>, Object>) RequestDeleteReceivedPost::new,
                            (Function<List<Integer>, Consumer<ByteBuf>>)
                                    ids -> new RequestDeleteReceivedPost(ids)::encode),
                    arguments(
                            GameClientExOpcode.REQUEST_DELETE_SENT_POST,
                            (Function<ByteBuf, Object>) RequestDeleteSentPost::decode,
                            (Function<List<Integer>, Object>) RequestDeleteSentPost::new,
                            (Function<List<Integer>, Consumer<ByteBuf>>)
                                    ids -> new RequestDeleteSentPost(ids)::encode));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("packets")
        void decode_shouldReadTheMailIds(
                GameClientExOpcode opcode,
                Function<ByteBuf, Object> decode,
                Function<List<Integer>, Object> create,
                Function<List<Integer>, Consumer<ByteBuf>> encoder) {
            assertThat(read(d(3) + d(1) + d(2) + d(3), decode)).isEqualTo(create.apply(List.of(1, 2, 3)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("packets")
        void decode_shouldReadAnEmptyList_whenTheCountIsZero(
                GameClientExOpcode opcode,
                Function<ByteBuf, Object> decode,
                Function<List<Integer>, Object> create,
                Function<List<Integer>, Consumer<ByteBuf>> encoder) {
            assertThat(read(d(0), decode)).isEqualTo(create.apply(List.of()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("packets")
        void encode_shouldWriteTheCountAndTheMailIds(
                GameClientExOpcode opcode,
                Function<ByteBuf, Object> decode,
                Function<List<Integer>, Object> create,
                Function<List<Integer>, Consumer<ByteBuf>> encoder) {
            assertThat(written(encoder.apply(List.of(7, 8)))).isEqualTo(ex(opcode) + d(2) + d(7) + d(8));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("packets")
        void decode_shouldFail_whenTheCountDoesNotFitTheFrame(
                GameClientExOpcode opcode,
                Function<ByteBuf, Object> decode,
                Function<List<Integer>, Object> create,
                Function<List<Integer>, Consumer<ByteBuf>> encoder) {
            assertThatThrownBy(() -> read(d(3) + d(1) + d(2), decode)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class RequestSendPostCodec {
        private static final int ATTACHMENTS = 8;

        private static String eightAttachmentsWithCod() {
            final StringBuilder body = new StringBuilder(s("Bob") + d(1) + s("Deal") + s("Pay on delivery"));
            body.append(d(ATTACHMENTS));
            for (int i = 0; i < ATTACHMENTS; i++) {
                body.append(d(0x10000001 + i)).append(q(i + 1L));
            }
            return body.append(q(5_000_000L)).toString();
        }

        private static RequestSendPost eightAttachmentsWithCodPacket() {
            final List<RequestSendPost.Attachment> attachments = new ArrayList<>();
            for (int i = 0; i < ATTACHMENTS; i++) {
                attachments.add(RequestSendPost.Attachment.builder()
                        .objectId(0x10000001 + i)
                        .count(i + 1L)
                        .build());
            }
            return RequestSendPost.builder()
                    .receiverName("Bob")
                    .cashOnDelivery(true)
                    .subject("Deal")
                    .body("Pay on delivery")
                    .attachments(attachments)
                    .codAdena(5_000_000L)
                    .build();
        }

        @Test
        void decode_shouldReadEightAttachmentsAndCashOnDelivery() {
            assertThat(read(eightAttachmentsWithCod(), RequestSendPost::decode))
                    .isEqualTo(eightAttachmentsWithCodPacket());
        }

        @Test
        void encode_shouldWriteEightAttachmentsAndCashOnDelivery() {
            assertThat(written(eightAttachmentsWithCodPacket()::encode))
                    .isEqualTo(ex(GameClientExOpcode.REQUEST_SEND_POST) + eightAttachmentsWithCod());
        }

        @Test
        void decode_shouldReadAPlainLetter_whenThereAreNoAttachments() {
            assertThat(read(s("Bob") + d(0) + s("Hi") + s("") + d(0) + q(0), RequestSendPost::decode))
                    .isEqualTo(RequestSendPost.builder()
                            .receiverName("Bob")
                            .subject("Hi")
                            .body("")
                            .attachments(List.of())
                            .build());
        }

        @Test
        void encode_shouldWriteAnEmptyBody_whenTheBodyIsNull() {
            final RequestSendPost packet = RequestSendPost.builder()
                    .receiverName("Bob")
                    .subject("Hi")
                    .attachments(List.of())
                    .build();

            assertThat(written(packet::encode))
                    .isEqualTo(
                            ex(GameClientExOpcode.REQUEST_SEND_POST) + s("Bob") + d(0) + s("Hi") + s("") + d(0) + q(0));
        }

        @Test
        void decode_shouldFail_whenTheAttachmentCountDoesNotFitTheFrame() {
            final String body = s("Bob") + d(0) + s("Hi") + s("") + d(2) + d(1) + q(1) + q(0);

            assertThatThrownBy(() -> read(body, RequestSendPost::decode)).isInstanceOf(PacketDecodeException.class);
        }

        @Test
        void decode_shouldFail_whenTheAmountIsMissing() {
            final String body = s("Bob") + d(1) + s("Hi") + s("") + d(1) + d(1) + q(1);

            assertThatThrownBy(() -> read(body, RequestSendPost::decode)).isInstanceOf(PacketDecodeException.class);
        }
    }
}
