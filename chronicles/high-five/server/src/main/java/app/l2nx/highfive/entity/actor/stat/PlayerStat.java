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
package app.l2nx.highfive.entity.actor.stat;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.data.holders.PetLevelData;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.SubClassHolder;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.actor.status.PlayerStatus;
import app.l2nx.highfive.entity.actor.templates.PlayerTemplate;
import app.l2nx.highfive.entity.actor.transform.TransformTemplate;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLevelChanged;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.mechanics.stats.MoveType;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExVitalityPointInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExVoteSystemInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowMemberListUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SocialAction;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;

@SingleWriter
public class PlayerStat extends PlayableStat {
    private int oldMaxHp;
    private int oldMaxMp;
    private int oldMaxCp;
    private float vitalityPoints = 1;
    private byte vitalityLevel = 0;

    private long startingExp;
    private int maxCubicCount = 1;
    private int talismanSlots;

    private boolean cloakSlot = false;
    private boolean pausedNevitHourglass = false;

    public static final int[] VITALITY_LEVELS = {240, 2000, 13000, 17000, 20000};

    public static final int MAX_VITALITY_POINTS = VITALITY_LEVELS[4];
    public static final int MIN_VITALITY_POINTS = 1;

    public PlayerStat(Player player, CreatureAccess access) {
        super(player, access);
    }

    @Override
    public boolean addExp(long value) {
        WorldPartition.checkOwner();
        final Player player = player();

        if (!player.getAccessLevel().canGainExp()) {
            return false;
        }

        if (!super.addExp(value)) {
            return false;
        }

        if (!player.isCursedWeaponEquipped() && (player.getKarma() > 0) && !player.isInsideZone(ZoneId.PVP)) {
            final int karmaLost = Formulas.calculateKarmaLost(player, value);
            if (karmaLost > 0) {
                player.setKarma(player.getKarma() - karmaLost);
                final SystemMessage msg = new SystemMessage(SystemMessageId.YOUR_KARMA_HAS_BEEN_CHANGED_TO_S1);
                msg.addInt(player.getKarma());
                player.sendPacket(msg);
            }
        }

        player.updateUserInfo();
        return true;
    }

