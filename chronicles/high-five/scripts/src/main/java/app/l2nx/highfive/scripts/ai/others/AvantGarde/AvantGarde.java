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
package app.l2nx.highfive.scripts.ai.others.AvantGarde;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.MultisellData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ScriptManager;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.AcquireSkillType;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.clientpackets.RequestAcquireSkill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AcquireSkillList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.scripts.custom.Validators.SubClassSkills;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Avant-Garde AI.<br>
 * Sub-Class Certification system, skill learning and certification canceling.<br>
 * Transformation skill learning and transformation scroll sell.
 * @author Zoey76
 */
@Slf4j
public class AvantGarde extends Script {
    // NPC
    private static final int AVANT_GARDE = 32323;

    // Items
    // @formatter:off
    private static final int[] ITEMS = {
        10280, 10281, 10282, 10283, 10284, 10285, 10286, 10287, 10288, 10289, 10290, 10291, 10292, 10293, 10294, 10612
    };
    // @formatter:on

    // Misc
    private static final String[] QUEST_VAR_NAMES = {
        "EmergentAbility65-", "EmergentAbility70-", "ClassAbility75-", "ClassAbility80-"
    };

    public AvantGarde() {
        addStartNpc(AVANT_GARDE);
        addTalkId(AVANT_GARDE);
        addFirstTalkId(AVANT_GARDE);
        addAcquireSkillId(AVANT_GARDE);
    }

    @Override
    public void onAcquireSkill(Npc npc, Player player, Skill skill, AcquireSkillType type) {
        switch (type) {
            case TRANSFORM: {
                showTransformSkillList(player);
                break;
            }
            case SUBCLASS: {
                showSubClassSkillList(player);
                break;
            }
        }
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        String htmltext = null;
        switch (event) {
            case "32323-02.html":
            case "32323-02a.html":
            case "32323-02b.html":
            case "32323-02c.html":
            case "32323-05.html":
            case "32323-05a.html":
            case "32323-05no.html":
            case "32323-06.html":
            case "32323-06no.html": {
                htmltext = event;
                break;
            }
            case "LearnTransformationSkill": {
                if (RequestAcquireSkill.canTransform(player)) {
                    showTransformSkillList(player);
                } else {
                    htmltext = "32323-03.html";
                }
                break;
            }
            case "BuyTransformationItems": {
                if (RequestAcquireSkill.canTransform(player)) {
                    MultisellData.getInstance().separateAndSend(32323001, player, npc, false);
                } else {
                    htmltext = "32323-04.html";
                }
                break;
            }
            case "LearnSubClassSkill": {
                if (!RequestAcquireSkill.canTransform(player)) {
                    htmltext = "32323-04.html";
                }

                if (player.isSubClassActive()) {
                    htmltext = "32323-08.html";
                } else {
                    boolean hasItems = false;
                    for (int id : ITEMS) {
                        if (player.getInventory().getItemByItemId(id) != null) {
                            hasItems = true;
                            break;
                        }
                    }

                    if (hasItems) {
                        showSubClassSkillList(player);
                    } else {
                        htmltext = "32323-08.html";
                    }
                }
                break;
            }
            case "CancelCertification": {
                if (player.getSubClasses().isEmpty()) {
                    htmltext = "32323-07.html";
                } else if (player.isSubClassActive()) {
                    htmltext = "32323-08.html";
                } else if (player.getAdena() < PlayerConfig.FEE_DELETE_SUBCLASS_SKILLS) {
                    htmltext = "32323-08no.html";
                } else {
                    QuestState qs = player.getQuestState(SubClassSkills.class.getSimpleName());
                    if (qs == null) {
                        qs = ScriptManager.getInstance()
                                .getScript(SubClassSkills.class.getSimpleName())
                                .newQuestState(player);
                    }

                    int activeCertifications = 0;
                    for (String varName : QUEST_VAR_NAMES) {
                        for (int i = 1; i <= PlayerConfig.MAX_SUBCLASS; i++) {
                            final String qvar = player.getVariables().getString(varName + i, "0");
                            if (!qvar.isEmpty() && (qvar.endsWith(";") || !qvar.equals("0"))) {
                                activeCertifications++;
                            }
                        }
                    }

                    if (activeCertifications == 0) {
                        htmltext = "32323-10no.html";
                    } else {
                        for (String varName : QUEST_VAR_NAMES) {
                            for (int i = 1; i <= PlayerConfig.MAX_SUBCLASS; i++) {
                                final String qvarName = varName + i;
                                final String qvar = player.getVariables().getString(qvarName, "0");
                                if (qvar.endsWith(";")) {
                                    final String skillIdVar = qvar.replace(";", "");
                                    if (StringUtils.isNumeric(skillIdVar)) {
                                        final int skillId = Integer.parseInt(skillIdVar);
                                        final Skill sk = SkillData.getInstance().getSkill(skillId, 1);
                                        if (sk != null) {
                                            player.removeSkill(sk);
                                            player.getVariables().set(qvarName, "0");
                                        }
                                    } else {
                                        log.warn("Invalid Sub-Class Skill Id: {} for {}!", skillIdVar, player);
                                    }
                                } else if (!qvar.isEmpty() && !qvar.equals("0")) {
                                    if (StringUtils.isNumeric(qvar)) {
                                        final int itemObjId = Integer.parseInt(qvar);
                                        Item itemInstance =
                                                player.getInventory().getItemByObjectId(itemObjId);
                                        if (itemInstance != null) {
                                            player.destroyItem(ItemProcessType.DESTROY, itemObjId, 1, player, false);
                                        } else {
                                            itemInstance = player.getWarehouse().getItemByObjectId(itemObjId);
                                            if (itemInstance != null) {
                                                log.warn(
                                                        "Somehow {} put a certification book into warehouse!",
                                                        player.getName());
                                                player.getWarehouse()
                                                        .destroyItem(
                                                                ItemProcessType.DESTROY,
                                                                itemInstance,
                                                                1,
                                                                player,
                                                                false);
                                            } else {
                                                log.warn("Somehow {} deleted a certification book!", player.getName());
                                            }
                                        }

                                        player.getVariables().set(qvarName, "0");
                                    } else {
                                        log.warn("Invalid item object Id: {} for {}!", qvar, player);
                                    }
                                }
                            }
                        }

                        player.reduceAdena(ItemProcessType.FEE, PlayerConfig.FEE_DELETE_SUBCLASS_SKILLS, npc, true);
                        htmltext = "32323-09no.html";
                        player.sendSkillList();
                    }

                    // Let's consume all certification books, even those not present in database.
                    for (int itemId : ITEMS) {
                        final Item item = player.getInventory().getItemByItemId(itemId);
                        if (item != null) {
                            log.warn(
                                    "{}: {} had 'extra' certification skill books while cancelling sub-class certifications!",
                                    getClass().getName(),
                                    player);
                            player.destroyItem(ItemProcessType.DESTROY, item, npc, false);
                        }
                    }
                }
                break;
            }
        }

        return htmltext;
    }

