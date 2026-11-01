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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.config.custom.SellBuffsConfig;
import app.l2nx.highfive.data.holders.SellBuffHolder;
import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.handler.CommunityBoardHandler;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPrivateStoreSetWholeMsg;
import app.l2nx.highfive.util.FormatUtil;
import app.l2nx.highfive.util.HtmlUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @author St3eT
 */
@Slf4j
public class SellBuffsManager implements IXmlReader {

    private static final Set<Integer> ALLOWED_BUFFS = new HashSet<>();
    private static final String HTML_FOLDER = "data/html/mods/SellBuffs/";

    protected SellBuffsManager() {
        load();
    }

    @Override
    public void load() {
        if (SellBuffsConfig.SELLBUFF_ENABLED) {
            ALLOWED_BUFFS.clear();
            parseDatapackFile("data/SellBuffData.xml");
            log.info("Loaded {} allowed buffs.", ALLOWED_BUFFS.size());
        }
    }

    @Override
    public void parseDocument(Document document, File file) {
        final NodeList node = document.getDocumentElement().getElementsByTagName("skill");
        for (int i = 0; i < node.getLength(); ++i) {
            final Element elem = (Element) node.item(i);
            final int skillId = Integer.parseInt(elem.getAttribute("id"));
            ALLOWED_BUFFS.add(skillId);
        }
    }

    public void sendSellMenu(Player player) {
        final String html = HtmCache.getInstance()
                .getHtm(player, HTML_FOLDER + (player.isSellingBuffs() ? "BuffMenu_already.html" : "BuffMenu.html"));
        CommunityBoardHandler.separateAndSend(html, player);
    }

    public void sendBuffChoiceMenu(Player player, int index) {
        String html = HtmCache.getInstance().getHtm(player, HTML_FOLDER + "BuffChoice.html");
        html = html.replace("%list%", buildSkillMenu(player, index));
        CommunityBoardHandler.separateAndSend(html, player);
    }

    public void sendBuffEditMenu(Player player) {
        String html = HtmCache.getInstance().getHtm(player, HTML_FOLDER + "BuffChoice.html");
        html = html.replace("%list%", buildEditMenu(player));
        CommunityBoardHandler.separateAndSend(html, player);
    }

    public void sendBuffMenu(Player player, Player seller, int index) {
        if (!seller.isSellingBuffs() || !seller.hasSellingBuffs()) {
            return;
        }

        String html = HtmCache.getInstance().getHtm(player, HTML_FOLDER + "BuffBuyMenu.html");
        html = html.replace("%list%", buildBuffMenu(seller, index));
        CommunityBoardHandler.separateAndSend(html, player);
    }

    public void startSellBuffs(Player player, String title) {
        player.sitDown();
        player.setSellingBuffs(true);
        player.setPrivateStoreType(PrivateStoreType.PACKAGE_SELL);
        player.getSellList().setTitle(title);
        player.getSellList().setPackaged(true);
        player.broadcastUserInfo();
        player.broadcastPacket(new ExPrivateStoreSetWholeMsg(player));
        sendSellMenu(player);
    }

    public void stopSellBuffs(Player player) {
        player.setSellingBuffs(false);
        player.setPrivateStoreType(PrivateStoreType.NONE);
        player.standUp();
        player.broadcastUserInfo();
        sendSellMenu(player);
    }

