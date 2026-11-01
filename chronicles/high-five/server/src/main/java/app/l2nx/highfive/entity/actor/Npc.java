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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.config.OlympiadConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.config.custom.FakePlayersConfig;
import app.l2nx.highfive.config.custom.PrivateStoreRangeConfig;
import app.l2nx.highfive.config.custom.PvpAnnounceConfig;
import app.l2nx.highfive.config.custom.PvpRewardItemConfig;
import app.l2nx.highfive.data.xml.DynamicExpRateData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.NpcPersonalAIData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.enums.npc.AISkillScope;
import app.l2nx.highfive.entity.actor.enums.npc.AIType;
import app.l2nx.highfive.entity.actor.holders.npc.FakePlayerHolder;
import app.l2nx.highfive.entity.actor.instance.ClanHallManager;
import app.l2nx.highfive.entity.actor.instance.Doorman;
import app.l2nx.highfive.entity.actor.instance.Fisherman;
import app.l2nx.highfive.entity.actor.instance.Merchant;
import app.l2nx.highfive.entity.actor.instance.Teleporter;
import app.l2nx.highfive.entity.actor.instance.Trainer;
import app.l2nx.highfive.entity.actor.instance.Warehouse;
import app.l2nx.highfive.entity.actor.stat.NpcStat;
import app.l2nx.highfive.entity.actor.status.NpcStatus;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.type.TownZone;
import app.l2nx.highfive.handler.BypassHandler;
import app.l2nx.highfive.handler.IBypassHandler;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.RaidBossSpawnManager;
import app.l2nx.highfive.managers.TownManager;
import app.l2nx.highfive.managers.WalkingManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcCanBeSeen;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcEventReceived;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcSkillFinished;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcSpawn;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcTeleport;
import app.l2nx.highfive.mechanics.events.returns.TerminateReturn;
import app.l2nx.highfive.mechanics.olympiad.Olympiad;
import app.l2nx.highfive.mechanics.script.QuestTimer;
import app.l2nx.highfive.mechanics.script.timers.TimerHolder;
import app.l2nx.highfive.mechanics.sevensigns.SevenSigns;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.mechanics.variables.NpcVariables;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AbstractNpcInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExChangeNpcState;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPrivateStoreSetWholeMsg;
import app.l2nx.highfive.network.protocol.p267.serverpackets.FakePlayerInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillUse;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreMsgBuy;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreMsgSell;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RecipeShopMsg;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerObjectInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import app.l2nx.highfive.taskmanagers.ItemsAutoDestroyTaskManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * This class represents a Non-Player-Creature in the world.<br>
 * It can be a monster or a friendly creature.<br>
 * It uses a template to fetch some static values.
 */
@Slf4j
@SingleWriter
public class Npc extends Creature {
    /** The interaction distance of the Npc(is used as offset in MovetoLocation method) */
    public static final int INTERACTION_DISTANCE = 250;
    /** Maximum distance where the drop may appear given this NPC position. */
    public static final int RANDOM_ITEM_DROP_LIMIT = 70;
    /** Ids of NPCs that see creatures through the OnCreatureSee event. */
    private static final IntOpenHashSet CREATURE_SEE_IDS = new IntOpenHashSet();
    /** The Spawn object that manage this Npc */
    private Spawn spawn;
    /** The flag to specify if this Npc is busy */
    private boolean busy = false;
    /** The busy message for this Npc */
    private String busyMessage;
    /** True if endDecayTask has already been called */
    private boolean decayed = false;
    /** The castle index in the array of Castle this Npc belongs to */
    private int castleIndex = -2;
    /** The fortress index in the array of Fort this Npc belongs to */
    private int fortIndex = -2;

    private boolean inTown = false;
    /** True if this Npc is autoattackable **/
    private boolean autoAttackable = false;
    /** Time of last social packet broadcast */
    private long lastSocialBroadcast = 0;
    /** Minimum interval between social packets */
    private static final int MINIMUM_SOCIAL_INTERVAL = 6000;
    /** Support for random animation switching */
    private boolean randomAnimationEnabled = true;

    private boolean randomWalkingEnabled = true;

    private boolean walker = false;

    private boolean talkable = getTemplate().isTalkable();

    private final boolean questMonster = getTemplate().isQuestMonster();

    private final boolean fakePlayer = getTemplate().isFakePlayer();

    private int leftHandItem; // Normally this shouldn't change from the template, but there exist exceptions.

    private int rightHandItem; // Normally this shouldn't change from the template, but there exist exceptions.

    private int enchantEffect; // Normally this shouldn't change from the template, but there exist exceptions.

    private double collisionHeight; // Used for npc grow effect skills.

    private double collisionRadius; // Used for npc grow effect skills.

    private int soulshotamount = 0;
    private int spiritshotamount = 0;

    private int displayEffect = 0;

    private int shotsMask = 0;

    private int killingBlowWeapon;

    private int scriptValue = 0;

    /** Map of summoned NPCs by this NPC. */
    private Int2ObjectOpenHashMap<Npc> summonedNpcs = null;

    private final List<QuestTimer> questTimers = new ArrayList<>();
    private final List<TimerHolder<?>> timerHolders = new ArrayList<>();

    /**
     * Creates a NPC.
     * @param template the NPC template
     */
    public Npc(NpcTemplate template) {
        // Call the Creature constructor to set the template of the Creature, copy skills from template to object.
        // and link _calculators to NPC_STD_CALCULATOR.
        super(template);
        setInstanceType(InstanceType.Npc);
        initCharStatusUpdateValues();

        // Initialize the "current" equipment.
        leftHandItem = getTemplate().getLHandId();
        rightHandItem = getTemplate().getRHandId();
        enchantEffect = NpcConfig.ENABLE_RANDOM_ENCHANT_EFFECT
                ? ClockBridge.random().nextInt(4, 22)
                : getTemplate().getWeaponEnchant();

        // Initialize the "current" collisions.
        collisionHeight = getTemplate().getFCollisionHeight();
        collisionRadius = getTemplate().getFCollisionRadius();
        setFlying(template.isFlying());
    }

    /**
     * Creates a NPC.
     * @param npcId the NPC ID
     */
    public Npc(int npcId) {
        this(NpcData.getInstance().getTemplate(npcId));
    }

    public Spawn getSpawn() {
        WorldPartition.checkOwner();
        return spawn;
    }

    public void setSpawn(Spawn spawn) {
        WorldPartition.checkOwner();
        this.spawn = spawn;
    }

    public boolean isBusy() {
        WorldPartition.checkOwner();
        return busy;
    }

    public void setBusy(boolean busy) {
        WorldPartition.checkOwner();
        this.busy = busy;
    }

    public String getBusyMessage() {
        WorldPartition.checkOwner();
        return busyMessage;
    }

    public void setBusyMessage(String busyMessage) {
        WorldPartition.checkOwner();
        this.busyMessage = busyMessage;
    }

    public boolean isDecayed() {
        WorldPartition.checkOwner();
        return decayed;
    }

    public void setDecayed(boolean decayed) {
        WorldPartition.checkOwner();
        this.decayed = decayed;
    }

    public void setAutoAttackable(boolean autoAttackable) {
        WorldPartition.checkOwner();
        this.autoAttackable = autoAttackable;
    }

    public void setRandomAnimationEnabled(boolean randomAnimationEnabled) {
        WorldPartition.checkOwner();
        this.randomAnimationEnabled = randomAnimationEnabled;
    }

    public boolean isRandomWalkingEnabled() {
        WorldPartition.checkOwner();
        return randomWalkingEnabled;
    }

    @Override
    public boolean isWalker() {
        WorldPartition.checkOwner();
        return walker;
    }

