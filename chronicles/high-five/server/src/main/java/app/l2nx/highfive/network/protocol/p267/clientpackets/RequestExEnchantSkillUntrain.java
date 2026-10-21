/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.EnchantSkillGroupsData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.EnchantSkillGroup.EnchantSkillHolder;
import app.l2nx.highfive.mechanics.skill.holders.EnchantSkillLearn;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExEnchantSkillInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExEnchantSkillInfoDetail;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExEnchantSkillResult;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Format (ch) dd c: (id) 0xD0 h: (subid) 0x33 d: skill id d: skill level
 * @author -Wooden-
 */
public class RequestExEnchantSkillUntrain extends ClientPacket {

    private static final Logger ENCHANT_SKILLS_LOG = LoggerFactory.getLogger("enchant.skills");

    private int skillId;
    private int skillLevel;

    @Override
    protected void readImpl() {
        skillId = readInt();
        skillLevel = readInt();
    }

    @Override
    protected void runImpl() {
        if ((skillId <= 0) || (skillLevel <= 0)) {
            return;
        }

        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (player.getPlayerClass().level() < 3) // requires to have 3rd class quest completed
        {
            player.sendPacket(
                    SystemMessageId
                            .YOU_CANNOT_USE_THE_SKILL_ENHANCING_FUNCTION_IN_THIS_CLASS_YOU_CAN_USE_CORRESPONDING_FUNCTION_WHEN_COMPLETING_THE_THIRD_CLASS_CHANGE);
            return;
        }

        if (player.getLevel() < 76) {
            player.sendPacket(
                    SystemMessageId
                            .YOU_CANNOT_USE_THE_SKILL_ENHANCING_FUNCTION_ON_THIS_LEVEL_YOU_CAN_USE_THE_CORRESPONDING_FUNCTION_ON_LEVELS_HIGHER_THAN_76LV);
            return;
        }

        if (!player.isAllowedToEnchantSkills()) {
            player.sendPacket(
                    SystemMessageId
                            .YOU_CANNOT_USE_THE_SKILL_ENHANCING_FUNCTION_IN_THIS_CLASS_YOU_CAN_USE_THE_SKILL_ENHANCING_FUNCTION_UNDER_OFF_BATTLE_STATUS_AND_CANNOT_USE_THE_FUNCTION_WHILE_TRANSFORMING_BATTLING_AND_ON_BOARD);
            return;
        }

        final EnchantSkillLearn s = EnchantSkillGroupsData.getInstance().getSkillEnchantmentBySkillId(skillId);
        if (s == null) {
            return;
        }

        if ((skillLevel % 100) == 0) {
            skillLevel = s.getBaseLevel();
        }

        final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
        if (skill == null) {
            return;
        }

        final int reqItemId = EnchantSkillGroupsData.UNTRAIN_ENCHANT_BOOK;
        final int beforeUntrainSkillLevel = player.getSkillLevel(skillId);
        if (((beforeUntrainSkillLevel - 1) != skillLevel)
                && (((beforeUntrainSkillLevel % 100) != 1) || (skillLevel != s.getBaseLevel()))) {
            return;
        }

        final Item spb = player.getInventory().getItemByItemId(reqItemId);
        // does not have spellbook
        if (PlayerConfig.ES_SP_BOOK_NEEDED && (spb == null)) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ALL_OF_THE_ITEMS_NEEDED_TO_ENCHANT_THAT_SKILL);
            return;
        }

        final EnchantSkillHolder esd = s.getEnchantSkillHolder(beforeUntrainSkillLevel);
        final int requireditems = esd.getAdenaCost();

        if (player.getInventory().getAdena() < requireditems) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ALL_OF_THE_ITEMS_NEEDED_TO_ENCHANT_THAT_SKILL);
            return;
        }

        boolean check = true;
        if (PlayerConfig.ES_SP_BOOK_NEEDED) {
            check &= player.destroyItem(ItemProcessType.NONE, spb.getObjectId(), 1, player, true);
        }

        check &= player.destroyItemByItemId(null, Inventory.ADENA_ID, requireditems, player, true);
        if (!check) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ALL_OF_THE_ITEMS_NEEDED_TO_ENCHANT_THAT_SKILL);
            return;
        }

        player.getStat().addSp((int) (esd.getSpCost() * 0.8));
        if (GeneralConfig.LOG_SKILL_ENCHANTS) {
            ENCHANT_SKILLS_LOG.info(
                    "Untrain:{} [{}] Account:{} IP:{}, Skill:{}, SPB:{}",
                    player.getName(),
                    player.getObjectId(),
                    player.getAccountName(),
                    player.getIPAddress(),
                    skill,
                    spb);
        }

        player.addSkill(skill, true);
        player.sendPacket(ExEnchantSkillResult.valueOf(true));

        player.updateUserInfo();
        if (skillLevel > 100) {
            final SystemMessage sm = new SystemMessage(
                    SystemMessageId
                            .UNTRAIN_OF_ENCHANT_SKILL_WAS_SUCCESSFUL_CURRENT_LEVEL_OF_ENCHANT_SKILL_S1_HAS_BEEN_DECREASED_BY_1);
            sm.addSkillName(skillId);
            player.sendPacket(sm);
        } else {
            final SystemMessage sm = new SystemMessage(
                    SystemMessageId
                            .UNTRAIN_OF_ENCHANT_SKILL_WAS_SUCCESSFUL_CURRENT_LEVEL_OF_ENCHANT_SKILL_S1_BECAME_0_AND_ENCHANT_SKILL_WILL_BE_INITIALIZED);
            sm.addSkillName(skillId);
            player.sendPacket(sm);
        }

        player.sendSkillList();
        final int afterUntrainSkillLevel = player.getSkillLevel(skillId);
        player.sendPacket(new ExEnchantSkillInfo(skillId, afterUntrainSkillLevel));
        player.sendPacket(new ExEnchantSkillInfoDetail(2, skillId, afterUntrainSkillLevel - 1, player));
        player.updateShortcuts(skillId, afterUntrainSkillLevel);
    }
}
