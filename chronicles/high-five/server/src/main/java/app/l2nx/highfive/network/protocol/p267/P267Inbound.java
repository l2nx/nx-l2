/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import app.l2nx.engine.core.session.command.Command;
import app.l2nx.highfive.character.command.CreateCharacter;
import app.l2nx.highfive.character.command.DeleteCharacter;
import app.l2nx.highfive.character.command.EnterWorld;
import app.l2nx.highfive.character.command.Restart;
import app.l2nx.highfive.character.command.RestoreCharacter;
import app.l2nx.highfive.character.command.SelectCharacter;
import app.l2nx.highfive.chat.command.SendChat;
import app.l2nx.highfive.config.DevelopmentConfig;
import app.l2nx.highfive.login.command.Authenticate;
import app.l2nx.highfive.movement.command.MoveBlocked;
import app.l2nx.highfive.movement.command.MoveTo;
import app.l2nx.highfive.movement.command.ReportPosition;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.InboundDecoder;
import app.l2nx.highfive.network.protocol.OpcodeTable;
import app.l2nx.highfive.network.protocol.OpcodeTable.RouteKind;
import app.l2nx.highfive.network.protocol.p267.clientpackets.ClientPacket;
import app.l2nx.highfive.network.protocol.p267.clientpackets.ClientPackets;
import app.l2nx.highfive.network.protocol.p267.clientpackets.ExClientPackets;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestBookMarkSlotInfo;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestChangeBookMarkSlot;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestDeleteBookMarkSlot;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestModifyBookMarkSlot;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestSaveBookMarkSlot;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestTeleportBookMark;
import app.l2nx.highfive.network.protocol.p267.decoder.CharacterDecoders;
import app.l2nx.highfive.network.protocol.p267.decoder.ChatDecoders;
import app.l2nx.highfive.network.protocol.p267.decoder.LoginDecoders;
import app.l2nx.highfive.network.protocol.p267.decoder.MovementDecoders;
import app.l2nx.highfive.session.GameContext;
import app.l2nx.protocol.game.p267.clientpackets.GameClientOpcode;
import io.netty.buffer.ByteBuf;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Ported packets decode to commands; every other packet of {@link ClientPackets}/{@link ExClientPackets} is a legacy
 * route. A ported packet leaves the enums, so a route is never both.
 */
@Slf4j
public final class P267Inbound {
    static final int EX_PREFIX = 0xD0;
    private static final int NET_PING_ANSWER = 0xB1;
    private static final int BOOKMARK = 0x51;
    private static final int BOOKMARK_PACKETS = 6;
    private static final InboundDecoder PING_ANSWER = (frame, connection) -> OpcodeTable.CONSUMED;

    private P267Inbound() {}

    /** Registration of the ported packets; lives only while {@link #build()} runs. */
    static final class Routes {
        private final OpcodeTable.Builder table;

        private Routes(OpcodeTable.Builder table) {
            this.table = table;
        }

        <C extends Command> void command(int opcode, Class<C> type, InboundDecoder decoder) {
            table.command(opcode, type, decoder);
        }

        /** The login: the only command read before authentication. */
        <C extends Command> void preAuthCommand(int opcode, Class<C> type, InboundDecoder decoder) {
            table.preAuthCommand(opcode, type, decoder);
        }
    }

