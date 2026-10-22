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
package app.l2nx.highfive.mechanics.sevensigns;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.ai.Intention;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.instance.FestivalMonster;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.groups.PartyMessageType;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillUse;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.sevensigns.SevenSignsFestivalScoreRepository;
import app.l2nx.highfive.repository.sevensigns.SevenSignsStatusRepository;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsFestivalScore;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsStatus;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Seven Signs Festival of Darkness Engine.<br>
 * TODO:<br>
 * <ul>
 * <li>Archer mobs should target healer characters over other party members.</li>
 * <li>Added 29 Sep: Players that leave a party during the Seven Signs Festival will now take damage and cannot be healed.</li>
 * </ul>
 * @author Tempy
 */
@Slf4j
@Component
public class SevenSignsFestival {
    private static volatile SevenSignsFestival instance;

    /**
     * These length settings are important! :)<br>
     * All times are relative to the ELAPSED time (in ms) since a festival begins.<br>
     * Festival manager start is the time after the server starts to begin the first festival cycle.<br>
     * The cycle length should ideally be at least 2x longer than the festival length.<br>
     * This allows ample time for players to sign-up to participate in the festival.<br>
     * The intermission is the time between the festival participants being moved to the "arenas" and the spawning of the first set of mobs.<br>
     * The monster swarm time is the time before the monsters swarm to the center of the arena, after they are spawned.<br>
     * The chest spawn time is for when the bonus festival chests spawn, usually towards the end of the festival.
     */
    // Read from the config on use: a constant would freeze whatever the config held when the class first loaded.
    public static long festivalSignupTime() {
        return FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH - FeatureConfig.ALT_FESTIVAL_LENGTH - 60000;
    }

    // Key Constants \\
    private static final int FESTIVAL_MAX_OFFSET_X = 230;
    private static final int FESTIVAL_MAX_OFFSET_Y = 230;
    private static final int FESTIVAL_DEFAULT_RESPAWN = 60; // Specify in seconds!

    public static final int FESTIVAL_COUNT = 5; // do not change without correcting db and SevenSigns itself !
    public static final int FESTIVAL_LEVEL_MAX_31 = 0;
    public static final int FESTIVAL_LEVEL_MAX_42 = 1;
    public static final int FESTIVAL_LEVEL_MAX_53 = 2;
    public static final int FESTIVAL_LEVEL_MAX_64 = 3;
    public static final int FESTIVAL_LEVEL_MAX_NONE = 4;

    // 500 maximum possible score
    public static final int[] FESTIVAL_LEVEL_SCORES = {60, 70, 100, 120, 150};

    public static final int FESTIVAL_OFFERING_ID = 5901;
    public static final int FESTIVAL_OFFERING_VALUE = 5;

    /**
     * The following contains all the necessary spawn data for:<br>
     * <ul>
     * <li>Player Start Locations
     * <li>
     * <li>Witches
     * <li>
     * <li>Monsters
     * <li>
     * <li>Chests</li>
     * </ul>
     * All data is given by: X, Y, Z (coords), Heading, NPC ID (if necessary).<br>
     * This may be moved externally in time, but the data should not change.
     */
    // @formatter:off
    public static final int[][] FESTIVAL_DAWN_PLAYER_SPAWNS = {
        {-79187, 113186, -4895, 0}, // 31 and below
        {-75918, 110137, -4895, 0}, // 42 and below
        {-73835, 111969, -4895, 0}, // 53 and below
        {-76170, 113804, -4895, 0}, // 64 and below
        {-78927, 109528, -4895, 0} // No level limit
    };

    public static final int[][] FESTIVAL_DUSK_PLAYER_SPAWNS = {
        {-77200, 88966, -5151, 0}, // 31 and below
        {-76941, 85307, -5151, 0}, // 42 and below
        {-74855, 87135, -5151, 0}, // 53 and below
        {-80208, 88222, -5151, 0}, // 64 and below
        {-79954, 84697, -5151, 0} // No level limit
    };

    protected static final int[][] FESTIVAL_DAWN_WITCH_SPAWNS = {
        {-79183, 113052, -4891, 0, 31132}, // 31 and below
        {-75916, 110270, -4891, 0, 31133}, // 42 and below
        {-73979, 111970, -4891, 0, 31134}, // 53 and below
        {-76174, 113663, -4891, 0, 31135}, // 64 and below
        {-78930, 109664, -4891, 0, 31136} // No level limit
    };

    protected static final int[][] FESTIVAL_DUSK_WITCH_SPAWNS = {
        {-77199, 88830, -5147, 0, 31142}, // 31 and below
        {-76942, 85438, -5147, 0, 31143}, // 42 and below
        {-74990, 87135, -5147, 0, 31144}, // 53 and below
        {-80207, 88222, -5147, 0, 31145}, // 64 and below
        {-79952, 84833, -5147, 0, 31146} // No level limit
    };

    protected static final int[][][] FESTIVAL_DAWN_PRIMARY_SPAWNS = {
        {
            /* Level 31 and Below - Offering of the Branded */
            {-78537, 113839, -4895, -1, 18009},
            {-78466, 113852, -4895, -1, 18010},
            {-78509, 113899, -4895, -1, 18010},
            {-78481, 112557, -4895, -1, 18009},
            {-78559, 112504, -4895, -1, 18010},
            {-78489, 112494, -4895, -1, 18010},
            {-79803, 112543, -4895, -1, 18012},
            {-79854, 112492, -4895, -1, 18013},
            {-79886, 112557, -4895, -1, 18014},
            {-79821, 113811, -4895, -1, 18015},
            {-79857, 113896, -4895, -1, 18017},
            {-79878, 113816, -4895, -1, 18018},

            // Archers and Marksmen \\
            {-79190, 113660, -4895, -1, 18011},
            {-78710, 113188, -4895, -1, 18011},
            {-79190, 112730, -4895, -1, 18016},
            {-79656, 113188, -4895, -1, 18016}
        },
        {
            /* Level 42 and Below - Apostate Offering */
            {-76558, 110784, -4895, -1, 18019},
            {-76607, 110815, -4895, -1, 18020}, // South West
            {-76559, 110820, -4895, -1, 18020},
            {-75277, 110792, -4895, -1, 18019},
            {-75225, 110801, -4895, -1, 18020}, // South East
            {-75262, 110832, -4895, -1, 18020},
            {-75249, 109441, -4895, -1, 18022},
            {-75278, 109495, -4895, -1, 18023}, // North East
            {-75223, 109489, -4895, -1, 18024},
            {-76556, 109490, -4895, -1, 18025},
            {-76607, 109469, -4895, -1, 18027}, // North West
            {-76561, 109450, -4895, -1, 18028},
            // Archers and Marksmen \\
            {-76399, 110144, -4895, -1, 18021},
            {-75912, 110606, -4895, -1, 18021},
            {-75444, 110144, -4895, -1, 18026},
            {-75930, 109665, -4895, -1, 18026}
        },
        {
            /* Level 53 and Below - Witch's Offering */
            {-73184, 111319, -4895, -1, 18029},
            {-73135, 111294, -4895, -1, 18030}, // South West
            {-73185, 111281, -4895, -1, 18030},
            {-74477, 111321, -4895, -1, 18029},
            {-74523, 111293, -4895, -1, 18030}, // South East
            {-74481, 111280, -4895, -1, 18030},
            {-74489, 112604, -4895, -1, 18032},
            {-74491, 112660, -4895, -1, 18033}, // North East
            {-74527, 112629, -4895, -1, 18034},
            {-73197, 112621, -4895, -1, 18035},
            {-73142, 112631, -4895, -1, 18037}, // North West
            {-73182, 112656, -4895, -1, 18038},

            // Archers and Marksmen \\
            {-73834, 112430, -4895, -1, 18031},
            {-74299, 111959, -4895, -1, 18031},
            {-73841, 111491, -4895, -1, 18036},
            {-73363, 111959, -4895, -1, 18036}
        },
        {
            /* Level 64 and Below - Dark Omen Offering */
            {-75543, 114461, -4895, -1, 18039},
            {-75514, 114493, -4895, -1, 18040}, // South West
            {-75488, 114456, -4895, -1, 18040},
            {-75521, 113158, -4895, -1, 18039},
            {-75504, 113110, -4895, -1, 18040}, // South East
            {-75489, 113142, -4895, -1, 18040},
            {-76809, 113143, -4895, -1, 18042},
            {-76860, 113138, -4895, -1, 18043}, // North East
            {-76831, 113112, -4895, -1, 18044},
            {-76831, 114441, -4895, -1, 18045},
            {-76840, 114490, -4895, -1, 18047}, // North West
            {-76864, 114455, -4895, -1, 18048},

            // Archers and Marksmen \\
            {-75703, 113797, -4895, -1, 18041},
            {-76180, 114263, -4895, -1, 18041},
            {-76639, 113797, -4895, -1, 18046},
            {-76180, 113337, -4895, -1, 18046}
        },
        {
            /* No Level Limit - Offering of Forbidden Path */
            {-79576, 108881, -4895, -1, 18049},
            {-79592, 108835, -4895, -1, 18050}, // South West
            {-79614, 108871, -4895, -1, 18050},
            {-79586, 110171, -4895, -1, 18049},
            {-79589, 110216, -4895, -1, 18050}, // South East
            {-79620, 110177, -4895, -1, 18050},
            {-78825, 110182, -4895, -1, 18052},
            {-78238, 110182, -4895, -1, 18053}, // North East
            {-78266, 110218, -4895, -1, 18054},
            {-78275, 108883, -4895, -1, 18055},
            {-78267, 108839, -4895, -1, 18057}, // North West
            {-78241, 108871, -4895, -1, 18058},

            // Archers and Marksmen \\
            {-79394, 109538, -4895, -1, 18051},
            {-78929, 109992, -4895, -1, 18051},
            {-78454, 109538, -4895, -1, 18056},
            {-78929, 109053, -4895, -1, 18056}
        }
    };

