/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.observation.Observation;
import app.l2nx.load.protocol.SessionListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class RecordingListener implements SessionListener {
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private Consumer<String> reaction = event -> {};

    public RecordingListener reacting(Consumer<String> reaction) {
        this.reaction = reaction;
        return this;
    }

    public List<String> events() {
        return events;
    }

    public List<Throwable> failures() {
        return failures;
    }

    @Override
    public void stateChanged(LoadBotState state) {
        record("state " + state);
    }

    @Override
    public void observed(Observation observation) {
        record("observed " + observation);
    }

    @Override
    public void acknowledged(IntentKind kind) {
        record("ack " + kind);
    }

    @Override
    public void rejected(IntentKind kind) {
        record("reject " + kind);
    }

    @Override
    public void failed(Throwable cause) {
        failures.add(cause);
        record("failed");
    }

    private void record(String event) {
        events.add(event);
        reaction.accept(event);
    }
}
