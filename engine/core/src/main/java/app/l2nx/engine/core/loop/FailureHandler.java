/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

@FunctionalInterface
public interface FailureHandler {
    void onFailure(PassPhase phase, Object source, RuntimeException failure);
}