    protected static final int[][][] FESTIVAL_DUSK_PRIMARY_SPAWNS = {
        {
            /* Level 31 and Below - Offering of the Branded */
            {-76542, 89653, -5151, -1, 18009},
            {-76509, 89637, -5151, -1, 18010},
            {-76548, 89614, -5151, -1, 18010},
            {-76539, 88326, -5151, -1, 18009},
            {-76512, 88289, -5151, -1, 18010},
            {-76546, 88287, -5151, -1, 18010},
            {-77879, 88308, -5151, -1, 18012},
            {-77886, 88310, -5151, -1, 18013},
            {-77879, 88278, -5151, -1, 18014},
            {-77857, 89605, -5151, -1, 18015},
            {-77858, 89658, -5151, -1, 18017},
            {-77891, 89633, -5151, -1, 18018},

            // Archers and Marksmen \\
            {-76728, 88962, -5151, -1, 18011},
            {-77194, 88494, -5151, -1, 18011},
            {-77660, 88896, -5151, -1, 18016},
            {-77195, 89438, -5151, -1, 18016}
        },
        {
            /* Level 42 and Below - Apostate's Offering */
            {-77585, 84650, -5151, -1, 18019},
            {-77628, 84643, -5151, -1, 18020},
            {-77607, 84613, -5151, -1, 18020},
            {-76603, 85946, -5151, -1, 18019},
            {-77606, 85994, -5151, -1, 18020},
            {-77638, 85959, -5151, -1, 18020},
            {-76301, 85960, -5151, -1, 18022},
            {-76257, 85972, -5151, -1, 18023},
            {-76286, 85992, -5151, -1, 18024},
            {-76281, 84667, -5151, -1, 18025},
            {-76291, 84611, -5151, -1, 18027},
            {-76257, 84616, -5151, -1, 18028},

            // Archers and Marksmen \\
            {-77419, 85307, -5151, -1, 18021},
            {-76952, 85768, -5151, -1, 18021},
            {-76477, 85312, -5151, -1, 18026},
            {-76942, 84832, -5151, -1, 18026}
        },
        {
            /* Level 53 and Below - Witch's Offering */
            {-74211, 86494, -5151, -1, 18029},
            {-74200, 86449, -5151, -1, 18030},
            {-74167, 86464, -5151, -1, 18030},
            {-75495, 86482, -5151, -1, 18029},
            {-75540, 86473, -5151, -1, 18030},
            {-75509, 86445, -5151, -1, 18030},
            {-75509, 87775, -5151, -1, 18032},
            {-75518, 87826, -5151, -1, 18033},
            {-75542, 87780, -5151, -1, 18034},
            {-74214, 87789, -5151, -1, 18035},
            {-74169, 87801, -5151, -1, 18037},
            {-74198, 87827, -5151, -1, 18038},

            // Archers and Marksmen \\
            {-75324, 87135, -5151, -1, 18031},
            {-74852, 87606, -5151, -1, 18031},
            {-74388, 87146, -5151, -1, 18036},
            {-74856, 86663, -5151, -1, 18036}
        },
        {
            /* Level 64 and Below - Dark Omen Offering */
            {-79560, 89007, -5151, -1, 18039},
            {-79521, 89016, -5151, -1, 18040},
            {-79544, 89047, -5151, -1, 18040},
            {-79552, 87717, -5151, -1, 18039},
            {-79552, 87673, -5151, -1, 18040},
            {-79510, 87702, -5151, -1, 18040},
            {-80866, 87719, -5151, -1, 18042},
            {-80897, 87689, -5151, -1, 18043},
            {-80850, 87685, -5151, -1, 18044},
            {-80848, 89013, -5151, -1, 18045},
            {-80887, 89051, -5151, -1, 18047},
            {-80891, 89004, -5151, -1, 18048},

            // Archers and Marksmen \\
            {-80205, 87895, -5151, -1, 18041},
            {-80674, 88350, -5151, -1, 18041},
            {-80209, 88833, -5151, -1, 18046},
            {-79743, 88364, -5151, -1, 18046}
        },
        {
            /* No Level Limit - Offering of Forbidden Path */
            {-80624, 84060, -5151, -1, 18049},
            {-80621, 84007, -5151, -1, 18050},
            {-80590, 84039, -5151, -1, 18050},
            {-80605, 85349, -5151, -1, 18049},
            {-80639, 85363, -5151, -1, 18050},
            {-80611, 85385, -5151, -1, 18050},
            {-79311, 85353, -5151, -1, 18052},
            {-79277, 85384, -5151, -1, 18053},
            {-79273, 85539, -5151, -1, 18054},
            {-79297, 84054, -5151, -1, 18055},
            {-79285, 84006, -5151, -1, 18057},
            {-79260, 84040, -5151, -1, 18058},

            // Archers and Marksmen \\
            {-79945, 85171, -5151, -1, 18051},
            {-79489, 84707, -5151, -1, 18051},
            {-79952, 84222, -5151, -1, 18056},
            {-80423, 84703, -5151, -1, 18056}
        }
    };

    protected static final int[][][] FESTIVAL_DAWN_SECONDARY_SPAWNS = {
        {
            /* 31 and Below */
            {-78757, 112834, -4895, -1, 18016},
            {-78581, 112834, -4895, -1, 18016},
            {-78822, 112526, -4895, -1, 18011},
            {-78822, 113702, -4895, -1, 18011},
            {-78822, 113874, -4895, -1, 18011},
            {-79524, 113546, -4895, -1, 18011},
            {-79693, 113546, -4895, -1, 18011},
            {-79858, 113546, -4895, -1, 18011},
            {-79545, 112757, -4895, -1, 18016},
            {-79545, 112586, -4895, -1, 18016},
        },
        {
            /* 42 and Below */
            {-75565, 110580, -4895, -1, 18026},
            {-75565, 110740, -4895, -1, 18026},
            {-75577, 109776, -4895, -1, 18021},
            {-75413, 109776, -4895, -1, 18021},
            {-75237, 109776, -4895, -1, 18021},
            {-76274, 109468, -4895, -1, 18021},
            {-76274, 109635, -4895, -1, 18021},
            {-76274, 109795, -4895, -1, 18021},
            {-76351, 110500, -4895, -1, 18056},
            {-76528, 110500, -4895, -1, 18056},
        },
        {
            /* 53 and Below */
            {-74191, 111527, -4895, -1, 18036},
            {-74191, 111362, -4895, -1, 18036},
            {-73495, 111611, -4895, -1, 18031},
            {-73327, 111611, -4895, -1, 18031},
            {-73154, 111611, -4895, -1, 18031},
            {-73473, 112301, -4895, -1, 18031},
            {-73473, 112475, -4895, -1, 18031},
            {-73473, 112649, -4895, -1, 18031},
            {-74270, 112326, -4895, -1, 18036},
            {-74443, 112326, -4895, -1, 18036},
        },
        {
            /* 64 and Below */
            {-75738, 113439, -4895, -1, 18046},
            {-75571, 113439, -4895, -1, 18046},
            {-75824, 114141, -4895, -1, 18041},
            {-75824, 114309, -4895, -1, 18041},
            {-75824, 114477, -4895, -1, 18041},
            {-76513, 114158, -4895, -1, 18041},
            {-76683, 114158, -4895, -1, 18041},
            {-76857, 114158, -4895, -1, 18041},
            {-76535, 113357, -4895, -1, 18056},
            {-76535, 113190, -4895, -1, 18056},
        },
        {
            /* No Level Limit */
            {-79350, 109894, -4895, -1, 18056},
            {-79534, 109894, -4895, -1, 18056},
            {-79285, 109187, -4895, -1, 18051},
            {-79285, 109019, -4895, -1, 18051},
            {-79285, 108860, -4895, -1, 18051},
            {-78587, 109172, -4895, -1, 18051},
            {-78415, 109172, -4895, -1, 18051},
            {-78249, 109172, -4895, -1, 18051},
            {-78575, 109961, -4895, -1, 18056},
            {-78575, 110130, -4895, -1, 18056},
        }
    };

