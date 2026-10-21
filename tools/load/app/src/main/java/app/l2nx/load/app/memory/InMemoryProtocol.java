/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.memory;

import app.l2nx.load.app.LoadProperties;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.protocol.ClientProtocol;
import app.l2nx.load.protocol.ClientSession;
import app.l2nx.load.protocol.SessionListener;
import io.netty.channel.EventLoop;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A run on this network-free server stand-in measures the generator's own overhead. */
@Component
@RequiredArgsConstructor
public final class InMemoryProtocol implements ClientProtocol {
    public static final String ID = "in-memory";

    private static final Set<IntentKind> CAPABILITIES = Collections.unmodifiableSet(EnumSet.allOf(IntentKind.class));

    private final LoadProperties properties;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<IntentKind> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ClientSession open(LoadBotIdentity identity, EventLoop loop, SessionListener listener) {
        final InMemorySession session = new InMemorySession(identity, loop, listener, properties.inMemory());
        session.connect();
        return session;
    }
}
