/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

/** An intent where only the latest counts (movement requests): a run of them collapses to its last one. */
public interface CoalescableCommand extends Command {}
