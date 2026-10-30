/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import app.l2nx.engine.core.loop.SingleWriter;

@SingleWriter
class AnnotatedSynchronized {
    private int value;

    synchronized void method() {
        value++;
    }

    void block() {
        synchronized (this) {
            value++;
        }
    }
}

@SingleWriter
enum AnnotatedSynchronizedEnum {
    INSTANCE;

    synchronized void method() {}
}

@SingleWriter
record AnnotatedSynchronizedRecord(int value) {
    synchronized int method() {
        return value;
    }
}

@SingleWriter
interface AnnotatedSynchronizedInterface {
    default void block() {
        synchronized (this) {
            hashCode();
        }
    }
}
