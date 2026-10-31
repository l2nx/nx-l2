/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.character.command.CreateCharacter;
import app.l2nx.highfive.character.command.DeleteCharacter;
import app.l2nx.highfive.character.command.EnterWorld;
import app.l2nx.highfive.character.command.Restart;
import app.l2nx.highfive.character.command.RestoreCharacter;
import app.l2nx.highfive.character.command.SelectCharacter;
import app.l2nx.protocol.game.p267.clientpackets.CharacterCreate;
import app.l2nx.protocol.game.p267.clientpackets.CharacterDelete;
import app.l2nx.protocol.game.p267.clientpackets.CharacterRestore;
import app.l2nx.protocol.game.p267.clientpackets.CharacterSelect;
import app.l2nx.protocol.game.p267.clientpackets.RequestRestart;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterDecodersTest {
    @Nested
    class CharacterSelectDecoding {
        @Test
        void characterSelect_shouldMapTheSlot() {
            final Object actual =
                    decode(new CharacterSelect(2)::encode, body -> CharacterDecoders.characterSelect(body, null));

            assertThat(actual).isEqualTo(new SelectCharacter(2));
        }
    }

    @Nested
    class CharacterCreateDecoding {
        @Test
        void characterCreate_shouldKeepNameSexClassAndLooksAndDropRaceAndStats() {
            final CharacterCreate packet = CharacterCreate.builder()
                    .name("Hero")
                    .race(1)
                    .female(true)
                    .classId(10)
                    .intelligence(21)
                    .strength(40)
                    .constitution(43)
                    .mentality(25)
                    .dexterity(30)
                    .wit(11)
                    .hairStyle(3)
                    .hairColor(2)
                    .face(1)
                    .build();

            final Object actual = decode(packet::encode, body -> CharacterDecoders.characterCreate(body, null));

            assertThat(actual)
                    .isEqualTo(CreateCharacter.builder()
                            .name("Hero")
                            .female(true)
                            .classId(10)
                            .hairStyle(3)
                            .hairColor(2)
                            .face(1)
                            .build());
        }

        @Test
        void characterCreate_shouldKeepAnEmptyNameForTheHandlerToAnswer() {
            final CharacterCreate packet = CharacterCreate.builder().name("").build();

            final Object actual = decode(packet::encode, body -> CharacterDecoders.characterCreate(body, null));

            assertThat(actual).isEqualTo(CreateCharacter.builder().name("").build());
        }
    }

    @Nested
    class SlotDecoding {
        @Test
        void characterDelete_shouldMapTheSlot() {
            final Object actual =
                    decode(new CharacterDelete(1)::encode, body -> CharacterDecoders.characterDelete(body, null));

            assertThat(actual).isEqualTo(new DeleteCharacter(1));
        }

        @Test
        void characterRestore_shouldMapTheSlot() {
            final Object actual =
                    decode(new CharacterRestore(4)::encode, body -> CharacterDecoders.characterRestore(body, null));

            assertThat(actual).isEqualTo(new RestoreCharacter(4));
        }
    }

    @Nested
    class WorldEntryDecoding {
        @Test
        void enterWorld_shouldCopyTheRouteAsUnsignedOctets() {
            final byte[] trace = new byte[20];
            for (int i = 0; i < trace.length; i++) {
                trace[i] = (byte) (200 + i - (i > 10 ? 100 : 0));
            }

            final EnterWorld actual = (EnterWorld) decode(
                    new app.l2nx.protocol.game.p267.clientpackets.EnterWorld(trace)::encode,
                    body -> CharacterDecoders.enterWorld(body, null));

            assertThat(actual)
                    .isEqualTo(new EnterWorld(Stream.of(
                                    "200.201.202.203",
                                    "204.205.206.207",
                                    "208.209.210.111",
                                    "112.113.114.115",
                                    "116.117.118.119")
                            .map(literal -> (Inet4Address) InetAddress.ofLiteral(literal))
                            .toList()));
        }

        @Test
        void restart_shouldNeedNoPayload() {
            final Object actual =
                    decode(RequestRestart.INSTANCE::encode, body -> CharacterDecoders.restart(body, null));

            assertThat(actual).isEqualTo(Restart.INSTANCE);
        }
    }

    private static Object decode(Consumer<ByteBuf> encoder, Function<ByteBuf, Object> decoder) {
        final ByteBuf frame = Unpooled.buffer();
        try {
            encoder.accept(frame);
            frame.skipBytes(1);
            return decoder.apply(frame);
        } finally {
            frame.release();
        }
    }
}
