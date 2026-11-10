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
package app.l2nx.highfive.entity.clan;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.partition.OfflineRows;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.model.CharacterClanMember;

@SingleWriter
public class ClanMember {

    private final Clan clan;

    private int objectId;
    private String name;
    private String title;
    private int powerGrade;
    private int level;
    private int classId;
    private boolean sex;
    private int raceOrdinal;

    private Player player;

    private int pledgeType;
    private int apprentice;
    private int sponsor;

    public ClanMember(Clan clan, CharacterClanMember clanMember) {
        if (clan == null) {
            throw new IllegalArgumentException("Cannot create a Clan Member with a null clan.");
        }

        this.clan = clan;
        name = clanMember.name();
        level = clanMember.level();
        classId = clanMember.classId();
        objectId = clanMember.objectId();
        pledgeType = clanMember.pledgeType();
        title = clanMember.title();
        powerGrade = clanMember.powerGrade();
        apprentice = clanMember.apprenticeId();
        sponsor = clanMember.sponsorId();
        sex = clanMember.sex() == Sex.FEMALE;
        raceOrdinal = clanMember.race().ordinal();
    }

    public ClanMember(Clan clan, Player player) {
        if (clan == null) {
            throw new IllegalArgumentException("Cannot create a Clan Member if player has a null clan.");
        }

        this.player = player;
        this.clan = clan;
        name = player.getName();
        level = player.getLevel();
        classId = player.getPlayerClass().getId();
        objectId = player.getObjectId();
        pledgeType = player.getPledgeType();
        powerGrade = player.getPowerGrade();
        title = player.getTitle();
        sponsor = 0;
        apprentice = 0;
        sex = player.getAppearance().isFemale();
        raceOrdinal = player.getRace().ordinal();
    }

    public Clan getClan() {
        WorldPartition.checkOwner();
        return clan;
    }

    public Player getPlayer() {
        WorldPartition.checkOwner();
        return player;
    }

    public void setPlayer(Player player) {
        WorldPartition.checkOwner();
        if ((player == null) && (this.player != null)) {
            // Snapshot the data, since the member outlives the player instance.
            name = this.player.getName();
            level = this.player.getLevel();
            classId = this.player.getPlayerClass().getId();
            objectId = this.player.getObjectId();
            powerGrade = this.player.getPowerGrade();
            pledgeType = this.player.getPledgeType();
            title = this.player.getTitle();
            apprentice = this.player.getApprentice();
            sponsor = this.player.getSponsor();
            sex = this.player.getAppearance().isFemale();
            raceOrdinal = this.player.getRace().ordinal();
        }

        if (player != null) {
            clan.addSkillEffects(player);
            if ((clan.getLevel() > 3) && player.isClanLeader()) {
                SiegeManager.getInstance().addSiegeSkills(player);
            }

            if (player.isClanLeader()) {
                clan.setLeader(this);
            }
        }

        this.player = player;
    }

    public boolean isOnline() {
        WorldPartition.checkOwner();
        return (player != null) && player.isOnline() && !player.isInOfflineMode();
    }

    public int getClassId() {
        WorldPartition.checkOwner();
        return player != null ? player.getPlayerClass().getId() : classId;
    }

    public int getLevel() {
        WorldPartition.checkOwner();
        return player != null ? player.getLevel() : level;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return player != null ? player.getName() : name;
    }

    public int getObjectId() {
        WorldPartition.checkOwner();
        return player != null ? player.getObjectId() : objectId;
    }

    public String getTitle() {
        WorldPartition.checkOwner();
        return player != null ? player.getTitle() : title;
    }

    public int getPledgeType() {
        WorldPartition.checkOwner();
        return player != null ? player.getPledgeType() : pledgeType;
    }

    public void setPledgeType(int pledgeType) {
        WorldPartition.checkOwner();
        this.pledgeType = pledgeType;
        OfflineRows.whenSettled(getObjectId(), this::applyPledgeType);
    }

    private void applyPledgeType() {
        if (player != null) {
            player.setPledgeType(pledgeType);
        } else {
            updatePledgeType();
        }
    }

    public void updatePledgeType() {
        WorldPartition.checkOwner();
        ClanTable.getInstance().updateMemberPledgeType(getObjectId(), pledgeType);
    }

