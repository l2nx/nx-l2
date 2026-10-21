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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.EnchantSkillGroupsData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.mechanics.skill.holders.EnchantSkillGroup.EnchantSkillHolder;
import app.l2nx.highfive.mechanics.skill.holders.EnchantSkillLearn;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author KenM
 */
public class ExEnchantSkillInfoDetail extends ServerPacket {
    private static final int TYPE_NORMAL_ENCHANT = 0;
    private static final int TYPE_SAFE_ENCHANT = 1;
    private static final int TYPE_UNTRAIN_ENCHANT = 2;
    private static final int TYPE_CHANGE_ENCHANT = 3;

    private int bookId = 0;
    private boolean reqCount = false;
    private int multi = 1;
    private final int type;
    private final int skillId;
    private final int skillLevel;
    private final int chance;
    private int sp;
    private final int adenacount;

    public ExEnchantSkillInfoDetail(int type, int skillId, int skillLevel, Player ply) {
        final EnchantSkillLearn enchantLearn =
                EnchantSkillGroupsData.getInstance().getSkillEnchantmentBySkillId(skillId);
        EnchantSkillHolder esd = null;

        // do we have this skill?
        if (enchantLearn != null) {
            if (skillLevel > 100) {
                esd = enchantLearn.getEnchantSkillHolder(skillLevel);
            } else {
                esd = enchantLearn.getFirstRouteGroup().getEnchantGroupDetails().get(0);
            }
        }

        if (esd == null) {
            throw new IllegalArgumentException(
                    "Skill " + skillId + " does not have enchant data for level " + skillLevel);
        }

        if (type == 0) {
            multi = EnchantSkillGroupsData.NORMAL_ENCHANT_COST_MULTIPLIER;
        } else if (type == 1) {
            multi = EnchantSkillGroupsData.SAFE_ENCHANT_COST_MULTIPLIER;
        }

        chance = esd.getRate(ply);
        sp = esd.getSpCost();
        if (type == TYPE_UNTRAIN_ENCHANT) {
            sp = (int) (0.8 * sp);
        }

        adenacount = esd.getAdenaCost() * multi;
        this.type = type;
        this.skillId = skillId;
        this.skillLevel = skillLevel;
        switch (type) {
            case TYPE_NORMAL_ENCHANT: {
                bookId = EnchantSkillGroupsData.NORMAL_ENCHANT_BOOK;
                reqCount = (this.skillLevel % 100) < 2;
                break;
            }
            case TYPE_SAFE_ENCHANT: {
                bookId = EnchantSkillGroupsData.SAFE_ENCHANT_BOOK;
                reqCount = true;
                break;
            }
            case TYPE_UNTRAIN_ENCHANT: {
                bookId = EnchantSkillGroupsData.UNTRAIN_ENCHANT_BOOK;
                reqCount = true;
                break;
            }
            case TYPE_CHANGE_ENCHANT: {
                bookId = EnchantSkillGroupsData.CHANGE_ENCHANT_BOOK;
                reqCount = true;
                break;
            }
            default: {
                return;
            }
        }

        if ((type != TYPE_SAFE_ENCHANT) && !PlayerConfig.ES_SP_BOOK_NEEDED) {
            reqCount = false;
        }
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.EX_ENCHANT_SKILL_INFO_DETAIL.writeId(this, buffer);
        buffer.writeInt(type);
        buffer.writeInt(skillId);
        buffer.writeInt(skillLevel);
        buffer.writeInt(sp * multi); // sp
        buffer.writeInt(chance); // exp
        buffer.writeInt(2); // items count?
        buffer.writeInt(Inventory.ADENA_ID); // Adena
        buffer.writeInt(adenacount); // Adena count
        buffer.writeInt(bookId); // ItemId Required
        buffer.writeInt(reqCount);
    }
}
