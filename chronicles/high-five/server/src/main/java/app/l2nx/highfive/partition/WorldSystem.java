/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.loop.Loop;

/**
 * A slice's periodic systems, installed on the world loop in its startup pass, before the world loads: a system
 * forgets the state of a previous world on install, so it must not drop what the load registers.
 */
public interface WorldSystem {
    void install(Loop loop);
}
