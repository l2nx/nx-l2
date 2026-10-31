/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621.pipeline;

import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.c621.model.ConnectionState;
import app.l2nx.network.metrics.model.DropReason;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Builder;

/**
 * The state is checked just before the handler runs, not at read: a packet read behind a state-changing one is judged
 * by the state that packet leaves.
 */
@Builder(toBuilder = true)
record LanePacket(LoginClient client, Consumer<LoginClient> handler, Set<ConnectionState> accepted)
        implements Runnable {
    @Override
    public void run() {
        // Input behind a close (e.g. a pipelined second login after a failed one) goes uncounted.
        if (client.isClosing()) {
            return;
        }
        if (!accepted.contains(client.getConnectionState())) {
            client.dropped(DropReason.WRONG_STATE);
            return;
        }
        handler.accept(client);
    }
}
