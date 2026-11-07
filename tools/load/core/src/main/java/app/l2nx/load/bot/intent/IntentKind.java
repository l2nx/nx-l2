/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

public enum IntentKind {
    LOGIN,
    SELECT_SERVER,
    CREATE_CHARACTER,
    SELECT_CHARACTER,
    ENTER_WORLD,
    MOVE_TO,
    SAY,
    ATTACK,
    USE_SKILL,
    REVIVE_AT_RESTART_POINT,
    RESURRECT_IN_PLACE,
    ADMIN_COMMAND,
    ADMIN_TELEPORT,
    LOGOUT
}
