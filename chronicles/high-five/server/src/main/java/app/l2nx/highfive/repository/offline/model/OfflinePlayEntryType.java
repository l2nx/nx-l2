/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.offline.model;

/**
 * What an offline play setting holds; the names of the {@code OfflinePlayTable.TYPE_*} constants Mobius stored as
 * numbers.
 */
public enum OfflinePlayEntryType {
    ACTIVE_SOULSHOT,
    AUTO_SUPPLY_ITEM,
    AUTO_BUFF,
    AUTO_SKILL,
    AUTO_ACTION,
    AUTO_POTION_ITEM,
    /** A flag without a target. */
    CUSTOM_AUTO_POTION_SYSTEM
}
