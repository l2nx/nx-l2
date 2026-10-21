/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.c621.clientpackets.LoginClientPacket;
import app.l2nx.network.metrics.model.DropReason;
import java.util.Set;

/**
 * A decoded packet on its connection's lane. The state is checked here, just before the packet runs, not when it is
 * read: a packet read right behind a state-changing one is judged by the state that packet leaves.
 */
record LanePacket(LoginClientPacket packet, Set<ConnectionState> accepted) implements Runnable {
    @Override
    public void run() {
        final LoginClient client = packet.getClient();
        // A packet behind the close, such as a pipelined second login after a failed one, goes uncounted like the
        // rest of a closing connection's input.
        if (client.isClosing()) {
            return;
        }
        if (!accepted.contains(client.getConnectionState())) {
            client.dropped(DropReason.WRONG_STATE);
            return;
        }
        packet.run();
    }
}
