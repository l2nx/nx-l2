/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

/** Stored as text; the order matches the legacy {@code Player.isOnlineInt()} values 0, 1, 2. */
public enum OnlineStatus {
    OFFLINE,
    ONLINE,
    DETACHED
}
