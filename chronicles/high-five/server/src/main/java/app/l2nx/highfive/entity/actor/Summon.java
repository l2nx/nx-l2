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
package app.l2nx.highfive.entity.actor;

import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.ai.SummonAI;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeoEngineConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.FakePlayersConfig;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.holders.npc.AggroInfo;
import app.l2nx.highfive.entity.actor.stat.SummonStat;
import app.l2nx.highfive.entity.actor.status.SummonStatus;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.ActionType;
import app.l2nx.highfive.entity.itemcontainer.PetInventory;
import app.l2nx.highfive.entity.zone.ZoneRegion;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.geoengine.pathfinding.PathFinding;
import app.l2nx.highfive.handler.IItemHandler;
import app.l2nx.highfive.handler.ItemHandler;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerSummonSpawn;
import app.l2nx.highfive.mechanics.olympiad.OlympiadGameManager;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.StoredEffect;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.AbstractNpcInfo.SummonInfo;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExPartyPetWindowAdd;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExPartyPetWindowDelete;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExPartyPetWindowUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PetDelete;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PetInfo;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PetItemList;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PetStatusUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.RelationChanged;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.TeleportToLocation;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.ArrayUtil;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.Setter;

public abstract class Summon extends Playable {
    @Getter
    @Setter
    private Player owner;

    @Getter
    private int attackRange = 36;

    private boolean followStatus = true;
    private boolean previousFollowStatus = true;
    protected boolean restoreSummon = true;
    private int shotsMask = 0;
    private ScheduledFuture<?> abnormalEffectTask;

    // @formatter:off
    private static final int[] PASSIVE_SUMMONS = {
        12564, 12621, 14702, 14703, 14704, 14705, 14706, 14707, 14708, 14709, 14710, 14711, 14712, 14713, 14714, 14715,
        14716, 14717, 14718, 14719, 14720, 14721, 14722, 14723, 14724, 14725, 14726, 14727, 14728, 14729, 14730, 14731,
        14732, 14733, 14734, 14735, 14736
    };
    // @formatter:on

    public Summon(NpcTemplate template, Player owner) {
        super(template);
        setInstanceType(InstanceType.Summon);
        setInstanceId(owner.getInstanceId());
        setShowSummonAnimation(true);
        this.owner = owner;
        getAI();

        final int x = owner.getX();
        final int y = owner.getY();
        final int z = owner.getZ();
        final Location location = GeoEngine.getInstance()
                .getValidLocation(
                        x,
                        y,
                        z,
                        x + ClockBridge.random().nextInt(-100, 101),
                        y + ClockBridge.random().nextInt(-100, 101),
                        z,
                        owner.getInstanceId());
        setXYZInvisible(location.getX(), location.getY(), location.getZ());
    }

