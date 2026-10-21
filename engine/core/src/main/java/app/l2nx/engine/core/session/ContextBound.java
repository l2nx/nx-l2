/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

/**
 * A session input that runs only while its game context (selected character, character in the world) is present. The
 * drain asks just before running it, so earlier queued items' changes are visible; otherwise it is rejected.
 */
public interface ContextBound {
    boolean hasContext();

    /** Tag value of the rejection meter and log: keep to a small fixed set. */
    String requiredContext();
}
