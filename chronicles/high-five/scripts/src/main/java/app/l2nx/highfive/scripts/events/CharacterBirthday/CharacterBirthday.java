/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.scripts.events.CharacterBirthday;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.managers.CharacterBirthdayManager;
import app.l2nx.highfive.managers.MailManager;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.ListenerRegisterType;
import app.l2nx.highfive.mechanics.events.annotations.RegisterEvent;
import app.l2nx.highfive.mechanics.events.annotations.RegisterType;
import app.l2nx.highfive.mechanics.events.holders.OnDailyReset;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.network.enums.MessageSenderType;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.repository.character.model.BirthdayCharacter;
import app.l2nx.highfive.util.LocationUtil;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Nyaran, Mobius
 */
@Slf4j
public class CharacterBirthday extends Script {
    // NPCs
    private static final int ALEGRIA = 32600;
    private static final int[] GATEKEEPERS = {
        30006, 30059, 30080, 30134, 30146, 30177, 30233, 30256, 30320, 30540, 30576, 30836, 30848, 30878, 30899, 31275,
        31320, 31964, 32163
    };

    // Misc
    private static int SPAWNS = 0;

    private CharacterBirthday() {
        addStartNpc(ALEGRIA);
        addStartNpc(GATEKEEPERS);
        addTalkId(ALEGRIA);
        addTalkId(GATEKEEPERS);
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        String htmltext = event;
        if (event.equalsIgnoreCase("despawn_npc")) {
            npc.doDie(player);
            SPAWNS--;

            htmltext = null;
        } else if (event.equalsIgnoreCase("change")) {
            // Change Hat
            if (hasQuestItems(player, 10250)) {
                takeItems(player, 10250, 1); // Adventurer Hat (Event)
                giveItems(player, 21594, 1); // Birthday Hat
                htmltext = null; // FIXME: Probably has html

                // Despawn npc
                npc.doDie(player);
                SPAWNS--;
            } else {
                htmltext = "32600-nohat.htm";
            }
        }

        return htmltext;
    }

    @Override
    public String onTalk(Npc npc, Player player) {
        if (SPAWNS >= 3) {
            return "busy.htm";
        }

        if (!LocationUtil.checkIfInRange(10, npc, player, true)) {
            final Npc spawned =
                    addSpawn(32600, player.getX() + 10, player.getY() + 10, player.getZ() + 20, 0, false, 0, true);
            startQuestTimer("despawn_npc", 180000, spawned, player);
            SPAWNS++;
        } else {
            return "tooclose.htm";
        }

        return null;
    }

    @RegisterEvent(EventType.ON_DAILY_RESET)
    @RegisterType(ListenerRegisterType.GLOBAL)
    public void onDailyReset(OnDailyReset event) {
        int birthdayGiftCount = 0;
        try {
            final Instant now = ClockBridge.wallClock().instant();
            for (BirthdayCharacter character :
                    CharacterBirthdayManager.getInstance().findBirthdays(now.minus(Duration.ofDays(1)), now)) {
                final String text = GeneralConfig.ALT_BIRTHDAY_MAIL_TEXT
                        .replace("$c1", character.name())
                        .replace("$s1", Integer.toString(character.age()));
                final MailMessage message = new MailMessage(
                        character.objectId(), GeneralConfig.ALT_BIRTHDAY_MAIL_SUBJECT, text, MessageSenderType.ALEGRIA);
                message.createAttachments()
                        .addItem(ItemProcessType.REWARD, GeneralConfig.ALT_BIRTHDAY_GIFT, 1, null, null);
                MailManager.getInstance().sendMessage(message);
                birthdayGiftCount++;
            }
        } catch (RuntimeException e) {
            log.warn("Error checking birthdays. {}", e.getMessage());
        }

        log.info("{} gifts sent.", birthdayGiftCount);
    }

    public static void main(String[] args) {
        new CharacterBirthday();
    }
}
