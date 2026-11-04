/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.memory;

import app.l2nx.load.app.LoadProperties;
import app.l2nx.load.bot.intent.AdminTeleport;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.MoveTo;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.Entered;
import app.l2nx.load.bot.observation.ObjectMoved;
import app.l2nx.load.protocol.ClientSession;
import app.l2nx.load.protocol.SessionListener;
import io.netty.channel.EventLoop;
import java.util.concurrent.TimeUnit;

/**
 * Every answer is a task on the load bot's event loop, so the listener is never called from inside {@link #send} or
 * {@link #connect}; after {@link #close()} pending answers are dropped.
 */
final class InMemorySession implements ClientSession {
    static final int OBJECT_ID_BASE = 1_000_000;

    // Any point works: nothing in memory depends on the world.
    static final Position SPAWN = Position.builder().x(0).y(0).z(0).build();

    private final LoadBotIdentity identity;
    private final EventLoop loop;
    private final SessionListener listener;
    private final long stepDelayNanos;
    private final long ackLatencyNanos;
    private boolean open = true;

    InMemorySession(
            LoadBotIdentity identity, EventLoop loop, SessionListener listener, LoadProperties.InMemory settings) {
        this.identity = identity;
        this.loop = loop;
        this.listener = listener;
        stepDelayNanos = settings.stepDelay().toNanos();
        ackLatencyNanos = settings.ackLatency().toNanos();
    }

    void connect() {
        step(() -> {
            listener.stateChanged(LoadBotState.LOGIN_CONNECT);
            step(() -> listener.stateChanged(LoadBotState.LOGIN_AUTH));
        });
    }

    @Override
    public void send(Intent intent) {
        final IntentKind kind = intent.kind();
        switch (kind) {
            case LOGIN -> step(() -> acknowledge(kind, LoadBotState.SERVER_LIST));
            case SELECT_SERVER ->
                step(() -> {
                    acknowledge(kind, LoadBotState.GAME_CONNECT);
                    step(() -> listener.stateChanged(LoadBotState.CHARACTER_SELECT));
                });
            case SELECT_CHARACTER -> step(() -> acknowledge(kind, LoadBotState.ENTERING));
            case ENTER_WORLD -> step(this::enter);
            case LOGOUT -> step(() -> acknowledge(kind, LoadBotState.CLOSING, LoadBotState.STOPPED));
            default -> later(ackLatencyNanos, () -> answer(intent));
        }
    }

    @Override
    public void close() {
        open = false;
    }

    private void enter() {
        listener.acknowledged(IntentKind.ENTER_WORLD);
        listener.observed(Entered.builder()
                .selfObjectId(OBJECT_ID_BASE + identity.index())
                .position(SPAWN)
                .build());
        listener.stateChanged(LoadBotState.IN_GAME);
    }

    // The echo of the own movement is what confirms a move on a real server; the other intents just confirm.
    private void answer(Intent intent) {
        final Position destination = switch (intent) {
            case MoveTo move -> move.destination();
            case AdminTeleport teleport -> teleport.destination();
            default -> null;
        };
        if (destination != null) {
            listener.observed(ObjectMoved.builder()
                    .objectId(OBJECT_ID_BASE + identity.index())
                    .position(destination)
                    .build());
        }
        listener.acknowledged(intent.kind());
    }

    private void acknowledge(IntentKind kind, LoadBotState... next) {
        listener.acknowledged(kind);
        for (LoadBotState state : next) {
            if (!open) {
                return;
            }
            listener.stateChanged(state);
        }
    }

    private void step(Runnable answer) {
        later(stepDelayNanos, answer);
    }

    private void later(long delayNanos, Runnable answer) {
        loop.schedule(
                () -> {
                    if (open) {
                        answer.run();
                    }
                },
                delayNanos,
                TimeUnit.NANOSECONDS);
    }
}