    private String buildBuffMenu(Player seller, int index) {
        final int ceiling = 10;
        int nextIndex = -1;
        int previousIndex = -1;
        int emptyFields = 0;
        final StringBuilder sb = new StringBuilder();
        final List<SellBuffHolder> sellList = new ArrayList<>();

        int count = 0;
        for (SellBuffHolder holder : seller.getSellingBuffs()) {
            count++;
            if ((count > index) && (count <= (ceiling + index))) {
                sellList.add(holder);
            }
        }

        if ((count > 10) && (count > (index + 10))) {
            nextIndex = index + 10;
        }

        if (index >= 10) {
            previousIndex = index - 10;
        }

        emptyFields = ceiling - sellList.size();

        sb.append("<br>");
        sb.append(HtmlUtil.getMpGauge(250, (long) seller.getCurrentMp(), seller.getMaxMp(), false));
        sb.append("<br>");

        sb.append("<table border=0 cellpadding=0 cellspacing=0 background=\"L2UI_CH3.refinewnd_back_Pattern\">");
        sb.append("<tr><td><br><br><br></td></tr>");
        sb.append("<tr>");
        sb.append("<td fixwidth=\"20\"></td>");
        sb.append("<td> <button action=\"\" value=\"Icon\" width=75 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Name\" width=155 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Level\" width=85 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"MP Cost\" width=100 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Price\" width=140 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Player\" width=90 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\""
                + " fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\"> </td>");
        sb.append(
                "<td> <button action=\"\" value=\"Pet\" width=90 height=23 back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\""
                        + " fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\"> </td>");
        sb.append("<td fixwidth=\"20\"></td>");
        sb.append("<tr><td><br></td></tr>");
        sb.append("</tr>");

        for (SellBuffHolder holder : sellList) {
            final Skill skill = seller.getKnownSkill(holder.getSkillId());
            if (skill == null) {
                emptyFields++;
                continue;
            }

            final ItemTemplate item = ItemData.getInstance().getTemplate(SellBuffsConfig.SELLBUFF_PAYMENT_ID);

            sb.append("<tr>");
            sb.append("<td fixwidth=\"20\"></td>");
            sb.append("<td align=center><img src=\"" + skill.getIcon() + "\" width=\"32\" height=\"32\"></td>");
            sb.append("<td align=center>" + skill.getName()
                    + (skill.getLevel() > 100
                            ? "<font color=\"LEVEL\"> + " + (skill.getLevel() % 100) + "</font></td>"
                            : "</td>"));
            sb.append("<td align=center>"
                    + ((skill.getLevel() > 100) ? SkillData.getInstance().getMaxLevel(skill.getId()) : skill.getLevel())
                    + "</td>");
            sb.append("<td align=center> <font color=\"1E90FF\">"
                    + (skill.getMpConsume() * SellBuffsConfig.SELLBUFF_MP_MULTIPLER) + "</font></td>");
            sb.append("<td align=center> " + FormatUtil.formatAdena(holder.getPrice()) + " <font color=\"LEVEL\"> "
                    + (item != null ? item.getName() : "") + "</font> </td>");
            sb.append("<td align=center fixwidth=\"50\"><button value=\"Buy Buff\" action=\"bypass -h sellbuffbuyskill "
                    + seller.getObjectId() + " " + skill.getId() + " " + index
                    + "\" width=\"85\" height=\"26\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
            sb.append("<td align=center fixwidth=\"50\"><button value=\"Buy Buff\" action=\"bypass -h"
                    + " sellbuffbuyskillPet "
                    + seller.getObjectId() + " " + skill.getId() + " " + index
                    + "\" width=\"85\" height=\"26\" back=\"L2UI_ct1.button_df\""
                    + " fore=\"L2UI_ct1.button_df\"></td>");
            sb.append("</tr>");
            sb.append("<tr><td><br><br></td></tr>");
        }

        for (int i = 0; i < emptyFields; i++) {
            sb.append("<tr>");
            sb.append("<td fixwidth=\"20\" height=\"32\"></td>");
            sb.append("<td align=center></td>");
            sb.append("<td align=left></td>");
            sb.append("<td align=center></td>");
            sb.append("<td align=center></font></td>");
            sb.append("<td align=center></td>");
            sb.append("<td align=center fixwidth=\"50\"></td>");
            sb.append("</tr>");
            sb.append("<tr><td><br><br></td></tr>");
        }

        sb.append("</table>");

        sb.append("<table width=\"250\" border=\"0\">");
        sb.append("<tr>");

        if (previousIndex > -1) {
            sb.append("<td align=left><button value=\"Previous Page\" action=\"bypass -h sellbuffbuymenu "
                    + seller.getObjectId() + " " + previousIndex
                    + "\" width=\"100\" height=\"30\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        }

        if (nextIndex > -1) {
            sb.append("<td align=right><button value=\"Next Page\" action=\"bypass -h sellbuffbuymenu "
                    + seller.getObjectId() + " " + nextIndex
                    + "\" width=\"100\" height=\"30\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        }
        sb.append("</tr>");
        sb.append("</table>");
        return sb.toString();
    }

    private String buildEditMenu(Player player) {
        final StringBuilder sb = new StringBuilder();

        sb.append("<table border=0 cellpadding=0 cellspacing=0 background=\"L2UI_CH3.refinewnd_back_Pattern\">");
        sb.append("<tr><td><br><br><br></td></tr>");
        sb.append("<tr>");
        sb.append("<td fixwidth=\"10\"></td>");
        sb.append("<td> <button action=\"\" value=\"Icon\" width=75 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Name\" width=150 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Level\" width=75 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Old Price\" width=100 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\""
                + " fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\"> </td>");
        sb.append("<td> <button action=\"\" value=\"New Price\" width=125 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\""
                + " fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\"> </td>");
        sb.append("<td> <button action=\"\" value=\"Action\" width=125 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\""
                + " fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\"> </td>");
        sb.append("<td> <button action=\"\" value=\"Remove\" width=85 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\""
                + " fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\"> </td>");
        sb.append("<td fixwidth=\"20\"></td>");
        sb.append("</tr>");

        if (!player.hasSellingBuffs()) {
            sb.append("</table>");
            sb.append("<br><br><br>");
            sb.append("You don't have added any buffs yet!");
        } else {
            for (SellBuffHolder holder : player.getSellingBuffs()) {
                final Skill skill = player.getKnownSkill(holder.getSkillId());
                if (skill == null) {
                    continue;
                }

                sb.append("<tr>");
                sb.append("<td fixwidth=\"20\"></td>");
                sb.append("<td align=center><img src=\"" + skill.getIcon() + "\" width=\"32\" height=\"32\"></td>");
                sb.append("<td align=left>" + skill.getName()
                        + (skill.getLevel() > 100
                                ? "<font color=\"LEVEL\"> + " + (skill.getLevel() % 100) + "</font></td>"
                                : "</td>"));
                sb.append("<td align=center>"
                        + ((skill.getLevel() > 100)
                                ? SkillData.getInstance().getMaxLevel(skill.getId())
                                : skill.getLevel())
                        + "</td>");
                sb.append("<td align=center> " + FormatUtil.formatAdena(holder.getPrice()) + " </td>");
                sb.append("<td align=center><edit var=\"price_" + skill.getId() + "\" width=120 type=\"number\"></td>");
                sb.append("<td align=center><button value=\"Edit\" action=\"bypass -h sellbuffchangeprice "
                        + skill.getId() + " $price_" + skill.getId()
                        + "\" width=\"85\" height=\"26\" back=\"L2UI_ct1.button_df\""
                        + " fore=\"L2UI_ct1.button_df\"></td>");
                sb.append("<td align=center><button value=\" X \" action=\"bypass -h sellbuffremove " + skill.getId()
                        + "\" width=\"26\" height=\"26\" back=\"L2UI_ct1.button_df\""
                        + " fore=\"L2UI_ct1.button_df\"></td>");
                sb.append("</tr>");
                sb.append("<tr><td><br><br></td></tr>");
            }
            sb.append("</table>");
        }

        return sb.toString();
    }

    private String buildSkillMenu(Player player, int index) {
        final int ceiling = index + 10;
        int nextIndex = -1;
        int previousIndex = -1;
        final StringBuilder sb = new StringBuilder();
        final List<Skill> skillList = new ArrayList<>();

        int count = 0;
        for (Skill skill : player.getAllSkills()) {
            if (ALLOWED_BUFFS.contains(skill.getId()) && !isInSellList(player, skill)) {
                count++;

                if ((count > index) && (count <= ceiling)) {
                    skillList.add(skill);
                }
            }
        }

        if ((count > 10) && (count > (index + 10))) {
            nextIndex = index + 10;
        }

        if (index >= 10) {
            previousIndex = index - 10;
        }

        sb.append("<table border=0 cellpadding=0 cellspacing=0 background=\"L2UI_CH3.refinewnd_back_Pattern\">");
        sb.append("<tr><td><br><br><br></td></tr>");
        sb.append("<tr>");
        sb.append("<td fixwidth=\"10\"></td>");
        sb.append("<td> <button action=\"\" value=\"Icon\" width=100 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Name\" width=175 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Level\" width=150 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Price\" width=150 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\">"
                + " </td>");
        sb.append("<td> <button action=\"\" value=\"Action\" width=125 height=23"
                + " back=\"L2UI_CT1.OlympiadWnd_DF_Watch_Down\""
                + " fore=\"L2UI_CT1.OlympiadWnd_DF_Watch\"> </td>");
        sb.append("<td fixwidth=\"20\"></td>");
        sb.append("</tr>");

        if (skillList.isEmpty()) {
            sb.append("</table>");
            sb.append("<br><br><br>");
            sb.append("At this moment you cannot add any buffs!");
        } else {
            for (Skill skill : skillList) {
                sb.append("<tr>");
                sb.append("<td fixwidth=\"20\"></td>");
                sb.append("<td align=center><img src=\"" + skill.getIcon() + "\" width=\"32\" height=\"32\"></td>");
                sb.append("<td align=left>" + skill.getName()
                        + (skill.getLevel() > 100
                                ? "<font color=\"LEVEL\"> + " + (skill.getLevel() % 100) + "</font></td>"
                                : "</td>"));
                sb.append("<td align=center>"
                        + ((skill.getLevel() > 100)
                                ? SkillData.getInstance().getMaxLevel(skill.getId())
                                : skill.getLevel())
                        + "</td>");
                sb.append("<td align=center><edit var=\"price_" + skill.getId() + "\" width=120 type=\"number\"></td>");
                sb.append("<td align=center fixwidth=\"50\"><button value=\"Add Buff\" action=\"bypass -h"
                        + " sellbuffaddskill "
                        + skill.getId() + " $price_" + skill.getId()
                        + "\" width=\"85\" height=\"26\" back=\"L2UI_ct1.button_df\""
                        + " fore=\"L2UI_ct1.button_df\"></td>");
                sb.append("</tr>");
                sb.append("<tr><td><br><br></td></tr>");
            }
            sb.append("</table>");
        }

        sb.append("<table width=\"250\" border=\"0\">");
        sb.append("<tr>");

        if (previousIndex > -1) {
            sb.append("<td align=left><button value=\"Previous Page\" action=\"bypass -h sellbuffadd " + previousIndex
                    + "\" width=\"100\" height=\"30\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        }

        if (nextIndex > -1) {
            sb.append("<td align=right><button value=\"Next Page\" action=\"bypass -h sellbuffadd " + nextIndex
                    + "\" width=\"100\" height=\"30\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        }
        sb.append("</tr>");
        sb.append("</table>");
        return sb.toString();
    }

    public boolean isInSellList(Player player, Skill skill) {
        for (SellBuffHolder holder : player.getSellingBuffs()) {
            if (holder.getSkillId() == skill.getId()) {
                return true;
            }
        }

        return false;
    }

    public boolean canStartSellBuffs(Player player) {
        if (player.isAlikeDead()) {
            player.sendMessage("You can't sell buffs in fake death!");
            return false;
        } else if (player.isInOlympiadMode() || OlympiadManager.getInstance().isRegistered(player)) {
            player.sendMessage("You can't sell buffs with Olympiad status!");
            return false;
        } else if (player.isRegisteredOnEvent()) {
            player.sendMessage("You can't sell buffs while registered in an event!");
            return false;
        } else if (player.isCursedWeaponEquipped() || (player.getKarma() > 0)) {
            player.sendMessage("You can't sell buffs in Chaotic state!");
            return false;
        } else if (player.isInDuel()) {
            player.sendMessage("You can't sell buffs in Duel state!");
            return false;
        } else if (player.isFishing()) {
            player.sendMessage("You can't sell buffs while fishing.");
            return false;
        } else if (player.isMounted() || player.isFlyingMounted() || player.isFlying()) {
            player.sendMessage("You can't sell buffs in Mount state!");
            return false;
        } else if (player.isTransformed()) {
            player.sendMessage("You can't sell buffs in Transform state!");
            return false;
        } else if (player.isInsideZone(ZoneId.NO_STORE) || !player.isInsideZone(ZoneId.PEACE) || player.isJailed()) {
            player.sendMessage("You can't sell buffs here!");
            return false;
        }

        return true;
    }

    public static SellBuffsManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final SellBuffsManager INSTANCE = new SellBuffsManager();
    }
}
