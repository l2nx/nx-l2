/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.network.protocol.OpcodeTable.Route;
import app.l2nx.highfive.network.protocol.OpcodeTable.RouteKind;
import app.l2nx.highfive.network.protocol.p267.clientpackets.ClientPackets;
import app.l2nx.highfive.network.protocol.p267.clientpackets.ExClientPackets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every opcode a High Five client sends is a command decoder, a legacy route or an explicit ignore; none is unknown. */
class P267InboundRoutesTest {
    private static final int NET_PING_ANSWER = 0xB1;
    // The bookmark entry has no supplier of its own: its six packets dispatch behind one extended opcode.
    private static final int BOOKMARK = 0x51;

    private static final List<Route> ROUTES = new P267Adapter().inboundRoutes();

    @Test
    void everyClientPacket_shouldBeALegacyRouteOrAnIgnore_readBeforeAuthenticationOnlyWithoutAContext() {
        for (ClientPackets packet : ClientPackets.values()) {
            // The prefix is never a route of its own, should an entry on it ever return to the enum.
            if (packet.getPacketId() == P267Inbound.EX_PREFIX) {
                continue;
            }
            final RouteKind expected = packet.implemented() ? RouteKind.LEGACY : RouteKind.IGNORE;
            assertThat(route(packet.getPacketId(), Route.NO_EX_OPCODE))
                    .as("%s", packet)
                    .hasValueSatisfying(route -> {
                        assertThat(route.kind()).isEqualTo(expected);
                        assertThat(route.preAuth()).isEqualTo(packet.getContext() == LegacyContext.LOGIN);
                    });
        }
        for (ExClientPackets packet : ExClientPackets.values()) {
            final RouteKind expected =
                    (packet.implemented() || (packet.getPacketId() == BOOKMARK)) ? RouteKind.LEGACY : RouteKind.IGNORE;
            assertThat(route(P267Inbound.EX_PREFIX, packet.getPacketId()))
                    .as("%s", packet)
                    .hasValueSatisfying(route -> {
                        assertThat(route.kind()).isEqualTo(expected);
                        assertThat(route.preAuth()).isFalse();
                    });
        }
    }

    @Test
    void everyRoute_shouldComeFromAClientPacket_aCommandDecoder_orThePingAnswer() {
        assertThat(ROUTES).isNotEmpty().allSatisfy(route -> {
            switch (route.kind()) {
                case COMMAND -> assertThat(route.command()).as("%s", route).isNotNull();
                case CONNECTION -> assertThat(route.opcode()).as("%s", route).isEqualTo(NET_PING_ANSWER);
                case LEGACY, IGNORE ->
                    assertThat(declared(route)).as("%s", route).isTrue();
            }
        });
    }

    @Test
    void pingAnswer_shouldBeConsumed_afterAuthentication() {
        assertThat(route(NET_PING_ANSWER, Route.NO_EX_OPCODE)).hasValueSatisfying(route -> {
            assertThat(route.kind()).isEqualTo(RouteKind.CONNECTION);
            assertThat(route.preAuth()).isFalse();
        });
    }

    @Test
    void packetsBeforeTheWorld_shouldBeTheLoginTheSelectionScreenAndTheLoadingQueries() {
        final Map<String, LegacyContext> actual = new TreeMap<>();
        Stream.concat(
                        Arrays.stream(ClientPackets.values()).map(p -> Map.entry(p.name(), p.getContext())),
                        Arrays.stream(ExClientPackets.values()).map(p -> Map.entry(p.name(), p.getContext())))
                .filter(entry -> entry.getValue() != LegacyContext.WORLD)
                .forEach(entry -> actual.put(entry.getKey(), entry.getValue()));

        assertThat(actual)
                .containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                        Map.entry("AUTH_LOGIN", LegacyContext.LOGIN),
                        Map.entry("LOGOUT", LegacyContext.ACCOUNT),
                        Map.entry("REQUEST_HARDWARE_INFO", LegacyContext.ACCOUNT),
                        Map.entry("NEW_CHARACTER", LegacyContext.LOBBY),
                        Map.entry("CHARACTER_CREATE", LegacyContext.LOBBY),
                        Map.entry("CHARACTER_DELETE", LegacyContext.LOBBY),
                        Map.entry("CHARACTER_RESTORE", LegacyContext.LOBBY),
                        Map.entry("CHARACTER_SELECT", LegacyContext.LOBBY),
                        Map.entry("REQUEST_GOTO_LOBBY", LegacyContext.LOBBY),
                        Map.entry("REQUEST_EX_2ND_PASSWORD_CHECK", LegacyContext.LOBBY),
                        Map.entry("REQUEST_EX_2ND_PASSWORD_VERIFY", LegacyContext.LOBBY),
                        Map.entry("REQUEST_EX_2ND_PASSWORD_REQ", LegacyContext.LOBBY),
                        Map.entry("ENTER_WORLD", LegacyContext.CHARACTER),
                        Map.entry("REQUEST_KEY_MAPPING", LegacyContext.CHARACTER),
                        Map.entry("REQUEST_MANOR_LIST", LegacyContext.CHARACTER),
                        Map.entry("REQUEST_ALL_FORTRESS_INFO", LegacyContext.CHARACTER),
                        Map.entry("REQUEST_SKILL_COOL_TIME", LegacyContext.CHARACTER)));
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
