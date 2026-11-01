/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.session.command.Command;
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
import app.l2nx.highfive.chat.command.SendChat;
import app.l2nx.highfive.login.command.Authenticate;
import app.l2nx.highfive.network.protocol.OpcodeTable.Route;
import app.l2nx.highfive.network.protocol.OpcodeTable.RouteKind;
import app.l2nx.highfive.network.protocol.p267.clientpackets.ClientPackets;
import app.l2nx.highfive.network.protocol.p267.clientpackets.ExClientPackets;
import app.l2nx.highfive.session.GameContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every opcode a High Five client sends is a command decoder, a legacy route or an explicit ignore; none is unknown. */
class P267InboundRoutesTest {
    private static final int NET_PING_ANSWER = 0xB1;

    private static final List<Route> ROUTES = new P267Adapter().inboundRoutes();

    @Nested
    class InboundRoutes {
        @Test
        void inboundRoutes_shouldGiveEveryClientPacketALegacyRouteOrAnIgnoreReadOnlyAfterAuthentication() {
            for (ClientPackets packet : ClientPackets.values()) {
                if (packet.getPacketId() == P267Inbound.EX_PREFIX) {
                    continue;
                }
                assertThat(route(packet.getPacketId(), Route.NO_EX_OPCODE))
                        .as("%s", packet)
                        .hasValueSatisfying(P267InboundRoutesTest::isLegacyOrIgnoreAfterAuthentication);
            }
            for (ExClientPackets packet : ExClientPackets.values()) {
                assertThat(route(P267Inbound.EX_PREFIX, packet.getPacketId()))
                        .as("%s", packet)
                        .hasValueSatisfying(P267InboundRoutesTest::isLegacyOrIgnoreAfterAuthentication);
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("pinnedRoutes")
        void inboundRoutes_shouldContainThePinnedRoute(String packet, Route expected) {
            assertThat(route(expected.opcode(), expected.exOpcode())).contains(expected);
        }

        static Stream<Arguments> pinnedRoutes() {
            return Stream.of(
                    pinned("REQUEST_ITEM_LIST", 0x14, Route.NO_EX_OPCODE, RouteKind.LEGACY, null),
                    pinned("REQUEST_BUY_PROCURE", 0xCF, Route.NO_EX_OPCODE, RouteKind.IGNORE, null),
                    pinned("SAY2", 0x49, Route.NO_EX_OPCODE, RouteKind.COMMAND, SendChat.class),
                    pinned("NET_PING answer", NET_PING_ANSWER, Route.NO_EX_OPCODE, RouteKind.CONNECTION, null),
                    pinned("REQUEST_MANOR_LIST", P267Inbound.EX_PREFIX, 0x01, RouteKind.LEGACY, null),
                    pinned("REQUEST_SERVER_LOGIN", P267Inbound.EX_PREFIX, 0x1A, RouteKind.IGNORE, null),
                    pinned("EX_BOOKMARK_PACKET", P267Inbound.EX_PREFIX, 0x51, RouteKind.LEGACY, null),
                    pinned(
                            "REQUEST_POST_ITEM_LIST",
                            P267Inbound.EX_PREFIX,
                            0x65,
                            RouteKind.COMMAND,
                            ListMailableItems.class),
                    pinned("REQUEST_SEND_POST", P267Inbound.EX_PREFIX, 0x66, RouteKind.COMMAND, SendMail.class),
                    pinned(
                            "REQUEST_RECEIVED_POST_LIST",
                            P267Inbound.EX_PREFIX,
                            0x67,
                            RouteKind.COMMAND,
                            ListReceivedMail.class),
                    pinned(
                            "REQUEST_DELETE_RECEIVED_POST",
                            P267Inbound.EX_PREFIX,
                            0x68,
                            RouteKind.COMMAND,
                            DeleteReceivedMail.class),
                    pinned(
                            "REQUEST_RECEIVED_POST",
                            P267Inbound.EX_PREFIX,
                            0x69,
                            RouteKind.COMMAND,
                            OpenReceivedMail.class),
                    pinned(
                            "REQUEST_POST_ATTACHMENT",
                            P267Inbound.EX_PREFIX,
                            0x6A,
                            RouteKind.COMMAND,
                            ReceiveAttachments.class),
                    pinned(
                            "REQUEST_REJECT_POST_ATTACHMENT",
                            P267Inbound.EX_PREFIX,
                            0x6B,
                            RouteKind.COMMAND,
                            ReturnMail.class),
                    pinned(
                            "REQUEST_SENT_POST_LIST",
                            P267Inbound.EX_PREFIX,
                            0x6C,
                            RouteKind.COMMAND,
                            ListSentMail.class),
                    pinned(
                            "REQUEST_DELETE_SENT_POST",
                            P267Inbound.EX_PREFIX,
                            0x6D,
                            RouteKind.COMMAND,
                            DeleteSentMail.class),
                    pinned("REQUEST_SENT_POST", P267Inbound.EX_PREFIX, 0x6E, RouteKind.COMMAND, OpenSentMail.class),
                    pinned(
                            "REQUEST_CANCEL_POST_ATTACHMENT",
                            P267Inbound.EX_PREFIX,
                            0x6F,
                            RouteKind.COMMAND,
                            CancelMail.class));
        }

        @Test
        void inboundRoutes_shouldComeFromAClientPacketACommandDecoderOrThePingAnswer() {
            assertThat(ROUTES).isNotEmpty().allSatisfy(route -> {
                switch (route.kind()) {
                    case COMMAND -> assertThat(route.command()).as("%s", route).isNotNull();
                    case CONNECTION ->
                        assertThat(route.opcode()).as("%s", route).isEqualTo(NET_PING_ANSWER);
                    case LEGACY, IGNORE ->
                        assertThat(declared(route)).as("%s", route).isTrue();
                }
            });
        }

        @Test
        void inboundRoutes_shouldConsumeThePingAnswer_whenAuthenticated() {
            assertThat(route(NET_PING_ANSWER, Route.NO_EX_OPCODE)).hasValueSatisfying(route -> {
                assertThat(route.kind()).isEqualTo(RouteKind.CONNECTION);
                assertThat(route.preAuth()).isFalse();
            });
        }

        @Test
        void inboundRoutes_shouldReadOnlyAuthLoginBeforeAuthentication() {
            assertThat(ROUTES.stream().filter(Route::preAuth)).singleElement().satisfies(route -> {
                assertThat(route.opcode()).isEqualTo(0x2B);
                assertThat(route.kind()).isEqualTo(RouteKind.COMMAND);
                assertThat(route.command()).isEqualTo(Authenticate.class);
            });
        }
    }

    @Nested
    class PacketContexts {
        @Test
        void getContext_shouldLeaveTheWorldToEveryPacketButTheLoginTheSelectionScreenAndTheLoadingQueries() {
            final Map<String, GameContext> actual = new TreeMap<>();
            Stream.concat(
                            Arrays.stream(ClientPackets.values()).map(p -> Map.entry(p.name(), p.getContext())),
                            Arrays.stream(ExClientPackets.values()).map(p -> Map.entry(p.name(), p.getContext())))
                    .filter(entry -> entry.getValue() != GameContext.WORLD)
                    .forEach(entry -> actual.put(entry.getKey(), entry.getValue()));

            assertThat(actual)
                    .containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                            Map.entry("LOGOUT", GameContext.ACCOUNT),
                            Map.entry("REQUEST_HARDWARE_INFO", GameContext.ACCOUNT),
                            Map.entry("NEW_CHARACTER", GameContext.LOBBY),
                            Map.entry("REQUEST_GOTO_LOBBY", GameContext.LOBBY),
                            Map.entry("REQUEST_EX_2ND_PASSWORD_CHECK", GameContext.LOBBY),
                            Map.entry("REQUEST_EX_2ND_PASSWORD_VERIFY", GameContext.LOBBY),
                            Map.entry("REQUEST_EX_2ND_PASSWORD_REQ", GameContext.LOBBY),
                            Map.entry("REQUEST_KEY_MAPPING", GameContext.CHARACTER),
                            Map.entry("REQUEST_MANOR_LIST", GameContext.CHARACTER),
                            Map.entry("REQUEST_ALL_FORTRESS_INFO", GameContext.CHARACTER),
                            Map.entry("REQUEST_SKILL_COOL_TIME", GameContext.CHARACTER)));
        }
    }

    private static Arguments pinned(
            String packet, int opcode, int exOpcode, RouteKind kind, Class<? extends Command> command) {
        return Arguments.of(
                packet,
                Route.builder()
                        .opcode(opcode)
                        .exOpcode(exOpcode)
                        .kind(kind)
                        .command(command)
                        .build());
    }

    private static void isLegacyOrIgnoreAfterAuthentication(Route route) {
        assertThat(route.kind()).isIn(RouteKind.LEGACY, RouteKind.IGNORE);
        assertThat(route.preAuth()).isFalse();
    }

    private static Optional<Route> route(int opcode, int exOpcode) {
        return ROUTES.stream()
                .filter(route -> (route.opcode() == opcode) && (route.exOpcode() == exOpcode))
                .findFirst();
    }

    private static boolean declared(Route route) {
        if (route.exOpcode() == Route.NO_EX_OPCODE) {
            return Arrays.stream(ClientPackets.values()).anyMatch(packet -> packet.getPacketId() == route.opcode());
        }
        return (route.opcode() == P267Inbound.EX_PREFIX)
                && Arrays.stream(ExClientPackets.values()).anyMatch(packet -> packet.getPacketId() == route.exOpcode());
    }
}