    public int getPowerGrade() {
        WorldPartition.checkOwner();
        return player != null ? player.getPowerGrade() : powerGrade;
    }

    public void setPowerGrade(int powerGrade) {
        WorldPartition.checkOwner();
        this.powerGrade = powerGrade;
        OfflineRows.whenSettled(getObjectId(), this::applyPowerGrade);
    }

    private void applyPowerGrade() {
        if (player != null) {
            player.setPowerGrade(powerGrade);
        } else {
            updatePowerGrade();
        }
    }

    public void updatePowerGrade() {
        WorldPartition.checkOwner();
        ClanTable.getInstance().updateMemberPowerGrade(getObjectId(), powerGrade);
    }

    public void setApprenticeAndSponsor(int apprenticeID, int sponsorID) {
        WorldPartition.checkOwner();
        apprentice = apprenticeID;
        sponsor = sponsorID;
    }

    public int getRaceOrdinal() {
        WorldPartition.checkOwner();
        return player != null ? player.getRace().ordinal() : raceOrdinal;
    }

    public boolean getSex() {
        WorldPartition.checkOwner();
        return player != null ? player.getAppearance().isFemale() : sex;
    }

    public int getSponsor() {
        WorldPartition.checkOwner();
        return player != null ? player.getSponsor() : sponsor;
    }

    public int getApprentice() {
        WorldPartition.checkOwner();
        return player != null ? player.getApprentice() : apprentice;
    }

    /**
     * @return the apprentice's or sponsor's name, "Error" when the id names no member of the clan any more (the client
     *     shows it as the name), or {@code null} when the member has neither.
     */
    public String getApprenticeOrSponsorName() {
        WorldPartition.checkOwner();
        if (player != null) {
            this.apprentice = player.getApprentice();
            this.sponsor = player.getSponsor();
        }

        if (this.apprentice != 0) {
            final ClanMember apprentice = clan.getClanMember(this.apprentice);
            if (apprentice != null) {
                return apprentice.getName();
            }

            return "Error";
        }

        if (this.sponsor != 0) {
            final ClanMember sponsor = clan.getClanMember(this.sponsor);
            if (sponsor != null) {
                return sponsor.getName();
            }

            return "Error";
        }

        return null;
    }

