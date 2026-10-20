/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

/** Work handed to a loop from any thread; it runs in the loop's inbox phase on the owner thread. */
@FunctionalInterface
public interface Message {
    void execute(Loop loop);
}
