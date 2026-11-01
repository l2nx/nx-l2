/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.fixture;

import app.l2nx.engine.core.persistence.value.ValueOutcome;
import app.l2nx.engine.core.persistence.value.ValueTransaction;
import app.l2nx.engine.core.persistence.value.ValueTransactionType;
import app.l2nx.engine.core.persistence.value.ValueTransactions;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Commits every transaction on the real runner but loses its result, as a dropped connection would: the caller gets
 * a genuine replay of the key, or an unknown outcome it has to look up.
 */
public final class LosingTransactions implements ValueTransactions {
    public enum Loss {
        REPLAYED,
        UNKNOWN
    }

    private final ValueTransactions delegate;
    private final Loss loss;

    public LosingTransactions(ValueTransactions delegate, Loss loss) {
        this.delegate = delegate;
        this.loss = loss;
    }

    @Override
    public <R> void submit(ValueTransaction<R> transaction, Executor resultLoop, Consumer<ValueOutcome<R>> onOutcome) {
        delegate.submit(transaction, Runnable::run, first -> {
            if (!(first instanceof ValueOutcome.Committed<R>)) {
                resultLoop.execute(() -> onOutcome.accept(first));
                return;
            }
            switch (loss) {
                case REPLAYED -> delegate.submit(transaction, resultLoop, onOutcome);
                case UNKNOWN ->
                    resultLoop.execute(() ->
                            onOutcome.accept(new ValueOutcome.Unknown<>(new IllegalStateException("connection lost"))));
            }
        });
    }

    @Override
    public void lookup(
            ValueTransactionType type,
            UUID idempotencyKey,
            Executor resultLoop,
            Consumer<ValueOutcome<Void>> onOutcome) {
        delegate.lookup(type, idempotencyKey, resultLoop, onOutcome);
    }
}
