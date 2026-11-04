/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.observation;

public sealed interface Observation
        permits Entered, ObjectSeen, ObjectGone, ObjectMoved, StatusChanged, Died, Revived, CastStarted {}