    protected static final int[][][] FESTIVAL_DUSK_SECONDARY_SPAWNS = {
        {
            /* 31 and Below */
            {-76844, 89304, -5151, -1, 18011},
            {-76844, 89479, -5151, -1, 18011},
            {-76844, 89649, -5151, -1, 18011},
            {-77544, 89326, -5151, -1, 18011},
            {-77716, 89326, -5151, -1, 18011},
            {-77881, 89326, -5151, -1, 18011},
            {-77561, 88530, -5151, -1, 18016},
            {-77561, 88364, -5151, -1, 18016},
            {-76762, 88615, -5151, -1, 18016},
            {-76594, 88615, -5151, -1, 18016},
        },
        {
            /* 42 and Below */
            {-77307, 84969, -5151, -1, 18021},
            {-77307, 84795, -5151, -1, 18021},
            {-77307, 84623, -5151, -1, 18021},
            {-76614, 84944, -5151, -1, 18021},
            {-76433, 84944, -5151, -1, 18021},
            {-76251, 84944, -5151, -1, 18021},
            {-76594, 85745, -5151, -1, 18026},
            {-76594, 85910, -5151, -1, 18026},
            {-77384, 85660, -5151, -1, 18026},
            {-77555, 85660, -5151, -1, 18026},
        },
        {
            /* 53 and Below */
            {-74517, 86782, -5151, -1, 18031},
            {-74344, 86782, -5151, -1, 18031},
            {-74185, 86782, -5151, -1, 18031},
            {-74496, 87464, -5151, -1, 18031},
            {-74496, 87636, -5151, -1, 18031},
            {-74496, 87815, -5151, -1, 18031},
            {-75298, 87497, -5151, -1, 18036},
            {-75460, 87497, -5151, -1, 18036},
            {-75219, 86712, -5151, -1, 18036},
            {-75219, 86531, -5151, -1, 18036},
        },
        {
            /* 64 and Below */
            {-79851, 88703, -5151, -1, 18041},
            {-79851, 88868, -5151, -1, 18041},
            {-79851, 89040, -5151, -1, 18041},
            {-80548, 88722, -5151, -1, 18041},
            {-80711, 88722, -5151, -1, 18041},
            {-80883, 88722, -5151, -1, 18041},
            {-80565, 87916, -5151, -1, 18046},
            {-80565, 87752, -5151, -1, 18046},
            {-79779, 87996, -5151, -1, 18046},
            {-79613, 87996, -5151, -1, 18046},
        },
        {
            /* No Level Limit */
            {-79271, 84330, -5151, -1, 18051},
            {-79448, 84330, -5151, -1, 18051},
            {-79601, 84330, -5151, -1, 18051},
            {-80311, 84367, -5151, -1, 18051},
            {-80311, 84196, -5151, -1, 18051},
            {-80311, 84015, -5151, -1, 18051},
            {-80556, 85049, -5151, -1, 18056},
            {-80384, 85049, -5151, -1, 18056},
            {-79598, 85127, -5151, -1, 18056},
            {-79598, 85303, -5151, -1, 18056},
        }
    };

    protected static final int[][][] FESTIVAL_DAWN_CHEST_SPAWNS = {
        {
            /* Level 31 and Below */
            {-78999, 112957, -4927, -1, 18109},
            {-79153, 112873, -4927, -1, 18109},
            {-79256, 112873, -4927, -1, 18109},
            {-79368, 112957, -4927, -1, 18109},
            {-79481, 113124, -4927, -1, 18109},
            {-79481, 113275, -4927, -1, 18109},
            {-79364, 113398, -4927, -1, 18109},
            {-79213, 113500, -4927, -1, 18109},
            {-79099, 113500, -4927, -1, 18109},
            {-78960, 113398, -4927, -1, 18109},
            {-78882, 113235, -4927, -1, 18109},
            {-78882, 113099, -4927, -1, 18109},
        },
        {
            /* Level 42 and Below */
            {-76119, 110383, -4927, -1, 18110},
            {-75980, 110442, -4927, -1, 18110},
            {-75848, 110442, -4927, -1, 18110},
            {-75720, 110383, -4927, -1, 18110},
            {-75625, 110195, -4927, -1, 18110},
            {-75625, 110063, -4927, -1, 18110},
            {-75722, 109908, -4927, -1, 18110},
            {-75863, 109832, -4927, -1, 18110},
            {-75989, 109832, -4927, -1, 18110},
            {-76130, 109908, -4927, -1, 18110},
            {-76230, 110079, -4927, -1, 18110},
            {-76230, 110215, -4927, -1, 18110},
        },
        {
            /* Level 53 and Below */
            {-74055, 111781, -4927, -1, 18111},
            {-74144, 111938, -4927, -1, 18111},
            {-74144, 112075, -4927, -1, 18111},
            {-74055, 112173, -4927, -1, 18111},
            {-73885, 112289, -4927, -1, 18111},
            {-73756, 112289, -4927, -1, 18111},
            {-73574, 112141, -4927, -1, 18111},
            {-73511, 112040, -4927, -1, 18111},
            {-73511, 111912, -4927, -1, 18111},
            {-73574, 111772, -4927, -1, 18111},
            {-73767, 111669, -4927, -1, 18111},
            {-73899, 111669, -4927, -1, 18111},
        },
        {
            /* Level 64 and Below */
            {-76008, 113566, -4927, -1, 18112},
            {-76159, 113485, -4927, -1, 18112},
            {-76267, 113485, -4927, -1, 18112},
            {-76386, 113566, -4927, -1, 18112},
            {-76482, 113748, -4927, -1, 18112},
            {-76482, 113885, -4927, -1, 18112},
            {-76371, 114029, -4927, -1, 18112},
            {-76220, 114118, -4927, -1, 18112},
            {-76092, 114118, -4927, -1, 18112},
            {-75975, 114029, -4927, -1, 18112},
            {-75861, 113851, -4927, -1, 18112},
            {-75861, 113713, -4927, -1, 18112},
        },
        {
            /* No Level Limit */
            {-79100, 109782, -4927, -1, 18113},
            {-78962, 109853, -4927, -1, 18113},
            {-78851, 109853, -4927, -1, 18113},
            {-78721, 109782, -4927, -1, 18113},
            {-78615, 109596, -4927, -1, 18113},
            {-78615, 109453, -4927, -1, 18113},
            {-78746, 109300, -4927, -1, 18113},
            {-78881, 109203, -4927, -1, 18113},
            {-79027, 109203, -4927, -1, 18113},
            {-79159, 109300, -4927, -1, 18113},
            {-79240, 109480, -4927, -1, 18113},
            {-79240, 109615, -4927, -1, 18113},
        }
    };

    protected static final int[][][] FESTIVAL_DUSK_CHEST_SPAWNS = {
        {
            /* Level 31 and Below */
            {-77016, 88726, -5183, -1, 18114},
            {-77136, 88646, -5183, -1, 18114},
            {-77247, 88646, -5183, -1, 18114},
            {-77380, 88726, -5183, -1, 18114},
            {-77512, 88883, -5183, -1, 18114},
            {-77512, 89053, -5183, -1, 18114},
            {-77378, 89287, -5183, -1, 18114},
            {-77254, 89238, -5183, -1, 18114},
            {-77095, 89238, -5183, -1, 18114},
            {-76996, 89287, -5183, -1, 18114},
            {-76901, 89025, -5183, -1, 18114},
            {-76901, 88891, -5183, -1, 18114},
        },
        {
            /* Level 42 and Below */
            {-77128, 85553, -5183, -1, 18115},
            {-77036, 85594, -5183, -1, 18115},
            {-76919, 85594, -5183, -1, 18115},
            {-76755, 85553, -5183, -1, 18115},
            {-76635, 85392, -5183, -1, 18115},
            {-76635, 85216, -5183, -1, 18115},
            {-76761, 85025, -5183, -1, 18115},
            {-76908, 85004, -5183, -1, 18115},
            {-77041, 85004, -5183, -1, 18115},
            {-77138, 85025, -5183, -1, 18115},
            {-77268, 85219, -5183, -1, 18115},
            {-77268, 85410, -5183, -1, 18115},
        },
        {
            /* Level 53 and Below */
            {-75150, 87303, -5183, -1, 18116},
            {-75150, 87175, -5183, -1, 18116},
            {-75150, 87175, -5183, -1, 18116},
            {-75150, 87303, -5183, -1, 18116},
            {-74943, 87433, -5183, -1, 18116},
            {-74767, 87433, -5183, -1, 18116},
            {-74556, 87306, -5183, -1, 18116},
            {-74556, 87184, -5183, -1, 18116},
            {-74556, 87184, -5183, -1, 18116},
            {-74556, 87306, -5183, -1, 18116},
            {-74757, 86830, -5183, -1, 18116},
            {-74927, 86830, -5183, -1, 18116},
        },
        {
            /* Level 64 and Below */
            {-80010, 88128, -5183, -1, 18117},
            {-80113, 88066, -5183, -1, 18117},
            {-80220, 88066, -5183, -1, 18117},
            {-80359, 88128, -5183, -1, 18117},
            {-80467, 88267, -5183, -1, 18117},
            {-80467, 88436, -5183, -1, 18117},
            {-80381, 88639, -5183, -1, 18117},
            {-80278, 88577, -5183, -1, 18117},
            {-80142, 88577, -5183, -1, 18117},
            {-80028, 88639, -5183, -1, 18117},
            {-79915, 88466, -5183, -1, 18117},
            {-79915, 88322, -5183, -1, 18117},
        },
        {
            /* No Level Limit */
            {-80153, 84947, -5183, -1, 18118},
            {-80003, 84962, -5183, -1, 18118},
            {-79848, 84962, -5183, -1, 18118},
            {-79742, 84947, -5183, -1, 18118},
            {-79668, 84772, -5183, -1, 18118},
            {-79668, 84619, -5183, -1, 18118},
            {-79772, 84471, -5183, -1, 18118},
            {-79888, 84414, -5183, -1, 18118},
            {-80023, 84414, -5183, -1, 18118},
            {-80166, 84471, -5183, -1, 18118},
            {-80253, 84600, -5183, -1, 18118},
            {-80253, 84780, -5183, -1, 18118},
        }
    };
    // @formatter:on

    private FestivalManager managerInstance;
    protected ScheduledFuture<?> managerScheduledTask;

    protected int signsCycle = SevenSigns.getInstance().getCurrentCycle();

    @Getter
    protected int currentFestivalCycle;

    protected long nextFestivalCycleStart;
    protected long nextFestivalStart;

    @Getter
    protected boolean festivalInitialized;

    @Getter
    protected boolean festivalInProgress;

    protected List<Integer> accumulatedBonuses = new ArrayList<>(); // The total bonus available (in Ancient Adena)

    private Npc dawnChatGuide;
    private Npc duskChatGuide;

    protected Map<Integer, List<Integer>> dawnFestivalParticipants = new HashMap<>();
    protected Map<Integer, List<Integer>> duskFestivalParticipants = new HashMap<>();