    static OpcodeTable build() {
        final OpcodeTable.Builder table = OpcodeTable.builder(EX_PREFIX);
        commands(new Routes(table));
        // The client's answer to NetPing only proves the connection alive; any read already resets the idle timer.
        table.route(NET_PING_ANSWER, RouteKind.CONNECTION, PING_ANSWER);
        for (ClientPackets packet : ClientPackets.values()) {
            final RouteKind kind = packet.implemented() ? RouteKind.LEGACY : RouteKind.IGNORE;
            final InboundDecoder decoder =
                    packet.implemented() ? legacy(packet::newPacket) : unimplemented(packet.name());
            if (packet.getContext() == GameContext.LOGIN) {
                throw new IllegalStateException(
                        packet + ": only the Authenticate command is read before authentication");
            }
            table.route(packet.getPacketId(), kind, decoder);
        }
        for (ExClientPackets packet : ExClientPackets.values()) {
            if (packet.getContext() == GameContext.LOGIN) {
                throw new IllegalStateException(packet + ": an extended packet is never read before authentication");
            }
            if (packet.getPacketId() == BOOKMARK) {
                // No supplier of its own: six packets hide behind a third id, each one a packet to migrate.
                table.exLegacy(packet.getPacketId(), P267Inbound::bookmark, BOOKMARK_PACKETS);
            } else if (packet.implemented()) {
                table.exRoute(packet.getPacketId(), RouteKind.LEGACY, legacy(packet::newPacket));
            } else {
                table.exRoute(packet.getPacketId(), RouteKind.IGNORE, unimplemented(packet.name()));
            }
        }
        return table.build();
    }

    private static void commands(Routes routes) {
        routes.preAuthCommand(GameClientOpcode.AUTH_LOGIN.opcode(), Authenticate.class, LoginDecoders::authLogin);
        routes.command(
                GameClientOpcode.CHARACTER_SELECT.opcode(), SelectCharacter.class, CharacterDecoders::characterSelect);
        routes.command(
                GameClientOpcode.CHARACTER_CREATE.opcode(), CreateCharacter.class, CharacterDecoders::characterCreate);
        routes.command(
                GameClientOpcode.CHARACTER_DELETE.opcode(), DeleteCharacter.class, CharacterDecoders::characterDelete);
        routes.command(
                GameClientOpcode.CHARACTER_RESTORE.opcode(),
                RestoreCharacter.class,
                CharacterDecoders::characterRestore);
        routes.command(GameClientOpcode.REQUEST_RESTART.opcode(), Restart.class, CharacterDecoders::restart);
        routes.command(GameClientOpcode.ENTER_WORLD.opcode(), EnterWorld.class, CharacterDecoders::enterWorld);
        routes.command(GameClientOpcode.MOVE_TO_LOCATION.opcode(), MoveTo.class, MovementDecoders::moveToLocation);
        routes.command(
                GameClientOpcode.VALIDATE_POSITION.opcode(), ReportPosition.class, MovementDecoders::validatePosition);
        routes.command(
                GameClientOpcode.CANNOT_MOVE_ANYMORE.opcode(), MoveBlocked.class, MovementDecoders::cannotMoveAnymore);
        routes.command(GameClientOpcode.SAY2.opcode(), SendChat.class, ChatDecoders::say2);
    }

    private static InboundDecoder legacy(Supplier<? extends ClientPacket> supplier) {
        return (frame, connection) -> {
            final ClientPacket packet = supplier.get();
            packet.decode(connection.client(), frame);
            return packet;
        };
    }

    private static InboundDecoder unimplemented(String name) {
        return (frame, connection) -> {
            if (DevelopmentConfig.DEBUG_UNKNOWN_PACKETS) {
                log.info("[C] unimplemented {}", name);
            }
            return OpcodeTable.IGNORED;
        };
    }

    private static Object bookmark(ByteBuf frame, GameConnection connection) {
        final ClientPacket packet = switch (frame.readIntLE()) {
            case 0 -> new RequestBookMarkSlotInfo();
            case 1 -> new RequestSaveBookMarkSlot();
            case 2 -> new RequestModifyBookMarkSlot();
            case 3 -> new RequestDeleteBookMarkSlot();
            case 4 -> new RequestTeleportBookMark();
            case 5 -> new RequestChangeBookMarkSlot();
            default -> null;
        };
        if (packet == null) {
            return OpcodeTable.UNKNOWN;
        }
        packet.decode(connection.client(), frame);
        return packet;
    }
}
