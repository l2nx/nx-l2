/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EnterWorldTest {
    private static Inet4Address hop(int last) throws UnknownHostException {
        return (Inet4Address) InetAddress.getByAddress(new byte[] {10, 0, 0, (byte) last});
    }

    @Nested
    class Route {
        @Test
        void route_shouldKeepTheTracedHops_whenTheCallerChangesItsList() throws UnknownHostException {
            final List<Inet4Address> route = new ArrayList<>(List.of(hop(1)));

            final EnterWorld actual = new EnterWorld(route);
            route.add(hop(2));

            assertThat(actual.route()).containsExactly(hop(1));
        }

        @Test
        void route_shouldRefuseChanges() throws UnknownHostException {
            final EnterWorld actual = new EnterWorld(List.of(hop(1)));

            assertThatThrownBy(() -> actual.route().add(hop(2))).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
