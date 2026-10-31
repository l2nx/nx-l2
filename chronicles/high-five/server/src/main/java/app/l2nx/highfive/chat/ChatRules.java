/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.chat;

import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.entity.actor.Player;

/** The faction filter of chat: neither rule filters anyone unless faction-specific chat is on. */
public final class ChatRules {
    private ChatRules() {}

    /** A sender without a faction is heard by nobody while faction-specific chat is on. */
    public static boolean sameFactionOrNone(Player sender, Player receiver) {
        return !factionSpecificChat()
                || (sender.isGood() && receiver.isGood())
                || (sender.isEvil() && receiver.isEvil());
    }

    /** A player without a faction opposes nobody. */
    public static boolean opposingFactions(Player sender, Player receiver) {
        return factionSpecificChat()
                && ((sender.isGood() && receiver.isEvil()) || (sender.isEvil() && receiver.isGood()));
    }

    private static boolean factionSpecificChat() {
        return FactionSystemConfig.FACTION_SYSTEM_ENABLED && FactionSystemConfig.FACTION_SPECIFIC_CHAT;
    }
}