    public static int calculatePledgeClass(Player player) {
        WorldPartition.checkOwner();
        int pledgeClass = 0;
        if (player == null) {
            return pledgeClass;
        }

        final Clan clan = player.getClan();
        if (clan != null) {
            switch (clan.getLevel()) {
                case 4: {
                    if (player.isClanLeader()) {
                        pledgeClass = 3;
                    }
                    break;
                }
                case 5: {
                    if (player.isClanLeader()) {
                        pledgeClass = 4;
                    } else {
                        pledgeClass = 2;
                    }
                    break;
                }
                case 6: {
                    switch (player.getPledgeType()) {
                        case -1: {
                            pledgeClass = 1;
                            break;
                        }
                        case 100:
                        case 200: {
                            pledgeClass = 2;
                            break;
                        }
                        case 0: {
                            if (player.isClanLeader()) {
                                pledgeClass = 5;
                            } else {
                                switch (clan.getLeaderSubPledge(player.getObjectId())) {
                                    case 100:
                                    case 200: {
                                        pledgeClass = 4;
                                        break;
                                    }
                                    case -1:
                                    default: {
                                        pledgeClass = 3;
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
                case 7: {
                    switch (player.getPledgeType()) {
                        case -1: {
                            pledgeClass = 1;
                            break;
                        }
                        case 100:
                        case 200: {
                            pledgeClass = 3;
                            break;
                        }
                        case 1001:
                        case 1002:
                        case 2001:
                        case 2002: {
                            pledgeClass = 2;
                            break;
                        }
                        case 0: {
                            if (player.isClanLeader()) {
                                pledgeClass = 7;
                            } else {
                                switch (clan.getLeaderSubPledge(player.getObjectId())) {
                                    case 100:
                                    case 200: {
                                        pledgeClass = 6;
                                        break;
                                    }
                                    case 1001:
                                    case 1002:
                                    case 2001:
                                    case 2002: {
                                        pledgeClass = 5;
                                        break;
                                    }
                                    case -1:
                                    default: {
                                        pledgeClass = 4;
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
                case 8: {
                    switch (player.getPledgeType()) {
                        case -1: {
                            pledgeClass = 1;
                            break;
                        }
                        case 100:
                        case 200: {
                            pledgeClass = 4;
                            break;
                        }
                        case 1001:
                        case 1002:
                        case 2001:
                        case 2002: {
                            pledgeClass = 3;
                            break;
                        }
                        case 0: {
                            if (player.isClanLeader()) {
                                pledgeClass = 8;
                            } else {
                                switch (clan.getLeaderSubPledge(player.getObjectId())) {
                                    case 100:
                                    case 200: {
                                        pledgeClass = 7;
                                        break;
                                    }
                                    case 1001:
                                    case 1002:
                                    case 2001:
                                    case 2002: {
                                        pledgeClass = 6;
                                        break;
                                    }
                                    case -1:
                                    default: {
                                        pledgeClass = 5;
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
                case 9: {
                    switch (player.getPledgeType()) {
                        case -1: {
                            pledgeClass = 1;
                            break;
                        }
                        case 100:
                        case 200: {
                            pledgeClass = 5;
                            break;
                        }
                        case 1001:
                        case 1002:
                        case 2001:
                        case 2002: {
                            pledgeClass = 4;
                            break;
                        }
                        case 0: {
                            if (player.isClanLeader()) {
                                pledgeClass = 9;
                            } else {
                                switch (clan.getLeaderSubPledge(player.getObjectId())) {
                                    case 100:
                                    case 200: {
                                        pledgeClass = 8;
                                        break;
                                    }
                                    case 1001:
                                    case 1002:
                                    case 2001:
                                    case 2002: {
                                        pledgeClass = 7;
                                        break;
                                    }
                                    case -1:
                                    default: {
                                        pledgeClass = 6;
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
                case 10: {
                    switch (player.getPledgeType()) {
                        case -1: {
                            pledgeClass = 1;
                            break;
                        }
                        case 100:
                        case 200: {
                            pledgeClass = 6;
                            break;
                        }
                        case 1001:
                        case 1002:
                        case 2001:
                        case 2002: {
                            pledgeClass = 5;
                            break;
                        }
                        case 0: {
                            if (player.isClanLeader()) {
                                pledgeClass = 10;
                            } else {
                                switch (clan.getLeaderSubPledge(player.getObjectId())) {
                                    case 100:
                                    case 200: {
                                        pledgeClass = 9;
                                        break;
                                    }
                                    case 1001:
                                    case 1002:
                                    case 2001:
                                    case 2002: {
                                        pledgeClass = 8;
                                        break;
                                    }
                                    case -1:
                                    default: {
                                        pledgeClass = 7;
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
                case 11: {
                    switch (player.getPledgeType()) {
                        case -1: {
                            pledgeClass = 1;
                            break;
                        }
                        case 100:
                        case 200: {
                            pledgeClass = 7;
                            break;
                        }
                        case 1001:
                        case 1002:
                        case 2001:
                        case 2002: {
                            pledgeClass = 6;
                            break;
                        }
                        case 0: {
                            if (player.isClanLeader()) {
                                pledgeClass = 11;
                            } else {
                                switch (clan.getLeaderSubPledge(player.getObjectId())) {
                                    case 100:
                                    case 200: {
                                        pledgeClass = 10;
                                        break;
                                    }
                                    case 1001:
                                    case 1002:
                                    case 2001:
                                    case 2002: {
                                        pledgeClass = 9;
                                        break;
                                    }
                                    case -1:
                                    default: {
                                        pledgeClass = 8;
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
                default: {
                    pledgeClass = 1;
                    break;
                }
            }
        }

        if (player.isNoble() && (pledgeClass < 5)) {
            pledgeClass = 5;
        }

        if (player.isHero() && (pledgeClass < 8)) {
            pledgeClass = 8;
        }

        return pledgeClass;
    }

    public void saveApprenticeAndSponsor(int apprentice, int sponsor) {
        WorldPartition.checkOwner();
        ClanTable.getInstance().updateMemberApprenticeAndSponsor(getObjectId(), apprentice, sponsor);
    }
}
