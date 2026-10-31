/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import io.netty.channel.EventLoop;
import io.netty.channel.IoHandlerFactory;
import java.util.Set;

public interface ClientProtocol {
    String id();

    /** A scenario needing more kinds does not start. */
    Set<IntentKind> capabilities();

    /** A scenario whose final state lies beyond it does not start: every load bot would stop on an error. */
    LoadBotState lastState();

    /** {@code false} for a stand-in with no server behind it: a run on it has no server meters to read. */
    boolean hasServer();

    /** Must match the channel class the adapter connects with. */
    IoHandlerFactory ioHandlerFactory();

    /**
     * Called for every load bot before any starts.
     *
     * @throws IllegalArgumentException when the protocol cannot carry the identity, such as an overlong account
     */
    default void checkIdentity(LoadBotIdentity identity) {}

    /** Non-blocking; the listener is first called from a later task on {@code loop}, never before this returns. */
    ClientSession open(LoadBotIdentity identity, EventLoop loop, SessionListener listener);
}