    @Override
    public void onSpawn() {
        super.onSpawn();

        if (PlayerConfig.SUMMON_STORE_SKILL_COOLTIME && !isTeleporting()) {
            restoreEffects();
        }

        setFollowStatus(true);
        updateAndBroadcastStatus(0);
        sendPacket(new RelationChanged(this, owner.getRelation(owner), false));
        World.forEachVisibleObjectInRange(
                getOwner(),
                Player.class,
                800,
                player -> player.sendPacket(
                        new RelationChanged(this, owner.getRelation(player), isAutoAttackable(player))));
        final Party party = owner.getParty();
        if (party != null) {
            party.broadcastToPartyMembers(owner, new ExPartyPetWindowAdd(this));
        }

        setShowSummonAnimation(false);

        // If someone comes into range now, the animation shouldn't show any more.
        restoreSummon = false;
        rechargeShots(true, true);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_SUMMON_SPAWN, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerSummonSpawn(this), this);
        }
    }

    @Override
    public SummonStat getStat() {
        return (SummonStat) super.getStat();
    }

    @Override
    public void initCharStat() {
        setStat(new SummonStat(this));
    }

    @Override
    public SummonStatus getStatus() {
        return (SummonStatus) super.getStatus();
    }

    @Override
    public void initCharStatus() {
        setStatus(new SummonStatus(this));
    }

    @Override
    protected CreatureAI initAI() {
        return new SummonAI(this);
    }

    @Override
    public NpcTemplate getTemplate() {
        return (NpcTemplate) super.getTemplate();
    }

    // This defines the action buttons, 1 for Summon, 2 for Pets.
    public abstract int getSummonType();

    @Override
    public void stopAllEffects() {
        super.stopAllEffects();
        updateAndBroadcastStatus(1);
    }

    @Override
    public void stopAllEffectsExceptThoseThatLastThroughDeath() {
        super.stopAllEffectsExceptThoseThatLastThroughDeath();
        updateAndBroadcastStatus(1);
    }

    @Override
    public void updateAbnormalEffect() {
        if (abnormalEffectTask == null) {
            abnormalEffectTask = ThreadPool.schedule(
                    () -> {
                        if (isSpawned()) {
                            World.forEachVisibleObject(
                                    this, Player.class, player -> player.sendPacket(new SummonInfo(this, player, 1)));
                        }

                        abnormalEffectTask = null;
                    },
                    50);
        }
    }

    public boolean isMountable() {
        return false;
    }

    public long getExpForThisLevel() {
        if (getLevel() >= ExperienceData.getInstance().getMaxPetLevel()) {
            return 0;
        }

        return ExperienceData.getInstance().getExpForLevel(getLevel());
    }

    public long getExpForNextLevel() {
        if (getLevel() >= (ExperienceData.getInstance().getMaxPetLevel() - 1)) {
            return 0;
        }

        return ExperienceData.getInstance().getExpForLevel(getLevel() + 1);
    }

    @Override
    public int getKarma() {
        return owner != null ? owner.getKarma() : 0;
    }

    @Override
    public byte getPvpFlag() {
        return owner != null ? owner.getPvpFlag() : 0;
    }

    @Override
    public Team getTeam() {
        return owner != null ? owner.getTeam() : Team.NONE;
    }

    @Override
    public int getId() {
        return getTemplate().getId();
    }

    public short getSoulShotsPerHit() {
        if (getTemplate().getSoulShot() > 0) {
            return (short) getTemplate().getSoulShot();
        }

        return 1;
    }

    public short getSpiritShotsPerHit() {
        if (getTemplate().getSpiritShot() > 0) {
            return (short) getTemplate().getSpiritShot();
        }

        return 1;
    }

    public void followOwner() {
        setFollowStatus(true);
    }

    protected Map<Skill, StoredEffect> effectsToStore() {
        final Map<Skill, StoredEffect> effects = new LinkedHashMap<>();
        final Set<Integer> storedSkills = new HashSet<>();
        for (BuffInfo info : getEffectList().getEffects()) {
            if (info == null) {
                continue;
            }

            final Skill skill = info.getSkill();

            if (skill.getAbnormalType() == AbnormalType.LIFE_FORCE_OTHERS) {
                continue;
            }

            if (skill.isToggle()) {
                continue;
            }

            // Dances and songs are not kept in retail.
            if (skill.isDance() && !PlayerConfig.ALT_STORE_DANCES) {
                continue;
            }

            if (storedSkills.contains(skill.getReuseHashCode())) {
                continue;
            }

            final StoredEffect stored = info.toStoredEffect();
            if (stored == null) {
                continue;
            }

            storedSkills.add(skill.getReuseHashCode());
            effects.put(skill, stored);
        }

        return effects;
    }

    @Override
    public boolean doDie(Creature killer) {
        if (isNoblesseBlessedAffected()) {
            stopEffects(EffectType.NOBLESSE_BLESSING);
            storeEffect(true);
        } else {
            storeEffect(false);
        }

        if (!super.doDie(killer)) {
            return false;
        }

        if (owner != null) {
            World.forEachVisibleObject(this, Attackable.class, targetMob -> {
                if (targetMob.isDead()) {
                    return;
                }

                final AggroInfo info = targetMob.getAggroList().get(this);
                if (info != null) {
                    targetMob.addDamageHate(owner, info.getDamage(), info.getHate());
                }
            });
        }

        addKillerHate(killer);

        DecayTaskManager.getInstance().add(this);
        return true;
    }

    public boolean doDie(Creature killer, boolean decayed) {
        if (!super.doDie(killer)) {
            return false;
        }

        addKillerHate(killer);

        if (!decayed) {
            DecayTaskManager.getInstance().add(this);
        }

        return true;
    }

    /**
     * Retail monster AIs add 500 hate against the owner to the monster that finishes a summon or pet.
     * @param killer the creature that killed this summon
     */
    private void addKillerHate(Creature killer) {
        if ((owner != null) && (killer != null) && killer.isAttackable() && !killer.isDead()) {
            killer.asAttackable().addDamageHate(owner, 0, 500);
        }
    }

    public void stopDecay() {
        DecayTaskManager.getInstance().cancel(this);
    }

    @Override
    public void onDecay() {
        deleteMe(owner);
    }

    @Override
    public void broadcastStatusUpdate() {
        super.broadcastStatusUpdate();
        updateAndBroadcastStatus(1);
    }

    public void deleteMe(Player owner) {
        if (owner != null) {
            owner.sendPacket(new PetDelete(getSummonType(), getObjectId()));
            final Party party = owner.getParty();
            if (party != null) {
                party.broadcastToPartyMembers(owner, new ExPartyPetWindowDelete(this));
            }
        }

        if (getInventory() != null) {
            getInventory().destroyAllItems(ItemProcessType.DESTROY, this.owner, this);
        }

        decayMe();
        if (owner != null) {
            owner.setPet(null);
        }

        super.deleteMe();
    }

    public void unSummon(Player owner) {
        if (isSpawned() && !isDead()) {
            stopHpMpRegeneration();

            getAI().stopFollow();
            if (owner != null) {
                owner.sendPacket(new PetDelete(getSummonType(), getObjectId()));
                final Party party = owner.getParty();
                if (party != null) {
                    party.broadcastToPartyMembers(owner, new ExPartyPetWindowDelete(this));
                }

                if ((getInventory() != null) && (getInventory().getSize() > 0)) {
                    this.owner.setPetInvItems(true);
                    sendPacket(
                            SystemMessageId
                                    .THERE_ARE_ITEMS_IN_YOUR_PET_INVENTORY_RENDERING_YOU_UNABLE_TO_SELL_TRADE_DROP_PET_SUMMONING_ITEMS_PLEASE_EMPTY_YOUR_PET_INVENTORY);
                } else {
                    this.owner.setPetInvItems(false);
                }
            }

            abortAttack();
            abortCast();
            storeMe();
            storeEffect(true);
            if (owner != null) {
                owner.setPet(null);
            }

            if (hasAI()) {
                getAI().stopAITask();
            }

            stopAllEffects();
            final ZoneRegion oldRegion = ZoneManager.getInstance().getRegion(this);
            decayMe();
            oldRegion.removeFromZones(this);

            setTarget(null);
            if (owner != null) {
                for (int itemId : owner.getAutoSoulShot()) {
                    final String handler = ((EtcItem) ItemData.getInstance().getTemplate(itemId)).getHandlerName();
                    if ((handler != null) && handler.contains("Beast")) {
                        owner.disableAutoShot(itemId);
                    }
                }
            }
        }
    }

    public void setAttackRange(int range) {
        attackRange = (range < 36) ? 36 : range;
    }

    public void setFollowStatus(boolean value) {
        followStatus = value;
        if (followStatus) {
            getAI().setIntentionFollow(owner);
        } else {
            getAI().setIntentionIdle();
        }
    }

    public boolean getFollowStatus() {
        return followStatus;
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        return (owner != null) && owner.isAutoAttackable(attacker);
    }

    public int getControlObjectId() {
        return 0;
    }

    public Weapon getActiveWeapon() {
        return null;
    }

    @Override
    public PetInventory getInventory() {
        return null;
    }

    public void setRestoreSummon(boolean value) {}

    @Override
    public Item getActiveWeaponInstance() {
        return null;
    }

    @Override
    public Weapon getActiveWeaponItem() {
        return null;
    }

    @Override
    public Item getSecondaryWeaponInstance() {
        return null;
    }

    @Override
    public Weapon getSecondaryWeaponItem() {
        return null;
    }

    @Override
    public boolean isInvul() {
        return super.isInvul() || owner.isSpawnProtected();
    }

    @Override
    public Party getParty() {
        if (owner == null) {
            return null;
        }

        return owner.getParty();
    }

    @Override
    public boolean isInParty() {
        return (owner != null) && owner.isInParty();
    }

    @Override
    public boolean useMagic(Skill skill, boolean forceUse, boolean dontMove) {
        if ((skill == null) || isDead() || (owner == null)) {
            return false;
        }

        if (skill.isPassive()) {
            return false;
        }

        if (isCastingNow()) {
            return false;
        }

        owner.setCurrentPetSkill(skill, forceUse, dontMove);

        WorldObject target = null;
        switch (skill.getTargetType()) {
            // OWNER_PET should be cast even if no target has been found
            case OWNER_PET: {
                target = owner;
                break;
            }
            // PARTY, AURA, SELF should be cast even if no target has been found
            case PARTY:
            case AURA:
            case FRONT_AURA:
            case BEHIND_AURA:
            case SELF:
            case AURA_CORPSE_MOB:
            case COMMAND_CHANNEL: {
                target = this;
                break;
            }
            default: {
                target = skill.getFirstOfTargetList(this);
                break;
            }
        }

        if (target == null) {
            sendPacket(SystemMessageId.YOUR_TARGET_CANNOT_BE_FOUND);
            return false;
        }

        if (isSkillDisabled(skill)) {
            sendPacket(SystemMessageId.THAT_PET_SERVITOR_SKILL_CANNOT_BE_USED_BECAUSE_IT_IS_RECHARGING);
            return false;
        }

        if (getCurrentMp() < (getStat().getMpConsume(skill) + getStat().getMpInitialConsume(skill))) {
            sendPacket(SystemMessageId.NOT_ENOUGH_MP);
            return false;
        }

        if (getCurrentHp() <= skill.getHpConsume()) {
            sendPacket(SystemMessageId.NOT_ENOUGH_HP);
            return false;
        }

        if ((this != target)
                && skill.isPhysical()
                && (GeoEngineConfig.PATHFINDING > 0)
                && (PathFinding.getInstance()
                                .findPath(
                                        getX(),
                                        getY(),
                                        getZ(),
                                        target.getX(),
                                        target.getY(),
                                        target.getZ(),
                                        getInstanceId(),
                                        false)
                        == null)) {
            sendPacket(SystemMessageId.CANNOT_SEE_TARGET);
            return false;
        }

        if (skill.hasNegativeEffect()) {
            if (owner == target) {
                return false;
            }

            // Summons can cast skills on NPCs inside peace zones.
            if (isInsidePeaceZone(this, target) && !owner.getAccessLevel().allowPeaceAttack()) {
                sendPacket(SystemMessageId.YOU_MAY_NOT_ATTACK_THIS_TARGET_IN_A_PEACEFUL_ZONE);
                return false;
            }

            if (owner.isInOlympiadMode() && !owner.isOlympiadStart()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            if (owner.isSiegeFriend(target)) {
                if (TerritoryWarManager.getInstance().isTWInProgress()) {
                    sendPacket(SystemMessageId.YOU_CANNOT_FORCE_ATTACK_A_MEMBER_OF_THE_SAME_TERRITORY);
                } else {
                    sendPacket(
                            SystemMessageId
                                    .FORCE_ATTACK_IS_IMPOSSIBLE_AGAINST_A_TEMPORARY_ALLIED_MEMBER_DURING_A_SIEGE);
                }

                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            if (target.isDoor()) {
                if (!target.isAutoAttackable(owner)) {
                    return false;
                }
            } else {
                if (!target.canBeAttacked() && !owner.getAccessLevel().allowPeaceAttack()) {
                    return false;
                }

                if (!target.isAutoAttackable(this)
                        && !forceUse
                        && !target.isNpc()
                        && (skill.getTargetType() != TargetType.AURA)
                        && (skill.getTargetType() != TargetType.FRONT_AURA)
                        && (skill.getTargetType() != TargetType.BEHIND_AURA)
                        && (skill.getTargetType() != TargetType.CLAN)
                        && (skill.getTargetType() != TargetType.PARTY)
                        && (skill.getTargetType() != TargetType.SELF)) {
                    return false;
                }
            }
        }

        getAI().setIntentionCast(skill, target);
        return true;
    }

    @Override
    public void setImmobilized(boolean value) {
        super.setImmobilized(value);

        if (value) {
            previousFollowStatus = followStatus;

            if (previousFollowStatus) {
                setFollowStatus(false);
            }
        } else {
            setFollowStatus(previousFollowStatus);
        }
    }

    @Override
    public void sendDamageMessage(Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss) {
        if (miss || (owner == null)) {
            return;
        }

        // Prevents the double spam of system messages, if the target is the owning player.
        if (target.getObjectId() != owner.getObjectId()) {
            if (pcrit || mcrit) {
                if (isServitor()) {
                    sendPacket(SystemMessageId.SUMMONED_MONSTER_S_CRITICAL_HIT);
                } else {
                    sendPacket(SystemMessageId.PET_S_CRITICAL_HIT);
                }
            }

            if (owner.isInOlympiadMode()
                    && target.isPlayer()
                    && target.asPlayer().isInOlympiadMode()
                    && (target.asPlayer().getOlympiadGameId() == owner.getOlympiadGameId())) {
                OlympiadGameManager.getInstance().notifyCompetitorDamage(getOwner(), damage);
            }

            final SystemMessage sm;
            if (target.isInvul() && !target.isNpc()) {
                sm = new SystemMessage(SystemMessageId.THE_ATTACK_HAS_BEEN_BLOCKED);
            } else {
                sm = new SystemMessage(SystemMessageId.C1_HAS_DONE_S3_POINTS_OF_DAMAGE_TO_C2);
                sm.addNpcName(this);
                sm.addString(target.getName());
                sm.addInt(damage);
            }

            sendPacket(sm);
        }
    }

    @Override
    public void reduceCurrentHp(double damage, Creature attacker, Skill skill) {
        super.reduceCurrentHp(damage, attacker, skill);
        if ((owner != null) && (attacker != null)) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_RECEIVED_S3_DAMAGE_FROM_C2);
            sm.addNpcName(this);
            sm.addString(attacker.getName());
            sm.addInt((int) damage);
            sendPacket(sm);
        }
    }

    @Override
    public void doCast(Skill skill) {
        final Player actingPlayer = getOwner();
        if (!actingPlayer.checkPvpSkill(getTarget(), skill)
                && !actingPlayer.getAccessLevel().allowPeaceAttack()) {
            actingPlayer.sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);

            actingPlayer.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        super.doCast(skill);
    }

    @Override
    public boolean isInCombat() {
        return (owner != null) && owner.isInCombat();
    }

    @Override
    public Player asPlayer() {
        return owner;
    }

    public void updateAndBroadcastStatus(int value) {
        if (owner == null) {
            return;
        }

        if (isSpawned()) {
            sendPacket(new PetInfo(this, value));
            sendPacket(new PetStatusUpdate(this));
            broadcastNpcInfo(value);

            final Party party = owner.getParty();
            if (party != null) {
                party.broadcastToPartyMembers(owner, new ExPartyPetWindowUpdate(this));
            }

            updateEffectIcons(true);
        }
    }

    public void broadcastNpcInfo(int value) {
        World.forEachVisibleObject(this, Player.class, player -> {
            if (player == owner) {
                return;
            }

            player.sendPacket(new SummonInfo(this, player, value));
        });
    }

    public boolean isHungry() {
        return false;
    }

    public int getWeapon() {
        return 0;
    }

    public int getArmor() {
        return 0;
    }

    @Override
    public void sendInfo(Player player) {
        if (player == owner) {
            player.sendPacket(new PetInfo(this, 0));

            // The PetInfo packet wipes the PartySpelled (list of active spells' icons). Re-add them.
            updateEffectIcons(true);
            if (isPet()) {
                player.sendPacket(new PetItemList(getInventory().getItems()));
            }
        } else {
            player.sendPacket(new SummonInfo(this, player, 0));
        }
    }

    @Override
    public synchronized void onTeleported() {
        super.onTeleported();
        sendPacket(new TeleportToLocation(this, getX(), getY(), getZ(), getHeading()));
    }

    @Override
    public boolean isUndead() {
        return getTemplate().getRace() == Race.UNDEAD;
    }

    public void switchMode() {}

    public void cancelAction() {
        if (!isMovementDisabled()) {
            getAI().setIntentionActive();
        }
    }

    public void doSummonAttack(WorldObject target) {
        if ((owner != null) && (target != null)) {
            setTarget(target);
            getAI().setIntentionAttack(target);
            if (target.isFakePlayer() && !FakePlayersConfig.FAKE_PLAYER_AUTO_ATTACKABLE) {
                owner.updatePvPStatus();
            }
        }
    }

    public boolean canAttack(boolean ctrlPressed) {
        if (owner == null) {
            return false;
        }

        final WorldObject target = owner.getTarget();
        if ((target == null) || (this == target) || (owner == target)) {
            return false;
        }

        // Sin eater, Big Boom, Wyvern can't attack with attack button.
        final int npcId = getId();
        if (ArrayUtil.contains(PASSIVE_SUMMONS, npcId)) {
            owner.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (isBetrayed()) {
            sendPacket(SystemMessageId.YOUR_PET_SERVITOR_IS_UNRESPONSIVE_AND_WILL_NOT_OBEY_ANY_ORDERS);
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (isPet() && ((getLevel() - owner.getLevel()) > 20)) {
            sendPacket(SystemMessageId.YOUR_PET_IS_TOO_HIGH_LEVEL_TO_CONTROL);
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (owner.isInOlympiadMode() && !owner.isOlympiadStart()) {
            owner.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (owner.isSiegeFriend(target)) {
            if (TerritoryWarManager.getInstance().isTWInProgress()) {
                sendPacket(SystemMessageId.YOU_CANNOT_FORCE_ATTACK_A_MEMBER_OF_THE_SAME_TERRITORY);
            } else {
                sendPacket(SystemMessageId.FORCE_ATTACK_IS_IMPOSSIBLE_AGAINST_A_TEMPORARY_ALLIED_MEMBER_DURING_A_SIEGE);
            }

            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (!owner.getAccessLevel().allowPeaceAttack() && owner.isInsidePeaceZone(this, target)) {
            sendPacket(SystemMessageId.YOU_MAY_NOT_ATTACK_THIS_TARGET_IN_A_PEACEFUL_ZONE);
            return false;
        }

        if (isLockedTarget()) {
            sendPacket(SystemMessageId.FAILED_TO_CHANGE_ATTACK_TARGET);
            return false;
        }

        // Summons can attack NPCs even when the owner cannot.
        if (!target.isAutoAttackable(owner) && !ctrlPressed && !target.isNpc()) {
            setFollowStatus(false);
            getAI().setIntentionFollow(target);
            sendPacket(SystemMessageId.INVALID_TARGET);
            return false;
        }

        // Siege golem AI only supports attacking doors and walls.
        if (target.isDoor() && (getTemplate().getRace() != Race.SIEGE_WEAPON)) {
            return false;
        }

        if (owner.calculateDistance3D(target) > 3000) {
            getAI().setIntentionFollow(owner);
            sendPacket(SystemMessageId.YOUR_TARGET_IS_OUT_OF_RANGE);
            return false;
        }

        if (!GeoEngine.getInstance().canSeeTarget(this, target)) {
            getAI().setIntentionFollow(owner);
            sendPacket(SystemMessageId.CANNOT_SEE_TARGET);
            return false;
        }

        return true;
    }

    @Override
    public void sendPacket(ServerPacket packet) {
        if (owner != null) {
            owner.sendPacket(packet);
        }
    }

    @Override
    public void sendPacket(SystemMessageId id) {
        if (owner != null) {
            owner.sendPacket(id);
        }
    }

    @Override
    public boolean isSummon() {
        return true;
    }

    @Override
    public Summon asSummon() {
        return this;
    }

    @Override
    public boolean isChargedShot(ShotType type) {
        return (shotsMask & type.getMask()) == type.getMask();
    }

    @Override
    public void setChargedShot(ShotType type, boolean charged) {
        if (charged) {
            shotsMask |= type.getMask();
        } else {
            shotsMask &= ~type.getMask();
        }
    }

    @Override
    public void rechargeShots(boolean physical, boolean magic) {
        Item item;
        IItemHandler handler;
        if ((owner.getAutoSoulShot() == null) || owner.getAutoSoulShot().isEmpty()) {
            return;
        }

        for (int itemId : owner.getAutoSoulShot()) {
            item = owner.getInventory().getItemByItemId(itemId);
            if (item != null) {
                if (magic && (item.getTemplate().getDefaultAction() == ActionType.SUMMON_SPIRITSHOT)) {
                    handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
                    if (handler != null) {
                        handler.onItemUse(owner, item, false);
                    }
                }

                if (physical && (item.getTemplate().getDefaultAction() == ActionType.SUMMON_SOULSHOT)) {
                    handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
                    if (handler != null) {
                        handler.onItemUse(owner, item, false);
                    }
                }
            } else {
                owner.removeAutoSoulShot(itemId);
            }
        }
    }

    @Override
    public int getClanId() {
        return (owner != null) ? owner.getClanId() : 0;
    }

    @Override
    public int getAllyId() {
        return (owner != null) ? owner.getAllyId() : 0;
    }

    public int getFormId() {
        int formId = 0;
        final int npcId = getId();
        if ((npcId == 16041) || (npcId == 16042)) {
            if (getLevel() > 69) {
                formId = 3;
            } else if (getLevel() > 64) {
                formId = 2;
            } else if (getLevel() > 59) {
                formId = 1;
            }
        } else if ((npcId == 16025) || (npcId == 16037)) {
            if (getLevel() > 69) {
                formId = 3;
            } else if (getLevel() > 64) {
                formId = 2;
            } else if (getLevel() > 59) {
                formId = 1;
            }
        }

        return formId;
    }

    @Override
    public boolean isOnEvent() {
        return (owner != null) && owner.isOnEvent();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append('(');
        sb.append(getId());
        sb.append(") Owner: ");
        sb.append(owner);
        return sb.toString();
    }
}
