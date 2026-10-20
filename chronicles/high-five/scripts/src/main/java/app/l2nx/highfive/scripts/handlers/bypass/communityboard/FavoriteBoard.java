/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
package app.l2nx.highfive.scripts.handlers.bypass.communityboard;

import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.CommunityBoardHandler;
import app.l2nx.highfive.handler.IParseBoardHandler;
import app.l2nx.highfive.managers.BbsFavoriteManager;
import app.l2nx.highfive.repository.forum.model.BbsFavorite;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;

/**
 * Favorite board.
 * @author Zoey76
 */
@Slf4j
public class FavoriteBoard implements IParseBoardHandler {
    private static final DateTimeFormatter FAVORITE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] COMMANDS = {"_bbsgetfav", "bbs_add_fav", "_bbsdelfav_"};

    @Override
    public String[] getCommandList() {
        return COMMANDS;
    }

    @Override
    public boolean onCommand(String command, Player player) {
        // None of this commands can be added to favorites.
        if (command.startsWith("_bbsgetfav")) {
            // Load Favorite links
            final String list = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/favorite_list.html");
            final StringBuilder sb = new StringBuilder();
            try {
                for (BbsFavorite favorite : BbsFavoriteManager.getInstance().findFavorites(player.getObjectId())) {
                    String link = list.replace("%fav_bypass%", favorite.bypass());
                    link = link.replace("%fav_title%", favorite.title());
                    link = link.replace(
                            "%fav_add_date%",
                            FAVORITE_FORMAT.format(favorite.addedAt()
                                    .atZone(ClockBridge.wallClock().getZone())));
                    link = link.replace("%fav_id%", String.valueOf(favorite.id()));
                    sb.append(link);
                }

                String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/favorite.html");
                html = html.replace("%fav_list%", sb.toString());
                CommunityBoardHandler.separateAndSend(html, player);
            } catch (RuntimeException e) {
                log.warn("Couldn't load favorite links for {}", player);
            }
        } else if (command.startsWith("bbs_add_fav")) {
            final String bypass = CommunityBoardHandler.getInstance().removeBypass(player);
            if (bypass != null) {
                final String[] parts = bypass.split("&", 2);
                if (parts.length != 2) {
                    log.warn("Couldn't add favorite link, {} it's not a valid bypass!", bypass);
                    return false;
                }

                final String title = parts[0].trim();
                final String link = parts[1].trim();
                if (title.isEmpty() || link.isEmpty()) {
                    log.warn("Couldn't add favorite link, {} has no title or no bypass!", bypass);
                    return false;
                }

                try {
                    BbsFavoriteManager.getInstance()
                            .addFavorite(
                                    player.getObjectId(),
                                    title,
                                    link,
                                    ClockBridge.wallClock().instant());

                    // Callback
                    onCommand("_bbsgetfav", player);
                } catch (RuntimeException e) {
                    log.warn("Couldn't add favorite link {} for {}", bypass, player);
                }
            }
        } else if (command.startsWith("_bbsdelfav_")) {
            final String favId = command.replace("_bbsdelfav_", "");
            final int favoriteId;
            try {
                favoriteId = Integer.parseInt(favId);
            } catch (NumberFormatException e) {
                log.warn("Couldn't delete favorite link, {} it's not a valid ID!", favId);
                return false;
            }

            try {
                BbsFavoriteManager.getInstance().deleteFavorite(player.getObjectId(), favoriteId);

                // Callback
                onCommand("_bbsgetfav", player);
            } catch (RuntimeException e) {
                log.warn("Couldn't delete favorite link ID {} for {}", favId, player);
            }
        }

        return true;
    }
}