    public boolean isTalkable() {
        WorldPartition.checkOwner();
        return talkable;
    }

    public void setTalkable(boolean talkable) {
        WorldPartition.checkOwner();
        this.talkable = talkable;
    }

    public boolean isQuestMonster() {
        WorldPartition.checkOwner();
        return questMonster;
    }

    @Override
    public boolean isFakePlayer() {
        WorldPartition.checkOwner();
        return fakePlayer;
    }

    public int getLeftHandItem() {
        WorldPartition.checkOwner();
        return leftHandItem;
    }

    public int getRightHandItem() {
        WorldPartition.checkOwner();
        return rightHandItem;
    }

    public int getEnchantEffect() {
        WorldPartition.checkOwner();
        return enchantEffect;
    }

    public double getCollisionHeight() {
        WorldPartition.checkOwner();
        return collisionHeight;
    }

    public void setCollisionHeight(double collisionHeight) {
        WorldPartition.checkOwner();
        this.collisionHeight = collisionHeight;
    }

    public double getCollisionRadius() {
        WorldPartition.checkOwner();
        return collisionRadius;
    }

    public void setCollisionRadius(double collisionRadius) {
        WorldPartition.checkOwner();
        this.collisionRadius = collisionRadius;
    }

    public int getDisplayEffect() {
        WorldPartition.checkOwner();
        return displayEffect;
    }

    public int getKillingBlowWeapon() {
        WorldPartition.checkOwner();
        return killingBlowWeapon;
    }

    public void setKillingBlowWeapon(int killingBlowWeapon) {
        WorldPartition.checkOwner();
        this.killingBlowWeapon = killingBlowWeapon;
    }

    public int getScriptValue() {
        WorldPartition.checkOwner();
        return scriptValue;
    }

    public void setScriptValue(int scriptValue) {
        WorldPartition.checkOwner();
        this.scriptValue = scriptValue;
    }

    public int getSoulShotChance() {
        WorldPartition.checkOwner();
        return getTemplate().getSoulShotChance();
    }

    public int getSpiritShotChance() {
        WorldPartition.checkOwner();
        return getTemplate().getSpiritShotChance();
    }

    public int getMinSkillChance() {
        WorldPartition.checkOwner();
        return getTemplate().getMinSkillChance();
    }

    public int getMaxSkillChance() {
        WorldPartition.checkOwner();
        return getTemplate().getMaxSkillChance();
    }

    /**
     * Verifies if the NPC can cast a skill given the minimum and maximum skill chances.
     * @return {@code true} if the NPC has chances of casting a skill
     */
    public boolean hasSkillChance() {
        WorldPartition.checkOwner();
        final RandomGenerator random = ClockBridge.random();
        final int minChance = getMinSkillChance();
        final int maxChance = getMaxSkillChance();
        return random.nextInt(100) < (minChance < maxChance ? random.nextInt(minChance, maxChance + 1) : minChance);
    }

    public boolean canMove() {
        WorldPartition.checkOwner();
        return getTemplate().canMove();
    }

    public boolean isChaos() {
        WorldPartition.checkOwner();
        return getTemplate().isChaos();
    }

    public List<Skill> getLongRangeSkills() {
        WorldPartition.checkOwner();
        return getTemplate().getAISkills(AISkillScope.LONG_RANGE);
    }

    public List<Skill> getShortRangeSkills() {
        WorldPartition.checkOwner();
        return getTemplate().getAISkills(AISkillScope.SHORT_RANGE);
    }

    /**
     * Send a packet SocialAction to all Player in the _KnownPlayers of the Npc and create a new RandomAnimation Task.
     * @param animationId
     */
    public void onRandomAnimation(int animationId) {
        WorldPartition.checkOwner();
        // Send a packet SocialAction to all Player in the _KnownPlayers of the Npc.
        final long now = ClockBridge.gameMillis();
        if ((now - lastSocialBroadcast) > MINIMUM_SOCIAL_INTERVAL) {
            lastSocialBroadcast = now;
            broadcastSocialAction(animationId);
        }
    }

    /**
     * @return true if the server allows Random Animation.
     */
    public boolean hasRandomAnimation() {
        WorldPartition.checkOwner();
        return ((GeneralConfig.MAX_NPC_ANIMATION > 0) && isRandomAnimationEnabled() && (getAiType() != AIType.CORPSE));
    }

    /**
     * @return {@code true}, if random animation is enabled, {@code false} otherwise.
     */
    public boolean isRandomAnimationEnabled() {
        WorldPartition.checkOwner();
        return !fakePlayer && randomAnimationEnabled;
    }

    public void setRandomWalking(boolean enabled) {
        WorldPartition.checkOwner();
        randomWalkingEnabled = enabled;
    }

    @Override
    public NpcStat getStat() {
        WorldPartition.checkOwner();
        return (NpcStat) super.getStat();
    }

    @Override
    public void initCharStat() {
        WorldPartition.checkOwner();
        setStat(new NpcStat(this));
    }

    @Override
    public NpcStatus getStatus() {
        WorldPartition.checkOwner();
        return (NpcStatus) super.getStatus();
    }

    @Override
    public void initCharStatus() {
        WorldPartition.checkOwner();
        setStatus(new NpcStatus(this));
    }

    /** Return the NpcTemplate of the Npc. */
    @Override
    public NpcTemplate getTemplate() {
        WorldPartition.checkOwner();
        return (NpcTemplate) super.getTemplate();
    }

    /**
     * Gets the NPC ID.
     * @return the NPC ID
     */
    @Override
    public int getId() {
        WorldPartition.checkOwner();
        return getTemplate().getId();
    }

    @Override
    public boolean canBeAttacked() {
        WorldPartition.checkOwner();
        return NpcConfig.ALT_ATTACKABLE_NPCS;
    }

