/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
class PlainSynchronized {
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
