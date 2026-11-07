/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

/** What a load bot wants to do, independent of the protocol; the client adapter maps it to packets. */
public sealed interface Intent
        permits Login,
                SelectServer,
                CreateCharacter,
                SelectCharacter,
                EnterWorld,
                MoveTo,
                Say,
                Attack,
                UseSkill,
                ReviveAtRestartPoint,
                ResurrectInPlace,
                AdminCommand,
                AdminTeleport,
                Logout {
    IntentKind kind();

    /**
     * Answer content matched with {@code equals}, so a late answer is not credited to the next intent of the kind;
     * {@code null}: the oldest pending intent of the kind takes the answer.
     */
    default Object correlation() {
        return null;
    }
}