    /**
     * Return the Level of this Npc contained in the NpcTemplate.
     */
    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return getTemplate().getLevel();
    }

    /**
     * @return True if the Npc is aggressive (ex : Monster in function of aggroRange).
     */
    public boolean isAggressive() {
        WorldPartition.checkOwner();
        return false;
    }

    /**
     * @return the Aggro Range of this Npc either contained in the NpcTemplate, or overriden by spawnlist AI value.
     */
    public int getAggroRange() {
        WorldPartition.checkOwner();
        return hasAIValue("aggroRange")
                ? getAIValue("aggroRange")
                : getTemplate().getAggroRange();
    }

    public boolean isInMyClan(Npc npc) {
        WorldPartition.checkOwner();
        return getTemplate().isClan(npc.getTemplate().getClans());
    }

    /**
     * Return True if this Npc is undead in function of the NpcTemplate.
     */
    @Override
    public boolean isUndead() {
        WorldPartition.checkOwner();
        return getTemplate().getRace() == Race.UNDEAD;
    }

    /**
     * Send a packet NpcInfo with state of abnormal effect to all Player in the _KnownPlayers of the Npc.
     */
    @Override
    public void updateAbnormalEffect() {
        WorldPartition.checkOwner();
        World.forEachVisibleObject(this, Player.class, player -> {
            if (!isVisibleFor(player)) {
                return;
            }

            if (fakePlayer) {
                player.sendPacket(new FakePlayerInfo(this));
            } else if (getRunSpeed() == 0) {
                player.sendPacket(new ServerObjectInfo(this, player));
            } else if (isSpawned()) {
                player.sendPacket(new AbstractNpcInfo.NpcInfo(this, player));
            }
        });
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        return autoAttackable;
    }

    /**
     * @return true if this Npc instance can be warehouse manager.
     */
    public boolean isWarehouse() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean canTarget(Player player) {
        WorldPartition.checkOwner();
        if (player.isOutOfControl()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (player.isLockedTarget() && (player.getLockedTarget() != this)) {
            player.sendPacket(SystemMessageId.FAILED_TO_CHANGE_ATTACK_TARGET);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        return true;
    }

    public boolean canInteract(Player player) {
        WorldPartition.checkOwner();
        if (player.isCastingNow() || player.isCastingSimultaneouslyNow()) {
            return false;
        } else if (player.isDead() || player.isFakeDeath()) {
            return false;
        } else if (player.isSitting()) {
            return false;
        } else if (player.isInStoreMode()) {
            return false;
        } else if (!isInsideRadius3D(player, INTERACTION_DISTANCE)) {
            return false;
        } else if (player.getInstanceId() != getInstanceId()) {
            return false;
        }

        return true;
    }

    /**
     * @return the Castle this Npc belongs to.
     */
    public Castle getCastle() {
        WorldPartition.checkOwner();
        // Get castle this NPC belongs to (excluding Attackable).
        if (castleIndex < 0) {
            final TownZone town = TownManager.getTown(getX(), getY(), getZ());
            if (town != null) {
                castleIndex = CastleManager.getInstance().getCastleIndex(town.getTaxById());
            }

            if (castleIndex < 0) {
                castleIndex = CastleManager.getInstance().findNearestCastleIndex(this);
            } else {
                inTown = true; // Npc was spawned in town.
            }
        }

        if (castleIndex < 0) {
            return null;
        }

        return CastleManager.getInstance().getCastles().get(castleIndex);
    }

    /**
     * Verify if the given player is this NPC's lord.
     * @param player the player to check
     * @return {@code true} if the player is clan leader and owner of a castle of fort that this NPC belongs to, {@code false} otherwise
     */
    public boolean isMyLord(Player player) {
        WorldPartition.checkOwner();
        if (player.isClanLeader()) {
            final int castleId = getCastle() != null ? getCastle().getResidenceId() : -1;
            final int fortId = getFort() != null ? getFort().getResidenceId() : -1;
            return (player.getClan().getCastleId() == castleId)
                    || (player.getClan().getFortId() == fortId);
        }

        return false;
    }

    public SiegableHall getConquerableHall() {
        WorldPartition.checkOwner();
        return CHSiegeManager.getInstance().getNearbyClanHall(getX(), getY(), 10000);
    }

    /**
     * Return closest castle in defined distance
     * @param maxDistance long
     * @return Castle
     */
    public Castle getCastle(long maxDistance) {
        WorldPartition.checkOwner();
        final int index = CastleManager.getInstance().findNearestCastleIndex(this, maxDistance);
        if (index < 0) {
            return null;
        }

        return CastleManager.getInstance().getCastles().get(index);
    }

    /**
     * @return the Fort this Npc belongs to.
     */
    public Fort getFort() {
        WorldPartition.checkOwner();
        // Get Fort this NPC belongs to (excluding Attackable)
        if (fortIndex < 0) {
            final Fort fort = FortManager.getInstance().getFort(getX(), getY(), getZ());
            if (fort != null) {
                fortIndex = FortManager.getInstance().getFortIndex(fort.getResidenceId());
            }

            if (fortIndex < 0) {
                fortIndex = FortManager.getInstance().findNearestFortIndex(this);
            }
        }

        if (fortIndex < 0) {
            return null;
        }

        return FortManager.getInstance().getForts().get(fortIndex);
    }

    /**
     * Return closest Fort in defined distance
     * @param maxDistance long
     * @return Fort
     */
    public Fort getFort(long maxDistance) {
        WorldPartition.checkOwner();
        final int index = FortManager.getInstance().findNearestFortIndex(this, maxDistance);
        if (index < 0) {
            return null;
        }

        return FortManager.getInstance().getForts().get(index);
    }

    public boolean isInTown() {
        WorldPartition.checkOwner();
        if (castleIndex < 0) {
            getCastle();
        }

        return inTown;
    }

    /**
     * Open a quest or chat window on client with the text of the Npc in function of the command.<br>
     * <br>
     * <b><u>Example of use</u>:</b>
     * <ul>
     * <li>Client packet : RequestBypassToServer</li>
     * </ul>
     * @param player
     * @param command The command string received from client
     */
    public void onBypassFeedback(Player player, String command) {
        WorldPartition.checkOwner();
        if (canInteract(player)) {
            if (busy && (busyMessage != null)) {
                player.sendPacket(ActionFailed.STATIC_PACKET);

                final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
                html.setFile(player, "data/html/npcbusy.htm");
                html.replace("%busymessage%", busyMessage);
                html.replace("%npcname%", getName());
                html.replace("%playername%", player.getName());
                player.sendPacket(html);
            } else {
                final IBypassHandler handler = BypassHandler.getInstance().getHandler(command);
                if (handler != null) {
                    handler.onCommand(command, player, this);
                } else {
                    LoggerFactory.getLogger(getClass()).info("Unknown NPC bypass: \"{}\" NpcId: {}", command, getId());
                }
            }
        }
    }

    /**
     * Return null (regular NPCs don't have weapons instances).
     */
    @Override
    public Item getActiveWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    /**
     * Return the weapon item equipped in the right hand of the Npc or null.
     */
    @Override
    public Weapon getActiveWeaponItem() {
        WorldPartition.checkOwner();
        // Get the weapon identifier equipped in the right hand of the Npc.
        final int weaponId = getTemplate().getRHandId();
        if (weaponId < 1) {
            return null;
        }

        // Get the weapon item equipped in the right hand of the Npc.
        final ItemTemplate item =
                ItemData.getInstance().getTemplate(getTemplate().getRHandId());
        if (!(item instanceof Weapon)) {
            return null;
        }

        return (Weapon) item;
    }

    /**
     * Return null (regular NPCs don't have weapons instances).
     */
    @Override
    public Item getSecondaryWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    /**
     * Return the weapon item equipped in the left hand of the Npc or null.
     */
    @Override
    public Weapon getSecondaryWeaponItem() {
        WorldPartition.checkOwner();
        // Get the weapon identifier equipped in the right hand of the Npc.
        final int weaponId = getTemplate().getLHandId();
        if (weaponId < 1) {
            return null;
        }

        // Get the weapon item equipped in the right hand of the Npc.
        final ItemTemplate item =
                ItemData.getInstance().getTemplate(getTemplate().getLHandId());
        if (!(item instanceof Weapon)) {
            return null;
        }

        return (Weapon) item;
    }

    /**
     * Send a Server->Client packet NpcHtmlMessage to the Player in order to display the message of the Npc.
     * @param player The Player who talks with the Npc
     * @param content The text of the NpcMessage
     */
    public void insertObjectIdAndShowChatWindow(Player player, String content) {
        WorldPartition.checkOwner();
        // Send a Server->Client packet NpcHtmlMessage to the Player in order to display the message of the Npc.
        player.sendPacket(
                new NpcHtmlMessage(getObjectId(), content.replaceAll("%objectId%", String.valueOf(getObjectId()))));
    }

    /**
     * <b><U Format of the pathfile</u>:</b>
     * <ul>
     * <li>if the file exists on the server (page number = 0) : <b>data/html/default/12006.htm</b> (npcId-page number)</li>
     * <li>if the file exists on the server (page number > 0) : <b>data/html/default/12006-1.htm</b> (npcId-page number)</li>
     * <li>if the file doesn't exist on the server : <b>data/html/npcdefault.htm</b> (message : "I have nothing to say to you")</li>
     * </ul>
     * @param npcId The Identifier of the Npc whose text must be display
     * @param value The number of the page to display
     * @return the pathfile of the selected HTML file in function of the npcId and of the page number.
     */
    public String getHtmlPath(int npcId, int value) {
        WorldPartition.checkOwner();
        String pom = "";
        if (value == 0) {
            pom = Integer.toString(npcId);
        } else {
            pom = npcId + "-" + value;
        }

        final String temp = "data/html/default/" + pom + ".htm";
        if (GeneralConfig.HTM_CACHE) {
            // If not running lazy cache the file must be in the cache or it does not exist.
            if (HtmCache.getInstance().contains(temp)) {
                return temp;
            }
        } else {
            final File file = new File(ServerConfig.DATAPACK_ROOT, temp);
            if (file.isFile()) {
                final String lowerCaseName = file.getName().toLowerCase();
                if (lowerCaseName.endsWith(".htm") || lowerCaseName.endsWith(".html")) {
                    return temp;
                }
            }
        }

        // If the file is not found, the standard message "I have nothing to say to you" is returned.
        return "data/html/npcdefault.htm";
    }

    public void showChatWindow(Player player) {
        WorldPartition.checkOwner();
        showChatWindow(player, 0);
    }

    /**
     * Returns true if html exists
     * @param player
     * @param type
     * @return boolean
     */
    private boolean showPkDenyChatWindow(Player player, String type) {
        final String html = HtmCache.getInstance().getHtm(player, "data/html/" + type + "/" + getId() + "-pk.htm");
        if (html != null) {
            insertObjectIdAndShowChatWindow(player, html);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return true;
        }

        return false;
    }

    /**
     * Open a chat window on client with the text of the Npc.<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Get the text of the selected HTML file in function of the npcId and of the page number</li>
     * <li>Send a Server->Client NpcHtmlMessage containing the text of the Npc to the Player</li>
     * <li>Send a Server->Client ActionFailed to the Player in order to avoid that the client wait another packet</li>
     * </ul>
     * @param player The Player that talk with the Npc
     * @param value The number of the page of the Npc to display
     */
    public void showChatWindow(Player player, int value) {
        WorldPartition.checkOwner();
        if (!talkable) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isCursedWeaponEquipped()
                && (!(player.getTarget() instanceof ClanHallManager) || !(player.getTarget() instanceof Doorman))) {
            player.setTarget(player);
            return;
        }

        if (player.getKarma() > 0) {
            if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_SHOP && (this instanceof Merchant)) {
                if (showPkDenyChatWindow(player, "merchant")) {
                    return;
                }
            } else if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_USE_GK && (this instanceof Teleporter)) {
                if (showPkDenyChatWindow(player, "teleporter")) {
                    return;
                }
            } else if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE && (this instanceof Warehouse)) {
                if (showPkDenyChatWindow(player, "warehouse")) {
                    return;
                }
            } else if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_SHOP && (this instanceof Fisherman)) {
                if (showPkDenyChatWindow(player, "fisherman")) {
                    return;
                }
            }
        }

        if (getTemplate().isType("Auctioneer") && (value == 0)) {
            return;
        }

        final int npcId = getTemplate().getId();

        /* For use with Seven Signs implementation */
        String filename = SevenSigns.SEVEN_SIGNS_HTML_PATH;
        final int sealAvariceOwner = SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_AVARICE);
        final int sealGnosisOwner = SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_GNOSIS);
        final int playerCabal = SevenSigns.getInstance().getPlayerCabal(player.getObjectId());
        final int compWinner = SevenSigns.getInstance().getCabalHighestScore();

        switch (npcId) {
            case 31127: //
            case 31128: //
            case 31129: // Dawn Festival Guides
            case 31130: //
            case 31131: //
            {
                filename += "festival/dawn_guide.htm";
                break;
            }
            case 31137: //
            case 31138: //
            case 31139: // Dusk Festival Guides
            case 31140: //
            case 31141: //
            {
                filename += "festival/dusk_guide.htm";
                break;
            }
            case 31092: // Black Marketeer of Mammon
            {
                filename += "blkmrkt_1.htm";
                break;
            }
            case 31113: // Merchant of Mammon
            {
                if (FeatureConfig.ALT_STRICT_SEVENSIGNS) {
                    switch (compWinner) {
                        case SevenSigns.CABAL_DAWN: {
                            if ((playerCabal != compWinner) || (playerCabal != sealAvariceOwner)) {
                                player.sendPacket(SystemMessageId.ONLY_A_LORD_OF_DAWN_MAY_USE_THIS);
                                player.sendPacket(ActionFailed.STATIC_PACKET);
                                return;
                            }
                            break;
                        }
                        case SevenSigns.CABAL_DUSK: {
                            if ((playerCabal != compWinner) || (playerCabal != sealAvariceOwner)) {
                                player.sendPacket(SystemMessageId.ONLY_A_REVOLUTIONARY_OF_DUSK_MAY_USE_THIS);
                                player.sendPacket(ActionFailed.STATIC_PACKET);
                                return;
                            }
                            break;
                        }
                        default: {
                            player.sendPacket(SystemMessageId.THE_SSQ_COMPETITION_PERIOD_IS_UNDERWAY);
                            return;
                        }
                    }
                }

                filename += "mammmerch_1.htm";
                break;
            }
            case 31126: // Blacksmith of Mammon
            {
                if (FeatureConfig.ALT_STRICT_SEVENSIGNS) {
                    switch (compWinner) {
                        case SevenSigns.CABAL_DAWN: {
                            if ((playerCabal != compWinner) || (playerCabal != sealGnosisOwner)) {
                                player.sendPacket(SystemMessageId.ONLY_A_LORD_OF_DAWN_MAY_USE_THIS);
                                player.sendPacket(ActionFailed.STATIC_PACKET);
                                return;
                            }
                            break;
                        }
                        case SevenSigns.CABAL_DUSK: {
                            if ((playerCabal != compWinner) || (playerCabal != sealGnosisOwner)) {
                                player.sendPacket(SystemMessageId.ONLY_A_REVOLUTIONARY_OF_DUSK_MAY_USE_THIS);
                                player.sendPacket(ActionFailed.STATIC_PACKET);
                                return;
                            }
                            break;
                        }
                        default: {
                            player.sendPacket(SystemMessageId.THE_SSQ_COMPETITION_PERIOD_IS_UNDERWAY);
                            return;
                        }
                    }
                }

                filename += "mammblack_1.htm";
                break;
            }
            case 31132:
            case 31133:
            case 31134:
            case 31135:
            case 31136: // Festival Witches
            case 31142:
            case 31143:
            case 31144:
            case 31145:
            case 31146: {
                filename += "festival/festival_witch.htm";
                break;
            }
            case 31688: {
                if (player.isNoble()) {
                    filename = Olympiad.OLYMPIAD_HTML_PATH + "noble_main.htm";
                } else {
                    filename = (getHtmlPath(npcId, value));
                }
                break;
            }
            case 31690:
            case 31769:
            case 31770:
            case 31771:
            case 31772: {
                if (player.isHero() || player.isNoble()) {
                    filename = Olympiad.OLYMPIAD_HTML_PATH + "hero_main.htm";
                } else {
                    filename = (getHtmlPath(npcId, value));
                }
                break;
            }
            case 36402: {
                if (player.getOlympiadBuffCount() > 0) {
                    filename = (player.getOlympiadBuffCount() == OlympiadConfig.OLYMPIAD_MAX_BUFFS
                            ? Olympiad.OLYMPIAD_HTML_PATH + "olympiad_buffs.htm"
                            : Olympiad.OLYMPIAD_HTML_PATH + "olympiad_5buffs.htm");
                } else {
                    filename = Olympiad.OLYMPIAD_HTML_PATH + "olympiad_nobuffs.htm";
                }
                break;
            }
            case 30298: // Blacksmith Pinter
            {
                if (player.isAcademyMember()) {
                    filename = (getHtmlPath(npcId, 1));
                } else {
                    filename = (getHtmlPath(npcId, value));
                }
                break;
            }
            default: {
                if ((npcId >= 31865) && (npcId <= 31918)) {
                    if (value == 0) {
                        filename += "rift/GuardianOfBorder.htm";
                    } else {
                        filename += "rift/GuardianOfBorder-" + value + ".htm";
                    }
                    break;
                }

                if (((npcId >= 31093) && (npcId <= 31094))
                        || ((npcId >= 31172) && (npcId <= 31201))
                        || ((npcId >= 31239) && (npcId <= 31254))) {
                    return;
                }

                // Get the text of the selected HTML file in function of the npcId and of the page number.
                filename = (getHtmlPath(npcId, value));
                break;
            }
        }

        // Send a Server->Client NpcHtmlMessage containing the text of the Npc to the Player.
        final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
        html.setFile(player, filename);
        if (this instanceof Merchant) {
            if (NpcConfig.LIST_PET_RENT_NPC.contains(npcId)) {
                html.replace("_Quest", "_RentPet\">Rent Pet</a><br><a action=\"bypass Script");
            }
        }

        html.replace("%npcname%", String.valueOf(getName()));
        html.replace("%objectId%", String.valueOf(getObjectId()));
        html.replace("%festivalMins%", SevenSignsFestival.getInstance().getTimeToNextFestivalStr());
        player.sendPacket(html);

        // Send a Server->Client ActionFailed to the Player in order to avoid that the client wait another packet.
        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    /**
     * Open a chat window on client with the text specified by the given file name and path, relative to the datapack root.
     * @param player The Player that talk with the Npc
     * @param filename The filename that contains the text to send
     */
    public void showChatWindow(Player player, String filename) {
        WorldPartition.checkOwner();
        // Send a Server->Client NpcHtmlMessage containing the text of the Npc to the Player.
        final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
        html.setFile(player, filename);
        html.replace("%objectId%", String.valueOf(getObjectId()));
        player.sendPacket(html);

        // Send a Server->Client ActionFailed to the Player in order to avoid that the client wait another packet.
        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    /**
     * @param level
     * @return the Exp Reward of this Npc (modified by RATE_XP, or from DynamicExpRates.xml if enabled).
     */
    public long getExpReward(int level) {
        WorldPartition.checkOwner();
        if (DynamicExpRateData.getInstance().isEnabled()) {
            return (long)
                    (getTemplate().getExp() * DynamicExpRateData.getInstance().getDynamicExpRate(level));
        }

        return (long) (getTemplate().getExp() * RatesConfig.RATE_XP);
    }

    /**
     * @param level
     * @return the SP Reward of this Npc (modified by RATE_SP, or from DynamicExpRates.xml if enabled).
     */
    public int getSpReward(int level) {
        WorldPartition.checkOwner();
        if (DynamicExpRateData.getInstance().isEnabled()) {
            return (int)
                    (getTemplate().getSP() * DynamicExpRateData.getInstance().getDynamicSpRate(level));
        }

        return (int) (getTemplate().getSP() * RatesConfig.RATE_SP);
    }

    /**
     * Kill the Npc (the corpse disappeared after 7 seconds).<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Create a DecayTask to remove the corpse of the Npc after 7 seconds</li>
     * <li>Set target to null and cancel Attack or Cast</li>
     * <li>Stop movement</li>
     * <li>Stop HP/MP/CP Regeneration task</li>
     * <li>Stop all active skills effects in progress on the Creature</li>
     * <li>Send the Server->Client packet StatusUpdate with current HP and MP to all other Player to inform</li>
     * <li>Notify Creature AI</li>
     * </ul>
     * @param killer The Creature who killed it
     */
    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        // Normally this wouldn't really be needed, but for those few exceptions,
        // we do need to reset the weapons back to the initial template weapon.
        leftHandItem = getTemplate().getLHandId();
        rightHandItem = getTemplate().getRHandId();
        collisionHeight = getTemplate().getFCollisionHeight();
        collisionRadius = getTemplate().getFCollisionRadius();

        final Weapon weapon = (killer != null) ? killer.getActiveWeaponItem() : null;
        killingBlowWeapon = (weapon != null) ? weapon.getId() : 0;
        if (fakePlayer && (killer != null) && killer.isPlayable()) {
            final Player player = killer.asPlayer();
            if (isScriptValue(0) && (getKarma() < 0)) {
                if (FakePlayersConfig.FAKE_PLAYER_KILL_KARMA) {
                    player.setKarma(
                            player.getKarma() + Formulas.calculateKarmaGain(player.getPkKills(), killer.isSummon()));
                    player.setPkKills(player.getPkKills() + 1);
                    player.broadcastUserInfo();
                    player.checkItemRestriction();

                    // pk item rewards
                    if (PvpRewardItemConfig.REWARD_PK_ITEM) {
                        if (!(PvpRewardItemConfig.DISABLE_REWARDS_IN_INSTANCES && (getInstanceId() != 0))
                                && //
                                !(PvpRewardItemConfig.DISABLE_REWARDS_IN_PVP_ZONES && isInsideZone(ZoneId.PVP))) {
                            player.addItem(
                                    ItemProcessType.REWARD,
                                    PvpRewardItemConfig.REWARD_PK_ITEM_ID,
                                    PvpRewardItemConfig.REWARD_PK_ITEM_AMOUNT,
                                    this,
                                    PvpRewardItemConfig.REWARD_PK_ITEM_MESSAGE);
                        }
                    }

                    // announce pk
                    if (PvpAnnounceConfig.ANNOUNCE_PK_PVP && !player.isGM()) {
                        final String msg = PvpAnnounceConfig.ANNOUNCE_PK_MSG
                                .replace("$killer", player.getName())
                                .replace("$target", getName());
                        if (PvpAnnounceConfig.ANNOUNCE_PK_PVP_NORMAL_MESSAGE) {
                            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_3);
                            sm.addString(msg);
                            World.broadcastToAllOnlinePlayers(sm);
                        } else {
                            World.broadcastToAllOnlinePlayers(msg, false);
                        }
                    }
                }
            } else if (FakePlayersConfig.FAKE_PLAYER_KILL_PVP) {
                player.setPvpKills(player.getPvpKills() + 1);
                player.broadcastUserInfo();

                // pvp item rewards
                if (PvpRewardItemConfig.REWARD_PVP_ITEM) {
                    if (!(PvpRewardItemConfig.DISABLE_REWARDS_IN_INSTANCES && (getInstanceId() != 0))
                            && //
                            !(PvpRewardItemConfig.DISABLE_REWARDS_IN_PVP_ZONES && isInsideZone(ZoneId.PVP))) {
                        player.addItem(
                                ItemProcessType.REWARD,
                                PvpRewardItemConfig.REWARD_PVP_ITEM_ID,
                                PvpRewardItemConfig.REWARD_PVP_ITEM_AMOUNT,
                                this,
                                PvpRewardItemConfig.REWARD_PVP_ITEM_MESSAGE);
                    }
                }

                // announce pvp
                if (PvpAnnounceConfig.ANNOUNCE_PK_PVP && !player.isGM()) {
                    final String msg = PvpAnnounceConfig.ANNOUNCE_PVP_MSG
                            .replace("$killer", player.getName())
                            .replace("$target", getName());
                    if (PvpAnnounceConfig.ANNOUNCE_PK_PVP_NORMAL_MESSAGE) {
                        final SystemMessage sm = new SystemMessage(SystemMessageId.S1_3);
                        sm.addString(msg);
                        World.broadcastToAllOnlinePlayers(sm);
                    } else {
                        World.broadcastToAllOnlinePlayers(msg, false);
                    }
                }
            }
        }

        DecayTaskManager.getInstance().add(this);
        return true;
    }

    @Override
    public void onSpawn() {
        WorldPartition.checkOwner();
        super.onSpawn();

        // Recharge shots
        soulshotamount = getTemplate().getSoulShot();
        spiritshotamount = getTemplate().getSpiritShot();
        killingBlowWeapon = 0;
        randomAnimationEnabled = getTemplate().isRandomAnimationEnabled();
        randomWalkingEnabled =
                !WalkingManager.getInstance().isTargeted(this) && getTemplate().isRandomWalkEnabled();

        if (isTeleporting()) {
            if (EventDispatcher.getInstance().hasListener(EventType.ON_NPC_TELEPORT, this)) {
                EventDispatcher.getInstance().notifyEventAsync(new OnNpcTeleport(this), this);
            }
        } else if (EventDispatcher.getInstance().hasListener(EventType.ON_NPC_SPAWN, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnNpcSpawn(this), this);
        }

        if (!isTeleporting()) {
            WalkingManager.getInstance().onSpawn(this);
        }

        if (CREATURE_SEE_IDS.contains(getId())) {
            initSeenCreatures();
        }
    }

    public static void addCreatureSeeId(int id) {
        WorldPartition.checkOwner();
        CREATURE_SEE_IDS.add(id);
    }

    /**
     * Remove the Npc from the world and update its spawn object (for a complete removal use the deleteMe method).<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Remove the Npc from the world when the decay task is launched</li>
     * <li>Decrease its spawn counter</li>
     * <li>Manage Siege task (killFlag, killCT)</li>
     * </ul>
     * <font color=#FF0000><b><u>Caution</u>: This method DOESN'T REMOVE the object from _allObjects of World </b></font><br>
     * <font color=#FF0000><b><u>Caution</u>: This method DOESN'T SEND Server->Client packets to players</b></font>
     */
    @Override
    public void onDecay() {
        WorldPartition.checkOwner();
        if (decayed) {
            return;
        }

        setDecayed(true);

        // Remove the Npc from the world when the decay task is launched.
        super.onDecay();

        // Decrease its spawn counter.
        if ((spawn != null) && !RaidBossSpawnManager.getInstance().isDefined(getId())) {
            spawn.decreaseCount(this);
        }

        // Notify Walking Manager.
        WalkingManager.getInstance().onDeath(this);

        // Removes itself from the summoned list.
        final Creature summoner = getSummoner();
        if ((summoner != null) && summoner.isNpc()) {
            summoner.asNpc().removeSummonedNpc(getObjectId());
        }

        // Stop all timers.
        stopQuestTimers();
        stopTimerHolders();

        // Clear script value.
        scriptValue = 0;
    }

    /**
     * Remove PROPERLY the Npc from the world.<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Remove the Npc from the world and update its spawn object</li>
     * <li>Remove all WorldObject from _knownObjects and _knownPlayer of the Npc then cancel Attack or Cast and notify AI</li>
     * <li>Remove WorldObject object from _allObjects of World</li>
     * </ul>
     * <font color=#FF0000><b><u>Caution</u>: This method DOESN'T SEND Server->Client packets to players</b></font><br>
     * UnAfraid: TODO: Add Listener here
     */
    @Override
    public boolean deleteMe() {
        WorldPartition.checkOwner();
        try {
            onDecay();
        } catch (Exception e) {
            log.error("Failed decayMe().", e);
        }

        if (isChannelized()) {
            getSkillChannelized().abortChannelization();
        }

        ZoneManager.getInstance().getRegion(this).removeFromZones(this);

        return super.deleteMe();
    }

    public void endDecayTask() {
        WorldPartition.checkOwner();
        if (!decayed) {
            DecayTaskManager.getInstance().cancel(this);
            onDecay();
        }
    }

    // Two functions to change the appearance of the equipped weapons on the NPC.
    // This is only useful for a few NPCs and is most likely going to be called from AI.
    public void setLHandId(int newWeaponId) {
        WorldPartition.checkOwner();
        leftHandItem = newWeaponId;
        updateAbnormalEffect();
    }

    public void setRHandId(int newWeaponId) {
        WorldPartition.checkOwner();
        rightHandItem = newWeaponId;
        updateAbnormalEffect();
    }

    public void setLRHandId(int newLWeaponId, int newRWeaponId) {
        WorldPartition.checkOwner();
        rightHandItem = newRWeaponId;
        leftHandItem = newLWeaponId;
        updateAbnormalEffect();
    }

    public void setEnchant(int newEnchantValue) {
        WorldPartition.checkOwner();
        enchantEffect = newEnchantValue;
        updateAbnormalEffect();
    }

    public boolean isShowName() {
        WorldPartition.checkOwner();
        return getTemplate().isShowName();
    }

    @Override
    public boolean isTargetable() {
        WorldPartition.checkOwner();
        return getTemplate().isTargetable();
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
        if (isVisibleFor(player)) {
            if (fakePlayer) {
                player.sendPacket(new FakePlayerInfo(this));

                // Private store message support.
                final FakePlayerHolder fakePlayerInfo = getTemplate().getFakePlayerInfo();
                final int storeType = fakePlayerInfo.getPrivateStoreType();
                if (storeType > 0) {
                    final String message = fakePlayerInfo.getPrivateStoreMessage();
                    if (!message.isEmpty()) {
                        switch (storeType) {
                            case 1: // Sell
                            {
                                player.sendPacket(new PrivateStoreMsgSell(getObjectId(), message));
                                break;
                            }
                            case 3: // Buy
                            {
                                player.sendPacket(new PrivateStoreMsgBuy(getObjectId(), message));
                                break;
                            }
                            case 5: // Manufacture
                            {
                                player.sendPacket(new RecipeShopMsg(getObjectId(), message));
                                break;
                            }
                            case 8: // Package Sell
                            {
                                player.sendPacket(new ExPrivateStoreSetWholeMsg(getObjectId(), message));
                                break;
                            }
                        }
                    }
                }
            } else if (getRunSpeed() == 0) {
                player.sendPacket(new ServerObjectInfo(this, player));
            } else {
                player.sendPacket(new AbstractNpcInfo.NpcInfo(this, player));
            }
        }
    }

    public void showNoTeachHtml(Player player) {
        WorldPartition.checkOwner();
        final int npcId = getId();
        String html = "";
        if (this instanceof Warehouse) {
            html = HtmCache.getInstance().getHtm(player, "data/html/warehouse/" + npcId + "-noteach.htm");
        } else if (this instanceof Trainer) {
            html = HtmCache.getInstance().getHtm(player, "data/html/trainer/" + npcId + "-noteach.htm");

            // Trainer Healer?
            if (html == null) {
                html = HtmCache.getInstance()
                        .getHtm(player, "data/scripts/ai/others/HealerTrainer/" + npcId + "-noteach.html");
            }
        }

        final NpcHtmlMessage noTeachMsg = new NpcHtmlMessage(getObjectId());
        if (html == null) {
            log.warn("Npc {} missing noTeach html!", npcId);
            noTeachMsg.setHtml("<html><body>I cannot teach you any skills.<br>You must find your current class"
                    + " teachers.</body></html>");
        } else {
            noTeachMsg.setHtml(html);
            noTeachMsg.replace("%objectId%", String.valueOf(getObjectId()));
        }

        player.sendPacket(noTeachMsg);
    }

    public Npc scheduleDespawn(long delay) {
        WorldPartition.checkOwner();
        WorldPartition.loop()
                .schedule(
                        () -> {
                            if (!decayed) {
                                deleteMe();
                            }
                        },
                        delay,
                        TimeUnit.MILLISECONDS);
        return this;
    }

    @Override
    protected void notifyQuestEventSkillFinished(Skill skill, WorldObject target) {
        if ((target != null)
                && target.isPlayable()
                && EventDispatcher.getInstance().hasListener(EventType.ON_NPC_SKILL_FINISHED, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnNpcSkillFinished.builder()
                                    .caster(this)
                                    .target(target.asPlayer())
                                    .skill(skill)
                                    .build(),
                            this);
        }
    }

    @Override
    public boolean isMovementDisabled() {
        WorldPartition.checkOwner();
        return super.isMovementDisabled() || !canMove() || (getAiType() == AIType.CORPSE);
    }

    public AIType getAiType() {
        WorldPartition.checkOwner();
        return getTemplate().getAIType();
    }

    public void setDisplayEffect(int value) {
        WorldPartition.checkOwner();
        if (value != displayEffect) {
            displayEffect = value;
            broadcastPacket(new ExChangeNpcState(getObjectId(), value));
        }
    }

    public int getColorEffect() {
        WorldPartition.checkOwner();
        return 0;
    }

    @Override
    public boolean isNpc() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public Npc asNpc() {
        WorldPartition.checkOwner();
        return this;
    }

    public void setTeam(Team team, boolean broadcast) {
        WorldPartition.checkOwner();
        super.setTeam(team);
        if (broadcast) {
            broadcastInfo();
        }
    }

    @Override
    public void setTeam(Team team) {
        WorldPartition.checkOwner();
        super.setTeam(team);
        broadcastInfo();
    }

    public void setWalker() {
        WorldPartition.checkOwner();
        walker = true;
    }

    @Override
    public boolean isChargedShot(ShotType type) {
        WorldPartition.checkOwner();
        return (shotsMask & type.getMask()) == type.getMask();
    }

    @Override
    public void setChargedShot(ShotType type, boolean charged) {
        WorldPartition.checkOwner();
        if (charged) {
            shotsMask |= type.getMask();
        } else {
            shotsMask &= ~type.getMask();
        }
    }

    @Override
    public void rechargeShots(boolean physical, boolean magic) {
        WorldPartition.checkOwner();
        if (fakePlayer && FakePlayersConfig.FAKE_PLAYER_USE_SHOTS) {
            if (physical) {
                broadcastPacket(new MagicSkillUse(this, this, 2154, 1, 0, 0));
                setChargedShot(ShotType.SOULSHOTS, true);
            }

            if (magic) {
                broadcastPacket(new MagicSkillUse(this, this, 2159, 1, 0, 0));
                setChargedShot(ShotType.SPIRITSHOTS, true);
            }
        } else if ((soulshotamount > 0) || (spiritshotamount > 0)) {
            if (physical) {
                if ((soulshotamount == 0) || (ClockBridge.random().nextInt(100) > getSoulShotChance())) {
                    return;
                }

                soulshotamount--;
                broadcastPacket(new MagicSkillUse(this, this, 2154, 1, 0, 0));
                setChargedShot(ShotType.SOULSHOTS, true);
            }

            if (magic) {
                if ((spiritshotamount == 0) || (ClockBridge.random().nextInt(100) > getSpiritShotChance())) {
                    return;
                }

                spiritshotamount--;
                broadcastPacket(new MagicSkillUse(this, this, 2159, 1, 0, 0));
                setChargedShot(ShotType.SPIRITSHOTS, true);
            }
        }
    }

    /**
     * @param value value to store
     * @return {@code true} if stored script value equals given value, {@code false} otherwise
     */
    public boolean isScriptValue(int value) {
        WorldPartition.checkOwner();
        return scriptValue == value;
    }

    /**
     * @param paramName the parameter name to check
     * @return given AI parameter value
     */
    public int getAIValue(String paramName) {
        WorldPartition.checkOwner();
        return hasAIValue(paramName)
                ? NpcPersonalAIData.getInstance().getAIValue(getSpawn().getName(), paramName)
                : -1;
    }

    /**
     * @param paramName the parameter name to check
     * @return {@code true} if given parameter is set for NPC, {@code false} otherwise
     */
    public boolean hasAIValue(String paramName) {
        WorldPartition.checkOwner();
        return (getSpawn() != null)
                && (getSpawn().getName() != null)
                && NpcPersonalAIData.getInstance().hasAIValue(getSpawn().getName(), paramName);
    }

    /**
     * @param npc NPC to check
     * @return {@code true} if both given NPC and this NPC is in the same spawn group, {@code false} otherwise
     */
    public boolean isInMySpawnGroup(Npc npc) {
        WorldPartition.checkOwner();
        return ((getSpawn() != null)
                && (npc.getSpawn() != null)
                && (getSpawn().getName() != null)
                && (getSpawn().getName().equals(npc.getSpawn().getName())));
    }

    /**
     * @return {@code true} if NPC currently located in own spawn point, {@code false} otherwise
     */
    public boolean staysInSpawnLoc() {
        WorldPartition.checkOwner();
        return ((spawn != null) && (spawn.getX() == getX()) && (spawn.getY() == getY()));
    }

    /**
     * @return {@code true} if {@link NpcVariables} instance is attached to current player's scripts, {@code false} otherwise.
     */
    public boolean hasVariables() {
        WorldPartition.checkOwner();
        return getScript(NpcVariables.class) != null;
    }

    /**
     * @return {@link NpcVariables} instance containing parameters regarding NPC.
     */
    public NpcVariables getVariables() {
        WorldPartition.checkOwner();
        final NpcVariables vars = getScript(NpcVariables.class);
        return vars != null ? vars : addScript(new NpcVariables());
    }

    /**
     * Send an "event" to all NPCs within given radius
     * @param eventName - name of event
     * @param radius - radius to send event
     * @param reference - WorldObject to pass, if needed
     */
    public void broadcastEvent(String eventName, int radius, WorldObject reference) {
        WorldPartition.checkOwner();
        World.forEachVisibleObjectInRange(this, Npc.class, radius, obj -> {
            if (obj.hasListener(EventType.ON_NPC_EVENT_RECEIVED)) {
                EventDispatcher.getInstance()
                        .notifyEventAsync(
                                OnNpcEventReceived.builder()
                                        .eventName(eventName)
                                        .sender(this)
                                        .receiver(obj)
                                        .reference(reference)
                                        .build(),
                                obj);
            }
        });
    }

    /**
     * Sends an event to a given object.
     * @param eventName the event name
     * @param receiver the receiver
     * @param reference the reference
     */
    public void sendScriptEvent(String eventName, WorldObject receiver, WorldObject reference) {
        WorldPartition.checkOwner();
        if (EventDispatcher.getInstance().hasListener(EventType.ON_NPC_EVENT_RECEIVED, receiver)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnNpcEventReceived.builder()
                                    .eventName(eventName)
                                    .sender(this)
                                    .receiver(receiver.asNpc())
                                    .reference(reference)
                                    .build(),
                            receiver);
        }
    }

    /**
     * Gets point in range between radiusMin and radiusMax from this NPC
     * @param radiusMin miminal range from NPC (not closer than)
     * @param radiusMax maximal range from NPC (not further than)
     * @return Location in given range from this NPC
     */
    public Location getPointInRange(int radiusMin, int radiusMax) {
        WorldPartition.checkOwner();
        if ((radiusMax == 0) || (radiusMax < radiusMin)) {
            return new Location(getX(), getY(), getZ());
        }

        final RandomGenerator random = ClockBridge.random();
        final int radius = random.nextInt(radiusMin, radiusMax + 1);
        final double angle = random.nextDouble() * 2 * Math.PI;
        return new Location(
                (int) (getX() + (radius * Math.cos(angle))), (int) (getY() + (radius * Math.sin(angle))), getZ());
    }

    /**
     * Drops an item.
     * @param creature the last attacker or main damage dealer
     * @param itemId the item ID
     * @param itemCount the item count
     * @return the dropped item
     */
    public Item dropItem(Creature creature, int itemId, long itemCount) {
        WorldPartition.checkOwner();
        Item item = null;
        for (int i = 0; i < itemCount; i++) {
            if (ItemData.getInstance().getTemplate(itemId) == null) {
                log.error("Item doesn't exist so cannot be dropped. Item ID: {} Quest: {}", itemId, getName());
                return null;
            }

            item = ItemManager.createItem(ItemProcessType.LOOT, itemId, itemCount, creature, this);
            if (item == null) {
                return null;
            }

            if (creature != null) {
                item.getDropProtection().protect(creature);
            }

            // Randomize drop position.
            final int newX =
                    (getX() + ClockBridge.random().nextInt((RANDOM_ITEM_DROP_LIMIT * 2) + 1)) - RANDOM_ITEM_DROP_LIMIT;
            final int newY =
                    (getY() + ClockBridge.random().nextInt((RANDOM_ITEM_DROP_LIMIT * 2) + 1)) - RANDOM_ITEM_DROP_LIMIT;
            final int newZ = getZ() + 20;

            item.dropMe(this, newX, newY, newZ);

            // Add drop to auto destroy item task.
            if (!GeneralConfig.LIST_PROTECTED_ITEMS.contains(itemId)
                    && (((GeneralConfig.AUTODESTROY_ITEM_AFTER > 0)
                                    && !item.getTemplate().hasExImmediateEffect())
                            || ((GeneralConfig.HERB_AUTO_DESTROY_TIME > 0)
                                    && item.getTemplate().hasExImmediateEffect()))) {
                ItemsAutoDestroyTaskManager.getInstance().addItem(item);
            }

            item.setProtected(false);

            // If stackable, end loop as entire count is included in 1 instance of item.
            if (item.isStackable() || !GeneralConfig.MULTIPLE_ITEM_DROP) {
                break;
            }
        }

        return item;
    }

    /**
     * Method overload for {@link Attackable#dropItem(Creature, int, long)}
     * @param creature the last attacker or main damage dealer
     * @param item the item holder
     * @return the dropped item
     */
    public Item dropItem(Creature creature, ItemHolder item) {
        WorldPartition.checkOwner();
        return dropItem(creature, item.getId(), item.getCount());
    }

    @Override
    public String getName() {
        WorldPartition.checkOwner();
        return getTemplate().getName();
    }

    @Override
    public boolean isVisibleFor(Player player) {
        WorldPartition.checkOwner();
        if (hasListener(EventType.ON_NPC_CAN_BE_SEEN)) {
            final TerminateReturn term = EventDispatcher.getInstance()
                    .notifyEvent(new OnNpcCanBeSeen(this, player), this, TerminateReturn.class);
            if (term != null) {
                return term.terminate();
            }
        }

        return super.isVisibleFor(player);
    }

    /**
     * Adds a summoned NPC.
     * @param npc the summoned NPC
     */
    public void addSummonedNpc(Npc npc) {
        WorldPartition.checkOwner();
        if (summonedNpcs == null) {
            summonedNpcs = new Int2ObjectOpenHashMap<>();
        }

        summonedNpcs.put(npc.getObjectId(), npc);

        npc.setSummoner(this);
    }

    /**
     * Removes a summoned NPC by object ID.
     * @param objectId the summoned NPC object ID
     */
    public void removeSummonedNpc(int objectId) {
        WorldPartition.checkOwner();
        if (summonedNpcs != null) {
            summonedNpcs.remove(objectId);
        }
    }

    /**
     * Gets the summoned NPC by object ID.
     * @param objectId the summoned NPC object ID
     * @return the summoned NPC
     */
    public Npc getSummonedNpc(int objectId) {
        WorldPartition.checkOwner();
        if (summonedNpcs != null) {
            return summonedNpcs.get(objectId);
        }

        return null;
    }

    /**
     * Gets the summoned NPC count.
     * @return the summoned NPC count
     */
    public int getSummonedNpcCount() {
        WorldPartition.checkOwner();
        return summonedNpcs != null ? summonedNpcs.size() : 0;
    }

    /**
     * Resets the summoned NPCs list.
     */
    public void resetSummonedNpcs() {
        WorldPartition.checkOwner();
        if (summonedNpcs != null) {
            summonedNpcs.clear();
        }
    }

    /**
     * Broadcasts NpcSay packet to all known players.
     * @param chatType the chat type
     * @param text the text
     */
    public void broadcastSay(ChatType chatType, String text) {
        WorldPartition.checkOwner();
        World.broadcastToVisiblePlayers(this, new NpcSay(this, chatType, text));
    }

    /**
     * Broadcasts NpcSay packet to all known players with NPC string id.
     * @param chatType the chat type
     * @param npcStringId the NPC string id
     * @param parameters the NPC string id parameters
     */
    public void broadcastSay(ChatType chatType, NpcStringId npcStringId, String... parameters) {
        WorldPartition.checkOwner();
        final NpcSay npcSay = new NpcSay(this, chatType, npcStringId);
        if (parameters != null) {
            for (String parameter : parameters) {
                if (parameter != null) {
                    npcSay.addStringParameter(parameter);
                }
            }
        }

        World.broadcastToVisiblePlayers(this, npcSay);
    }

    /**
     * Broadcasts NpcSay packet to all known players with custom string in specific radius.
     * @param chatType the chat type
     * @param text the text
     * @param radius the radius
     */
    public void broadcastSay(ChatType chatType, String text, int radius) {
        WorldPartition.checkOwner();
        World.broadcastToVisiblePlayersInRange(this, new NpcSay(this, chatType, text), radius);
    }

    /**
     * Broadcasts NpcSay packet to all known players with NPC string id in specific radius.
     * @param chatType the chat type
     * @param npcStringId the NPC string id
     * @param radius the radius
     */
    public void broadcastSay(ChatType chatType, NpcStringId npcStringId, int radius) {
        WorldPartition.checkOwner();
        World.broadcastToVisiblePlayersInRange(this, new NpcSay(this, chatType, npcStringId), radius);
    }

    @Override
    public int getMinShopDistance() {
        WorldPartition.checkOwner();
        return PrivateStoreRangeConfig.SHOP_MIN_RANGE_FROM_NPC;
    }

    public void addQuestTimer(QuestTimer questTimer) {
        WorldPartition.checkOwner();
        questTimers.add(questTimer);
    }

    public void removeQuestTimer(QuestTimer questTimer) {
        WorldPartition.checkOwner();
        questTimers.remove(questTimer);
    }

    public void stopQuestTimers() {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<QuestTimer> timers = ScratchBuffers.snapshot(questTimers)) {
            for (QuestTimer timer : timers) {
                timer.cancelTask();
            }
        }

        questTimers.clear();
    }

    public void addTimerHolder(TimerHolder<?> timer) {
        WorldPartition.checkOwner();
        timerHolders.add(timer);
    }

    public void removeTimerHolder(TimerHolder<?> timer) {
        WorldPartition.checkOwner();
        timerHolders.remove(timer);
    }

    public void stopTimerHolders() {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<TimerHolder<?>> timers = ScratchBuffers.snapshot(timerHolders)) {
            for (TimerHolder<?> timer : timers) {
                timer.cancelTask();
            }
        }

        timerHolders.clear();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(':');
        sb.append(((NpcTemplate) uncheckedTemplate()).getName());
        sb.append('(');
        sb.append(((NpcTemplate) uncheckedTemplate()).getId());
        sb.append(")[");
        sb.append(getObjectId());
        sb.append(']');
        return sb.toString();
    }
}