    @Override
    public String onFirstTalk(Npc npc, Player player) {
        return "32323-01.html";
    }

    @Override
    public String onTalk(Npc npc, Player talker) {
        return "32323-01.html";
    }

    /**
     * Display the Sub-Class Skill list to the player.
     * @param player the player
     */
    public static void showSubClassSkillList(Player player) {
        final List<SkillLearn> subClassSkills = SkillTreeData.getInstance().getAvailableSubClassSkills(player);
        final AcquireSkillList asl = new AcquireSkillList(AcquireSkillType.SUBCLASS);
        int count = 0;

        for (SkillLearn s : subClassSkills) {
            if (SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel()) != null) {
                count++;
                asl.addSkill(s.getSkillId(), s.getSkillLevel(), s.getSkillLevel(), 0, 0);
            }
        }

        if (count > 0) {
            player.sendPacket(asl);
        } else {
            player.sendPacket(SystemMessageId.THERE_ARE_NO_OTHER_SKILLS_TO_LEARN);
        }
    }

    /**
     * This displays Transformation Skill List to the player.
     * @param player the active character.
     */
    public static void showTransformSkillList(Player player) {
        final List<SkillLearn> skills = SkillTreeData.getInstance().getAvailableTransformSkills(player);
        final AcquireSkillList asl = new AcquireSkillList(AcquireSkillType.TRANSFORM);
        int counts = 0;

        for (SkillLearn s : skills) {
            if (SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel()) != null) {
                counts++;
                asl.addSkill(s.getSkillId(), s.getSkillLevel(), s.getSkillLevel(), s.getLevelUpSp(), 0);
            }
        }

        if (counts == 0) {
            final int minlevel = SkillTreeData.getInstance()
                    .getMinLevelForNewSkill(player, SkillTreeData.getInstance().getTransformSkillTree());
            if (minlevel > 0) {
                // No more skills to learn, come back when you level.
                final SystemMessage sm = new SystemMessage(
                        SystemMessageId
                                .YOU_DO_NOT_HAVE_ANY_FURTHER_SKILLS_TO_LEARN_COME_BACK_WHEN_YOU_HAVE_REACHED_LEVEL_S1);
                sm.addInt(minlevel);
                player.sendPacket(sm);
            } else {
                player.sendPacket(SystemMessageId.THERE_ARE_NO_OTHER_SKILLS_TO_LEARN);
            }
        } else {
            player.sendPacket(asl);
        }
    }

    public static void main(String[] args) {
        new AvantGarde();
    }
}
