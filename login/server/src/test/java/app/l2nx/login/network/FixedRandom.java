/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.network;

import java.util.random.RandomGenerator;

final class FixedRandom implements RandomGenerator {
    private final int value;

    FixedRandom(int value) {
        this.value = value;
    }

    @Override
    public int nextInt() {
        return value;
    }

    @Override
    public long nextLong() {
        return value;
    }
}
