/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.OpcodeTable;
import app.l2nx.highfive.network.protocol.OpcodeTable.RouteKind;
import app.l2nx.highfive.network.protocol.p267.clientpackets.ClientPacket;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestBookMarkSlotInfo;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestChangeBookMarkSlot;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestDeleteBookMarkSlot;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestItemList;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestModifyBookMarkSlot;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestSaveBookMarkSlot;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestTeleportBookMark;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.protocol.game.p267.crypt.GameFrameCipher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class P267AdapterTest {
    private final P267Adapter adapter = new P267Adapter();
    private final EmbeddedChannel channel = new EmbeddedChannel();
    // Serialization only: the channel has no outbound queue.
    private final GameConnection connection = new GameConnection(
            channel, new GameFrameCipher(), null, Connections.metrics(channel), NetworkProperties.defaults());

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Nested
    class Accepts {
        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {267, 268, 271, 273})
        void accepts_shouldTakeHighFiveRevisions(int version) {
            assertThat(adapter.accepts(version)).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {140, 266, 269, 270, 272, 274, 557, -2})
        void accepts_shouldRefuseOtherRevisions(int version) {
            assertThat(adapter.accepts(version)).isFalse();
        }
    }

    @Nested
    class Frames {
        @Test
        void keyFrame_shouldUseTheVersionCheckLayout() {
            final byte[] key = HexFormat.of().parseHex("0102030405060708c8279301a16c3197");

            final ByteBuf frame = adapter.keyFrame(UnpooledByteBufAllocator.DEFAULT, key);

            assertThat(HexFormat.of().formatHex(ByteBufUtil.getBytes(frame, 2, frame.readableBytes() - 2)))
                    .isEqualTo("2e01" + "0102030405060708" + "01000000" + "00000000" + "01" + "00000000");
            frame.release();
        }

        @Test
        void pingFrame_shouldBeNetPingWithTheId() {
            final ByteBuf frame = adapter.pingFrame(UnpooledByteBufAllocator.DEFAULT, 0x01020304);

            assertThat(HexFormat.of().formatHex(ByteBufUtil.getBytes(frame, 2, frame.readableBytes() - 2)))
                    .isEqualTo("d904030201");
            frame.release();
        }

        @Test
        void serverCloseFrame_shouldBeServerClose() {
            final ByteBuf frame = adapter.serverCloseFrame(UnpooledByteBufAllocator.DEFAULT);

            assertThat(HexFormat.of().formatHex(ByteBufUtil.getBytes(frame, 2, frame.readableBytes() - 2)))
                    .isEqualTo("20");
            frame.release();
        }
    }

    @Nested
    class Decode {
        @Test
        void decode_shouldRouteALegacyPacketNeedingACharacterInTheWorldByDefault() {
            assertThat(decode(0x14))
                    .isInstanceOfSatisfying(
                            RequestItemList.class,
                            packet -> assertThat(packet.requiredContext()).isEqualTo("WORLD"));
            assertThat(entry(0x14).preAuth()).isFalse();
            assertThat(entry(0xFE)).isNull();
        }

        @Test
        void decode_shouldReadOnlyTheLoginBeforeAuthentication() {
            assertThat(entry(0x2B).preAuth()).isTrue();
            assertThat(entry(0x12).preAuth()).isFalse();
        }

        @Test
        void decode_shouldConsumePingAnswersOnTheIoThread_whenAuthenticated() {
            final OpcodeTable.Entry actual = entry(0xB1);

            assertThat(actual.kind()).isEqualTo(RouteKind.CONNECTION);
            assertThat(actual.preAuth()).isFalse();
            assertThat(decode(0xB1, 0, 0, 0, 0)).isSameAs(OpcodeTable.CONSUMED);
        }

        @Test
        void decode_shouldIgnoreAKnownPacketWithoutAClass() {
            // 0xCF is REQUEST_BUY_PROCURE: known to the client, never implemented by Mobius.
            assertThat(decode(0xCF)).isSameAs(OpcodeTable.IGNORED);
        }

        @ParameterizedTest(name = "{0} = {1}")
        @MethodSource("bookmarkPackets")
        void decode_shouldResolveTheSixBookmarkPacketsByTheThirdId(
                int bookmarkId, Class<? extends ClientPacket> expected) {
            // Zero padding is a body every bookmark packet reads without running out: empty strings and zero ints.
            assertThat(decode(0xD0, 0x51, 0x00, bookmarkId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
                    .isInstanceOf(expected);
        }

        @Test
        void decode_shouldNotResolveABookmarkPacket_whenPastTheSixth() {
            assertThat(decode(0xD0, 0x51, 0x00, 6, 0, 0, 0)).isSameAs(OpcodeTable.UNKNOWN);
        }

        static Stream<Arguments> bookmarkPackets() {
            return Stream.of(
                    Arguments.of(0, RequestBookMarkSlotInfo.class),
                    Arguments.of(1, RequestSaveBookMarkSlot.class),
                    Arguments.of(2, RequestModifyBookMarkSlot.class),
                    Arguments.of(3, RequestDeleteBookMarkSlot.class),
                    Arguments.of(4, RequestTeleportBookMark.class),
                    Arguments.of(5, RequestChangeBookMarkSlot.class));
        }
    }

    private Object decode(int... bytes) {
        final ByteBuf frame = frame(bytes);
        try {
            return adapter.lookup(frame).decoder().decode(frame, connection);
        } finally {
            frame.release();
        }
    }

    private OpcodeTable.Entry entry(int opcode) {
        final ByteBuf frame = frame(opcode);
        try {
            return adapter.lookup(frame);
        } finally {
            frame.release();
        }
    }

    private static ByteBuf frame(int... bytes) {
        final ByteBuf frame = Unpooled.buffer();
        for (int b : bytes) {
            frame.writeByte(b);
        }
        return frame;
    }
}