    public boolean addExpAndSp(double addToExpValue, double addToSpValue, boolean useBonuses) {
        WorldPartition.checkOwner();
        final Player player = player();

        if (!player.getAccessLevel().canGainExp()) {
            return false;
        }

        double addToExp = addToExpValue;
        double addToSp = addToSpValue;

        final double baseExp = addToExp;
        final double baseSp = addToSp;
        double bonusExp = 1;
        double bonusSp = 1;

        if (PlayerConfig.NEVIT_ENABLED && (addToExp > 0) && !player.isInsideZone(ZoneId.PEACE)) {
            player.startNevitHourglassTask();

            if (player.getEffectList().getFirstEffect(EffectType.NEVITS_HOURGLASS) == null) {
                setPausedNevitHourglassStatus(false);
            }
        }

        if (useBonuses) {
            bonusExp = getExpBonusMultiplier();
            bonusSp = getSpBonusMultiplier();
        }

        if (PlayerConfig.NEVIT_ENABLED && (addToExp > 0) && !player.isInsideZone(ZoneId.PEACE)) {
            player.getNevitSystem().startAdventTask();
        }

        addToExp *= bonusExp;
        addToSp *= bonusSp;
        double ratioTakenByPlayer = 0;

        if (player.hasPet() && (player.calculateDistance3D(player.getSummon()) < PlayerConfig.ALT_PARTY_RANGE)) {
            final Pet pet = player.getSummon().asPet();
            ratioTakenByPlayer = pet.getPetLevelData().getOwnerExpTaken() / 100f;

            // Cap at 100%: a customized pet ratio above it would leave the owner negative exp/sp.
            if (ratioTakenByPlayer > 1) {
                ratioTakenByPlayer = 1;
            }

            if (!pet.isDead()) {
                pet.addExpAndSp(addToExp * (1 - ratioTakenByPlayer), addToSp * (1 - ratioTakenByPlayer));
            }

            addToExp *= ratioTakenByPlayer;
            addToSp *= ratioTakenByPlayer;
        }

        final long finalExp = Math.round(addToExp);
        final long finalSp = Math.round(addToSp);
        final boolean expAdded = addExp(finalExp);
        final boolean spAdded = addSp(finalSp);
        if (!expAdded && spAdded) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_ACQUIRED_S1_SP);
            sm.addLong(finalSp);
            player.sendPacket(sm);
        } else if (expAdded && !spAdded) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S1_EXPERIENCE);
            sm.addLong(finalExp);
            player.sendPacket(sm);
        } else if ((finalExp > 0) || (finalSp > 0)) {
            final SystemMessage sm;
            if ((addToExp - baseExp) > 0) {
                sm = new SystemMessage(SystemMessageId.YOU_HAVE_ACQUIRED_S1_EXP_BONUS_S2_AND_S3_SP_BONUS_S4);
                sm.addLong(finalExp);
                sm.addLong(Math.round(addToExp - baseExp));
                sm.addLong(finalSp);
                sm.addLong(Math.round(addToSp - baseSp));
            } else {
                sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S1_EXPERIENCE_AND_S2_SP);
                sm.addLong((long) addToExp);
                sm.addLong((long) addToSp);
            }

            player.sendPacket(sm);
        }

        return true;
    }

    @Override
    public boolean removeExpAndSp(long addToExp, long addToSp) {
        WorldPartition.checkOwner();
        return removeExpAndSp(addToExp, addToSp, true);
    }

    public boolean removeExpAndSp(long addToExp, long addToSp, boolean sendMessage) {
        WorldPartition.checkOwner();
        final int level = getLevel();
        if (!super.removeExpAndSp(addToExp, addToSp)) {
            return false;
        }

        if (sendMessage) {
            SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_EXPERIENCE_HAS_DECREASED_BY_S1);
            sm.addLong(addToExp);
            final Player player = player();
            player.sendPacket(sm);
            sm = new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1);
            sm.addLong(addToSp);
            player.sendPacket(sm);
            if (getLevel() < level) {
                player.broadcastStatusUpdate();
            }
        }

        return true;
    }

    @Override
    public boolean addLevel(byte value) {
        WorldPartition.checkOwner();
        if ((getLevel() + value) > (ExperienceData.getInstance().getMaxLevel() - 1)) {
            return false;
        }

        final Player player = player();
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_LEVEL_CHANGED, player)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerLevelChanged.builder()
                                    .player(player)
                                    .oldLevel(getLevel())
                                    .newLevel(getLevel() + value)
                                    .build(),
                            player);
        }

        final boolean levelIncreased = super.addLevel(value);
        if (levelIncreased) {
            if (!PlayerConfig.DISABLE_TUTORIAL) {
                final QuestState qs = player.getQuestState("Q00255_Tutorial");
                if (qs != null) {
                    qs.getQuest().notifyEvent("CE40", null, player);
                }
            }

            player.setCurrentCp(getMaxCp());
            player.broadcastPacket(new SocialAction(player.getObjectId(), SocialAction.LEVEL_UP));
            player.sendPacket(SystemMessageId.YOUR_LEVEL_HAS_INCREASED);
        }

        player.rewardSkills();

        final Clan clan = player.getClan();
        if (clan != null) {
            clan.updateClanMember(player);
            clan.broadcastToOnlineMembers(new PledgeShowMemberListUpdate(player));
        }

        if (player.isInParty()) {
            player.getParty().recalculatePartyLevel();
        }

        if (player.isTransformed() || player.isInStance()) {
            player.getTransformation().onLevelUp(player);
        }

        if (player.hasPet()) {
            final Pet pet = player.getSummon().asPet();
            if (pet.getPetData().isSynchLevel() && (pet.getLevel() != getLevel())) {
                final byte availableLevel = (byte) Math.min(pet.getPetData().getMaxLevel(), getLevel());
                pet.getStat().setLevel(availableLevel);
                pet.getStat().getExpForLevel(availableLevel);
                pet.setCurrentHp(pet.getMaxHp());
                pet.setCurrentMp(pet.getMaxMp());
                pet.broadcastPacket(new SocialAction(player.getObjectId(), SocialAction.LEVEL_UP));
                pet.updateAndBroadcastStatus(1);
            }
        }

        final StatusUpdate su = new StatusUpdate(player);
        su.addAttribute(StatusUpdate.LEVEL, getLevel());
        su.addAttribute(StatusUpdate.MAX_CP, getMaxCp());
        su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
        su.addAttribute(StatusUpdate.MAX_MP, getMaxMp());
        player.sendPacket(su);

        player.refreshOverloaded();

        player.refreshExpertisePenalty();

        player.updateUserInfo();

        if (PlayerConfig.NEVIT_ENABLED) {
            player.sendPacket(new ExVoteSystemInfo(player));
            player.getNevitSystem().addPoints(2000);
        }

        return levelIncreased;
    }

    @Override
    public boolean addSp(long value) {
        WorldPartition.checkOwner();
        if (!super.addSp(value)) {
            return false;
        }

        final Player player = player();
        final StatusUpdate su = new StatusUpdate(player);
        su.addAttribute(StatusUpdate.SP, (int) getSp());
        player.sendPacket(su);

        return true;
    }

    @Override
    public long getExpForLevel(int level) {
        WorldPartition.checkOwner();
        return ExperienceData.getInstance().getExpForLevel(level);
    }

    @Override
    public Player getActiveChar() {
        WorldPartition.checkOwner();
        return player();
    }

    @Override
    protected long exp() {
        final Player player = player();
        if (player.isSubClassActive()) {
            return player.getSubClass(player.getClassIndex()).getExp();
        }

        return super.exp();
    }

    public long getBaseExp() {
        WorldPartition.checkOwner();
        return super.exp();
    }

    @Override
    public void setExp(long value) {
        WorldPartition.checkOwner();
        final Player player = player();
        if (player.isSubClassActive()) {
            player.getSubClass(player.getClassIndex()).setExp(value);
        } else {
            super.setExp(value);
        }
    }

    public long getStartingExp() {
        WorldPartition.checkOwner();
        return startingExp;
    }

    public void setStartingExp(long value) {
        WorldPartition.checkOwner();
        if (GeneralConfig.BOTREPORT_ENABLE) {
            startingExp = value;
        }
    }

    public int getMaxCubicCount() {
        WorldPartition.checkOwner();
        return maxCubicCount;
    }

    public void setMaxCubicCount(int value) {
        WorldPartition.checkOwner();
        maxCubicCount = value;
    }

    public int getTalismanSlots() {
        WorldPartition.checkOwner();
        return talismanSlots;
    }

    public void addTalismanSlots(int count) {
        WorldPartition.checkOwner();
        talismanSlots += count;
    }

    public boolean canEquipCloak() {
        WorldPartition.checkOwner();
        if (!player().hasEnteredWorld()) {
            return true;
        }

        return cloakSlot;
    }

    public void setCloakSlotStatus(boolean cloakSlot) {
        WorldPartition.checkOwner();
        this.cloakSlot = cloakSlot;
    }

    public boolean hasPausedNevitHourglass() {
        WorldPartition.checkOwner();
        return pausedNevitHourglass;
    }

    public void setPausedNevitHourglassStatus(boolean value) {
        WorldPartition.checkOwner();
        pausedNevitHourglass = value;
        final Player player = player();
        player.sendPacket(new ExVoteSystemInfo(player));
    }

    @Override
    protected byte level() {
        final Player player = player();
        if (player.isSubClassActive()) {
            final SubClassHolder holder = player.getSubClass(player.getClassIndex());
            if (holder != null) {
                return holder.getLevel();
            }
        }

        return super.level();
    }

    public byte getBaseLevel() {
        WorldPartition.checkOwner();
        return super.level();
    }

    @Override
    public void setLevel(byte value) {
        WorldPartition.checkOwner();
        byte level = value;
        if (level > (ExperienceData.getInstance().getMaxLevel() - 1)) {
            level = (byte) (ExperienceData.getInstance().getMaxLevel() - 1);
        }

        final Player player = player();
        if (player.isSubClassActive()) {
            player.getSubClass(player.getClassIndex()).setLevel(value);
        } else {
            super.setLevel(level);
        }
    }

    @Override
    protected int maxCp() {
        // Get the Max CP (base+modifier) of the Player.
        final Player player = player();
        final int val = (player == null)
                ? 1
                : (int) calc(Stat.MAX_CP, ((PlayerTemplate) template()).getBaseCpMax(level()), null, null);
        if (val != oldMaxCp) {
            oldMaxCp = val;

            // Launch a regen task if the new Max CP is higher than the old one.
            if (player != null) {
                final PlayerStatus status = player.getStatus();
                if (status.getCurrentCp() != val) {
                    status.setCurrentCp(status.getCurrentCp()); // trigger start of regeneration
                }
            }
        }

        return val;
    }

    @Override
    protected int maxHp() {
        // Get the Max HP (base+modifier) of the Player.
        final Player player = player();
        final int val = (player == null)
                ? 1
                : (int) calc(Stat.MAX_HP, ((PlayerTemplate) template()).getBaseHpMax(level()), null, null);
        if (val != oldMaxHp) {
            oldMaxHp = val;

            // Launch a regen task if the new Max HP is higher than the old one.
            if (player != null) {
                final PlayerStatus status = player.getStatus();
                if (status.getCurrentHp() != val) {
                    status.setCurrentHp(status.getCurrentHp()); // trigger start of regeneration
                }
            }
        }

        return val;
    }

    @Override
    protected int maxMp() {
        // Get the Max MP (base+modifier) of the Player.
        final Player player = player();
        final int val = (player == null)
                ? 1
                : (int) calc(Stat.MAX_MP, ((PlayerTemplate) template()).getBaseMpMax(level()), null, null);
        if (val != oldMaxMp) {
            oldMaxMp = val;

            // Launch a regen task if the new Max MP is higher than the old one.
            if (player != null) {
                final PlayerStatus status = player.getStatus();
                if (status.getCurrentMp() != val) {
                    status.setCurrentMp(status.getCurrentMp()); // trigger start of regeneration
                }
            }
        }

        return val;
    }

    @Override
    protected long sp() {
        final Player player = player();
        if (player.isSubClassActive()) {
            return player.getSubClass(player.getClassIndex()).getSp();
        }

        return super.sp();
    }

    public long getBaseSp() {
        WorldPartition.checkOwner();
        return super.sp();
    }

    @Override
    public void setSp(long value) {
        WorldPartition.checkOwner();
        final Player player = player();
        if (player.isSubClassActive()) {
            player.getSubClass(player.getClassIndex()).setSp(value);
        } else {
            super.setSp(value);
        }
    }

    /**
     * @param type movement type
     * @return the base move speed of given movement type.
     */
    @Override
    protected double baseMoveSpeed(MoveType type) {
        final Player player = player();
        if (player.isTransformed()) {
            final TransformTemplate template = player.getTransformation().getTemplate(player);
            if (template != null) {
                return template.getBaseMoveSpeed(type);
            }
        } else if (player.isMounted()) {
            final PetLevelData data =
                    PetDataTable.getInstance().getPetLevelData(player.getMountNpcId(), player.getMountLevel());
            if (data != null) {
                return data.getSpeedOnRide(type);
            }
        }

        return super.baseMoveSpeed(type);
    }

    @Override
    protected double runSpeed() {
        double val = super.runSpeed() + PlayerConfig.RUN_SPD_BOOST;

        // Apply max run speed cap.
        final Player player = player();
        if ((val > PlayerConfig.MAX_RUN_SPEED) && !player.isGM()) {
            return PlayerConfig.MAX_RUN_SPEED;
        }

        // Check for mount penalties.
        if (player.isMounted()) {
            // if level diff with mount >= 10, it decreases move speed by 50%
            if ((player.getMountLevel() - player.getLevel()) >= 10) {
                val /= 2;
            }

            // if mount is hungry, it decreases move speed by 50%
            if (player.isHungry()) {
                val /= 2;
            }
        }

        return val;
    }

    @Override
    protected double walkSpeed() {
        double val = super.walkSpeed() + PlayerConfig.RUN_SPD_BOOST;

        // Apply max run speed cap.
        final Player player = player();
        if ((val > PlayerConfig.MAX_RUN_SPEED) && !player.isGM()) {
            return PlayerConfig.MAX_RUN_SPEED;
        }

        if (player.isMounted()) {
            // if level diff with mount >= 10, it decreases move speed by 50%
            if ((player.getMountLevel() - player.getLevel()) >= 10) {
                val /= 2;
            }

            // if mount is hungry, it decreases move speed by 50%
            if (player.isHungry()) {
                val /= 2;
            }
        }

        return val;
    }

    @Override
    protected double pAtkSpd() {
        final double val = super.pAtkSpd();
        if ((val > PlayerConfig.MAX_PATK_SPEED) && !player().isGM()) {
            return PlayerConfig.MAX_PATK_SPEED;
        }

        return val;
    }

    private void updateVitalityLevel(boolean quiet) {
        final byte level;
        if (vitalityPoints <= VITALITY_LEVELS[0]) {
            level = 0;
        } else if (vitalityPoints <= VITALITY_LEVELS[1]) {
            level = 1;
        } else if (vitalityPoints <= VITALITY_LEVELS[2]) {
            level = 2;
        } else if (vitalityPoints <= VITALITY_LEVELS[3]) {
            level = 3;
        } else {
            level = 4;
        }

        if (!quiet && (level != vitalityLevel)) {
            final Player player = player();
            if (level < vitalityLevel) {
                player.sendPacket(SystemMessageId.YOUR_VITALITY_HAS_DECREASED);
            } else {
                player.sendPacket(SystemMessageId.YOUR_VITALITY_HAS_INCREASED);
            }

            if (level == 0) {
                player.sendPacket(SystemMessageId.YOUR_VITALITY_IS_FULLY_EXHAUSTED);
            } else if (level == 4) {
                player.sendPacket(SystemMessageId.YOUR_VITALITY_IS_AT_MAXIMUM);
            }
        }

        vitalityLevel = level;
    }

    /*
     * Return current vitality points in integer format
     */
    public int getVitalityPoints() {
        WorldPartition.checkOwner();
        return (int) vitalityPoints;
    }

    /*
     * Set current vitality points to this value if quiet = true - does not send system messages
     */
    public void setVitalityPoints(int value, boolean quiet) {
        WorldPartition.checkOwner();
        final int points = Math.clamp(value, MIN_VITALITY_POINTS, MAX_VITALITY_POINTS);
        if (points == vitalityPoints) {
            return;
        }

        vitalityPoints = points;
        updateVitalityLevel(quiet);
        player().sendPacket(new ExVitalityPointInfo((int) vitalityPoints));
    }

    public void updateVitalityPoints(float value, boolean useRates, boolean quiet) {
        WorldPartition.checkOwner();
        if ((value == 0) || !PlayerConfig.ENABLE_VITALITY) {
            return;
        }

        float points = value;
        if (useRates) {
            final Player player = player();
            if (player.isLucky()) {
                return;
            }

            if (points < 0) // vitality consumed
            {
                int stat = (int) calc(Stat.VITALITY_CONSUME_RATE, 1, player, null);
                if (player.getNevitSystem().isAdventBlessingActive()) {
                    stat = -10; // increase Vitality During Blessing
                }

                if (stat == 0) {
                    return;
                }

                if (stat < 0) {
                    points = -points;
                }
            }

            if (points > 0) {
                // vitality increased
                points *= RatesConfig.RATE_VITALITY_GAIN;
            } else {
                // vitality decreased
                points *= RatesConfig.RATE_VITALITY_LOST;
            }
        }

        if (points > 0) {
            points = Math.min(vitalityPoints + points, MAX_VITALITY_POINTS);
        } else {
            points = Math.max(vitalityPoints + points, MIN_VITALITY_POINTS);
        }

        if (Math.abs(points - vitalityPoints) <= 1e-6) {
            return;
        }

        vitalityPoints = points;
        updateVitalityLevel(quiet);
    }

    public double getVitalityMultiplier() {
        WorldPartition.checkOwner();
        return vitalityMultiplier();
    }

    private double vitalityMultiplier() {
        double vitality = 1.0;
        if (PlayerConfig.ENABLE_VITALITY) {
            switch (effectiveVitalityLevel()) {
                case 1: {
                    vitality = RatesConfig.RATE_VITALITY_LEVEL_1;
                    break;
                }
                case 2: {
                    vitality = RatesConfig.RATE_VITALITY_LEVEL_2;
                    break;
                }
                case 3: {
                    vitality = RatesConfig.RATE_VITALITY_LEVEL_3;
                    break;
                }
                case 4: {
                    vitality = RatesConfig.RATE_VITALITY_LEVEL_4;
                    break;
                }
            }
        }

        return vitality;
    }

    /**
     * @return the vitalityLevel
     */
    public byte getVitalityLevel() {
        WorldPartition.checkOwner();
        return effectiveVitalityLevel();
    }

    private byte effectiveVitalityLevel() {
        if (player().getNevitSystem().isAdventBlessingActive()) {
            return 4;
        }

        return vitalityLevel;
    }

    public double getExpBonusMultiplier() {
        WorldPartition.checkOwner();
        double bonus = 1.0;
        double vitality = 1.0;
        double nevits = 1.0;
        double hunting = 1.0;
        double bonusExp = 1.0;

        // Bonus from Vitality System.
        vitality = vitalityMultiplier();

        // Bonus from Nevit's Blessing
        nevits = player().getNevitHourglassMultiplier();

        // Bonus from Nevit's Hunting
        // TODO: Nevit's hunting bonus

        // Bonus exp from skills.
        bonusExp = 1 + (calc(Stat.BONUS_EXP, 0, null, null) / 100);
        if (vitality > 1.0) {
            bonus += (vitality - 1);
        }

        if (nevits > 1.0) {
            bonus += (nevits - 1);
        }

        if (hunting > 1.0) {
            bonus += (hunting - 1);
        }

        if (bonusExp > 1) {
            bonus += (bonusExp - 1);
        }

        // Check for abnormal bonuses.
        bonus = Math.max(bonus, 1);
        if (PlayerConfig.MAX_BONUS_EXP > 0) {
            bonus = Math.min(bonus, PlayerConfig.MAX_BONUS_EXP);
        }

        return bonus;
    }

    public double getSpBonusMultiplier() {
        WorldPartition.checkOwner();
        double bonus = 1.0;
        double vitality = 1.0;
        double nevits = 1.0;
        double hunting = 1.0;
        double bonusSp = 1.0;

        // Bonus from Vitality System.
        vitality = vitalityMultiplier();

        // Bonus from Nevit's Blessing
        nevits = player().getNevitHourglassMultiplier();

        // Bonus from Nevit's Hunting
        // TODO: Nevit's hunting bonus

        // Bonus sp from skills.
        bonusSp = 1 + (calc(Stat.BONUS_SP, 0, null, null) / 100);
        if (vitality > 1.0) {
            bonus += (vitality - 1);
        }

        if (nevits > 1.0) {
            bonus += (nevits - 1);
        }

        if (hunting > 1.0) {
            bonus += (hunting - 1);
        }

        if (bonusSp > 1) {
            bonus += (bonusSp - 1);
        }

        // Check for abnormal bonuses.
        bonus = Math.max(bonus, 1);
        if (PlayerConfig.MAX_BONUS_SP > 0) {
            bonus = Math.min(bonus, PlayerConfig.MAX_BONUS_SP);
        }

        return bonus;
    }

    public double getBonusDropAdenaMultiplier() {
        WorldPartition.checkOwner();
        return 1 + (calc(Stat.BONUS_DROP_ADENA, 0, null, null) / 100);
    }

    public double getBonusDropAmountMultiplier() {
        WorldPartition.checkOwner();
        return 1 + (calc(Stat.BONUS_DROP_AMOUNT, 0, null, null) / 100);
    }

    public double getBonusDropRateMultiplier() {
        WorldPartition.checkOwner();
        return 1 + (calc(Stat.BONUS_DROP_RATE, 0, null, null) / 100);
    }

    public double getBonusSpoilRateMultiplier() {
        WorldPartition.checkOwner();
        return 1 + (calc(Stat.BONUS_SPOIL_RATE, 0, null, null) / 100);
    }

    private Player player() {
        return (Player) creature();
    }
}
