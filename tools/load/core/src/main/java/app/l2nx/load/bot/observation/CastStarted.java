/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.observation;

import java.time.Duration;

/** The load bot began a cast of this length; the server refuses an attack until it ends. */
public record CastStarted(Duration hitTime) implements Observation {}
