/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.entity.actor.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ResourceLock("FactionSystemConfig")
class ChatRulesTest {
    enum Faction {
        GOOD,
        EVIL,
        NONE
    }

    private boolean factionSystem;
    private boolean factionChat;

    @BeforeEach
    void saveConfig() {
        factionSystem = FactionSystemConfig.FACTION_SYSTEM_ENABLED;
        factionChat = FactionSystemConfig.FACTION_SPECIFIC_CHAT;
    }

    @AfterEach
    void restoreConfig() {
        FactionSystemConfig.FACTION_SYSTEM_ENABLED = factionSystem;
        FactionSystemConfig.FACTION_SPECIFIC_CHAT = factionChat;
    }

    @Nested
    class SameFactionOrNone {
        @ParameterizedTest(name = "system {0}, faction chat {1}: {2} to {3} heard {4}")
        @CsvSource({
            "false, true,  GOOD, EVIL, true",
            "true,  false, GOOD, EVIL, true",
            "true,  true,  GOOD, GOOD, true",
            "true,  true,  EVIL, EVIL, true",
            "true,  true,  GOOD, EVIL, false",
            "true,  true,  NONE, NONE, false",
            "true,  true,  NONE, GOOD, false"
        })
        void sameFactionOrNone_shouldFollowTheFactionChatConfig(
                boolean system, boolean factionChat, Faction sender, Faction receiver, boolean expected) {
            FactionSystemConfig.FACTION_SYSTEM_ENABLED = system;
            FactionSystemConfig.FACTION_SPECIFIC_CHAT = factionChat;

            assertThat(ChatRules.sameFactionOrNone(player(sender), player(receiver)))
                    .isEqualTo(expected);
        }
    }

    @Nested
    class OpposingFactions {
        @ParameterizedTest(name = "system {0}, faction chat {1}: {2} and {3} opposing {4}")
        @CsvSource({
            "false, true,  GOOD, EVIL, false",
            "true,  false, GOOD, EVIL, false",
            "true,  true,  GOOD, EVIL, true",
            "true,  true,  EVIL, GOOD, true",
            "true,  true,  GOOD, GOOD, false",
            "true,  true,  NONE, EVIL, false"
        })
        void opposingFactions_shouldFollowTheFactionChatConfig(
                boolean system, boolean factionChat, Faction sender, Faction receiver, boolean expected) {
            FactionSystemConfig.FACTION_SYSTEM_ENABLED = system;
            FactionSystemConfig.FACTION_SPECIFIC_CHAT = factionChat;

            assertThat(ChatRules.opposingFactions(player(sender), player(receiver)))
                    .isEqualTo(expected);
        }
    }

    private static Player player(Faction faction) {
        final Player player = mock(Player.class);
        when(player.isGood()).thenReturn(faction == Faction.GOOD);
        when(player.isEvil()).thenReturn(faction == Faction.EVIL);
        return player;
    }
}
