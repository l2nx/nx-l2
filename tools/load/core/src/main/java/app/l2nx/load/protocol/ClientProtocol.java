/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import io.netty.channel.EventLoop;
import java.util.Set;

/** A client adapter of one protocol: maps intents to packets and server packets to observations. */
public interface ClientProtocol {
    String id();

    /** Intent kinds this protocol can express; a load scenario that needs more does not start. */
    Set<IntentKind> capabilities();

    /**
     * Opens a session for one load bot. Non-blocking: the adapter connects on {@code loop} and walks the lifecycle
     * itself, reporting each step to {@code listener}, starting with {@code LOGIN_CONNECT}. The listener is first
     * called from a later task on {@code loop}, never before this method returns.
     */
    ClientSession open(LoadBotIdentity identity, EventLoop loop, SessionListener listener);
}
