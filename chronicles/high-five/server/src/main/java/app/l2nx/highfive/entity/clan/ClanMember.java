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

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.SiegeManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class holds the clan members data.
 */
@Slf4j
public class ClanMember {

    @Getter
    private final Clan clan;

    private int objectId;
    private String name;
    private String title;
    private int powerGrade;
    private int level;
    private int classId;
    private boolean sex;
    private int raceOrdinal;

    @Getter
    private Player player;

    private int pledgeType;
    private int apprentice;
    private int sponsor;

    /**
     * Used to restore a clan member from the database.
     * @param clan the clan where the clan member belongs.
     * @param clanMember the clan member result set
     * @throws SQLException if the columnLabel is not valid or a database error occurs
     */
    public ClanMember(Clan clan, ResultSet clanMember) throws SQLException {
        if (clan == null) {
            throw new IllegalArgumentException("Cannot create a Clan Member with a null clan.");
        }

        this.clan = clan;
        name = clanMember.getString("char_name");
        level = clanMember.getInt("level");
        classId = clanMember.getInt("classid");
        objectId = clanMember.getInt("charId");
        pledgeType = clanMember.getInt("subpledge");
        title = clanMember.getString("title");
        powerGrade = clanMember.getInt("power_grade");
        apprentice = clanMember.getInt("apprentice");
        sponsor = clanMember.getInt("sponsor");
        sex = clanMember.getInt("sex") != 0;
        raceOrdinal = clanMember.getInt("race");
    }

    /**
     * Creates a clan member from a player instance.
     * @param clan the clan where the player belongs
     * @param player the player from which the clan member will be created
     */
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

    /**
     * Sets the player instance.
     * @param player the new player instance
     */
    public void setPlayer(Player player) {
        if ((player == null) && (this.player != null)) {
            // This is here to keep the data when the player logs off.
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

    /**
     * Verifies if the clan member is online.
     * @return {@code true} if is online
     */
    public boolean isOnline() {
        return (player != null) && player.isOnline() && !player.isInOfflineMode();
    }

    /**
     * Gets the class id.
     * @return the classId
     */
    public int getClassId() {
        return player != null ? player.getPlayerClass().getId() : classId;
    }

    /**
     * Gets the level.
     * @return the level
     */
    public int getLevel() {
        return player != null ? player.getLevel() : level;
    }

    /**
     * Gets the name.
     * @return the name
     */
    public String getName() {
        return player != null ? player.getName() : name;
    }

    /**
     * Gets the object id.
     * @return Returns the objectId.
     */
    public int getObjectId() {
        return player != null ? player.getObjectId() : objectId;
    }

    /**
     * Gets the title.
     * @return the title
     */
    public String getTitle() {
        return player != null ? player.getTitle() : title;
    }

    /**
     * Gets the pledge type.
     * @return the pledge type
     */
    public int getPledgeType() {
        return player != null ? player.getPledgeType() : pledgeType;
    }

    /**
     * Sets the pledge type.
     * @param pledgeType the new pledge type
     */
    public void setPledgeType(int pledgeType) {
        this.pledgeType = pledgeType;
        if (player != null) {
            player.setPledgeType(pledgeType);
        } else {
            // Db save if char not logged in.
            updatePledgeType();
        }
    }

    /**
     * Update pledge type.
     */
    public void updatePledgeType() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE characters SET subpledge=? WHERE charId=?")) {
            ps.setLong(1, pledgeType);
            ps.setInt(2, getObjectId());
            ps.execute();
        } catch (Exception e) {
            log.warn("Could not update pledge type: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the power grade.
     * @return the power grade
     */
    public int getPowerGrade() {
        return player != null ? player.getPowerGrade() : powerGrade;
    }

    /**
     * Sets the power grade.
     * @param powerGrade the new power grade
     */
    public void setPowerGrade(int powerGrade) {
        this.powerGrade = powerGrade;
        if (player != null) {
            player.setPowerGrade(powerGrade);
        } else {
            // Db save if char not logged in.
            updatePowerGrade();
        }
    }

    /**
     * Update the characters table of the database with power grade.
     */
    public void updatePowerGrade() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE characters SET power_grade=? WHERE charId=?")) {
            ps.setLong(1, powerGrade);
            ps.setInt(2, getObjectId());
            ps.execute();
        } catch (Exception e) {
            log.warn("Could not update power _grade: {}", e.getMessage(), e);
        }
    }

    /**
     * Sets the apprentice and sponsor.
     * @param apprenticeID the apprentice id
     * @param sponsorID the sponsor id
     */
    public void setApprenticeAndSponsor(int apprenticeID, int sponsorID) {
        apprentice = apprenticeID;
        sponsor = sponsorID;
    }

    /**
     * Gets the player's race ordinal.
     * @return the race ordinal
     */
    public int getRaceOrdinal() {
        return player != null ? player.getRace().ordinal() : raceOrdinal;
    }

    /**
     * Gets the player's sex.
     * @return true if female
     */
    public boolean getSex() {
        return player != null ? player.getAppearance().isFemale() : sex;
    }

    /**
     * Gets the sponsor.
     * @return the sponsor
     */
    public int getSponsor() {
        return player != null ? player.getSponsor() : sponsor;
    }

    /**
     * Gets the apprentice.
     * @return the apprentice
     */
    public int getApprentice() {
        return player != null ? player.getApprentice() : apprentice;
    }

    /**
     * Gets the apprentice or sponsor name.
     * @return the apprentice or sponsor name
     */
    public String getApprenticeOrSponsorName() {
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

        return "";
    }

    /**
     * Calculate pledge class.
     * @param player the player
     * @return the int
     */
    public static int calculatePledgeClass(Player player) {
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

    /**
     * Save apprentice and sponsor.
     * @param apprentice the apprentice
     * @param sponsor the sponsor
     */
    public void saveApprenticeAndSponsor(int apprentice, int sponsor) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE characters SET apprentice=?,sponsor=? WHERE charId=?")) {
            ps.setInt(1, apprentice);
            ps.setInt(2, sponsor);
            ps.setInt(3, getObjectId());
            ps.execute();
        } catch (SQLException e) {
            log.warn("Could not save apprentice/sponsor: {}", e.getMessage(), e);
        }
    }
}
