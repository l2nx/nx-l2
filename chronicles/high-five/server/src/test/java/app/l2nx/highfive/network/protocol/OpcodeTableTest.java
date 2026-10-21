/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.session.command.Command;
import app.l2nx.highfive.network.protocol.OpcodeTable.Route;
import app.l2nx.highfive.network.protocol.OpcodeTable.RouteKind;
import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OpcodeTableTest {
    private final StubAdapter adapter = new StubAdapter("stub", 1, 0);

    @Nested
    class Lookup {
        @Test
        void lookup_shouldReturnTheRouteWithItsKind_andWhetherItIsReadBeforeAuthentication() {
            final OpcodeTable.Entry actual = lookup(Unpooled.buffer().writeByte(0x01));

            assertThat(actual.kind()).isEqualTo(RouteKind.COMMAND);
            assertThat(actual.preAuth()).isTrue();
            assertThat(lookup(Unpooled.buffer().writeByte(0x02)).preAuth()).isFalse();
        }

        @Test
        void lookup_shouldReturnNull_forAnUnknownOpcode() {
            assertThat(lookup(Unpooled.buffer().writeByte(0x44))).isNull();
            assertThat(lookup(Unpooled.buffer().writeByte(0xD0).writeShortLE(0x7FFF)))
                    .isNull();
        }

        @Test
        void lookup_shouldReadTheExtendedOpcodeLittleEndian_andLeaveTheFrameAtTheBody() {
            final ByteBuf frame =
                    Unpooled.buffer().writeByte(0xD0).writeShortLE(0x10).writeIntLE(9);
            try {
                final OpcodeTable.Entry actual = adapter.lookup(frame);

                assertThat(actual.preAuth()).isFalse();
                assertThat(actual.decoder().decode(frame, null)).isEqualTo(new StubAdapter.Probe(9));
            } finally {
                frame.release();
            }
        }

        @Test
        void lookup_shouldThrowADecodeError_whenTheExtendedSubOpcodeIsMissing() {
            final ByteBuf frame = Unpooled.buffer().writeByte(0xD0).writeByte(1);
            try {
                assertThatThrownBy(() -> adapter.lookup(frame)).isInstanceOf(PacketDecodeException.class);
            } finally {
                frame.release();
            }
        }

        private OpcodeTable.Entry lookup(ByteBuf frame) {
            try {
                return adapter.lookup(frame);
            } finally {
                frame.release();
            }
        }
    }

    @Nested
    class Build {
        @Test
        void builder_shouldRefuseTwoRoutesForOneOpcode() {
            assertThatThrownBy(() -> OpcodeTable.builder(0xD0)
                            .route(0x01, RouteKind.LEGACY, (f, c) -> null)
                            .command(0x01, StubAdapter.Probe.class, (f, c) -> null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void builder_shouldRefuseToRouteTheExtendedPrefix() {
            assertThatThrownBy(() -> OpcodeTable.builder(0xD0).route(0xD0, RouteKind.LEGACY, (f, c) -> null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void builder_shouldRefuseACommandRouteWithoutItsCommandType() {
            assertThatThrownBy(() -> OpcodeTable.builder(0xD0).route(0x01, RouteKind.COMMAND, (f, c) -> null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void legacyCount_shouldCountDistinctLegacyOpcodes_andEveryPacketBehindADispatchingRoute() {
            final OpcodeTable table = OpcodeTable.builder(0xD0)
                    .route(0x10, RouteKind.LEGACY, (f, c) -> null)
                    .command(0x11, StubAdapter.Probe.class, (f, c) -> null)
                    .route(0x12, RouteKind.IGNORE, (frame, c) -> OpcodeTable.IGNORED)
                    .exRoute(0x20, RouteKind.LEGACY, (f, c) -> null)
                    .exLegacy(0x51, (f, c) -> null, 6)
                    .build();

            assertThat(table.legacyCount()).isEqualTo(1 + 1 + 6);
        }

        @Test
        void routes_shouldRecordTheKindOfEveryOpcode_andWhetherItIsReadBeforeAuthentication() {
            final OpcodeTable table = OpcodeTable.builder(0xD0)
                    .preAuthCommand(0x01, StubAdapter.Probe.class, (f, c) -> null)
                    .route(0x02, RouteKind.CONNECTION, (f, c) -> OpcodeTable.CONSUMED)
                    .exRoute(0x10, RouteKind.IGNORE, (frame, c) -> OpcodeTable.IGNORED)
                    .build();

            assertThat(table.routes())
                    .containsExactlyInAnyOrder(
                            route(true, 0x01, Route.NO_EX_OPCODE, RouteKind.COMMAND, StubAdapter.Probe.class),
                            route(false, 0x02, Route.NO_EX_OPCODE, RouteKind.CONNECTION, null),
                            route(false, 0xD0, 0x10, RouteKind.IGNORE, null));
        }

        private static Route route(
                boolean preAuth, int opcode, int exOpcode, RouteKind kind, Class<? extends Command> command) {
            return Route.builder()
                    .preAuth(preAuth)
                    .opcode(opcode)
                    .exOpcode(exOpcode)
                    .kind(kind)
                    .command(command)
                    .build();
        }
    }
}
