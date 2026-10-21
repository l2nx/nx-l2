/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.observation;

/** A world object, the load bot itself included, died. */
public record Died(int objectId) implements Observation {}
