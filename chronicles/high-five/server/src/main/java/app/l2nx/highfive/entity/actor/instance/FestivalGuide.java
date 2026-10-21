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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.mechanics.sevensigns.SevenSigns;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.NpcHtmlMessage;
import lombok.Getter;

/**
 * Festival of Darkness Guide (Seven Signs)
 * @author Tempy
 */
public class FestivalGuide extends Npc {
    @Getter
    private final int festivalType;

    @Getter
    private final int festivalOracle;

    private final int blueStonesNeeded;
    private final int greenStonesNeeded;
    private final int redStonesNeeded;

    /**
     * Creates a festival guide.
     * @param template the festival guide NPC template
     */
    public FestivalGuide(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.FestivalGuide);

        switch (getId()) {
            case 31127:
            case 31132: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_31;
                festivalOracle = SevenSigns.CABAL_DAWN;
                blueStonesNeeded = 900;
                greenStonesNeeded = 540;
                redStonesNeeded = 270;
                break;
            }
            case 31128:
            case 31133: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_42;
                festivalOracle = SevenSigns.CABAL_DAWN;
                blueStonesNeeded = 1500;
                greenStonesNeeded = 900;
                redStonesNeeded = 450;
                break;
            }
            case 31129:
            case 31134: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_53;
                festivalOracle = SevenSigns.CABAL_DAWN;
                blueStonesNeeded = 3000;
                greenStonesNeeded = 1800;
                redStonesNeeded = 900;
                break;
            }
            case 31130:
            case 31135: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_64;
                festivalOracle = SevenSigns.CABAL_DAWN;
                blueStonesNeeded = 4500;
                greenStonesNeeded = 2700;
                redStonesNeeded = 1350;
                break;
            }
            case 31131:
            case 31136: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_NONE;
                festivalOracle = SevenSigns.CABAL_DAWN;
                blueStonesNeeded = 6000;
                greenStonesNeeded = 3600;
                redStonesNeeded = 1800;
                break;
            }
            case 31137:
            case 31142: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_31;
                festivalOracle = SevenSigns.CABAL_DUSK;
                blueStonesNeeded = 900;
                greenStonesNeeded = 540;
                redStonesNeeded = 270;
                break;
            }
            case 31138:
            case 31143: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_42;
                festivalOracle = SevenSigns.CABAL_DUSK;
                blueStonesNeeded = 1500;
                greenStonesNeeded = 900;
                redStonesNeeded = 450;
                break;
            }
            case 31139:
            case 31144: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_53;
                festivalOracle = SevenSigns.CABAL_DUSK;
                blueStonesNeeded = 3000;
                greenStonesNeeded = 1800;
                redStonesNeeded = 900;
                break;
            }
            case 31140:
            case 31145: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_64;
                festivalOracle = SevenSigns.CABAL_DUSK;
                blueStonesNeeded = 4500;
                greenStonesNeeded = 2700;
                redStonesNeeded = 1350;
                break;
            }
            case 31141:
            case 31146: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_NONE;
                festivalOracle = SevenSigns.CABAL_DUSK;
                blueStonesNeeded = 6000;
                greenStonesNeeded = 3600;
                redStonesNeeded = 1800;
                break;
            }
            default: {
                festivalType = SevenSignsFestival.FESTIVAL_LEVEL_MAX_NONE;
                festivalOracle = SevenSigns.CABAL_NULL;
                blueStonesNeeded = 0;
                greenStonesNeeded = 0;
                redStonesNeeded = 0;
            }
        }
    }

    public int getStoneCount(int stoneType) {
        switch (stoneType) {
            case SevenSigns.SEAL_STONE_BLUE_ID: {
                return blueStonesNeeded;
            }
            case SevenSigns.SEAL_STONE_GREEN_ID: {
                return greenStonesNeeded;
            }
            case SevenSigns.SEAL_STONE_RED_ID: {
                return redStonesNeeded;
            }
            default: {
                return -1;
            }
        }
    }

    public void showChatWindow(Player player, int value, String suffix, boolean isDescription) {
        String filename = SevenSigns.SEVEN_SIGNS_HTML_PATH + "festival/";
        filename += (isDescription) ? "desc_" : "festival_";
        filename += (suffix != null) ? value + suffix + ".htm" : value + ".htm";

        // Send a Server->Client NpcHtmlMessage containing the text of the Npc to the Player
        final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
        html.setFile(player, filename);
        html.replace("%objectId%", String.valueOf(getObjectId()));
        html.replace("%festivalType%", SevenSignsFestival.getFestivalName(festivalType));
        html.replace(
                "%cycleMins%", String.valueOf(SevenSignsFestival.getInstance().getMinsToNextCycle()));
        if (!isDescription && "2b".equals(value + suffix)) {
            html.replace("%minFestivalPartyMembers%", String.valueOf(FeatureConfig.ALT_FESTIVAL_MIN_PLAYER));
        }

        // If the stats or bonus table is required, construct them.
        if (value == 5) {
            html.replace("%statsTable%", getStatsTable());
        }

        if (value == 6) {
            html.replace("%bonusTable%", getBonusTable());
        }

        // festival's fee
        if (value == 1) {
            html.replace("%blueStoneNeeded%", String.valueOf(blueStonesNeeded));
            html.replace("%greenStoneNeeded%", String.valueOf(greenStonesNeeded));
            html.replace("%redStoneNeeded%", String.valueOf(redStonesNeeded));
        }

        player.sendPacket(html);

        // Send a Server->Client ActionFailed to the Player in order to avoid that the client wait another packet
        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    private static String getStatsTable() {
        final StringBuilder tableHtml = new StringBuilder(1000);

        // Get the scores for each of the festival level ranges (types).
        for (int i = 0; i < 5; i++) {
            final int dawnScore = SevenSignsFestival.getInstance().getHighestScore(SevenSigns.CABAL_DAWN, i);
            final int duskScore = SevenSignsFestival.getInstance().getHighestScore(SevenSigns.CABAL_DUSK, i);
            final String festivalName = SevenSignsFestival.getFestivalName(i);
            String winningCabal = "Children of Dusk";
            if (dawnScore > duskScore) {
                winningCabal = "Children of Dawn";
            } else if (dawnScore == duskScore) {
                winningCabal = "None";
            }

            tableHtml.append("<tr><td width=\"100\" align=\"center\">" + festivalName
                    + "</td><td align=\"center\" width=\"35\">" + duskScore + "</td><td align=\"center\" width=\"35\">"
                    + dawnScore + "</td><td align=\"center\" width=\"130\">" + winningCabal + "</td></tr>");
        }

        return tableHtml.toString();
    }

    private static String getBonusTable() {
        final StringBuilder tableHtml = new StringBuilder(500);

        // Get the accumulated scores for each of the festival level ranges (types).
        for (int i = 0; i < 5; i++) {
            final int accumScore = SevenSignsFestival.getInstance().getAccumulatedBonus(i);
            final String festivalName = SevenSignsFestival.getFestivalName(i);
            tableHtml.append("<tr><td align=\"center\" width=\"150\">" + festivalName
                    + "</td><td align=\"center\" width=\"150\">" + accumScore + "</td></tr>");
        }

        return tableHtml.toString();
    }
}
