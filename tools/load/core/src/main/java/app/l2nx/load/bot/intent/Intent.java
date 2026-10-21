/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

/** What a load bot wants to do, independent of the protocol; the client adapter maps it to packets. */
public sealed interface Intent
        permits Login,
                SelectServer,
                SelectCharacter,
                EnterWorld,
                MoveTo,
                Attack,
                UseSkill,
                Say,
                ResurrectAtRestartPoint,
                Logout {
    IntentKind kind();
}
