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
import static app.l2nx.protocol.game.p267.WireHex.s;
import static app.l2nx.protocol.game.p267.WireHex.zeros;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterPacketsTest {
    @Nested
    class ProtocolVersionCodec {
        @Test
        void decode_shouldReadTheVersion() {
            assertThat(read(d(267), ProtocolVersion::decode)).isEqualTo(new ProtocolVersion(267));
        }

        @Test
        void encode_shouldWriteTheOpcodeAndTheVersion() {
            assertThat(written(new ProtocolVersion(273)::encode)).isEqualTo("0e" + d(273));
        }
    }

    @Nested
    class AuthLoginCodec {
        private static final String BODY = s("Alice") + d(2) + d(1) + d(3) + d(4);

        @Test
        void decode_shouldReadTheLoginAsSentAndTheKeysInWireOrder() {
            assertThat(read(BODY, AuthLogin::decode))
                    .isEqualTo(AuthLogin.builder()
                            .login("Alice")
                            .playOk2(2)
                            .playOk1(1)
                            .loginOk1(3)
                            .loginOk2(4)
                            .build());
        }

        @Test
        void encode_shouldWriteWhatDecodeReads() {
            final AuthLogin packet = read(BODY, AuthLogin::decode);

            assertThat(written(packet::encode)).isEqualTo("2b" + BODY);
        }

        @Test
        void decode_shouldFail_whenTheLoginIsTooLong() {
            final String body = s("a".repeat(AuthLogin.MAX_LOGIN + 1)) + zeros(16);

            assertThatThrownBy(() -> read(body, AuthLogin::decode)).isInstanceOf(PacketDecodeException.class);
        }

        @Test
        void decode_shouldFail_whenAKeyIsMissing() {
            assertThatThrownBy(() -> read(s("alice") + d(2) + d(1) + d(3), AuthLogin::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }

        @Test
        void toString_shouldNotPrintTheSessionKey() {
            assertThat(read(BODY, AuthLogin::decode).toString()).isEqualTo("AuthLogin[login=Alice]");
        }
    }

    @Nested
    class CharacterSelectCodec {
        @Test
        void decode_shouldReadTheSlotAndSkipTheTrailer() {
            assertThat(read(d(3) + h(7) + d(1) + d(2) + d(3), CharacterSelect::decode))
                    .isEqualTo(new CharacterSelect(3));
        }

        @Test
        void decode_shouldFail_whenTheTrailerIsShort() {
            assertThatThrownBy(() -> read(d(3) + h(0), CharacterSelect::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }

        @Test
        void encode_shouldWriteTheTrailerAsZero() {
            assertThat(written(new CharacterSelect(2)::encode)).isEqualTo("12" + d(2) + zeros(14));
        }
    }

    @Nested
    class CharacterCreateCodec {
        private static final String BODY =
                s("Bob") + d(0) + d(1) + d(18) + d(21) + d(40) + d(43) + d(25) + d(30) + d(11) + d(1) + d(2) + d(0);

        private static final CharacterCreate PACKET = CharacterCreate.builder()
                .name("Bob")
                .race(0)
                .female(true)
                .classId(18)
                .intelligence(21)
                .strength(40)
                .constitution(43)
                .mentality(25)
                .dexterity(30)
                .wit(11)
                .hairStyle(1)
                .hairColor(2)
                .face(0)
                .build();

        @Test
        void decode_shouldReadEveryField() {
            assertThat(read(BODY, CharacterCreate::decode)).isEqualTo(PACKET);
        }

        @Test
        void decode_shouldTakeAnyNonZeroSexAsFemale() {
            final String body = s("Bob") + d(0) + d(7) + zeros(40);

            assertThat(read(body, CharacterCreate::decode).female()).isTrue();
        }

        @Test
        void encode_shouldWriteWhatDecodeReads() {
            assertThat(written(PACKET::encode)).isEqualTo("0c" + BODY);
        }

        @Test
        void decode_shouldKeepALongNameForTheHandlerToRefuse() {
            final String name = "b".repeat(65);

            assertThat(read(s(name) + zeros(48), CharacterCreate::decode).name())
                    .isEqualTo(name);
        }

        @Test
        void decode_shouldFail_whenTheNameRunsToTheEndOfTheFrame() {
            assertThatThrownBy(() -> read("6200".repeat(65), CharacterCreate::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class CharacterDeleteCodec {
        @Test
        void decode_shouldRoundTripTheSlot() {
            assertThat(read(d(4), CharacterDelete::decode)).isEqualTo(new CharacterDelete(4));
            assertThat(written(new CharacterDelete(4)::encode)).isEqualTo("0d" + d(4));
        }

        @Test
        void decode_shouldFail_whenTheSlotIsMissing() {
            assertThatThrownBy(() -> read(h(1), CharacterDelete::decode)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class CharacterRestoreCodec {
        @Test
        void decode_shouldRoundTripTheSlot() {
            assertThat(read(d(5), CharacterRestore::decode)).isEqualTo(new CharacterRestore(5));
            assertThat(written(new CharacterRestore(5)::encode)).isEqualTo("7b" + d(5));
        }
    }

    @Nested
    class RequestRestartCodec {
        @Test
        void decode_shouldBeTheOpcodeOnly() {
            assertThat(read("", RequestRestart::decode)).isSameAs(RequestRestart.INSTANCE);
            assertThat(written(RequestRestart.INSTANCE::encode)).isEqualTo("57");
        }
    }

    @Nested
    class EnterWorldCodec {
        private static final String TRACE = c(192) + c(168) + c(1) + c(10) + zeros(16);

        @Test
        void decode_shouldSkipTheUnknownBytesAndReadTheTraceUnsigned() {
            final EnterWorld packet = read(zeros(84) + TRACE, EnterWorld::decode);

            assertThat(packet.octet(0, 0)).isEqualTo(192);
            assertThat(packet.octet(0, 3)).isEqualTo(10);
            assertThat(packet.octet(4, 3)).isZero();
        }

        @Test
        void encode_shouldWriteZeroesForTheUnknownBytes() {
            final EnterWorld packet = read(zeros(84) + TRACE, EnterWorld::decode);

            assertThat(written(packet::encode)).isEqualTo("11" + zeros(84) + TRACE);
        }

        @Test
        void equals_shouldCompareTheTraceByContent() {
            assertThat(read(zeros(84) + TRACE, EnterWorld::decode))
                    .isEqualTo(read(zeros(84) + TRACE, EnterWorld::decode));
        }

        @Test
        void decode_shouldFail_whenTheTraceIsShort() {
            assertThatThrownBy(() -> read(zeros(84) + zeros(19), EnterWorld::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }
}