    protected Map<Integer, List<Integer>> dawnPreviousParticipants = new HashMap<>();
    protected Map<Integer, List<Integer>> duskPreviousParticipants = new HashMap<>();

    private final Map<Integer, Long> dawnFestivalScores = new HashMap<>();
    private final Map<Integer, Long> duskFestivalScores = new HashMap<>();

    /**
     * festivalData is essentially an instance of the seven_signs_festival table and should be treated as such. Data is initially accessed by the related Seven Signs cycle, with signsCycle representing data for the current round of Festivals. The actual table data is stored as a series of StatSet
     * constructs. These are accessed by the use of an offset based on the number of festivals, thus: offset = FESTIVAL_COUNT + festivalId (Data for Dawn is always accessed by offset > FESTIVAL_COUNT)
     */
    private final Map<Integer, Map<Integer, StatSet>> festivalData = new HashMap<>();

    private final SevenSignsFestivalScoreRepository scoreRepository;
    private final SevenSignsStatusRepository statusRepository;

    public SevenSignsFestival(
            SevenSignsFestivalScoreRepository scoreRepository, SevenSignsStatusRepository statusRepository) {
        this.scoreRepository = scoreRepository;
        this.statusRepository = statusRepository;
        instance = this;
    }

    /**
     * Starts the festival manager; {@link SevenSigns#load()} restored the festival data before.
     */
    public void load() {
        if (SevenSigns.getInstance().isSealValidationPeriod()) {
            log.info("Initialization bypassed due to Seal Validation in effect.");
            return;
        }

        Spawn.addSpawnListener(this);
        startFestivalManager();
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static SevenSignsFestival getInstance() {
        final SevenSignsFestival current = instance;
        if (current == null) {
            throw new IllegalStateException("SevenSignsFestival is used before the Spring context created it.");
        }
        return current;
    }

    /**
     * Returns the associated name (level range) to a given festival ID.
     * @param festivalID
     * @return String festivalName
     */
    public static String getFestivalName(int festivalID) {
        String festivalName;

        switch (festivalID) {
            case FESTIVAL_LEVEL_MAX_31: {
                festivalName = "Level 31 or lower";
                break;
            }
            case FESTIVAL_LEVEL_MAX_42: {
                festivalName = "Level 42 or lower";
                break;
            }
            case FESTIVAL_LEVEL_MAX_53: {
                festivalName = "Level 53 or lower";
                break;
            }
            case FESTIVAL_LEVEL_MAX_64: {
                festivalName = "Level 64 or lower";
                break;
            }
            default: {
                festivalName = "No Level Limit";
                break;
            }
        }

        return festivalName;
    }

    /**
     * Returns the maximum allowed player level for the given festival type.
     * @param festivalId
     * @return int maxLevel
     */
    public static int getMaxLevelForFestival(int festivalId) {
        int maxLevel = (ExperienceData.getInstance().getMaxLevel() - 1);

        switch (festivalId) {
            case FESTIVAL_LEVEL_MAX_31: {
                maxLevel = 31;
                break;
            }
            case FESTIVAL_LEVEL_MAX_42: {
                maxLevel = 42;
                break;
            }
            case FESTIVAL_LEVEL_MAX_53: {
                maxLevel = 53;
                break;
            }
            case FESTIVAL_LEVEL_MAX_64: {
                maxLevel = 64;
                break;
            }
        }

        return maxLevel;
    }

    /**
     * Returns true if the monster ID given is of an archer/marksman type.
     * @param npcId
     * @return boolean isArcher
     */
    protected static boolean isFestivalArcher(int npcId) {
        if ((npcId < 18009) || (npcId > 18108)) {
            return false;
        }

        final int identifier = npcId % 10;
        return ((identifier == 4) || (identifier == 9));
    }

    /**
     * Returns true if the monster ID given is a festival chest.
     * @param npcId
     * @return boolean isChest
     */
    protected static boolean isFestivalChest(int npcId) {
        return ((npcId < 18109) || (npcId > 18118));
    }

    /**
     * Primarily used to terminate the Festival Manager, when the Seven Signs period changes.
     * @return ScheduledFuture festManagerScheduler
     */
    protected ScheduledFuture<?> getFestivalManagerSchedule() {
        if (managerScheduledTask == null) {
            startFestivalManager();
        }

        return managerScheduledTask;
    }

    /**
     * Used to start the Festival Manager, if the current period is not Seal Validation.
     */
    protected void startFestivalManager() {
        // Start the Festival Manager for the first time after the server has started
        // at the specified time, then invoke it automatically after every cycle.
        managerInstance = new FestivalManager();
        setNextFestivalStart(FeatureConfig.ALT_FESTIVAL_MANAGER_START + festivalSignupTime());
        managerScheduledTask = WorldPartition.loop()
                .scheduleAtFixedRate(
                        managerInstance,
                        FeatureConfig.ALT_FESTIVAL_MANAGER_START,
                        FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH,
                        TimeUnit.MILLISECONDS);
        managerInstance.ticks = managerScheduledTask;

        log.info(
                "The first Festival of Darkness cycle begins in {} minute(s).",
                (FeatureConfig.ALT_FESTIVAL_MANAGER_START / 60000));
    }

    /**
     * Restores saved festival data, basic settings from the properties file and past high score data from the database.
     */
    protected void restoreFestivalData() {
        festivalData.clear();
        accumulatedBonuses.clear();
        try {
            for (SevenSignsFestivalScore score : scoreRepository.findAll()) {
                int festivalId = score.festivalId();
                final StatSet festivalDat = new StatSet();
                festivalDat.set("festivalId", festivalId);
                festivalDat.set("cabal", score.cabal());
                festivalDat.set("cycle", score.cycle());
                festivalDat.set("date", String.valueOf(epochMillisOrZero(score.scoredAt())));
                festivalDat.set("score", score.score());
                setMembers(festivalDat, score.memberIds());
                if (score.cabal().equals("dawn")) {
                    festivalId += FESTIVAL_COUNT;
                }

                final Map<Integer, StatSet> tempData =
                        festivalData.computeIfAbsent(score.cycle(), _ -> new HashMap<>());
                tempData.put(festivalId, festivalDat);
            }

            final SevenSignsStatus status = statusRepository.find();
            currentFestivalCycle = status.festivalCycle();
            accumulatedBonuses.addAll(status.accumulatedBonuses());
        } catch (DataAccessException e) {
            log.error("Failed to load configuration: {}", e.getMessage(), e);
        }
    }

    /**
     * Keeps the member object ids for the score and their names, joined by commas, for display.
     */
    private static void setMembers(StatSet festivalDat, List<Integer> memberIds) {
        final StringJoiner names = new StringJoiner(",");
        for (int memberId : memberIds) {
            final String name = CharInfoTable.getInstance().getNameById(memberId);
            if (name != null) {
                names.add(name);
            }
        }

        festivalDat.set("memberIds", List.copyOf(memberIds));
        festivalDat.set("members", names.toString());
    }

    private static List<Integer> getMemberIds(StatSet festivalDat) {
        return festivalDat.getList("memberIds", Integer.class, List.of());
    }

    /**
     * Stores current festival data, basic settings to the properties file and past high score data to the database. If updateSettings = true, then all Seven Signs data is updated in the database.
     * @param updateSettings
     */
    public void saveFestivalData(boolean updateSettings) {
        final List<SevenSignsFestivalScore> scores = new ArrayList<>();
        for (Map<Integer, StatSet> currCycleData : festivalData.values()) {
            for (StatSet festivalDat : currCycleData.values()) {
                scores.add(SevenSignsFestivalScore.builder()
                        .cycle(festivalDat.getInt("cycle"))
                        .festivalId(festivalDat.getInt("festivalId"))
                        .cabal(festivalDat.getString("cabal"))
                        .scoredAt(instantOrNull(Long.parseLong(festivalDat.getString("date"))))
                        .score(festivalDat.getInt("score"))
                        .memberIds(getMemberIds(festivalDat))
                        .build());
            }
        }

        try {
            scoreRepository.saveAll(scores);
        } catch (DataAccessException e) {
            log.error("Failed to save configuration: {}", e.getMessage(), e);
        }

        // Updates Seven Signs DB data also, so call only if really necessary.
        if (updateSettings) {
            SevenSigns.getInstance().saveSevenSignsStatus();
        }
    }

    /**
     * If a clan member is a member of the highest-ranked party in the Festival of Darkness, 100 points are added per member
     */
    protected void rewardHighestRanked() {
        for (int festivalId : new int[] {
            FESTIVAL_LEVEL_MAX_31,
            FESTIVAL_LEVEL_MAX_42,
            FESTIVAL_LEVEL_MAX_53,
            FESTIVAL_LEVEL_MAX_64,
            FESTIVAL_LEVEL_MAX_NONE
        }) {
            final StatSet overallData = getOverallHighestScoreData(festivalId);
            if (overallData != null) {
                for (int memberId : getMemberIds(overallData)) {
                    addReputationPointsForPartyMemberClan(memberId);
                }
            }
        }
    }

    private void addReputationPointsForPartyMemberClan(int memberId) {
        final Player player = World.getPlayer(memberId);
        final Clan clan = player != null ? player.getClan() : findClanOf(memberId);
        if (clan != null) {
            clan.addReputationScore(FeatureConfig.FESTIVAL_WIN_POINTS);
            final SystemMessage sm = new SystemMessage(
                    SystemMessageId
                            .CLAN_MEMBER_C1_WAS_AN_ACTIVE_MEMBER_OF_THE_HIGHEST_RANKED_PARTY_IN_THE_FESTIVAL_OF_DARKNESS_S2_POINTS_HAVE_BEEN_ADDED_TO_YOUR_CLAN_S_REPUTATION_SCORE);
            sm.addString(
                    player != null
                            ? player.getName()
                            : CharInfoTable.getInstance().getNameById(memberId));
            sm.addInt(FeatureConfig.FESTIVAL_WIN_POINTS);
            clan.broadcastToOnlineMembers(sm);
        }
    }

    private static Clan findClanOf(int memberId) {
        for (Clan clan : ClanTable.getInstance().getClans()) {
            if (clan.isMember(memberId)) {
                return clan;
            }
        }

        return null;
    }

    /**
     * Used to reset all festival data at the beginning of a new quest event period.
     * @param updateSettings
     */
    protected void resetFestivalData(boolean updateSettings) {
        currentFestivalCycle = 0;
        signsCycle = SevenSigns.getInstance().getCurrentCycle();

        // Set all accumulated bonuses back to 0.
        for (int i = 0; i < FESTIVAL_COUNT; i++) {
            accumulatedBonuses.set(i, 0);
        }

        dawnFestivalParticipants.clear();
        dawnPreviousParticipants.clear();
        dawnFestivalScores.clear();

        duskFestivalParticipants.clear();
        duskPreviousParticipants.clear();
        duskFestivalScores.clear();

        // Set up a new data set for the current cycle of festivals
        final Map<Integer, StatSet> newData = new HashMap<>();
        for (int i = 0; i < (FESTIVAL_COUNT * 2); i++) {
            int festivalId = i;
            if (i >= FESTIVAL_COUNT) {
                festivalId -= FESTIVAL_COUNT;
            }

            // Create a new StatSet with "default" data for Dusk
            final StatSet tempStats = new StatSet();
            tempStats.set("festivalId", festivalId);
            tempStats.set("cycle", signsCycle);
            tempStats.set("date", "0");
            tempStats.set("score", 0);
            setMembers(tempStats, List.of());
            if (i >= FESTIVAL_COUNT) {
                tempStats.set("cabal", SevenSigns.getCabalShortName(SevenSigns.CABAL_DAWN));
            } else {
                tempStats.set("cabal", SevenSigns.getCabalShortName(SevenSigns.CABAL_DUSK));
            }

            newData.put(i, tempStats);
        }

        // Add the newly created cycle data to the existing festival data, and
        // subsequently save it to the database.
        festivalData.put(signsCycle, newData);

        saveFestivalData(updateSettings);

        // Remove any unused blood offerings from online players.
        for (Player player : World.getPlayers()) {
            final Item bloodOfferings = player.getInventory().getItemByItemId(FESTIVAL_OFFERING_ID);
            if (bloodOfferings != null) {
                player.destroyItem(ItemProcessType.FEE, bloodOfferings, null, false);
            }
        }

        log.info("Reinitialized engine for next competition period.");
    }

    public void setNextCycleStart() {
        nextFestivalCycleStart = ClockBridge.gameMillis() + FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH;
    }

    public void setNextFestivalStart(long milliFromNow) {
        nextFestivalStart = ClockBridge.gameMillis() + milliFromNow;
    }

    public long getMinsToNextCycle() {
        if (SevenSigns.getInstance().isSealValidationPeriod()) {
            return -1;
        }

        return (nextFestivalCycleStart - ClockBridge.gameMillis()) / 60000;
    }

    public int getMinsToNextFestival() {
        if (SevenSigns.getInstance().isSealValidationPeriod()) {
            return -1;
        }

        return (int) (((nextFestivalStart - ClockBridge.gameMillis()) / 60000) + 1);
    }

    public String getTimeToNextFestivalStr() {
        if (SevenSigns.getInstance().isSealValidationPeriod()) {
            return "<font color=\"FF0000\">This is the Seal Validation period. Festivals will resume next week.</font>";
        }

        return "<font color=\"FF0000\">The next festival will begin in " + getMinsToNextFestival()
                + " minute(s).</font>";
    }

    /**
     * Returns the current festival ID and oracle ID that the specified player is in, but will return the default of {-1, -1} if the player is not found as a participant.
     * @param player
     * @return int[] playerFestivalInfo
     */
    public int[] getFestivalForPlayer(Player player) {
        final int[] playerFestivalInfo = {-1, -1};
        int festivalId = 0;

        while (festivalId < FESTIVAL_COUNT) {
            List<Integer> participants = dawnFestivalParticipants.get(festivalId);

            // If there are no participants in this festival, move on to the next.
            if ((participants != null) && participants.contains(player.getObjectId())) {
                playerFestivalInfo[0] = SevenSigns.CABAL_DAWN;
                playerFestivalInfo[1] = festivalId;
                return playerFestivalInfo;
            }

            participants = duskFestivalParticipants.get(++festivalId);
            if ((participants != null) && participants.contains(player.getObjectId())) {
                playerFestivalInfo[0] = SevenSigns.CABAL_DUSK;
                playerFestivalInfo[1] = festivalId;
                return playerFestivalInfo;
            }

            festivalId++;
        }

        // Return default data if the player is not found as a participant.
        return playerFestivalInfo;
    }

    public boolean isParticipant(Player player) {
        if (SevenSigns.getInstance().isSealValidationPeriod()) {
            return false;
        }

        if (managerInstance == null) {
            return false;
        }

        for (List<Integer> participants : dawnFestivalParticipants.values()) {
            if ((participants != null) && participants.contains(player.getObjectId())) {
                return true;
            }
        }

        for (List<Integer> participants : duskFestivalParticipants.values()) {
            if ((participants != null) && participants.contains(player.getObjectId())) {
                return true;
            }
        }

        return false;
    }

    public List<Integer> getParticipants(int oracle, int festivalId) {
        if (oracle == SevenSigns.CABAL_DAWN) {
            return dawnFestivalParticipants.get(festivalId);
        }

        return duskFestivalParticipants.get(festivalId);
    }

    public List<Integer> getPreviousParticipants(int oracle, int festivalId) {
        if (oracle == SevenSigns.CABAL_DAWN) {
            return dawnPreviousParticipants.get(festivalId);
        }

        return duskPreviousParticipants.get(festivalId);
    }

    public void setParticipants(int oracle, int festivalId, Party festivalParty) {
        List<Integer> participants = null;
        if (festivalParty != null) {
            participants = new ArrayList<>(festivalParty.getMemberCount());
            for (Player player : festivalParty.getMembers()) {
                if (player == null) {
                    continue;
                }

                participants.add(player.getObjectId());
            }
        }

        if (oracle == SevenSigns.CABAL_DAWN) {
            dawnFestivalParticipants.put(festivalId, participants);
        } else {
            duskFestivalParticipants.put(festivalId, participants);
        }
    }

    public void updateParticipants(Player player, Party festivalParty) {
        if (!isParticipant(player)) {
            return;
        }

        final int[] playerFestInfo = getFestivalForPlayer(player);
        final int oracle = playerFestInfo[0];
        final int festivalId = playerFestInfo[1];
        if (festivalId > -1) {
            if (festivalInitialized) {
                final DarknessFestival festivalInst = managerInstance.getFestivalInstance(oracle, festivalId);
                if (festivalParty == null) {
                    for (int partyMemberObjId : getParticipants(oracle, festivalId)) {
                        final Player partyMember = World.getPlayer(partyMemberObjId);
                        if (partyMember == null) {
                            continue;
                        }

                        festivalInst.relocatePlayer(partyMember, true);
                    }
                } else {
                    festivalInst.relocatePlayer(player, true);
                }
            }

            setParticipants(oracle, festivalId, festivalParty);

            // Check on disconnect if min player in party
            if ((festivalParty != null) && (festivalParty.getMemberCount() < FeatureConfig.ALT_FESTIVAL_MIN_PLAYER)) {
                updateParticipants(player, null); // under minimum count
                festivalParty.removePartyMember(player, PartyMessageType.EXPELLED);
            }
        }
    }

    public long getFinalScore(int oracle, int festivalId) {
        if (oracle == SevenSigns.CABAL_DAWN) {
            return dawnFestivalScores.get(festivalId);
        }

        return duskFestivalScores.get(festivalId);
    }

    public int getHighestScore(int oracle, int festivalId) {
        return getHighestScoreData(oracle, festivalId).getInt("score");
    }

    /**
     * Returns a stats set containing the highest score <b>this cycle</b> for the the specified cabal and associated festival ID.
     * @param oracle
     * @param festivalId
     * @return StatSet festivalDat
     */
    public StatSet getHighestScoreData(int oracle, int festivalId) {
        int offsetId = festivalId;
        if (oracle == SevenSigns.CABAL_DAWN) {
            offsetId += 5;
        }

        // Attempt to retrieve existing score data (if found), otherwise create a
        // new blank data set and display a console warning.
        StatSet currData = null;

        try {
            currData = festivalData.get(signsCycle).get(offsetId);
        } catch (Exception e) {
            currData = new StatSet();
            currData.set("score", 0);
            setMembers(currData, List.of());
        }

        return currData;
    }

    /**
     * Returns a stats set containing the highest ever recorded score data for the specified festival.
     * @param festivalId
     * @return StatSet result
     */
    public StatSet getOverallHighestScoreData(int festivalId) {
        StatSet result = null;
        int highestScore = 0;
        for (Map<Integer, StatSet> currCycleData : festivalData.values()) {
            for (StatSet currFestData : currCycleData.values()) {
                final int currFestID = currFestData.getInt("festivalId");
                if (currFestID != festivalId) {
                    continue;
                }

                final int festivalScore = currFestData.getInt("score");
                if (festivalScore > highestScore) {
                    highestScore = festivalScore;
                    result = currFestData;
                }
            }
        }

        return result;
    }

    /**
     * Set the final score details for the last participants of the specified festival data. Returns <b>true</b> if the score is higher than that previously recorded <b>this cycle</b>.
     * @param player
     * @param oracle
     * @param festivalId
     * @param offeringScore
     * @return boolean isHighestScore
     */
    public boolean setFinalScore(Player player, int oracle, int festivalId, long offeringScore) {
        final int currDawnHighScore = getHighestScore(SevenSigns.CABAL_DAWN, festivalId);
        final int currDuskHighScore = getHighestScore(SevenSigns.CABAL_DUSK, festivalId);
        int thisCabalHighScore = 0;
        int otherCabalHighScore = 0;
        if (oracle == SevenSigns.CABAL_DAWN) {
            thisCabalHighScore = currDawnHighScore;
            otherCabalHighScore = currDuskHighScore;
            dawnFestivalScores.put(festivalId, offeringScore);
        } else {
            thisCabalHighScore = currDuskHighScore;
            otherCabalHighScore = currDawnHighScore;
            duskFestivalScores.put(festivalId, offeringScore);
        }

        final StatSet currFestData = getHighestScoreData(oracle, festivalId);

        // Check if this is the highest score for this level range so far for the player's cabal.
        if (offeringScore > thisCabalHighScore) {
            // If the current score is greater than that for the other cabal,
            // then they already have the points from this festival.
            if (thisCabalHighScore > otherCabalHighScore) {
                return false;
            }

            final List<Integer> prevParticipants = getPreviousParticipants(oracle, festivalId);

            // Update the highest scores and party list.
            currFestData.set("date", String.valueOf(ClockBridge.wallMillis()));
            currFestData.set("score", offeringScore);
            setMembers(currFestData, prevParticipants);

            // Only add the score to the cabal's overall if it's higher than the other cabal's score.
            if (offeringScore > otherCabalHighScore) {
                final int contribPoints = FESTIVAL_LEVEL_SCORES[festivalId];

                // Give this cabal the festival points, while deducting them from the other.
                SevenSigns.getInstance().addFestivalScore(oracle, contribPoints);
            }

            saveFestivalData(true);

            return true;
        }

        return false;
    }

    public int getAccumulatedBonus(int festivalId) {
        return accumulatedBonuses.get(festivalId);
    }

    public int getTotalAccumulatedBonus() {
        int totalAccumBonus = 0;
        for (int accumBonus : accumulatedBonuses) {
            totalAccumBonus += accumBonus;
        }

        return totalAccumBonus;
    }

    public void addAccumulatedBonus(int festivalId, int stoneType, int stoneAmount) {
        int eachStoneBonus = 0;

        switch (stoneType) {
            case SevenSigns.SEAL_STONE_BLUE_ID: {
                eachStoneBonus = SevenSigns.SEAL_STONE_BLUE_VALUE;
                break;
            }
            case SevenSigns.SEAL_STONE_GREEN_ID: {
                eachStoneBonus = SevenSigns.SEAL_STONE_GREEN_VALUE;
                break;
            }
            case SevenSigns.SEAL_STONE_RED_ID: {
                eachStoneBonus = SevenSigns.SEAL_STONE_RED_VALUE;
                break;
            }
        }

        final int newTotalBonus = accumulatedBonuses.get(festivalId) + (stoneAmount * eachStoneBonus);
        accumulatedBonuses.set(festivalId, newTotalBonus);
    }

    /**
     * Calculate and return the proportion of the accumulated bonus for the festival where the player was in the winning party, if the winning party's cabal won the event. The accumulated bonus is then updated, with the player's share deducted.
     * @param player
     * @return playerBonus (the share of the bonus for the party)
     */
    public int distribAccumulatedBonus(Player player) {
        final int playerCabal = SevenSigns.getInstance().getPlayerCabal(player.getObjectId());
        if (playerCabal != SevenSigns.getInstance().getCabalHighestScore()) {
            return 0;
        }

        int playerBonus = 0;

        final Map<Integer, StatSet> festivalDataMap = this.festivalData.get(signsCycle);
        if (festivalDataMap != null) {
            for (StatSet festivalData : festivalDataMap.values()) {
                final List<Integer> memberIds = getMemberIds(festivalData);
                if (memberIds.contains(player.getObjectId())) {
                    final int festivalId = festivalData.getInt("festivalId");
                    final int numPartyMembers = memberIds.size();
                    final int totalAccumBonus = accumulatedBonuses.get(festivalId);
                    playerBonus = totalAccumBonus / numPartyMembers;
                    accumulatedBonuses.set(festivalId, totalAccumBonus - playerBonus);
                    break;
                }
            }
        }

        return playerBonus;
    }

    /**
     * Used to send a "shout" message to all players currently present in an Oracle.<br>
     * Primarily used for Festival Guide and Witch related speech.
     * @param npcString
     */
    public void sendMessageToAll(NpcStringId npcString) {
        if ((dawnChatGuide == null) || (duskChatGuide == null)) {
            return;
        }

        sendMessageToAll(npcString, dawnChatGuide);
        sendMessageToAll(npcString, duskChatGuide);
    }

    /**
     * @param npcString
     * @param npc
     */
    public void sendMessageToAll(NpcStringId npcString, Npc npc) {
        final CreatureSay cs = new CreatureSay(npc, ChatType.NPC_SHOUT, npcString);
        if (npcString.getParamCount() == 1) {
            cs.addStringParameter(String.valueOf(getMinsToNextFestival()));
        }

        npc.broadcastPacket(cs);
    }

    /**
     * Basically a wrapper-call to signal to increase the challenge of the specified festival.
     * @param oracle
     * @param festivalId
     * @return boolean isChalIncreased
     */
    public boolean increaseChallenge(int oracle, int festivalId) {
        final DarknessFestival festivalInst = managerInstance.getFestivalInstance(oracle, festivalId);
        return festivalInst.increaseChallenge();
    }

    /**
     * Used with the SpawnListener, to update the required "chat guide" instances, for use with announcements in the oracles.
     * @param npc
     */
    public void npcSpawned(Npc npc) {
        if (npc == null) {
            return;
        }

        final int npcId = npc.getId();

        // If the spawned NPC ID matches the ones we need, assign their instances.
        if (npcId == 31127) {
            dawnChatGuide = npc;
        }

        if (npcId == 31137) {
            duskChatGuide = npc;
        }
    }

    /**
     * The FestivalManager class is the main runner of all the festivals. It is used for easier integration and management of all running festivals.
     */
    private class FestivalManager implements Runnable {
        protected Map<Integer, DarknessFestival> festivalInstances;

        public FestivalManager() {
            festivalInstances = new HashMap<>();

            // Increment the cycle counter.
            currentFestivalCycle++;

            // Set the next start timers.
            setNextCycleStart();
            setNextFestivalStart(FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH - festivalSignupTime());
        }

        // A tick that comes while a cycle still runs starts the next cycle once it ends, one cycle per tick, as the
        // executor before the world loop ran a late fixed-rate task.
        private int lateTicks;
        private boolean cycleRunning;
        private long elapsedTime;
        private ScheduledFuture<?> ticks;

        @Override
        public void run() {
            if (cycleRunning) {
                lateTicks++;
                return;
            }

            stage(this::startCycle);
        }

        private void startCycle() {
            // The manager shouldn't be running if Seal Validation is in effect.
            if (SevenSigns.getInstance().isSealValidationPeriod()) {
                return;
            }

            // If the next period is due to start before the end of this
            // festival cycle, then don't run it.
            if (SevenSigns.getInstance().getMilliToPeriodChange() < FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH) {
                return;
            } else if (getMinsToNextFestival() == 2) {
                sendMessageToAll(NpcStringId.THE_MAIN_EVENT_WILL_START_IN_2_MINUTES_PLEASE_REGISTER_NOW);
            }

            cycleRunning = true;

            // Stand by until the allowed signup period has elapsed.
            next(this::closeSignup, festivalSignupTime());
        }

        private void closeSignup() {
            // Clear past participants, they can no longer register their score if not done so already.
            dawnPreviousParticipants.clear();
            duskPreviousParticipants.clear();

            // Get rid of random monsters that avoided deletion after last festival
            for (DarknessFestival festivalInst : festivalInstances.values()) {
                festivalInst.unspawnMobs();
            }

            awaitParticipants();
        }

        // Start only if participants signed up
        private void awaitParticipants() {
            if (duskFestivalParticipants.isEmpty() && dawnFestivalParticipants.isEmpty()) {
                setNextCycleStart();
                setNextFestivalStart(FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH - festivalSignupTime());
                next(this::retrySignup, FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH - festivalSignupTime());
                return;
            }

            initiate();
        }

        private void retrySignup() {
            try {
                for (DarknessFestival festivalInst : festivalInstances.values()) {
                    if (!festivalInst.npcInsts.isEmpty()) {
                        festivalInst.unspawnMobs();
                    }
                }
            } catch (RuntimeException e) {
                // A failed cleanup never ended the wait for participants.
                log.warn(e.getMessage());
            }

            awaitParticipants();
        }

        private void initiate() {
            /* INITIATION */
            // Set the festival timer to 0, as it is just beginning.
            elapsedTime = 0;

            // Create the instances for the festivals in both Oracles,
            // but only if they have participants signed up for them.
            for (int i = 0; i < FESTIVAL_COUNT; i++) {
                if (duskFestivalParticipants.get(i) != null) {
                    festivalInstances.put(10 + i, new DarknessFestival(SevenSigns.CABAL_DUSK, i));
                }

                if (dawnFestivalParticipants.get(i) != null) {
                    festivalInstances.put(20 + i, new DarknessFestival(SevenSigns.CABAL_DAWN, i));
                }
            }

            // Prevent future signups while festival is in progress.
            festivalInitialized = true;
            setNextFestivalStart(FeatureConfig.ALT_FESTIVAL_CYCLE_LENGTH);
            sendMessageToAll(NpcStringId.THE_MAIN_EVENT_IS_NOW_STARTING);

            // Stand by for a short length of time before starting the festival.
            next(this::firstSpawn, FeatureConfig.ALT_FESTIVAL_FIRST_SPAWN);
        }

        private void firstSpawn() {
            elapsedTime = FeatureConfig.ALT_FESTIVAL_FIRST_SPAWN;

            // Participants can now opt to increase the challenge, if desired.
            festivalInProgress = true;

            /* PROPOGATION */
            // Sequentially set all festivals to begin, spawn the Festival Witch and notify participants.
            for (DarknessFestival festivalInst : festivalInstances.values()) {
                festivalInst.festivalStart();
                festivalInst.sendMessageToParticipants(NpcStringId.THE_MAIN_EVENT_IS_NOW_STARTING);
            }

            // After a short time period, move all idle spawns to the center of the arena.
            next(this::firstSwarm, FeatureConfig.ALT_FESTIVAL_FIRST_SWARM - FeatureConfig.ALT_FESTIVAL_FIRST_SPAWN);
        }

        private void firstSwarm() {
            elapsedTime += FeatureConfig.ALT_FESTIVAL_FIRST_SWARM - FeatureConfig.ALT_FESTIVAL_FIRST_SPAWN;
            for (DarknessFestival festivalInst : festivalInstances.values()) {
                festivalInst.moveMonstersToCenter();
            }

            // Stand by until the time comes for the second spawn.
            next(this::secondSpawn, FeatureConfig.ALT_FESTIVAL_SECOND_SPAWN - FeatureConfig.ALT_FESTIVAL_FIRST_SWARM);
        }

        private void secondSpawn() {
            // Spawn an extra set of monsters (archers) on the free platforms with
            // a faster respawn when killed.
            for (DarknessFestival festivalInst : festivalInstances.values()) {
                festivalInst.spawnFestivalMonsters(FESTIVAL_DEFAULT_RESPAWN / 2, 2);

                final long end = (FeatureConfig.ALT_FESTIVAL_LENGTH - FeatureConfig.ALT_FESTIVAL_SECOND_SPAWN) / 60000;
                if (end == 2) {
                    festivalInst.sendMessageToParticipants(
                            NpcStringId.THE_FESTIVAL_OF_DARKNESS_WILL_END_IN_TWO_MINUTES);
                } else {
                    festivalInst.sendMessageToParticipants(
                            "The Festival of Darkness will end in " + end + " minute(s).");
                }
            }

            elapsedTime += FeatureConfig.ALT_FESTIVAL_SECOND_SPAWN - FeatureConfig.ALT_FESTIVAL_FIRST_SWARM;

            // After another short time period, again move all idle spawns to the center of the arena.
            next(this::secondSwarm, FeatureConfig.ALT_FESTIVAL_SECOND_SWARM - FeatureConfig.ALT_FESTIVAL_SECOND_SPAWN);
        }

        private void secondSwarm() {
            for (DarknessFestival festivalInst : festivalInstances.values()) {
                festivalInst.moveMonstersToCenter();
            }

            elapsedTime += FeatureConfig.ALT_FESTIVAL_SECOND_SWARM - FeatureConfig.ALT_FESTIVAL_SECOND_SPAWN;

            // Stand by until the time comes for the chests to be spawned.
            next(this::chestSpawn, FeatureConfig.ALT_FESTIVAL_CHEST_SPAWN - FeatureConfig.ALT_FESTIVAL_SECOND_SWARM);
        }

        private void chestSpawn() {
            // Spawn the festival chests, which enable the team to gain greater rewards
            // for each chest they kill.
            for (DarknessFestival festivalInst : festivalInstances.values()) {
                festivalInst.spawnFestivalMonsters(FESTIVAL_DEFAULT_RESPAWN, 3);
                festivalInst.sendMessageToParticipants(
                        "The chests have spawned! Be quick, the festival will end soon."); // FIXME What is the
                // correct npcString?
            }

            elapsedTime += FeatureConfig.ALT_FESTIVAL_CHEST_SPAWN - FeatureConfig.ALT_FESTIVAL_SECOND_SWARM;

            // Stand by and wait until it's time to end the festival.
            next(this::end, FeatureConfig.ALT_FESTIVAL_LENGTH - elapsedTime);
        }

        private void end() {
            // Participants can no longer opt to increase the challenge, as the festival will soon close.
            festivalInProgress = false;

            /* TERMINATION */
            // Sequentially begin the ending sequence for all running festivals.
            for (DarknessFestival festivalInst : festivalInstances.values()) {
                festivalInst.festivalEnd();
            }

            // Clear the participants list for the next round of signups.
            dawnFestivalParticipants.clear();
            duskFestivalParticipants.clear();

            // Allow signups for the next festival cycle.
            festivalInitialized = false;
            sendMessageToAll(NpcStringId.THAT_WILL_DO_I_LL_MOVE_YOU_TO_THE_OUTSIDE_SOON);
            endCycle();
        }

        private void next(Runnable stage, long delayMillis) {
            WorldPartition.loop().schedule(() -> stage(stage), delayMillis, TimeUnit.MILLISECONDS);
        }

        private void stage(Runnable stage) {
            try {
                stage.run();
            } catch (Exception e) {
                log.warn(e.getMessage());
                endCycle();
            }
        }

        private void endCycle() {
            cycleRunning = false;
            if (lateTicks == 0) {
                return;
            }

            if ((ticks != null) && ticks.isCancelled()) {
                lateTicks = 0;
                return;
            }

            lateTicks--;
            stage(this::startCycle);
        }

        /**
         * Returns the running instance of a festival for the given Oracle and festivalID.<br>
         * A <b>null</b> value is returned if there are no participants in that festival.
         * @param oracle
         * @param festivalIdValue
         * @return DarknessFestival festivalInst
         */
        public DarknessFestival getFestivalInstance(int oracle, int festivalIdValue) {
            if (!festivalInitialized) {
                return null;
            }

            /*
             * Compute the offset if a Dusk instance is required. ID: 0 1 2 3 4 Dusk 1:1011121314 Dawn 2:2021222324
             */

            int festivalId = festivalIdValue;
            festivalId += (oracle == SevenSigns.CABAL_DUSK) ? 10 : 20;
            return festivalInstances.get(festivalId);
        }
    }

    /**
     * Each running festival is represented by an DarknessFestival class. It contains all the spawn information and data for the running festival. All festivals are managed by the FestivalManager class, which must be initialized first.
     */
    private class DarknessFestival {
        protected final int cabal;
        protected final int levelRange;
        protected boolean challengeIncreased;

        private FestivalSpawn startLocation;
        private FestivalSpawn witchSpawn;

        private Npc witchInst;
        List<FestivalMonster> npcInsts = new ArrayList<>();

        private List<Integer> participants;
        private final Map<Integer, FestivalSpawn> originalLocations = new ConcurrentHashMap<>();

        protected DarknessFestival(int cabal, int levelRange) {
            this.cabal = cabal;
            this.levelRange = levelRange;
            if (cabal == SevenSigns.CABAL_DAWN) {
                participants = dawnFestivalParticipants.get(levelRange);
                witchSpawn = new FestivalSpawn(FESTIVAL_DAWN_WITCH_SPAWNS[levelRange]);
                startLocation = new FestivalSpawn(FESTIVAL_DAWN_PLAYER_SPAWNS[levelRange]);
            } else {
                participants = duskFestivalParticipants.get(levelRange);
                witchSpawn = new FestivalSpawn(FESTIVAL_DUSK_WITCH_SPAWNS[levelRange]);
                startLocation = new FestivalSpawn(FESTIVAL_DUSK_PLAYER_SPAWNS[levelRange]);
            }

            // FOR TESTING!
            if (participants == null) {
                participants = new ArrayList<>();
            }

            festivalInit();
        }

        protected void festivalInit() {
            // Teleport all players to arena and notify them.
            if ((participants != null) && !participants.isEmpty()) {
                try {
                    for (int participantObjId : participants) {
                        final Player participant = World.getPlayer(participantObjId);
                        if (participant == null) {
                            continue;
                        }

                        originalLocations.put(
                                participantObjId,
                                new FestivalSpawn(
                                        participant.getX(),
                                        participant.getY(),
                                        participant.getZ(),
                                        participant.getHeading()));

                        // Randomize the spawn point around the specific centerpoint for each player.
                        int x = startLocation.x;
                        int y = startLocation.y;
                        if (ClockBridge.random().nextBoolean()) {
                            x += ClockBridge.random().nextInt(FESTIVAL_MAX_OFFSET_X);
                            y += ClockBridge.random().nextInt(FESTIVAL_MAX_OFFSET_Y);
                        } else {
                            x -= ClockBridge.random().nextInt(FESTIVAL_MAX_OFFSET_X);
                            y -= ClockBridge.random().nextInt(FESTIVAL_MAX_OFFSET_Y);
                        }

                        participant.getAI().setIntentionIdle();
                        participant.teleToLocation(new Location(x, y, startLocation.z), true);

                        // Remove all buffs from all participants on entry. Works like the skill Cancel.
                        participant.stopAllEffectsExceptThoseThatLastThroughDeath();

                        // Remove any stray blood offerings in inventory
                        final Item bloodOfferings = participant.getInventory().getItemByItemId(FESTIVAL_OFFERING_ID);
                        if (bloodOfferings != null) {
                            participant.destroyItem(ItemProcessType.FEE, bloodOfferings, null, true);
                        }
                    }
                } catch (NullPointerException e) {
                    // deleteMe handling should teleport party out in case of disconnect
                }
            }

            // Spawn the festival witch for this arena
            try {
                final Spawn npcSpawn = new Spawn(witchSpawn.npcId);
                npcSpawn.setXYZ(witchSpawn.x, witchSpawn.y, witchSpawn.z);
                npcSpawn.setHeading(witchSpawn.heading);
                npcSpawn.setAmount(1);
                npcSpawn.setRespawnDelay(1);

                // Needed as doSpawn() is required to be called also for the Npc it returns.
                npcSpawn.startRespawn();

                SpawnTable.getInstance().addSpawn(npcSpawn);
                witchInst = npcSpawn.doSpawn();
            } catch (Exception e) {
                log.warn("Error while spawning Festival Witch ID {}: {}", witchSpawn.npcId, e.getMessage(), e);
            }

            // Make it appear as though the Witch has apparated there.
            MagicSkillUse msu = new MagicSkillUse(witchInst, witchInst, 2003, 1, 1, 0);
            witchInst.broadcastPacket(msu);

            // And another one...:D
            msu = new MagicSkillUse(witchInst, witchInst, 2133, 1, 1, 0);
            witchInst.broadcastPacket(msu);

            // Send a message to all participants from the witch.
            sendMessageToParticipants(NpcStringId.THE_MAIN_EVENT_WILL_START_IN_2_MINUTES_PLEASE_REGISTER_NOW);
        }

        protected void festivalStart() {
            spawnFestivalMonsters(FESTIVAL_DEFAULT_RESPAWN, 0);
        }

        protected void moveMonstersToCenter() {
            for (FestivalMonster festivalMob : npcInsts) {
                if (festivalMob.isDead()) {
                    continue;
                }

                // Only move monsters that are idle or doing their usual functions.
                final Intention currIntention = festivalMob.getAI().getIntention();
                if ((currIntention != Intention.IDLE) && (currIntention != Intention.ACTIVE)) {
                    continue;
                }

                int x = startLocation.x;
                int y = startLocation.y;

                /*
                 * Random X and Y coords around the player start location, up to half of the maximum allowed offset are generated to prevent the mobs from all moving to the exact same place.
                 */
                if (ClockBridge.random().nextBoolean()) {
                    x += ClockBridge.random().nextInt(FESTIVAL_MAX_OFFSET_X);
                    y += ClockBridge.random().nextInt(FESTIVAL_MAX_OFFSET_Y);
                } else {
                    x -= ClockBridge.random().nextInt(FESTIVAL_MAX_OFFSET_X);
                    y -= ClockBridge.random().nextInt(FESTIVAL_MAX_OFFSET_Y);
                }

                festivalMob.setRunning();
                festivalMob
                        .getAI()
                        .setIntentionMoveTo(new Location(
                                x, y, startLocation.z, ClockBridge.random().nextInt(65536)));
            }
        }

        /**
         * Used to spawn monsters unique to the festival.<br>
         * Valid SpawnTypes:<br>
         * 0 - All Primary Monsters (starting monsters)<br>
         * 1 - Same as 0, but without archers/marksmen. (used for challenge increase)<br>
         * 2 - Secondary Monsters (archers)<br>
         * 3 - Festival Chests
         * @param respawnDelay
         * @param spawnType
         */
        protected void spawnFestivalMonsters(int respawnDelay, int spawnType) {
            int[][] npcSpawns = null;

            switch (spawnType) {
                case 0:
                case 1: {
                    npcSpawns = (cabal == SevenSigns.CABAL_DAWN)
                            ? FESTIVAL_DAWN_PRIMARY_SPAWNS[levelRange]
                            : FESTIVAL_DUSK_PRIMARY_SPAWNS[levelRange];
                    break;
                }
                case 2: {
                    npcSpawns = (cabal == SevenSigns.CABAL_DAWN)
                            ? FESTIVAL_DAWN_SECONDARY_SPAWNS[levelRange]
                            : FESTIVAL_DUSK_SECONDARY_SPAWNS[levelRange];
                    break;
                }
                case 3: {
                    npcSpawns = (cabal == SevenSigns.CABAL_DAWN)
                            ? FESTIVAL_DAWN_CHEST_SPAWNS[levelRange]
                            : FESTIVAL_DUSK_CHEST_SPAWNS[levelRange];
                    break;
                }
                default: {
                    return;
                }
            }

            for (int[] spawn : npcSpawns) {
                final FestivalSpawn currSpawn = new FestivalSpawn(spawn);

                // Only spawn archers/marksmen if specified to do so.
                if ((spawnType == 1) && isFestivalArcher(currSpawn.npcId)) {
                    continue;
                }

                try {
                    final Spawn npcSpawn = new Spawn(currSpawn.npcId);
                    npcSpawn.setXYZ(currSpawn.x, currSpawn.y, currSpawn.z);
                    npcSpawn.setHeading(ClockBridge.random().nextInt(65536));
                    npcSpawn.setAmount(1);
                    npcSpawn.setRespawnDelay(respawnDelay);

                    // Needed as doSpawn() is required to be called also for the Npc it returns.
                    npcSpawn.startRespawn();

                    SpawnTable.getInstance().addSpawn(npcSpawn);
                    final FestivalMonster festivalMob = (FestivalMonster) npcSpawn.doSpawn();

                    // Set the offering bonus to 2x or 5x the amount per kill,
                    // if this spawn is part of an increased challenge or is a festival chest.
                    if (spawnType == 1) {
                        festivalMob.setOfferingBonus(2);
                    } else if (spawnType == 3) {
                        festivalMob.setOfferingBonus(5);
                    }

                    npcInsts.add(festivalMob);
                } catch (Exception e) {
                    log.warn("Error while spawning NPC ID {}: {}", currSpawn.npcId, e.getMessage(), e);
                }
            }
        }

        protected boolean increaseChallenge() {
            if (challengeIncreased) {
                return false;
            }

            // Set this flag to true to make sure that this can only be done once.
            challengeIncreased = true;

            // Spawn more festival monsters, but this time with a twist.
            spawnFestivalMonsters(FESTIVAL_DEFAULT_RESPAWN, 1);
            return true;
        }

        public void sendMessageToParticipants(NpcStringId npcStringId) {
            if ((participants != null) && !participants.isEmpty()) {
                witchInst.broadcastPacket(new CreatureSay(witchInst, ChatType.NPC_GENERAL, npcStringId));
            }
        }

        public void sendMessageToParticipants(String npcString) {
            if ((participants != null) && !participants.isEmpty()) {
                witchInst.broadcastPacket(
                        new CreatureSay(witchInst, ChatType.NPC_GENERAL, "Festival Witch", npcString));
            }
        }

        protected void festivalEnd() {
            if ((participants != null) && !participants.isEmpty()) {
                for (int participantObjId : participants) {
                    try {
                        final Player participant = World.getPlayer(participantObjId);
                        if (participant == null) {
                            continue;
                        }

                        relocatePlayer(participant, false);
                        participant.sendMessage(
                                "The festival has ended. Your party leader must now register your score before the"
                                        + " next festival takes place.");
                    } catch (Exception e) {
                        // Ignore.
                    }
                }

                if (cabal == SevenSigns.CABAL_DAWN) {
                    dawnPreviousParticipants.put(levelRange, participants);
                } else {
                    duskPreviousParticipants.put(levelRange, participants);
                }
            }

            participants = null;
            unspawnMobs();
        }

        protected void unspawnMobs() {
            // Delete all the NPCs in the current festival arena.
            if (witchInst != null) {
                witchInst.getSpawn().stopRespawn();
                witchInst.deleteMe();
                SpawnTable.getInstance().removeSpawn(witchInst.getSpawn());
            }

            for (FestivalMonster monsterInst : npcInsts) {
                if (monsterInst != null) {
                    monsterInst.getSpawn().stopRespawn();
                    monsterInst.deleteMe();
                    SpawnTable.getInstance().removeSpawn(monsterInst.getSpawn());
                }
            }
        }

        public void relocatePlayer(Player participant, boolean isRemoving) {
            try {
                final FestivalSpawn origPosition = originalLocations.get(participant.getObjectId());
                if (isRemoving) {
                    originalLocations.remove(participant.getObjectId());
                }

                participant.getAI().setIntentionIdle();
                participant.teleToLocation(new Location(origPosition.x, origPosition.y, origPosition.z), true);
                participant.sendMessage("You have been removed from the festival arena.");
            } catch (Exception e) {
                // If an exception occurs, just move the player to the nearest town.
                try {
                    participant.teleToLocation(TeleportWhereType.TOWN);
                    participant.sendMessage("You have been removed from the festival arena.");
                } catch (Exception e2) {
                    // Ignore.
                }
            }
        }
    }

    private static class FestivalSpawn {
        protected final int x;
        protected final int y;
        protected final int z;
        protected final int heading;
        protected final int npcId;

        protected FestivalSpawn(int x, int y, int z, int heading) {
            this.x = x;
            this.y = y;
            this.z = z;

            // Generate a random heading if no positive one given.
            this.heading = (heading < 0) ? ClockBridge.random().nextInt(65536) : heading;
            npcId = -1;
        }

        protected FestivalSpawn(int[] spawnData) {
            x = spawnData[0];
            y = spawnData[1];
            z = spawnData[2];
            heading = (spawnData[3] < 0) ? ClockBridge.random().nextInt(65536) : spawnData[3];
            if (spawnData.length > 4) {
                npcId = spawnData[4];
            } else {
                npcId = -1;
            }
        }
    }
}
