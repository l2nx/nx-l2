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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.data.enums.CategoryType;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.CategoryData;
import app.l2nx.highfive.data.xml.ClassListData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.actor.holders.player.SubClassHolder;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.Clan.SubPledge;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.skill.enums.AcquireSkillType;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AcquireSkillList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillLaunched;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillUse;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @version $Revision: 1.4.2.3.2.8 $ $Date: 2005/03/29 23:15:15 $
 */
@Slf4j
@SingleWriter
public class VillageMaster extends Folk {

    private static final Set<PlayerClass> mainSubclassSet;
    private static final Set<PlayerClass> neverSubclassed = EnumSet.of(PlayerClass.OVERLORD, PlayerClass.WARSMITH);
    private static final Set<PlayerClass> subclasseSet1 = EnumSet.of(
            PlayerClass.DARK_AVENGER, PlayerClass.PALADIN, PlayerClass.TEMPLE_KNIGHT, PlayerClass.SHILLIEN_KNIGHT);
    private static final Set<PlayerClass> subclasseSet2 =
            EnumSet.of(PlayerClass.TREASURE_HUNTER, PlayerClass.ABYSS_WALKER, PlayerClass.PLAINS_WALKER);
    private static final Set<PlayerClass> subclasseSet3 =
            EnumSet.of(PlayerClass.HAWKEYE, PlayerClass.SILVER_RANGER, PlayerClass.PHANTOM_RANGER);
    private static final Set<PlayerClass> subclasseSet4 =
            EnumSet.of(PlayerClass.WARLOCK, PlayerClass.ELEMENTAL_SUMMONER, PlayerClass.PHANTOM_SUMMONER);
    private static final Set<PlayerClass> subclasseSet5 =
            EnumSet.of(PlayerClass.SORCERER, PlayerClass.SPELLSINGER, PlayerClass.SPELLHOWLER);
    private static final EnumMap<PlayerClass, Set<PlayerClass>> subclassSetMap = new EnumMap<>(PlayerClass.class);

    static {
        final Set<PlayerClass> subclasses =
                CategoryData.getInstance().getCategoryByType(CategoryType.THIRD_CLASS_GROUP).stream()
                        .map(PlayerClass::getPlayerClass)
                        .collect(Collectors.toSet());
        subclasses.removeAll(neverSubclassed);
        mainSubclassSet = subclasses;
        subclassSetMap.put(PlayerClass.DARK_AVENGER, subclasseSet1);
        subclassSetMap.put(PlayerClass.PALADIN, subclasseSet1);
        subclassSetMap.put(PlayerClass.TEMPLE_KNIGHT, subclasseSet1);
        subclassSetMap.put(PlayerClass.SHILLIEN_KNIGHT, subclasseSet1);
        subclassSetMap.put(PlayerClass.TREASURE_HUNTER, subclasseSet2);
        subclassSetMap.put(PlayerClass.ABYSS_WALKER, subclasseSet2);
        subclassSetMap.put(PlayerClass.PLAINS_WALKER, subclasseSet2);
        subclassSetMap.put(PlayerClass.HAWKEYE, subclasseSet3);
        subclassSetMap.put(PlayerClass.SILVER_RANGER, subclasseSet3);
        subclassSetMap.put(PlayerClass.PHANTOM_RANGER, subclasseSet3);
        subclassSetMap.put(PlayerClass.WARLOCK, subclasseSet4);
        subclassSetMap.put(PlayerClass.ELEMENTAL_SUMMONER, subclasseSet4);
        subclassSetMap.put(PlayerClass.PHANTOM_SUMMONER, subclasseSet4);
        subclassSetMap.put(PlayerClass.SORCERER, subclasseSet5);
        subclassSetMap.put(PlayerClass.SPELLSINGER, subclasseSet5);
        subclassSetMap.put(PlayerClass.SPELLHOWLER, subclasseSet5);
    }

