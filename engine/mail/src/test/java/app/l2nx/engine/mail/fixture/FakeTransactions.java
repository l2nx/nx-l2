/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.fixture;

import app.l2nx.engine.core.persistence.value.ValueOutcome;
import app.l2nx.engine.core.persistence.value.ValueTransaction;
import app.l2nx.engine.core.persistence.value.ValueTransactionType;
import app.l2nx.engine.core.persistence.value.ValueTransactions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** A runner that executes nothing and answers the outcomes a test queued, in order; lookups likewise. */
public final class FakeTransactions implements ValueTransactions {
    private final Queue<Function<ValueTransaction<?>, ValueOutcome<?>>> outcomes = new ArrayDeque<>();
    private final Queue<ValueOutcome<Void>> lookups = new ArrayDeque<>();
    private final List<ValueTransaction<?>> submitted = new ArrayList<>();

    public void answer(Function<ValueTransaction<?>, ValueOutcome<?>> outcome) {
        outcomes.add(outcome);
    }

    public void answer(ValueOutcome<?> outcome) {
        outcomes.add(transaction -> outcome);
    }

    public void answerLookup(ValueOutcome<Void> outcome) {
        lookups.add(outcome);
    }

    public List<ValueTransaction<?>> submitted() {
        return List.copyOf(submitted);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> void submit(ValueTransaction<R> transaction, Executor resultLoop, Consumer<ValueOutcome<R>> onOutcome) {
        submitted.add(transaction);
        final Function<ValueTransaction<?>, ValueOutcome<?>> outcome = outcomes.remove();
        resultLoop.execute(() -> onOutcome.accept((ValueOutcome<R>) outcome.apply(transaction)));
    }

    @Override
    public void lookup(
            ValueTransactionType type,
            UUID idempotencyKey,
            Executor resultLoop,
            Consumer<ValueOutcome<Void>> onOutcome) {
        final ValueOutcome<Void> outcome = lookups.remove();
        resultLoop.execute(() -> onOutcome.accept(outcome));
    }
}
