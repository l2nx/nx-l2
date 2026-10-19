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
package app.l2nx.highfive.scripts.handlers.chat.commands.admin;

import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IAdminCommandHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.serverpackets.MagicSkillUse;
import java.util.StringTokenizer;

/**
 * @version $Revision: 1.2 $ $Date: 2004/06/27 08:12:59 $
 */
public class AdminTest implements IAdminCommandHandler {
    private static final String[] ADMIN_COMMANDS = {"admin_skill_test"};

    @Override
    public boolean onCommand(String command, Player activeChar) {
        if (command.startsWith("admin_skill_test")) {
            try {
                final StringTokenizer st = new StringTokenizer(command);
                st.nextToken();
                final int id = Integer.parseInt(st.nextToken());
                adminTestSkill(activeChar, id, command.startsWith("admin_skill_test"));
            } catch (Exception e) {
                activeChar.sendSysMessage("Command format is //skill_test <ID>");
            }
        }

        return true;
    }

    /**
     * @param activeChar
     * @param id
     * @param msu
     */
    private void adminTestSkill(Player activeChar, int id, boolean msu) {
        Creature caster;
        final WorldObject target = activeChar.getTarget();
        if (!target.isCreature()) {
            caster = activeChar;
        } else {
            caster = target.asCreature();
        }

        final Skill skill = SkillData.getInstance().getSkill(id, 1);
        if (skill != null) {
            caster.setTarget(activeChar);
            if (msu) {
                caster.broadcastPacket(
                        new MagicSkillUse(caster, activeChar, id, 1, skill.getHitTime(), skill.getReuseDelay()));
            } else {
                caster.doCast(skill);
            }
        }
    }

    @Override
    public String[] getCommandList() {
        return ADMIN_COMMANDS;
    }
}