    public VillageMaster(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.VillageMaster);
    }

    @Override
    public String getHtmlPath(int npcId, int value) {
        WorldPartition.checkOwner();
        String pom = "";
        if (value == 0) {
            pom = Integer.toString(npcId);
        } else {
            pom = npcId + "-" + value;
        }

        return "data/html/villagemaster/" + pom + ".htm";
    }

    @Override
    public void onBypassFeedback(Player player, String command) {
        WorldPartition.checkOwner();
        final String[] commandStr = command.split(" ");
        final String actualCommand = commandStr[0];
        String cmdParams = "";
        String cmdParams2 = "";
        if (commandStr.length >= 2) {
            cmdParams = commandStr[1];
        }

        if (commandStr.length >= 3) {
            cmdParams2 = commandStr[2];
        }

        if (actualCommand.equalsIgnoreCase("create_clan")) {
            if (cmdParams.isEmpty()) {
                player.sendPacket(SystemMessageId.PLEASE_CREATE_YOUR_CLAN_NAME);
                return;
            }

            if (!cmdParams2.isEmpty() || !isValidName(cmdParams)) {
                player.sendPacket(SystemMessageId.CLAN_NAME_IS_INVALID);
                return;
            }

            ClanTable.getInstance().createClan(player, cmdParams);
        } else if (actualCommand.equalsIgnoreCase("create_academy")) {
            if (cmdParams.isEmpty()) {
                return;
            }

            createSubPledge(player, cmdParams, null, Clan.SUBUNIT_ACADEMY, 5);
        } else if (actualCommand.equalsIgnoreCase("rename_pledge")) {
            if (cmdParams.isEmpty() || cmdParams2.isEmpty()) {
                return;
            }

            renameSubPledge(player, Integer.parseInt(cmdParams), cmdParams2);
        } else if (actualCommand.equalsIgnoreCase("create_royal")) {
            if (cmdParams.isEmpty()) {
                return;
            }

            createSubPledge(player, cmdParams, cmdParams2, Clan.SUBUNIT_ROYAL1, 6);
        } else if (actualCommand.equalsIgnoreCase("create_knight")) {
            if (cmdParams.isEmpty()) {
                return;
            }

            createSubPledge(player, cmdParams, cmdParams2, Clan.SUBUNIT_KNIGHT1, 7);
        } else if (actualCommand.equalsIgnoreCase("assign_subpl_leader")) {
            if (cmdParams.isEmpty()) {
                return;
            }

            assignSubPledgeLeader(player, cmdParams, cmdParams2);
        } else if (actualCommand.equalsIgnoreCase("create_ally")) {
            if (cmdParams.isEmpty()) {
                return;
            }

            if (player.getClan() == null) {
                player.sendPacket(SystemMessageId.ONLY_CLAN_LEADERS_MAY_CREATE_ALLIANCES);
            } else {
                player.getClan().createAlly(player, cmdParams);
            }
        } else if (actualCommand.equalsIgnoreCase("dissolve_ally")) {
            player.getClan().dissolveAlly(player);
        } else if (actualCommand.equalsIgnoreCase("dissolve_clan")) {
            dissolveClan(player, player.getClanId());
        } else if (actualCommand.equalsIgnoreCase("change_clan_leader")) {
            if (cmdParams.isEmpty()) {
                return;
            }

            if (!player.isClanLeader()) {
                player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
                return;
            }

            if (player.getName().equalsIgnoreCase(cmdParams)) {
                return;
            }

            final Clan clan = player.getClan();
            final ClanMember member = clan.getClanMember(cmdParams);
            if (member == null) {
                final SystemMessage sm = new SystemMessage(SystemMessageId.S1_DOES_NOT_EXIST);
                sm.addString(cmdParams);
                player.sendPacket(sm);
                return;
            }

            if (!member.isOnline()) {
                player.sendPacket(SystemMessageId.THAT_PLAYER_IS_NOT_CURRENTLY_ONLINE);
                return;
            }

            // To avoid clans with null clan leader, academy members shouldn't be eligible for clan leader.
            if (member.getPlayer().isAcademyMember()) {
                player.sendPacket(SystemMessageId.THAT_PRIVILEGE_CANNOT_BE_GRANTED_TO_A_CLAN_ACADEMY_MEMBER);
                return;
            }

            if (PlayerConfig.ALT_CLAN_LEADER_INSTANT_ACTIVATION) {
                clan.setNewLeader(member);
            } else {
                final NpcHtmlMessage msg = new NpcHtmlMessage(getObjectId());
                if (clan.getNewLeaderId() == 0) {
                    clan.setNewLeaderId(member.getObjectId(), true);
                    msg.setFile(player, "data/scripts/village_master/ClanMaster/9000-07-success.htm");
                } else {
                    msg.setFile(player, "data/scripts/village_master/ClanMaster/9000-07-in-progress.htm");
                }

                player.sendPacket(msg);
            }
        } else if (actualCommand.equalsIgnoreCase("cancel_clan_leader_change")) {
            if (!player.isClanLeader()) {
                player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
                return;
            }

            final Clan clan = player.getClan();
            final NpcHtmlMessage msg = new NpcHtmlMessage(getObjectId());
            if (clan.getNewLeaderId() != 0) {
                clan.setNewLeaderId(0, true);
                msg.setFile(player, "data/scripts/village_master/ClanMaster/9000-07-canceled.htm");
            } else {
                msg.setHtml(
                        "<html><body>You don't have clan leader delegation applications submitted yet!</body></html>");
            }

            player.sendPacket(msg);
        } else if (actualCommand.equalsIgnoreCase("recover_clan")) {
            recoverClan(player, player.getClanId());
        } else if (actualCommand.equalsIgnoreCase("increase_clan_level")) {
            if (player.getClan().levelUpClan(player)) {
                player.broadcastSkillPacket(new MagicSkillUse(player, 5103, 1, 0, 0), player);
                player.broadcastSkillPacket(new MagicSkillLaunched(player, 5103, 1), player);
            }
        } else if (actualCommand.equalsIgnoreCase("learn_clan_skills")) {
            showPledgeSkillList(player);
        } else if (command.startsWith("Subclass")) {
            if (player.isCastingNow() || player.isAllSkillsDisabled()) {
                player.sendPacket(SystemMessageId.SUBCLASSES_MAY_NOT_BE_CREATED_OR_CHANGED_WHILE_A_SKILL_IS_IN_USE);
                return;
            }

            if (OlympiadManager.getInstance().isRegisteredInComp(player)) {
                OlympiadManager.getInstance().unRegisterNoble(player);
            }

            final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());

            if (player.getTransformation() != null) {
                html.setFile(player, "data/html/villagemaster/SubClass_NoTransformed.htm");
                player.sendPacket(html);
                return;
            }

            if (player.hasSummon()) {
                html.setFile(player, "data/html/villagemaster/SubClass_NoSummon.htm");
                player.sendPacket(html);
                return;
            }

            if (!player.isInventoryUnder90(true)) {
                player.sendPacket(
                        SystemMessageId
                                .A_SUB_CLASS_CANNOT_BE_CREATED_OR_CHANGED_BECAUSE_YOU_HAVE_EXCEEDED_YOUR_INVENTORY_LIMIT);
                return;
            }

            if (player.getWeightPenalty() >= 2) {
                player.sendPacket(
                        SystemMessageId.A_SUB_CLASS_CANNOT_BE_CREATED_OR_CHANGED_WHILE_YOU_ARE_OVER_YOUR_WEIGHT_LIMIT);
                return;
            }

            int cmdChoice = 0;
            int paramOne = 0;
            int paramTwo = 0;
            try {
                cmdChoice = Integer.parseInt(command.substring(9, 10).trim());
                int endIndex = command.indexOf(' ', 11);
                if (endIndex == -1) {
                    endIndex = command.length();
                }

                if (command.length() > 11) {
                    paramOne = Integer.parseInt(command.substring(11, endIndex).trim());
                    if (command.length() > endIndex) {
                        paramTwo = Integer.parseInt(command.substring(endIndex).trim());
                    }
                }
            } catch (Exception nfe) {
                log.warn("{}: Wrong numeric values for command {}", VillageMaster.class.getName(), command);
            }

            Set<PlayerClass> subsAvailable = null;
            switch (cmdChoice) {
                case 0: {
                    html.setFile(player, getSubClassMenu(player.getRace()));
                    break;
                }
                case 1: {
                    if (player.getTotalSubClasses() >= PlayerConfig.MAX_SUBCLASS) {
                        html.setFile(player, getSubClassFail());
                        break;
                    }

                    subsAvailable = getAvailableSubClasses(player);
                    if ((subsAvailable != null) && !subsAvailable.isEmpty()) {
                        html.setFile(player, "data/html/villagemaster/SubClass_Add.htm");
                        final StringBuilder content1 = new StringBuilder(200);
                        for (PlayerClass subClass : subsAvailable) {
                            content1.append("<a action=\"bypass npc_%objectId%_Subclass 4 " + subClass.getId()
                                    + "\" msg=\"1268;"
                                    + ClassListData.getInstance()
                                            .getClass(subClass.getId())
                                            .className() + "\">"
                                    + ClassListData.getInstance()
                                            .getClass(subClass.getId())
                                            .className() + "</a><br>");
                        }

                        html.replace("%list%", content1.toString());
                    } else {
                        if ((player.getRace() == Race.ELF) || (player.getRace() == Race.DARK_ELF)) {
                            html.setFile(player, "data/html/villagemaster/SubClass_Fail_Elves.htm");
                            player.sendPacket(html);
                        } else if (player.getRace() == Race.KAMAEL) {
                            html.setFile(player, "data/html/villagemaster/SubClass_Fail_Kamael.htm");
                            player.sendPacket(html);
                        } else {
                            // TODO: Retail message
                            player.sendMessage("There are no sub classes available at this time.");
                        }
                        return;
                    }
                    break;
                }
                case 2: {
                    if (player.getSubClasses().isEmpty()) {
                        html.setFile(player, "data/html/villagemaster/SubClass_ChangeNo.htm");
                    } else {
                        final StringBuilder content2 = new StringBuilder(200);
                        if (checkVillageMaster(player.getBaseClass())) {
                            content2.append("<a action=\"bypass -h npc_%objectId%_Subclass 5 0\">"
                                    + ClassListData.getInstance()
                                            .getClass(player.getBaseClass())
                                            .className() + "</a><br>");
                        }

                        for (Iterator<SubClassHolder> subList = iterSubClasses(player); subList.hasNext(); ) {
                            final SubClassHolder subClass = subList.next();
                            if (checkVillageMaster(subClass.getPlayerClass())) {
                                content2.append("<a action=\"bypass -h npc_%objectId%_Subclass 5 "
                                        + subClass.getClassIndex() + "\">"
                                        + ClassListData.getInstance()
                                                .getClass(subClass.getId())
                                                .className() + "</a><br>");
                            }
                        }

                        if (!content2.isEmpty()) {
                            html.setFile(player, "data/html/villagemaster/SubClass_Change.htm");
                            html.replace("%list%", content2.toString());
                        } else {
                            html.setFile(player, "data/html/villagemaster/SubClass_ChangeNotFound.htm");
                        }
                    }
                    break;
                }
                case 3: {
                    if ((player.getSubClasses() == null)
                            || player.getSubClasses().isEmpty()) {
                        html.setFile(player, "data/html/villagemaster/SubClass_ModifyEmpty.htm");
                        break;
                    }

                    if (player.getTotalSubClasses() > 3) {
                        html.setFile(player, "data/html/villagemaster/SubClass_ModifyCustom.htm");
                        final StringBuilder content3 = new StringBuilder(200);
                        int classIndex = 1;
                        for (Iterator<SubClassHolder> subList = iterSubClasses(player); subList.hasNext(); ) {
                            final SubClassHolder subClass = subList.next();
                            content3.append(
                                    "Sub-class " + classIndex++ + "<br><a action=\"bypass -h npc_%objectId%_Subclass 6 "
                                            + subClass.getClassIndex() + "\">"
                                            + ClassListData.getInstance()
                                                    .getClass(subClass.getId())
                                                    .className() + "</a><br>");
                        }

                        html.replace("%list%", content3.toString());
                    } else {
                        // retail html contain only 3 subclasses
                        html.setFile(player, "data/html/villagemaster/SubClass_Modify.htm");
                        if (player.getSubClasses().containsKey(1)) {
                            html.replace(
                                    "%sub1%",
                                    ClassListData.getInstance()
                                            .getClass(player.getSubClasses()
                                                    .get(1)
                                                    .getId())
                                            .className());
                        } else {
                            html.replace("<a action=\"bypass npc_%objectId%_Subclass 6 1\">%sub1%</a><br>", "");
                        }

                        if (player.getSubClasses().containsKey(2)) {
                            html.replace(
                                    "%sub2%",
                                    ClassListData.getInstance()
                                            .getClass(player.getSubClasses()
                                                    .get(2)
                                                    .getId())
                                            .className());
                        } else {
                            html.replace("<a action=\"bypass npc_%objectId%_Subclass 6 2\">%sub2%</a><br>", "");
                        }

                        if (player.getSubClasses().containsKey(3)) {
                            html.replace(
                                    "%sub3%",
                                    ClassListData.getInstance()
                                            .getClass(player.getSubClasses()
                                                    .get(3)
                                                    .getId())
                                            .className());
                        } else {
                            html.replace("<a action=\"bypass npc_%objectId%_Subclass 6 3\">%sub3%</a><br>", "");
                        }
                    }
                    break;
                }
                case 4: {
                    if (!player.getClient().getFloodProtectors().canChangeSubclass()) {
                        return;
                    }

                    boolean allowAddition = true;
                    if (player.getTotalSubClasses() >= PlayerConfig.MAX_SUBCLASS) {
                        allowAddition = false;
                    }

                    if (player.getLevel() < 75) {
                        allowAddition = false;
                    }

                    if (allowAddition && !player.getSubClasses().isEmpty()) {
                        for (Iterator<SubClassHolder> subList = iterSubClasses(player); subList.hasNext(); ) {
                            final SubClassHolder subClass = subList.next();
                            if (subClass.getLevel() < 75) {
                                allowAddition = false;
                                break;
                            }
                        }
                    }

                    if (allowAddition && !PlayerConfig.ALT_GAME_SUBCLASS_WITHOUT_QUESTS) {
                        allowAddition = checkQuests(player);
                    }

                    if (allowAddition && isValidNewSubClass(player, paramOne)) {
                        if (!player.addSubClass(paramOne, player.getTotalSubClasses() + 1)) {
                            return;
                        }

                        player.setActiveClass(player.getTotalSubClasses());

                        html.setFile(player, "data/html/villagemaster/SubClass_AddOk.htm");
                        player.sendPacket(SystemMessageId.THE_NEW_SUBCLASS_HAS_BEEN_ADDED);
                    } else {
                        html.setFile(player, getSubClassFail());
                    }
                    break;
                }
                case 5: {
                    if (!player.getClient().getFloodProtectors().canChangeSubclass()) {
                        return;
                    }

                    if (player.getClassIndex() == paramOne) {
                        html.setFile(player, "data/html/villagemaster/SubClass_Current.htm");
                        break;
                    }

                    if (paramOne == 0) {
                        if (!checkVillageMaster(player.getBaseClass())) {
                            return;
                        }
                    } else {
                        try {
                            if (!checkVillageMaster(
                                    player.getSubClasses().get(paramOne).getPlayerClass())) {
                                return;
                            }
                        } catch (NullPointerException e) {
                            return;
                        }
                    }

                    player.setActiveClass(paramOne);
                    player.sendPacket(SystemMessageId.YOU_HAVE_SUCCESSFULLY_SWITCHED_TO_YOUR_SUBCLASS);
                    return;
                }
                case 6: {
                    if ((paramOne < 1) || (paramOne > PlayerConfig.MAX_SUBCLASS)) {
                        return;
                    }

                    subsAvailable = getAvailableSubClasses(player);

                    if ((subsAvailable == null) || subsAvailable.isEmpty()) {
                        // TODO: Retail message
                        player.sendMessage("There are no sub classes available at this time.");
                        return;
                    }

                    final StringBuilder content6 = new StringBuilder(200);
                    for (PlayerClass subClass : subsAvailable) {
                        content6.append("<a action=\"bypass npc_%objectId%_Subclass 7 " + paramOne + " "
                                + subClass.getId() + "\" msg=\"1445;\">"
                                + ClassListData.getInstance()
                                        .getClass(subClass.getId())
                                        .className() + "</a><br>");
                    }

                    switch (paramOne) {
                        case 1: {
                            html.setFile(player, "data/html/villagemaster/SubClass_ModifyChoice1.htm");
                            break;
                        }
                        case 2: {
                            html.setFile(player, "data/html/villagemaster/SubClass_ModifyChoice2.htm");
                            break;
                        }
                        case 3: {
                            html.setFile(player, "data/html/villagemaster/SubClass_ModifyChoice3.htm");
                            break;
                        }
                        default: {
                            html.setFile(player, "data/html/villagemaster/SubClass_ModifyChoice.htm");
                        }
                    }

                    html.replace("%list%", content6.toString());
                    break;
                }
                case 7: {
                    // The subclass entry is removed from the list even when the change fails.
                    if (!player.getClient().getFloodProtectors().canChangeSubclass()) {
                        return;
                    }

                    if (!isValidNewSubClass(player, paramTwo)) {
                        return;
                    }

                    if (player.modifySubClass(paramOne, paramTwo)) {
                        player.abortCast();
                        player.stopAllEffectsExceptThoseThatLastThroughDeath();
                        player.stopAllEffectsNotStayOnSubclassChange();
                        player.stopCubics();
                        player.setActiveClass(paramOne);

                        html.setFile(player, "data/html/villagemaster/SubClass_ModifyOk.htm");
                        html.replace(
                                "%name%",
                                ClassListData.getInstance().getClass(paramTwo).className());
                        player.sendPacket(SystemMessageId.THE_NEW_SUBCLASS_HAS_BEEN_ADDED);
                    } else {
                        // modifySubClass() dropped the subclass from memory; a stale class index throws
                        // IndexOutOfBoundsException.
                        player.setActiveClass(0);
                        player.sendMessage(
                                "The sub class could not be added, you have been reverted to your base class.");
                        return;
                    }
                    break;
                }
            }

            html.replace("%objectId%", String.valueOf(getObjectId()));
            player.sendPacket(html);
        } else {
            super.onBypassFeedback(player, command);
        }
    }

    protected String getSubClassMenu(Race race) {
        if (PlayerConfig.ALT_GAME_SUBCLASS_EVERYWHERE || (race != Race.KAMAEL)) {
            return "data/html/villagemaster/SubClass.htm";
        }

        return "data/html/villagemaster/SubClass_NoOther.htm";
    }

    protected String getSubClassFail() {
        return "data/html/villagemaster/SubClass_Fail.htm";
    }

    protected boolean checkQuests(Player player) {
        if (player.isNoble()) {
            return true;
        }

        QuestState qs = player.getQuestState("Q00234_FatesWhisper");
        if ((qs == null) || !qs.isCompleted()) {
            return false;
        }

        qs = player.getQuestState("Q00235_MimirsElixir");
        if ((qs == null) || !qs.isCompleted()) {
            return false;
        }

        return true;
    }

    private Set<PlayerClass> getAvailableSubClasses(Player player) {
        final int currentBaseId = player.getBaseClass();
        final PlayerClass baseCID = PlayerClass.getPlayerClass(currentBaseId);

        final int baseClassId;
        if (baseCID.level() > 2) {
            baseClassId = baseCID.getParent().getId();
        } else {
            baseClassId = currentBaseId;
        }

        final Set<PlayerClass> availSubs = getSubclasses(player, baseClassId);
        if ((availSubs != null) && !availSubs.isEmpty()) {
            for (Iterator<PlayerClass> availSub = availSubs.iterator(); availSub.hasNext(); ) {
                final PlayerClass pclass = availSub.next();

                if (!checkVillageMaster(pclass)) {
                    availSub.remove();
                    continue;
                }

                final int availClassId = pclass.getId();
                final PlayerClass cid = PlayerClass.getPlayerClass(availClassId);
                SubClassHolder prevSubClass;
                PlayerClass subClassId;
                for (Iterator<SubClassHolder> subList = iterSubClasses(player); subList.hasNext(); ) {
                    prevSubClass = subList.next();
                    subClassId = PlayerClass.getPlayerClass(prevSubClass.getId());
                    if (subClassId.equalsOrChildOf(cid)) {
                        availSub.remove();
                        break;
                    }
                }
            }
        }

        return availSubs;
    }

    public Set<PlayerClass> getSubclasses(Player player, int classId) {
        WorldPartition.checkOwner();
        Set<PlayerClass> subclasses = null;
        final PlayerClass pClass = PlayerClass.getPlayerClass(classId);
        if (CategoryData.getInstance().isInCategory(CategoryType.THIRD_CLASS_GROUP, classId)
                || (CategoryData.getInstance().isInCategory(CategoryType.FOURTH_CLASS_GROUP, classId))) {
            subclasses = EnumSet.copyOf(mainSubclassSet);
            subclasses.remove(pClass);

            if (player.getRace() == Race.KAMAEL) {
                for (PlayerClass cid : PlayerClass.values()) {
                    if (cid.getRace() != Race.KAMAEL) {
                        subclasses.remove(cid);
                    }
                }

                if (player.getAppearance().isFemale()) {
                    subclasses.remove(PlayerClass.MALE_SOULBREAKER);
                } else {
                    subclasses.remove(PlayerClass.FEMALE_SOULBREAKER);
                }

                if (!player.getSubClasses().containsKey(2)
                        || (player.getSubClasses().get(2).getLevel() < 75)) {
                    subclasses.remove(PlayerClass.INSPECTOR);
                }
            } else {
                if (player.getRace() == Race.ELF) {
                    for (PlayerClass cid : PlayerClass.values()) {
                        if (cid.getRace() == Race.DARK_ELF) {
                            subclasses.remove(cid);
                        }
                    }
                } else if (player.getRace() == Race.DARK_ELF) {
                    for (PlayerClass cid : PlayerClass.values()) {
                        if (cid.getRace() == Race.ELF) {
                            subclasses.remove(cid);
                        }
                    }
                }

                for (PlayerClass cid : PlayerClass.values()) {
                    if (cid.getRace() == Race.KAMAEL) {
                        subclasses.remove(cid);
                    }
                }
            }

            final Set<PlayerClass> unavailableClasses = subclassSetMap.get(pClass);
            if (unavailableClasses != null) {
                subclasses.removeAll(unavailableClasses);
            }
        }

        if (subclasses != null) {
            final PlayerClass currClassId = player.getPlayerClass();
            for (PlayerClass tempClass : subclasses) {
                if (currClassId.equalsOrChildOf(tempClass)) {
                    subclasses.remove(tempClass);
                }
            }
        }

        return subclasses;
    }

    private boolean isValidNewSubClass(Player player, int classId) {
        if (!checkVillageMaster(classId)) {
            return false;
        }

        final PlayerClass cid = PlayerClass.getPlayerClass(classId);
        SubClassHolder sub;
        PlayerClass subClassId;
        for (Iterator<SubClassHolder> subList = iterSubClasses(player); subList.hasNext(); ) {
            sub = subList.next();
            subClassId = PlayerClass.getPlayerClass(sub.getId());
            if (subClassId.equalsOrChildOf(cid)) {
                return false;
            }
        }

        final int currentBaseId = player.getBaseClass();
        final PlayerClass baseCID = PlayerClass.getPlayerClass(currentBaseId);

        final int baseClassId;
        if (baseCID.level() > 2) {
            baseClassId = baseCID.getParent().getId();
        } else {
            baseClassId = currentBaseId;
        }

        final Set<PlayerClass> availSubs = getSubclasses(player, baseClassId);
        if ((availSubs == null) || availSubs.isEmpty()) {
            return false;
        }

        boolean found = false;
        for (PlayerClass pclass : availSubs) {
            if (pclass.getId() == classId) {
                found = true;
                break;
            }
        }

        return found;
    }

    protected boolean checkVillageMasterRace(PlayerClass pClass) {
        return true;
    }

    protected boolean checkVillageMasterTeachType(PlayerClass pClass) {
        return true;
    }

    public boolean checkVillageMaster(int classId) {
        WorldPartition.checkOwner();
        return checkVillageMaster(PlayerClass.getPlayerClass(classId));
    }

    public boolean checkVillageMaster(PlayerClass pclass) {
        WorldPartition.checkOwner();
        if (PlayerConfig.ALT_GAME_SUBCLASS_EVERYWHERE) {
            return true;
        }

        return checkVillageMasterRace(pclass) && checkVillageMasterTeachType(pclass);
    }

    private static Iterator<SubClassHolder> iterSubClasses(Player player) {
        return player.getSubClasses().values().iterator();
    }

    private void dissolveClan(Player player, int clanId) {
        if (!player.isClanLeader()) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return;
        }

        final Clan clan = player.getClan();
        if (clan.getAllyId() != 0) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_DISPERSE_THE_CLANS_IN_YOUR_ALLIANCE);
            return;
        }

        if (clan.isAtWar()) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_DISSOLVE_A_CLAN_WHILE_ENGAGED_IN_A_WAR);
            return;
        }

        if ((clan.getCastleId() != 0) || (clan.getHideoutId() != 0) || (clan.getFortId() != 0)) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_DISSOLVE_A_CLAN_WHILE_OWNING_A_CLAN_HALL_OR_CASTLE);
            return;
        }

        for (Castle castle : CastleManager.getInstance().getCastles()) {
            if (SiegeManager.getInstance().checkIsRegistered(clan, castle.getResidenceId())) {
                player.sendPacket(
                        SystemMessageId.YOU_CANNOT_DISSOLVE_A_CLAN_DURING_A_SIEGE_OR_WHILE_PROTECTING_A_CASTLE);
                return;
            }
        }

        for (Fort fort : FortManager.getInstance().getForts()) {
            if (FortSiegeManager.getInstance().checkIsRegistered(clan, fort.getResidenceId())) {
                player.sendPacket(
                        SystemMessageId.YOU_CANNOT_DISSOLVE_A_CLAN_DURING_A_SIEGE_OR_WHILE_PROTECTING_A_CASTLE);
                return;
            }
        }

        if (player.isInsideZone(ZoneId.SIEGE)) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_DISSOLVE_A_CLAN_DURING_A_SIEGE_OR_WHILE_PROTECTING_A_CASTLE);
            return;
        }

        if (clan.getDissolvingExpiryTime() > ClockBridge.wallMillis()) {
            player.sendPacket(SystemMessageId.YOU_HAVE_ALREADY_REQUESTED_THE_DISSOLUTION_OF_YOUR_CLAN);
            return;
        }

        clan.setDissolvingExpiryTime(ClockBridge.wallMillis() + (PlayerConfig.ALT_CLAN_DISSOLVE_DAYS * 86400000));
        clan.updateClanInDB();

        // The clan leader should take the XP penalty of a full death.
        player.calculateDeathExpPenalty(null, false);
        ClanTable.getInstance().scheduleRemoveClan(clan.getId());
    }

    private void recoverClan(Player player, int clanId) {
        if (!player.isClanLeader()) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return;
        }

        final Clan clan = player.getClan();
        clan.setDissolvingExpiryTime(0);
        clan.updateClanInDB();
    }

    private void createSubPledge(Player player, String clanName, String leaderName, int pledgeType, int minClanLvl) {
        if (!player.isClanLeader()) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return;
        }

        final Clan clan = player.getClan();
        if (clan.getLevel() < minClanLvl) {
            if (pledgeType == Clan.SUBUNIT_ACADEMY) {
                player.sendPacket(SystemMessageId.TO_ESTABLISH_A_CLAN_ACADEMY_YOUR_CLAN_MUST_BE_LEVEL_5_OR_HIGHER);
            } else {
                player.sendPacket(SystemMessageId.THE_CONDITIONS_NECESSARY_TO_CREATE_A_MILITARY_UNIT_HAVE_NOT_BEEN_MET);
            }

            return;
        }

        if (!StringUtils.isAlphanumeric(clanName) || !isValidName(clanName) || (2 > clanName.length())) {
            player.sendPacket(SystemMessageId.CLAN_NAME_IS_INVALID);
            return;
        }

        if (clanName.length() > 16) {
            player.sendPacket(SystemMessageId.CLAN_NAME_S_LENGTH_IS_INCORRECT);
            return;
        }

        for (Clan tempClan : ClanTable.getInstance().getClans()) {
            if (tempClan.getSubPledge(clanName) != null) {
                if (pledgeType == Clan.SUBUNIT_ACADEMY) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.S1_ALREADY_EXISTS);
                    sm.addString(clanName);
                    player.sendPacket(sm);
                } else {
                    player.sendPacket(
                            SystemMessageId
                                    .ANOTHER_MILITARY_UNIT_IS_ALREADY_USING_THAT_NAME_PLEASE_ENTER_A_DIFFERENT_NAME);
                }

                return;
            }
        }

        if (pledgeType != Clan.SUBUNIT_ACADEMY) {
            final ClanMember member = clan.getClanMember(leaderName);
            if ((member == null)
                    || (member.getPledgeType() != 0)
                    || (clan.getLeaderSubPledge(member.getObjectId()) > 0)) {
                if (pledgeType >= Clan.SUBUNIT_KNIGHT1) {
                    player.sendPacket(SystemMessageId.THE_CAPTAIN_OF_THE_ORDER_OF_KNIGHTS_CANNOT_BE_APPOINTED);
                } else if (pledgeType >= Clan.SUBUNIT_ROYAL1) {
                    player.sendPacket(SystemMessageId.THE_CAPTAIN_OF_THE_ROYAL_GUARD_CANNOT_BE_APPOINTED);
                }

                return;
            }
        }

        final int leaderId = pledgeType != Clan.SUBUNIT_ACADEMY
                ? clan.getClanMember(leaderName).getObjectId()
                : 0;
        if (clan.createSubPledge(player, pledgeType, leaderId, clanName) == null) {
            return;
        }

        SystemMessage sm;
        if (pledgeType == Clan.SUBUNIT_ACADEMY) {
            sm = new SystemMessage(SystemMessageId.CONGRATULATIONS_THE_S1_S_CLAN_ACADEMY_HAS_BEEN_CREATED);
            sm.addString(player.getClan().getName());
        } else if (pledgeType >= Clan.SUBUNIT_KNIGHT1) {
            sm = new SystemMessage(SystemMessageId.THE_KNIGHTS_OF_S1_HAVE_BEEN_CREATED);
            sm.addString(player.getClan().getName());
        } else if (pledgeType >= Clan.SUBUNIT_ROYAL1) {
            sm = new SystemMessage(SystemMessageId.THE_ROYAL_GUARD_OF_S1_HAVE_BEEN_CREATED);
            sm.addString(player.getClan().getName());
        } else {
            sm = new SystemMessage(SystemMessageId.YOUR_CLAN_HAS_BEEN_CREATED);
        }

        player.sendPacket(sm);

        if (pledgeType != Clan.SUBUNIT_ACADEMY) {
            final ClanMember leaderSubPledge = clan.getClanMember(leaderName);
            final Player leaderPlayer = leaderSubPledge.getPlayer();
            if (leaderPlayer != null) {
                leaderPlayer.setPledgeClass(ClanMember.calculatePledgeClass(leaderPlayer));
                leaderPlayer.updateUserInfo();
            }
        }
    }

    private void renameSubPledge(Player player, int pledgeType, String pledgeName) {
        if (!player.isClanLeader()) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return;
        }

        final SubPledge subPledge = player.getClan().getSubPledge(pledgeType);
        if (subPledge == null) {
            player.sendMessage("Pledge don't exists.");
            return;
        }

        if (!StringUtils.isAlphanumeric(pledgeName) || !isValidName(pledgeName) || (2 > pledgeName.length())) {
            player.sendPacket(SystemMessageId.CLAN_NAME_IS_INVALID);
            return;
        }

        if (pledgeName.length() > 16) {
            player.sendPacket(SystemMessageId.CLAN_NAME_S_LENGTH_IS_INCORRECT);
            return;
        }

        subPledge.setName(pledgeName);
        final Clan clan = player.getClan();
        clan.updateSubPledgeInDB(subPledge.getId());
        clan.broadcastClanStatus();
        player.sendMessage("Pledge name changed.");
    }

    private void assignSubPledgeLeader(Player player, String clanName, String leaderName) {
        if (!player.isClanLeader()) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return;
        }

        if (leaderName.length() > 16) {
            player.sendPacket(SystemMessageId.YOUR_TITLE_CANNOT_EXCEED_16_CHARACTERS_IN_LENGTH_PLEASE_TRY_AGAIN);
            return;
        }

        if (player.getName().equals(leaderName)) {
            player.sendPacket(SystemMessageId.THE_CAPTAIN_OF_THE_ROYAL_GUARD_CANNOT_BE_APPOINTED);
            return;
        }

        final SubPledge subPledge = player.getClan().getSubPledge(clanName);
        if ((subPledge == null) || (subPledge.getId() == Clan.SUBUNIT_ACADEMY)) {
            player.sendPacket(SystemMessageId.CLAN_NAME_IS_INVALID);
            return;
        }

        final Clan clan = player.getClan();
        final ClanMember member = clan.getClanMember(leaderName);
        if ((member == null) || (member.getPledgeType() != 0) || (clan.getLeaderSubPledge(member.getObjectId()) > 0)) {
            if (subPledge.getId() >= Clan.SUBUNIT_KNIGHT1) {
                player.sendPacket(SystemMessageId.THE_CAPTAIN_OF_THE_ORDER_OF_KNIGHTS_CANNOT_BE_APPOINTED);
            } else if (subPledge.getId() >= Clan.SUBUNIT_ROYAL1) {
                player.sendPacket(SystemMessageId.THE_CAPTAIN_OF_THE_ROYAL_GUARD_CANNOT_BE_APPOINTED);
            }

            return;
        }

        subPledge.setLeaderId(member.getObjectId());
        clan.updateSubPledgeInDB(subPledge.getId());

        final Player leaderPlayer = member.getPlayer();
        if (leaderPlayer != null) {
            leaderPlayer.setPledgeClass(ClanMember.calculatePledgeClass(leaderPlayer));
            leaderPlayer.updateUserInfo();
        }

        clan.broadcastClanStatus();
        final SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_BEEN_SELECTED_AS_THE_CAPTAIN_OF_S2);
        sm.addString(leaderName);
        sm.addString(clanName);
        clan.broadcastToOnlineMembers(sm);
    }

    public static void showPledgeSkillList(Player player) {
        WorldPartition.checkOwner();
        if (!player.isClanLeader()) {
            final NpcHtmlMessage html = new NpcHtmlMessage();
            html.setFile(player, "data/html/villagemaster/NotClanLeader.htm");
            player.sendPacket(html);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final Clan clan = player.getClan();
        final List<SkillLearn> skills = SkillTreeData.getInstance().getAvailablePledgeSkills(clan);
        final AcquireSkillList asl = new AcquireSkillList(AcquireSkillType.PLEDGE);
        int counts = 0;
        for (SkillLearn s : skills) {
            asl.addSkill(
                    s.getSkillId(),
                    s.getSkillLevel(),
                    s.getSkillLevel(),
                    s.getLevelUpSp(),
                    s.getSocialClass().ordinal());
            counts++;
        }

        if (counts == 0) {
            if (clan.getLevel() < 8) {
                final SystemMessage sm = new SystemMessage(
                        SystemMessageId
                                .YOU_DO_NOT_HAVE_ANY_FURTHER_SKILLS_TO_LEARN_COME_BACK_WHEN_YOU_HAVE_REACHED_LEVEL_S1);
                if (clan.getLevel() < 5) {
                    sm.addInt(5);
                } else {
                    sm.addInt(clan.getLevel() + 1);
                }

                player.sendPacket(sm);
            } else {
                final NpcHtmlMessage html = new NpcHtmlMessage();
                html.setFile(player, "data/html/villagemaster/NoMoreSkills.htm");
                player.sendPacket(html);
            }
        } else {
            player.sendPacket(asl);
        }

        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    private static boolean isValidName(String name) {
        return ServerConfig.CLAN_NAME_TEMPLATE_PATTERN.matcher(name).matches();
    }
}
