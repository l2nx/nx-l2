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
    public static final int INTERACTION_DISTANCE = 250;
    /** Maximum distance where the drop may appear given this NPC position. */
    public static final int RANDOM_ITEM_DROP_LIMIT = 70;
    /** Ids of NPCs that see creatures through the OnCreatureSee event. */
    private static final IntOpenHashSet CREATURE_SEE_IDS = new IntOpenHashSet();

    private Spawn spawn;
    private boolean busy = false;
    private String busyMessage;
    private boolean decayed = false;
    private int castleIndex = -2;
    private int fortIndex = -2;

    private boolean inTown = false;
    private boolean autoAttackable = false;
    private long lastSocialBroadcast = 0;
    /** Milliseconds. */
    private static final int MINIMUM_SOCIAL_INTERVAL = 6000;

    private boolean randomAnimationEnabled = true;

    private boolean randomWalkingEnabled = true;

    private boolean walker = false;

    private boolean talkable = getTemplate().isTalkable();

    private final boolean questMonster = getTemplate().isQuestMonster();

    private final boolean fakePlayer = getTemplate().isFakePlayer();

    private int leftHandItem; // Normally this shouldn't change from the template, but there exist exceptions.

    private int rightHandItem;

    private int enchantEffect;

    private double collisionHeight; // Used for npc grow effect skills.

    private double collisionRadius; // Used for npc grow effect skills.

    private int soulshotamount = 0;
    private int spiritshotamount = 0;

    private int displayEffect = 0;

    private int shotsMask = 0;

    private int killingBlowWeapon;

    private int scriptValue = 0;

    private Int2ObjectOpenHashMap<Npc> summonedNpcs = null;

    private final List<QuestTimer> questTimers = new ArrayList<>();
    private final List<TimerHolder<?>> timerHolders = new ArrayList<>();

    public Npc(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.Npc);
        initCharStatusUpdateValues();

        leftHandItem = getTemplate().getLHandId();
        rightHandItem = getTemplate().getRHandId();
        enchantEffect = NpcConfig.ENABLE_RANDOM_ENCHANT_EFFECT
                ? ClockBridge.random().nextInt(4, 22)
                : getTemplate().getWeaponEnchant();

        collisionHeight = getTemplate().getFCollisionHeight();
        collisionRadius = getTemplate().getFCollisionRadius();
        setFlying(template.isFlying());
    }

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

    public void onRandomAnimation(int animationId) {
        WorldPartition.checkOwner();
        final long now = ClockBridge.gameMillis();
        if ((now - lastSocialBroadcast) > MINIMUM_SOCIAL_INTERVAL) {
            lastSocialBroadcast = now;
            broadcastSocialAction(animationId);
        }
    }

    public boolean hasRandomAnimation() {
        WorldPartition.checkOwner();
        return ((GeneralConfig.MAX_NPC_ANIMATION > 0) && isRandomAnimationEnabled() && (getAiType() != AIType.CORPSE));
    }

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

    @Override
    public NpcTemplate getTemplate() {
        WorldPartition.checkOwner();
        return (NpcTemplate) super.getTemplate();
    }

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

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return getTemplate().getLevel();
    }

    public boolean isAggressive() {
        WorldPartition.checkOwner();
        return false;
    }

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

    @Override
    public boolean isUndead() {
        WorldPartition.checkOwner();
        return getTemplate().getRace() == Race.UNDEAD;
    }

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

    public Castle getCastle() {
        WorldPartition.checkOwner();
        if (castleIndex < 0) {
            final TownZone town = TownManager.getTown(getX(), getY(), getZ());
            if (town != null) {
                castleIndex = CastleManager.getInstance().getCastleIndex(town.getTaxById());
            }

            if (castleIndex < 0) {
                castleIndex = CastleManager.getInstance().findNearestCastleIndex(this);
            } else {
                inTown = true;
            }
        }

        if (castleIndex < 0) {
            return null;
        }

        return CastleManager.getInstance().getCastles().get(castleIndex);
    }

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

    public Castle getCastle(long maxDistance) {
        WorldPartition.checkOwner();
        final int index = CastleManager.getInstance().findNearestCastleIndex(this, maxDistance);
        if (index < 0) {
            return null;
        }

        return CastleManager.getInstance().getCastles().get(index);
    }

    public Fort getFort() {
        WorldPartition.checkOwner();
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

    @Override
    public Item getActiveWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Weapon getActiveWeaponItem() {
        WorldPartition.checkOwner();
        final int weaponId = getTemplate().getRHandId();
        if (weaponId < 1) {
            return null;
        }

        final ItemTemplate item =
                ItemData.getInstance().getTemplate(getTemplate().getRHandId());
        if (!(item instanceof Weapon)) {
            return null;
        }

        return (Weapon) item;
    }

    @Override
    public Item getSecondaryWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Weapon getSecondaryWeaponItem() {
        WorldPartition.checkOwner();
        final int weaponId = getTemplate().getLHandId();
        if (weaponId < 1) {
            return null;
        }

        final ItemTemplate item =
                ItemData.getInstance().getTemplate(getTemplate().getLHandId());
        if (!(item instanceof Weapon)) {
            return null;
        }

        return (Weapon) item;
    }

    public void insertObjectIdAndShowChatWindow(Player player, String content) {
        WorldPartition.checkOwner();
        player.sendPacket(
                new NpcHtmlMessage(getObjectId(), content.replaceAll("%objectId%", String.valueOf(getObjectId()))));
    }

    /** Resolves {@code data/html/default/<npcId>[-<page>].htm}, else {@code data/html/npcdefault.htm}. */
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

        return "data/html/npcdefault.htm";
    }

    public void showChatWindow(Player player) {
        WorldPartition.checkOwner();
        showChatWindow(player, 0);
    }

    private boolean showPkDenyChatWindow(Player player, String type) {
        final String html = HtmCache.getInstance().getHtm(player, "data/html/" + type + "/" + getId() + "-pk.htm");
        if (html != null) {
            insertObjectIdAndShowChatWindow(player, html);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return true;
        }

        return false;
    }

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

                filename = (getHtmlPath(npcId, value));
                break;
            }
        }

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

        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    public void showChatWindow(Player player, String filename) {
        WorldPartition.checkOwner();
        final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
        html.setFile(player, filename);
        html.replace("%objectId%", String.valueOf(getObjectId()));
        player.sendPacket(html);

        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    /** Modified by RATE_XP, or by DynamicExpRates.xml when enabled. */
    public long getExpReward(int level) {
        WorldPartition.checkOwner();
        if (DynamicExpRateData.getInstance().isEnabled()) {
            return (long)
                    (getTemplate().getExp() * DynamicExpRateData.getInstance().getDynamicExpRate(level));
        }

        return (long) (getTemplate().getExp() * RatesConfig.RATE_XP);
    }

    /** Modified by RATE_SP, or by DynamicExpRates.xml when enabled. */
    public int getSpReward(int level) {
        WorldPartition.checkOwner();
        if (DynamicExpRateData.getInstance().isEnabled()) {
            return (int)
                    (getTemplate().getSP() * DynamicExpRateData.getInstance().getDynamicSpRate(level));
        }

        return (int) (getTemplate().getSP() * RatesConfig.RATE_SP);
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        // Scripts may swap weapons; reset to the template ones.
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

    /** Does not remove the object from the World's object map and sends no packets to players. */
    @Override
    public void onDecay() {
        WorldPartition.checkOwner();
        if (decayed) {
            return;
        }

        setDecayed(true);

        super.onDecay();

        if ((spawn != null) && !RaidBossSpawnManager.getInstance().isDefined(getId())) {
            spawn.decreaseCount(this);
        }

        WalkingManager.getInstance().onDeath(this);

        final Creature summoner = getSummoner();
        if ((summoner != null) && summoner.isNpc()) {
            summoner.asNpc().removeSummonedNpc(getObjectId());
        }

        stopQuestTimers();
        stopTimerHolders();

        scriptValue = 0;
    }

    /** Full removal from the world, known objects and the World's object map; sends no packets to players. */
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

    public boolean isScriptValue(int value) {
        WorldPartition.checkOwner();
        return scriptValue == value;
    }

    public int getAIValue(String paramName) {
        WorldPartition.checkOwner();
        return hasAIValue(paramName)
                ? NpcPersonalAIData.getInstance().getAIValue(getSpawn().getName(), paramName)
                : -1;
    }

    public boolean hasAIValue(String paramName) {
        WorldPartition.checkOwner();
        return (getSpawn() != null)
                && (getSpawn().getName() != null)
                && NpcPersonalAIData.getInstance().hasAIValue(getSpawn().getName(), paramName);
    }

    public boolean isInMySpawnGroup(Npc npc) {
        WorldPartition.checkOwner();
        return ((getSpawn() != null)
                && (npc.getSpawn() != null)
                && (getSpawn().getName() != null)
                && (getSpawn().getName().equals(npc.getSpawn().getName())));
    }

    public boolean staysInSpawnLoc() {
        WorldPartition.checkOwner();
        return ((spawn != null) && (spawn.getX() == getX()) && (spawn.getY() == getY()));
    }

    public boolean hasVariables() {
        WorldPartition.checkOwner();
        return getScript(NpcVariables.class) != null;
    }

    public NpcVariables getVariables() {
        WorldPartition.checkOwner();
        final NpcVariables vars = getScript(NpcVariables.class);
        return vars != null ? vars : addScript(new NpcVariables());
    }

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

            final int newX =
                    (getX() + ClockBridge.random().nextInt((RANDOM_ITEM_DROP_LIMIT * 2) + 1)) - RANDOM_ITEM_DROP_LIMIT;
            final int newY =
                    (getY() + ClockBridge.random().nextInt((RANDOM_ITEM_DROP_LIMIT * 2) + 1)) - RANDOM_ITEM_DROP_LIMIT;
            final int newZ = getZ() + 20;

            item.dropMe(this, newX, newY, newZ);

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

    public void addSummonedNpc(Npc npc) {
        WorldPartition.checkOwner();
        if (summonedNpcs == null) {
            summonedNpcs = new Int2ObjectOpenHashMap<>();
        }

        summonedNpcs.put(npc.getObjectId(), npc);

        npc.setSummoner(this);
    }

    public void removeSummonedNpc(int objectId) {
        WorldPartition.checkOwner();
        if (summonedNpcs != null) {
            summonedNpcs.remove(objectId);
        }
    }

    public Npc getSummonedNpc(int objectId) {
        WorldPartition.checkOwner();
        if (summonedNpcs != null) {
            return summonedNpcs.get(objectId);
        }

        return null;
    }

    public int getSummonedNpcCount() {
        WorldPartition.checkOwner();
        return summonedNpcs != null ? summonedNpcs.size() : 0;
    }

    public void resetSummonedNpcs() {
        WorldPartition.checkOwner();
        if (summonedNpcs != null) {
            summonedNpcs.clear();
        }
    }

    public void broadcastSay(ChatType chatType, String text) {
        WorldPartition.checkOwner();
        World.broadcastToVisiblePlayers(this, new NpcSay(this, chatType, text));
    }

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

    public void broadcastSay(ChatType chatType, String text, int radius) {
        WorldPartition.checkOwner();
        World.broadcastToVisiblePlayersInRange(this, new NpcSay(this, chatType, text), radius);
    }

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
        sb.append(uncheckedObjectId());
        sb.append(']');
        return sb.toString();
    }
}
