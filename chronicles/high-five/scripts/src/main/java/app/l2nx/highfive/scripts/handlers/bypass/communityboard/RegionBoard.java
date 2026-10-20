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
package app.l2nx.highfive.scripts.handlers.bypass.communityboard;

import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.residences.AuctionableHall;
import app.l2nx.highfive.handler.CommunityBoardHandler;
import app.l2nx.highfive.handler.IWriteBoardHandler;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Region board.
 * @author Zoey76, Skache
 */
@Slf4j
public class RegionBoard implements IWriteBoardHandler {
    private static final DateTimeFormatter SIEGE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // Region data
    // @formatter:off
    private static final int[] REGIONS = {1049, 1052, 1053, 1057, 1060, 1059, 1248, 1247, 1056};
    // @formatter:on
    private static final String[] COMMANDS = {"_bbsloc"};

    @Override
    public String[] getCommandList() {
        return COMMANDS;
    }

    @Override
    public boolean onCommand(String command, Player player) {
        if (command.equals("_bbsloc")) {
            CommunityBoardHandler.getInstance().addBypass(player, "Region", command);

            final String list = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/region_list.html");
            final StringBuilder sb = new StringBuilder();
            final List<Castle> castles = CastleManager.getInstance().getCastles();

            for (int i = 0; i < REGIONS.length; i++) {
                final Castle castle = castles.get(i);
                final Clan clan = ClanTable.getInstance().getClan(castle.getOwnerId());

                String link = list.replace("%region_id%", String.valueOf(i))
                        .replace("%region_name%", String.valueOf(REGIONS[i]))
                        .replace(
                                "%region_owning_clan%",
                                (clan != null
                                        ? "<a action=\"bypass _bbsclan_clanhome;" + clan.getId() + "\">"
                                                + clan.getName() + "</a>"
                                        : "NPC"))
                        .replace(
                                "%region_owning_clan_alliance%",
                                ((clan != null) && (clan.getAllyName() != null) ? clan.getAllyName() : "None"))
                        .replace("%region_tax_rate%", (castle.getTaxRate() * 100) + "%");
                sb.append(link);
            }

            String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/region.html");
            html = html.replace("%region_list%", sb.toString());
            CommunityBoardHandler.separateAndSend(html, player);
        } else if (command.startsWith("_bbsloc;")) {
            CommunityBoardHandler.getInstance().addBypass(player, "Region>", command);

            final String id = command.replace("_bbsloc;", "");
            if (!StringUtils.isNumeric(id)) {
                log.warn("{} sent an invalid region bypass {}!", player, command);
                return false;
            }

            final int regionId = Integer.parseInt(id);
            final List<Castle> castles = CastleManager.getInstance().getCastles();
            if ((regionId < 0) || (regionId >= castles.size())) {
                log.warn("{} requested invalid region ID {}!", player, regionId);
                return false;
            }

            final Castle castle = castles.get(regionId);
            final Clan clan = ClanTable.getInstance().getClan(castle.getOwnerId());
            String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/region_show.html");
            html = html.replace("%regionName%", castle.getName())
                    .replace("%tax%", (castle.getTaxRate() * 100) + "%")
                    .replace("%lord%", clan != null ? clan.getLeaderName() : "NPC")
                    .replace(
                            "%clanName%",
                            (clan != null
                                    ? "<a action=\"bypass _bbsclan_clanhome;" + clan.getId() + "\">" + clan.getName()
                                            + "</a>"
                                    : "NPC"))
                    .replace(
                            "%allyName%",
                            ((clan != null) && (clan.getAllyName() != null) ? clan.getAllyName() : "None"))
                    .replace(
                            "%siegeDate%",
                            SIEGE_FORMAT.format(
                                    Instant.ofEpochMilli(castle.getSiegeDate().getTimeInMillis())
                                            .atZone(ClockBridge.wallClock().getZone())));

            final StringBuilder hallsList = new StringBuilder();
            hallsList.append(
                    "<br><br><table width=610 bgcolor=A7A19A><tr><td width=5></td><td width=200>Clan Hall Name</td><td"
                            + " width=200>Owning Clan</td><td width=200>Clan Leader Name</td><td"
                            + " width=5></td></tr></table><br1>");
            final List<AuctionableHall> clanHalls = getClanHallsByLocation(castle.getName());
            if (clanHalls.isEmpty()) {
                hallsList.append("<table width=610><tr><td align=\"left\">There are no clan halls in this"
                        + " region.</td></tr></table>");
            } else {
                for (AuctionableHall hall : clanHalls) {
                    Clan hallOwner = ClanTable.getInstance().getClan(hall.getOwnerId());
                    hallsList
                            .append("<table><tr><td width=5></td><td width=200>")
                            .append(hall.getName())
                            .append("</td><td width=200>")
                            .append(
                                    hallOwner != null
                                            ? "<a action=\"bypass _bbsclan_clanhome;" + hallOwner.getId() + "\">"
                                                    + hallOwner.getName() + "</a>"
                                            : "None")
                            .append("</td><td width=200>")
                            .append(hallOwner != null ? hallOwner.getLeaderName() : "None")
                            .append("</td><td width=5></td></tr></table><br1><img src=\"L2UI.Squaregray\" width=605"
                                    + " height=1><br1>");
                }
            }

            html = html.replace("%hallsList%", hallsList.toString());
            CommunityBoardHandler.separateAndSend(html, player);
        }

        return true;
    }

    private static List<AuctionableHall> getClanHallsByLocation(String location) {
        final List<AuctionableHall> clanHalls = new ArrayList<>();
        for (AuctionableHall hall :
                ClanHallTable.getInstance().getAllAuctionableClanHalls().values()) {
            if (hall.getLocation().equalsIgnoreCase(location)) {
                clanHalls.add(hall);
            }
        }

        clanHalls.sort(Comparator.comparingInt(AuctionableHall::getId));
        return clanHalls;
    }

    @Override
    public boolean writeCommunityBoardCommand(
            Player player, String arg1, String arg2, String arg3, String arg4, String arg5) {
        // TODO: Implement.
        return false;
    }
}
