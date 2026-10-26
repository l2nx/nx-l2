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

import app.l2nx.highfive.ai.AttackableAI;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.ai.Intention;
import app.l2nx.highfive.cache.RelationCache;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeoEngineConfig;
import app.l2nx.highfive.config.GrandBossConfig;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.PvpConfig;
import app.l2nx.highfive.config.custom.BossAnnouncementsConfig;
import app.l2nx.highfive.config.custom.CaptchaConfig;
import app.l2nx.highfive.config.custom.ChampionMonstersConfig;
import app.l2nx.highfive.config.custom.ClassBalanceConfig;
import app.l2nx.highfive.config.custom.FakePlayersConfig;
import app.l2nx.highfive.data.enums.CategoryType;
import app.l2nx.highfive.data.holders.AccessLevel;
import app.l2nx.highfive.data.xml.CategoryData;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.data.xml.FenceData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.SendMessageLocalisationData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.WorldRegion;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.holders.creature.EffectList;
import app.l2nx.highfive.entity.actor.holders.creature.InvulSkillHolder;
import app.l2nx.highfive.entity.actor.holders.creature.TimeStamp;
import app.l2nx.highfive.entity.actor.instance.GrandBoss;
import app.l2nx.highfive.entity.actor.instance.QuestGuard;
import app.l2nx.highfive.entity.actor.instance.RaidBoss;
import app.l2nx.highfive.entity.actor.stat.CreatureStat;
import app.l2nx.highfive.entity.actor.status.CreatureStatus;
import app.l2nx.highfive.entity.actor.tasks.creature.HitTask;
import app.l2nx.highfive.entity.actor.tasks.creature.MagicUseTask;
import app.l2nx.highfive.entity.actor.tasks.creature.QueuedMagicUseTask;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.actor.transform.Transform;
import app.l2nx.highfive.entity.actor.transform.TransformTemplate;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneRegion;
import app.l2nx.highfive.entity.zone.type.WaterZone;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.geoengine.pathfinding.GeoLocation;
import app.l2nx.highfive.geoengine.pathfinding.PathFinding;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.managers.CaptchaManager;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.managers.ScriptManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.effects.EffectFlag;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureAttack;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureAttackAvoid;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureAttacked;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureDamageDealt;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureDamageReceived;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureDeath;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureKilled;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureSee;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureSkillUse;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureTeleported;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcSkillSee;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.attackable.OnAttackableFactionCall;
import app.l2nx.highfive.mechanics.events.listeners.AbstractEventListener;
import app.l2nx.highfive.mechanics.events.returns.TerminateReturn;
import app.l2nx.highfive.mechanics.options.OptionSkillHolder;
import app.l2nx.highfive.mechanics.options.OptionSkillType;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.AbnormalVisualEffect;
import app.l2nx.highfive.mechanics.skill.BuffFinishTask;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.CommonSkill;
import app.l2nx.highfive.mechanics.skill.EffectScope;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.SkillChannelized;
import app.l2nx.highfive.mechanics.skill.SkillChannelizer;
import app.l2nx.highfive.mechanics.skill.enums.FlyType;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.skill.holders.SkillUseHolder;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.mechanics.stats.BaseStat;
import app.l2nx.highfive.mechanics.stats.Calculator;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.AbstractFunction;
import app.l2nx.highfive.movement.Movement;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AbstractNpcInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Attack;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ChangeMoveType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ChangeWaitType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExRotation;
import app.l2nx.highfive.network.protocol.p267.serverpackets.FakePlayerInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.FlyToLocation;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillCanceled;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillLaunched;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillUse;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MoveToLocation;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MoveToPawn;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Revive;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerObjectInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SetupGauge;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SocialAction;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StopMove;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.TeleportToLocation;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import app.l2nx.highfive.taskmanagers.CreatureSeeTaskManager;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.util.LocationUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.random.RandomGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * Mother class of all character objects of the world (PC, NPC...)<br>
 * Creature:<br>
 * <ul>
 * <li>Door</li>
 * <li>Playable</li>
 * <li>Npc</li>
 * <li>StaticObject</li>
 * <li>Trap</li>
 * <li>Vehicle</li>
 * </ul>
 * <b>Concept of CreatureTemplate:</b><br>
 * Each Creature owns generic and static properties (ex : all Keltir have the same number of HP...).<br>
 * All of those properties are stored in a different template for each type of Creature.<br>
 * Each template is loaded once in the server cache memory (reduce memory use).<br>
 * When a new instance of Creature is spawned, server just create a link between the instance and the template.<br>
 * This link is stored in {@link #template}
 * @version $Revision: 1.53.2.45.2.34 $ $Date: 2005/04/11 10:06:08 $
 */
@Slf4j
public abstract class Creature extends WorldObject {
    @Getter
    private final Set<Creature> attackByList = ConcurrentHashMap.newKeySet(1);

    @Getter
    @Setter
    private volatile boolean castingNow = false;

    @Getter
    @Setter
    private volatile boolean castingSimultaneouslyNow = false;

    @Getter
    @Setter
    private Skill lastSkillCast;

    @Getter
    @Setter
    private Skill lastSimultaneousSkillCast;

    @Getter
    @Setter
    private boolean dead = false;

    @Getter
    @Setter
    private boolean immobilized = false;

    @Getter
    @Setter
    private boolean overloaded = false;

    @Setter
    private boolean paralyzed = false;

    private boolean pendingRevive = false;

    @Getter
    private boolean running = false;

    @Getter
    @Setter
    protected boolean showSummonAnimation = false;

    @Getter
    @Setter
    protected boolean teleporting = false;

    @Setter
    private boolean invul = false;

    @Getter
    @Setter
    private boolean mortal = true;

    @Getter
    @Setter
    private boolean flying = false;

    @Getter
    @Setter
    private CreatureStat stat;

    @Getter
    @Setter
    private CreatureStatus status;

    @Getter
    @Setter(lombok.AccessLevel.PROTECTED)
    private CreatureTemplate template;

    @Getter
    private String title;

    public static final double MAX_HP_BAR_PX = 352.0;

    private double hpUpdateIncCheck = .0;
    private double hpUpdateDecCheck = .0;
    private double hpUpdateInterval = .0;

    @Getter
    @Setter
    private int karma = 0;

    @Getter
    private Calculator[] calculators;

    @Getter
    private final Map<Integer, Skill> skills = new ConcurrentHashMap<>();

    @Getter
    private final Map<Integer, TimeStamp> skillReuseTimeStamps = new ConcurrentHashMap<>();

    @Getter
    private final Map<Integer, TimeStamp> itemReuseTimeStamps = new ConcurrentHashMap<>();

    @Getter
    private final Map<Integer, Long> disabledSkills = new ConcurrentHashMap<>();

    private boolean allSkillsDisabled;

    private final byte[] zones = new byte[ZoneId.getZoneCount()];
    protected final Location lastZoneValidateLocation = new Location(getX(), getY(), getZ());

    private final StampedLock attackLock = new StampedLock();

    @Getter
    @Setter
    private Team team = Team.NONE;

    @Getter
    @Setter
    private boolean lethalable = true;

    @Getter
    private final Map<Integer, OptionSkillHolder> triggerSkills = new ConcurrentHashMap<>(1);

    @Getter(lombok.AccessLevel.PRIVATE)
    private final Map<Integer, InvulSkillHolder> invulAgainstSkills = new ConcurrentHashMap<>(1);

    @Getter
    private final EffectList effectList = new EffectList(this);

    @Getter
    @Setter
    private Creature summoner = null;

    private SkillChannelizer channelizer = null;

    private SkillChannelized channelized = null;

    private final BuffFinishTask buffFinishTask = new BuffFinishTask();

    @Getter
    private int abnormalVisualEffects;

    @Getter
    private int abnormalVisualEffectSpecial;

    @Getter
    private int abnormalVisualEffectEvent;

    protected MoveData move;

    @Getter
    @Setter
    private boolean cursorKeyMovement = false;

    @Getter
    private WorldObject target;
    /** Represents the time where the attack should end, in nanoseconds. */
    @Getter
    private volatile long attackEndTime;

    @Getter
    private int bowAttackEndTime;

    @Getter
    private int castInterruptTime;

    private static final Calculator[] NPC_STD_CALCULATOR = Formulas.getStdNPCCalculators();

    private volatile CreatureAI ai = null;

    @Setter
    protected Future<?> skillCast;

    protected Future<?> skillCast2;

    @Getter
    private final Map<Integer, RelationCache> knownRelations = new ConcurrentHashMap<>();

    private Set<Creature> seenCreatures = null;
    private int seenCreatureRange = PlayerConfig.ALT_PARTY_RANGE;

    @Getter
    private final List<Item> fakePlayerDrops = new CopyOnWriteArrayList<>();

    private OnCreatureSkillUse onCreatureSkillUse = null;

    public Creature(CreatureTemplate template) {
        this(IdManager.getInstance().getNextId(), template);
    }

    public Creature(int objectId, CreatureTemplate template) {
        super(objectId);
        if (template == null) {
            throw new NullPointerException("Template is null!");
        }

        setInstanceType(InstanceType.Creature);
        initCharStat();
        initCharStatus();

        this.template = template;
        if (isDoor()) {
            calculators = Formulas.getStdDoorCalculators();
        } else if (isNpc()) {
            calculators = NPC_STD_CALCULATOR;

            // Own copy: spell effects mutate the skill map, which must not leak to other NPCs of the same type.
            for (Skill skill : template.getSkills().values()) {
                addSkill(skill);
            }
        } else {
            calculators = new Calculator[Stat.NUM_STATS];
            if (isSummon()) {
                for (Skill skill : template.getSkills().values()) {
                    addSkill(skill);
                }
            }

            Formulas.addFuncsToNewCharacter(this);
        }

        setInvul(true);
    }

    public Inventory getInventory() {
        return null;
    }

    public boolean destroyItemByItemId(
            ItemProcessType process, int itemId, long count, WorldObject reference, boolean sendMessage) {
        // Default: NPCs consume virtual items for their skills.
        // TODO: should be logged if even happens.. should be false
        return true;
    }

    public boolean destroyItem(
            ItemProcessType process, int objectId, long count, WorldObject reference, boolean sendMessage) {
        // Default: NPCs consume virtual items for their skills.
        // TODO: should be logged if even happens.. should be false
        return true;
    }

    @Override
    public boolean isInsideZone(ZoneId zone) {
        final Instance instance = InstanceManager.getInstance().getInstance(getInstanceId());
        switch (zone) {
            case PVP: {
                if ((instance != null) && instance.isPvP()) {
                    return true;
                }

                return (zones[ZoneId.PVP.ordinal()] > 0)
                        && (zones[ZoneId.PEACE.ordinal()] == 0)
                        && (zones[ZoneId.NO_PVP.ordinal()] == 0);
            }
            case PEACE: {
                if ((instance != null) && instance.isPvP()) {
                    return false;
                }
            }
        }

        return zones[zone.ordinal()] > 0;
    }

    public void setInsideZone(ZoneId zone, boolean state) {
        synchronized (zones) {
            if (state) {
                zones[zone.ordinal()]++;
            } else if (zones[zone.ordinal()] > 0) {
                zones[zone.ordinal()]--;
            }
        }
    }

    public boolean isTransformed() {
        return false;
    }

    public Transform getTransformation() {
        return null;
    }

    public void untransform() {}

    public boolean isGM() {
        return false;
    }

    public AccessLevel getAccessLevel() {
        return null;
    }

    protected void initCharStatusUpdateValues() {
        hpUpdateIncCheck = stat.getMaxHp();
        hpUpdateInterval = hpUpdateIncCheck / MAX_HP_BAR_PX;
        hpUpdateDecCheck = hpUpdateIncCheck - hpUpdateInterval;
    }

    /** Does not remove the object from the World's object map and sends no packets to players. */
    public void onDecay() {
        if (PlayerConfig.DISCONNECT_AFTER_DEATH && isPlayer()) {
            final Player player = asPlayer();
            if (player.isOnline()) {
                Disconnection.of(player)
                        .storeAndDeleteWith(new SystemMessage(SendMessageLocalisationData.getLocalisation(
                                player,
                                "60 min. have passed after the death of your character, so you were"
                                        + " disconnected from the game.")));
            }
        } else {
            decayMe();
            final ZoneRegion region = ZoneManager.getInstance().getRegion(this);
            if (region != null) {
                region.removeFromZones(this);
            }
        }
    }

    @Override
    public void onSpawn() {
        super.onSpawn();

        buffFinishTask.start();

        revalidateZone(true);

        if (this instanceof GrandBoss) {
            if (BossAnnouncementsConfig.GRANDBOSS_SPAWN_ANNOUNCEMENTS
                    && (!isInInstance() || BossAnnouncementsConfig.GRANDBOSS_INSTANCE_ANNOUNCEMENTS)
                    && !isMinion()
                    && !isRaidMinion()) {
                final String name = NpcData.getInstance().getTemplate(getId()).getName();
                if ((name != null)
                        && !BossAnnouncementsConfig.RAIDBOSSES_EXCLUDED_FROM_SPAWN_ANNOUNCEMENTS.contains(getId())) {
                    World.broadcastToAllOnlinePlayers(name + " has spawned!");
                    World.broadcastToAllOnlinePlayersOnScreen(name + " has spawned!");
                }
            }
        } else if (isRaid()
                && BossAnnouncementsConfig.RAIDBOSS_SPAWN_ANNOUNCEMENTS
                && (!isInInstance() || BossAnnouncementsConfig.RAIDBOSS_INSTANCE_ANNOUNCEMENTS)
                && !isMinion()
                && !isRaidMinion()) {
            final String name = NpcData.getInstance().getTemplate(getId()).getName();
            if ((name != null)
                    && !BossAnnouncementsConfig.RAIDBOSSES_EXCLUDED_FROM_SPAWN_ANNOUNCEMENTS.contains(getId())) {
                World.broadcastToAllOnlinePlayers(name + " has spawned!");
                World.broadcastToAllOnlinePlayersOnScreen(name + " has spawned!");
            }
        }
    }

    public synchronized void onTeleported() {
        if (!teleporting) {
            return;
        }

        spawnMe(getX(), getY(), getZ());
        setTeleporting(false);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_TELEPORTED, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnCreatureTeleported(this), this);
        }
    }

    public void addAttackerToAttackByList(Creature creature) {}

    public void broadcastPacket(ServerPacket packet) {
        packet.sendInBroadcast();
        try {
            World.forEachVisibleObject(this, Player.class, player -> {
                if (isVisibleFor(player)) {
                    player.sendPacket(packet);
                }
            });
        } finally {
            packet.releaseBroadcast();
        }
    }

    // TODO: Add target logic.
    public void broadcastSkillPacket(ServerPacket packet, WorldObject target) {
        broadcastPacket(packet);
    }

    // TODO: Add targets logic.
    public void broadcastSkillPacket(ServerPacket packet, Collection<WorldObject> targets) {
        broadcastPacket(packet);
    }

    public void broadcastMoveToLocation() {
        broadcastMoveToLocation(false);
    }

    public void broadcastMoveToLocation(boolean force) {
        final MoveData move = this.move;
        if (move == null) {
            return;
        }

        // Broadcast MoveToLocation when forced or once per second.
        final int gameTicks = GameTimeTaskManager.getInstance().getGameTicks();
        if (!force && !cursorKeyMovement && (move.moveTimestamp > 0) && ((gameTicks - move.lastBroadcastTime) < 10)) {
            return;
        }

        move.lastBroadcastTime = gameTicks;

        if (isPlayable()) {
            broadcastPacket(new MoveToLocation(this));
        } else {
            final CreatureAI ai = hasAI() ? getAI() : null;
            final Intention intention = ai != null ? ai.getIntention() : null;
            final WorldObject target =
                    ((intention == Intention.ATTACK) || (intention == Intention.FOLLOW)) ? this.target : null;
            if (target != null) {
                if ((target != this) && !isOnGeodataPath(move)) {
                    broadcastPacket(new MoveToPawn(this, target, getAI().getClientMovingToPawnOffset()));
                } else {
                    broadcastPacket(new MoveToLocation(this));
                }
            } else {
                final WorldRegion region = getWorldRegion();
                if (((region != null) && region.areNeighborsActive())) {
                    broadcastPacket(new MoveToLocation(this));
                }
            }
        }
    }

    public void broadcastSocialAction(int id) {
        if (isPlayable()) {
            broadcastPacket(new SocialAction(getObjectId(), id));
        } else {
            final WorldRegion region = getWorldRegion();
            if ((region != null) && region.areNeighborsActive()) {
                broadcastPacket(new SocialAction(getObjectId(), id));
            }
        }
    }

    protected boolean needHpUpdate() {
        final double currentHp = status.getCurrentHp();
        final double maxHp = stat.getMaxHp();
        if ((currentHp <= 1.0) || (maxHp < MAX_HP_BAR_PX)) {
            return true;
        }

        if ((currentHp <= hpUpdateDecCheck) || (currentHp >= hpUpdateIncCheck)) {
            if (currentHp == maxHp) {
                hpUpdateIncCheck = currentHp + 1;
                hpUpdateDecCheck = currentHp - hpUpdateInterval;
            } else {
                final double doubleMulti = currentHp / hpUpdateInterval;
                int intMulti = (int) doubleMulti;
                hpUpdateDecCheck = hpUpdateInterval * (doubleMulti < intMulti ? intMulti - 1 : intMulti);
                hpUpdateIncCheck = hpUpdateDecCheck + hpUpdateInterval;
            }

            return true;
        }

        return false;
    }

    /** HP and MP only; CP is not sent. */
    public void broadcastStatusUpdate() {
        if (status.getStatusListener().isEmpty() || !needHpUpdate()) {
            return;
        }

        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.MAX_HP, stat.getMaxHp());
        su.addAttribute(StatusUpdate.CUR_HP, (int) status.getCurrentHp());

        for (Creature temp : status.getStatusListener()) {
            if (temp != null) {
                temp.sendPacket(su);
            }
        }
    }

    public void sendMessage(String text) {}

    public void teleToLocation(int xValue, int yValue, int zValue, int headingValue, int instanceId, int randomOffset) {
        if (isPlayer() && !asPlayer().isOnline()) {
            return;
        }

        int x = xValue;
        int y = yValue;
        int z = flying ? zValue : GeoEngine.getInstance().getHeight(x, y, zValue);
        int heading = headingValue;

        if (pendingRevive) {
            doRevive();
        }

        stopMove(null);
        abortAttack();
        abortCast();
        setTarget(null);

        setTeleporting(true);

        getAI().setIntentionActive();

        decayMe();

        if (PlayerConfig.OFFSET_ON_TELEPORT_ENABLED || (randomOffset > 0)) {
            final RandomGenerator random = ClockBridge.random();
            x = xValue + (randomOffset > 0 ? random.nextInt(-randomOffset, randomOffset + 1) : -randomOffset);
            y = yValue + (randomOffset > 0 ? random.nextInt(-randomOffset, randomOffset + 1) : -randomOffset);

            int count = 0;
            final float collision = Math.min(getTemplate().getCollisionRadius() * 2, randomOffset);
            while ((count++ < 100)
                    && ((LocationUtil.calculateDistance(xValue, yValue, zValue, x, y, z, true, false) < collision)
                            || !GeoEngine.getInstance().canSeeTarget(xValue, yValue, zValue, x, y, z, instanceId)
                            || !GeoEngine.getInstance().canMoveToTarget(xValue, yValue, zValue, x, y, z, instanceId))) {
                x = xValue + (randomOffset > 0 ? random.nextInt(-randomOffset, randomOffset + 1) : -randomOffset);
                y = yValue + (randomOffset > 0 ? random.nextInt(-randomOffset, randomOffset + 1) : -randomOffset);
            }

            if (!flying) {
                final Location validLocation =
                        GeoEngine.getInstance().getValidLocation(xValue, yValue, z, x, y, z, instanceId);
                x = validLocation.getX();
                y = validLocation.getY();
                z = validLocation.getZ();
            }
        }

        z += 5;

        broadcastPacket(new TeleportToLocation(this, x, y, z, heading));

        setInstanceId(instanceId);

        setXYZ(x, y, z);

        if (heading != 0) {
            setHeading(heading);
        }

        // Allow recall of the detached characters.
        if (isPlayer()) {
            final Player player = asPlayer();
            final GameClient client = player.getClient();
            if ((client != null) && client.isDetached()) {
                onTeleported();
            }
        } else {
            onTeleported();
        }

        revalidateZone(true);
    }

    public void teleToLocation(int x, int y, int z, int heading, int instanceId, boolean randomOffset) {
        teleToLocation(x, y, z, heading, instanceId, (randomOffset) ? PlayerConfig.MAX_OFFSET_ON_TELEPORT : 0);
    }

    public void teleToLocation(int x, int y, int z, int heading, int instanceId) {
        teleToLocation(x, y, z, heading, instanceId, 0);
    }

    public void teleToLocation(int x, int y, int z, int heading, boolean randomOffset) {
        teleToLocation(x, y, z, heading, -1, (randomOffset) ? PlayerConfig.MAX_OFFSET_ON_TELEPORT : 0);
    }

    public void teleToLocation(int x, int y, int z, int heading) {
        teleToLocation(x, y, z, heading, -1, 0);
    }

    public void teleToLocation(int x, int y, int z, boolean randomOffset) {
        teleToLocation(x, y, z, 0, -1, (randomOffset) ? PlayerConfig.MAX_OFFSET_ON_TELEPORT : 0);
    }

    public void teleToLocation(int x, int y, int z) {
        teleToLocation(x, y, z, 0, -1, 0);
    }

    public void teleToLocation(ILocational loc, int randomOffset) {
        teleToLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getHeading(), loc.getInstanceId(), randomOffset);
    }

    public void teleToLocation(ILocational loc, int instanceId, int randomOffset) {
        teleToLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getHeading(), instanceId, randomOffset);
    }

    public void teleToLocation(ILocational loc, boolean randomOffset) {
        teleToLocation(
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getHeading(),
                loc.getInstanceId(),
                (randomOffset) ? PlayerConfig.MAX_OFFSET_ON_TELEPORT : 0);
    }

    public void teleToLocation(ILocational loc) {
        teleToLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getHeading(), loc.getInstanceId(), 0);
    }

    public void teleToLocation(TeleportWhereType teleportWhere) {
        teleToLocation(MapRegionData.getInstance().getTeleToLocation(this, teleportWhere), true);
    }

    private boolean canUseRangeWeapon() {
        if (isTransformed()) {
            return true;
        }

        if (isPlayer()) {
            final Weapon weaponItem = getActiveWeaponItem();
            if ((weaponItem == null) || !weaponItem.isRange()) {
                return false;
            }

            if (!checkAndEquipArrows()) {
                getAI().setIntentionIdle();
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(
                        weaponItem.isBow()
                                ? SystemMessageId.YOU_HAVE_RUN_OUT_OF_ARROWS
                                : SystemMessageId.NOT_ENOUGH_BOLTS);
                return false;
            }

            if (bowAttackEndTime <= GameTimeTaskManager.getInstance().getGameTicks()) {
                int mpConsume = weaponItem.getMpConsume();
                if ((weaponItem.getReducedMpConsume() > 0)
                        && (ClockBridge.random().nextInt(100) < weaponItem.getReducedMpConsumeChance())) {
                    mpConsume = weaponItem.getReducedMpConsume();
                }

                mpConsume = (int) calcStat(Stat.BOW_MP_CONSUME_RATE, mpConsume, null, null);
                if (status.getCurrentMp() < mpConsume) {
                    WorldPartition.loop().schedule(() -> getAI().notifyActionReadyToAct(), 1000, TimeUnit.MILLISECONDS);
                    sendPacket(SystemMessageId.NOT_ENOUGH_MP);
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return false;
                }

                if (mpConsume > 0) {
                    status.reduceMp(mpConsume);
                }

                bowAttackEndTime = (5 * GameTimeTaskManager.TICKS_PER_SECOND)
                        + GameTimeTaskManager.getInstance().getGameTicks();
            } else {
                WorldPartition.loop().schedule(() -> getAI().notifyActionReadyToAct(), 1000, TimeUnit.MILLISECONDS);
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }
        } else if (isNpc()) {
            if (bowAttackEndTime > GameTimeTaskManager.getInstance().getGameTicks()) {
                return false;
            }
        }

        return true;
    }

    public void doAttack(Creature target) {
        final long stamp = attackLock.tryWriteLock();
        if (stamp == 0) {
            return;
        }

        try {
            if ((target == null) || isAttackDisabled() || !target.isTargetable()) {
                return;
            }

            if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_ATTACK, this)) {
                final TerminateReturn attackReturn = EventDispatcher.getInstance()
                        .notifyEvent(
                                OnCreatureAttack.builder()
                                        .attacker(this)
                                        .target(target)
                                        .build(),
                                this,
                                TerminateReturn.class);
                if ((attackReturn != null) && attackReturn.terminate()) {
                    getAI().setIntentionActive();
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                }
            }

            if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_ATTACKED, target)) {
                final TerminateReturn attackedReturn = EventDispatcher.getInstance()
                        .notifyEvent(
                                OnCreatureAttacked.builder()
                                        .attacker(this)
                                        .target(target)
                                        .build(),
                                target,
                                TerminateReturn.class);
                if ((attackedReturn != null) && attackedReturn.terminate()) {
                    getAI().setIntentionActive();
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                }
            }

            if (!isAlikeDead()) {
                if ((isNpc() && target.isAlikeDead()) || !isInSurroundingRegion(target)) {
                    getAI().setIntentionActive();
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                } else if (isPlayer()) {
                    if (target.isDead()) {
                        getAI().setIntentionActive();
                        sendPacket(ActionFailed.STATIC_PACKET);
                        return;
                    }

                    if (isTransformed() && !getTransformation().canAttack()) {
                        sendPacket(ActionFailed.STATIC_PACKET);
                        return;
                    }
                }
            }

            if (getActiveWeaponItem() != null) {
                final Weapon wpn = getActiveWeaponItem();
                if (!wpn.isAttackWeapon() && !isGM()) {
                    if (wpn.getItemType() == WeaponType.FISHINGROD) {
                        sendPacket(
                                SystemMessageId
                                        .YOU_LOOK_ODDLY_AT_THE_FISHING_POLE_IN_DISBELIEF_AND_REALIZE_THAT_YOU_CAN_T_ATTACK_ANYTHING_WITH_THIS);
                    } else {
                        sendPacket(SystemMessageId.THAT_WEAPON_CANNOT_PERFORM_ANY_ATTACKS);
                    }

                    sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                }
            }

            final Player player = asPlayer();
            if (player != null) {
                if (player.inObserverMode()) {
                    sendPacket(SystemMessageId.OBSERVERS_CANNOT_PARTICIPATE);
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                } else if (player.isSiegeFriend(target)) {
                    if (TerritoryWarManager.getInstance().isTWInProgress()) {
                        sendPacket(SystemMessageId.YOU_CANNOT_FORCE_ATTACK_A_MEMBER_OF_THE_SAME_TERRITORY);
                    } else {
                        sendPacket(
                                SystemMessageId
                                        .FORCE_ATTACK_IS_IMPOSSIBLE_AGAINST_A_TEMPORARY_ALLIED_MEMBER_DURING_A_SIEGE);
                    }

                    sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                } else if (target.isInsidePeaceZone(player)) {
                    getAI().setIntentionActive();
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                }
            } else if (isInsidePeaceZone(this, target)) {
                getAI().setIntentionActive();
                sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }

            stopEffectsOnAction();

            if (!GeoEngine.getInstance().canSeeTarget(this, target)) {
                sendPacket(SystemMessageId.CANNOT_SEE_TARGET);
                getAI().setIntentionActive();
                sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }

            if (isMoving()) {
                stopMove(getLocation());
            }

            final Weapon weaponItem = getActiveWeaponItem();
            final int timeAtk = calculateTimeBetweenAttacks();
            final int timeToHit = timeAtk / 2;
            final Attack attack = new Attack(
                    this,
                    target,
                    isChargedShot(ShotType.SOULSHOTS),
                    (weaponItem != null) ? weaponItem.getCrystalTypePlus().getLevel() : 0);
            setHeading(LocationUtil.calculateHeadingFrom(this, target));
            final int reuse = calculateReuseTime(weaponItem);
            final long currentTime = ClockBridge.gameMillis();
            boolean hitted = false;
            switch (getAttackType()) {
                case BOW: {
                    if (!canUseRangeWeapon()) {
                        return;
                    }

                    attackEndTime = currentTime + timeToHit + (reuse / 2);
                    hitted = doAttackHitByBow(attack, target, timeAtk, reuse);
                    break;
                }
                case CROSSBOW: {
                    if (!canUseRangeWeapon()) {
                        return;
                    }

                    attackEndTime = currentTime + timeToHit + (reuse / 2);
                    hitted = doAttackHitByCrossBow(attack, target, timeAtk, reuse);
                    break;
                }
                case POLE: {
                    attackEndTime = currentTime + timeAtk;
                    hitted = doAttackHitByPole(attack, target, timeToHit);
                    break;
                }
                case FIST: {
                    if (!isPlayer()) {
                        attackEndTime = currentTime + timeAtk;
                        hitted = doAttackHitSimple(attack, target, timeToHit);
                        break;
                    }
                    // Fallthrough.
                }
                case DUAL:
                case DUALFIST:
                case DUALDAGGER: {
                    attackEndTime = currentTime + timeAtk;
                    hitted = doAttackHitByDual(attack, target, timeToHit);
                    break;
                }
                default: {
                    attackEndTime = currentTime + timeAtk;
                    hitted = doAttackHitSimple(attack, target, timeToHit);
                    break;
                }
            }

            // Guards against int overflow.
            if (attackEndTime < currentTime) {
                attackEndTime = currentTime + Integer.MAX_VALUE;
            }

            if (isFakePlayer()
                    && !FakePlayersConfig.FAKE_PLAYER_AUTO_ATTACKABLE
                    && (target.isPlayable() || target.isFakePlayer())) {
                final Npc npc = asNpc();
                if (!npc.isScriptValue(1)) {
                    npc.setScriptValue(1);
                    broadcastInfo();
                    ScriptManager.getInstance().getScript("PvpFlaggingStopTask").notifyEvent("FLAG_CHECK", npc, null);
                }
            }

            if ((player != null) && !player.isInsideZone(ZoneId.PVP) && (player != target)) {
                AttackStanceTaskManager.getInstance().addAttackStanceTask(player);
                if (player.getSummon() != target) {
                    if (((target.isRaid() && PvpConfig.FLAG_PLAYER_ON_RAID_ATTACK)
                            || (target.isChampion() && PvpConfig.FLAG_PLAYER_ON_CHAMPION_ATTACK))) {
                        player.updatePvPStatus();
                    } else {
                        player.updatePvPStatus(target);
                    }
                }
            } else if (PvpConfig.FLAG_PLAYER_ON_SUMMON_PET_RAID_CHAMPION_ATTACK && (isSummon() || isPet())) {
                final Player owner = isSummon() ? asSummon().getOwner() : asPet().getOwner();
                if ((owner != null)
                        && !owner.isInsideZone(ZoneId.PVP)
                        && (owner != target)
                        && (owner.getSummon() != target)) {
                    if (((target.isRaid() && PvpConfig.FLAG_PLAYER_ON_RAID_ATTACK)
                            || (target.isChampion() && PvpConfig.FLAG_PLAYER_ON_CHAMPION_ATTACK))) {
                        AttackStanceTaskManager.getInstance().addAttackStanceTask(owner);
                        owner.updatePvPStatus();
                    }
                }
            }

            if (!hitted) {
                abortAttack();
            } else {
                setChargedShot(ShotType.SOULSHOTS, false);
                if (player != null) {
                    if (player.isCursedWeaponEquipped()) {
                        if (!target.isInvul()) {
                            target.setCurrentCp(0);
                        }
                    } else if (player.isHero()
                            && target.isPlayer()
                            && target.asPlayer().isCursedWeaponEquipped()) {
                        target.setCurrentCp(0);
                    }
                }
            }

            if (attack.hasHits()) {
                broadcastPacket(attack);
            }

            WorldPartition.loop()
                    .schedule(() -> getAI().notifyActionReadyToAct(), timeAtk + reuse, TimeUnit.MILLISECONDS);
        } finally {
            attackLock.unlockWrite(stamp);
        }
    }

    private boolean doAttackHitByBow(Attack attack, Creature target, int sAtk, int reuse) {
        int damage1 = 0;
        byte shld1 = 0;
        boolean crit1 = false;

        final boolean miss1 = Formulas.calcHitMiss(this, target);

        reduceArrowCount(false);

        move = null;

        if (!miss1) {
            shld1 = Formulas.calcShldUse(this, target);

            crit1 = Formulas.calcCrit(this, target);

            damage1 = (int) Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.hasSoulshot());

            // Bow damage falls off at 60% of full hit range or less and grows above it.
            if (PlayerConfig.CALCULATE_DISTANCE_BOW_DAMAGE) {
                damage1 *= (calculateDistance3D(target) / 4000) + 0.8;
            }
        }

        if (isPlayer()) {
            sendPacket(new SetupGauge(getObjectId(), SetupGauge.RED, sAtk + reuse));
        }

        WorldPartition.loop()
                .schedule(
                        new HitTask(this, target, damage1, crit1, miss1, shld1, attack.hasSoulshot(), true),
                        sAtk,
                        TimeUnit.MILLISECONDS);

        final int gameTime = GameTimeTaskManager.getInstance().getGameTicks();
        bowAttackEndTime = gameTime + ((sAtk + reuse) / GameTimeTaskManager.MILLIS_IN_TICK);

        // Guards against int overflow.
        if (bowAttackEndTime < gameTime) {
            bowAttackEndTime = Integer.MAX_VALUE;
        }

        attack.addHit(target, damage1, miss1, crit1, shld1);

        return !miss1;
    }

    private boolean doAttackHitByCrossBow(Attack attack, Creature target, int sAtk, int reuse) {
        int damage1 = 0;
        byte shld1 = 0;
        boolean crit1 = false;

        final boolean miss1 = Formulas.calcHitMiss(this, target);

        reduceArrowCount(true);

        move = null;

        if (!miss1) {
            shld1 = Formulas.calcShldUse(this, target);

            crit1 = Formulas.calcCrit(this, target);

            damage1 = (int) Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.hasSoulshot());
        }

        if (isPlayer()) {
            sendPacket(SystemMessageId.YOUR_CROSSBOW_IS_PREPARING_TO_FIRE);

            sendPacket(new SetupGauge(getObjectId(), SetupGauge.RED, sAtk + reuse));
        }

        WorldPartition.loop()
                .schedule(
                        new HitTask(this, target, damage1, crit1, miss1, shld1, attack.hasSoulshot(), true),
                        sAtk,
                        TimeUnit.MILLISECONDS);

        final int gameTime = GameTimeTaskManager.getInstance().getGameTicks();
        bowAttackEndTime = gameTime + ((sAtk + reuse) / GameTimeTaskManager.MILLIS_IN_TICK);

        // Guards against int overflow.
        if (bowAttackEndTime < gameTime) {
            bowAttackEndTime = Integer.MAX_VALUE;
        }

        attack.addHit(target, damage1, miss1, crit1, shld1);

        return !miss1;
    }

    private boolean doAttackHitByDual(Attack attack, Creature target, int sAtk) {
        int damage1 = 0;
        int damage2 = 0;
        byte shld1 = 0;
        byte shld2 = 0;
        boolean crit1 = false;
        boolean crit2 = false;

        final boolean miss1 = Formulas.calcHitMiss(this, target);
        final boolean miss2 = Formulas.calcHitMiss(this, target);

        if (!miss1) {
            shld1 = Formulas.calcShldUse(this, target);

            crit1 = Formulas.calcCrit(this, target);

            damage1 = (int) Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.hasSoulshot());
            damage1 /= 2;
        }

        if (!miss2) {
            shld2 = Formulas.calcShldUse(this, target);

            crit2 = Formulas.calcCrit(this, target);

            damage2 = (int) Formulas.calcPhysDam(this, target, null, shld2, crit2, attack.hasSoulshot());
            damage2 /= 2;
        }

        WorldPartition.loop()
                .schedule(
                        new HitTask(this, target, damage1, crit1, miss1, shld1, attack.hasSoulshot(), true),
                        sAtk / 2,
                        TimeUnit.MILLISECONDS);

        WorldPartition.loop()
                .schedule(
                        new HitTask(this, target, damage2, crit2, miss2, shld2, attack.hasSoulshot(), false),
                        sAtk,
                        TimeUnit.MILLISECONDS);

        attack.addHit(target, damage1, miss1, crit1, shld1);
        attack.addHit(target, damage2, miss2, crit2, shld2);

        return !miss1 || !miss2;
    }

    private boolean doAttackHitByPole(Attack attack, Creature target, int sAtk) {
        boolean hitted = doAttackHitSimple(attack, target, 100, sAtk, true);

        if (!isAffected(EffectFlag.POLEARM_SINGLE_TARGET)) {
            // Without Polearm Mastery (skill 216) max simultaneous attacks is 3 (1 by default + 2 in skill 3599).
            int attackCountMax = (int) stat.calcStat(Stat.ATTACK_COUNT_MAX, 1, null, null);
            if (attackCountMax > 1) {
                final double headingAngle = LocationUtil.convertHeadingToDegree(getHeading());
                final int maxRadius = stat.getPhysicalAttackRange();
                final int physicalAttackAngle = stat.getPhysicalAttackAngle();
                double attackpercent = 85;
                for (Creature obj : World.getVisibleObjectsInRange(this, Creature.class, maxRadius)) {
                    if (obj == target) {
                        continue;
                    }

                    if (obj.isAlikeDead()) {
                        continue;
                    }

                    if (!obj.isAutoAttackable(this)) {
                        continue;
                    }

                    if (Math.abs(calculateDirectionTo(obj) - headingAngle) > physicalAttackAngle) {
                        continue;
                    }

                    if (obj.isPet() && isPlayer() && (obj.asPet().getOwner() == asPlayer())) {
                        continue;
                    }

                    if (isAttackable() && obj.isPlayer() && this.target.isAttackable()) {
                        continue;
                    }

                    if (isAttackable() && obj.isAttackable() && !asAttackable().isChaos()) {
                        continue;
                    }

                    hitted |= doAttackHitSimple(attack, obj, attackpercent, sAtk, false);
                    attackpercent /= 1.15;
                    if (--attackCountMax <= 0) {
                        break;
                    }
                }
            }
        }

        return hitted;
    }

    private boolean doAttackHitSimple(Attack attack, Creature target, int sAtk) {
        return doAttackHitSimple(attack, target, 100, sAtk, true);
    }

    private boolean doAttackHitSimple(
            Attack attack, Creature target, double attackpercent, int sAtk, boolean rechargeShots) {
        int damage1 = 0;
        byte shld1 = 0;
        boolean crit1 = false;

        final boolean miss1 = Formulas.calcHitMiss(this, target);

        if (!miss1) {
            shld1 = Formulas.calcShldUse(this, target);

            crit1 = Formulas.calcCrit(this, target);

            damage1 = (int) Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.hasSoulshot());
            if (attackpercent != 100) {
                damage1 = (int) ((damage1 * attackpercent) / 100);
            }
        }

        WorldPartition.loop()
                .schedule(
                        new HitTask(this, target, damage1, crit1, miss1, shld1, attack.hasSoulshot(), rechargeShots),
                        sAtk,
                        TimeUnit.MILLISECONDS);

        attack.addHit(target, damage1, miss1, crit1, shld1);

        return !miss1;
    }

    public void doCast(Skill skill) {
        beginCast(skill, false);
    }

    public void doSimultaneousCast(Skill skill) {
        beginCast(skill, true);
    }

    public void doCast(Skill skill, Creature target, List<WorldObject> targets) {
        if (!checkDoCastConditions(skill)) {
            setCastingNow(false);
            return;
        }

        if (skill.isSimultaneousCast()) {
            doSimultaneousCast(skill, target, targets);
            return;
        }

        stopEffectsOnAction();

        beginCast(skill, false, target, targets);
    }

    public void doSimultaneousCast(Skill skill, Creature target, List<WorldObject> targets) {
        if (!checkDoCastConditions(skill)) {
            setCastingSimultaneouslyNow(false);
            return;
        }

        stopEffectsOnAction();

        beginCast(skill, true, target, targets);
    }

    private void beginCast(Skill skill, boolean isSimultaneous) {
        if (isAttackable() && isMoving()) {
            return;
        }

        if (!checkDoCastConditions(skill)) {
            if (isSimultaneous) {
                setCastingSimultaneouslyNow(false);
            } else {
                setCastingNow(false);
            }

            if (isPlayer()) {
                getAI().setIntentionActive();
            }
            return;
        }

        boolean simultaneously = isSimultaneous;
        if (skill.isSimultaneousCast() && !simultaneously) {
            simultaneously = true;
        }

        stopEffectsOnAction();

        Creature target = null;

        final List<WorldObject> targets = skill.getTargetList(this);
        boolean doit = false;

        switch (skill.getTargetType()) {
            case AREA_SUMMON: // We need it to correct facing
            {
                target = isPlayer() ? asPlayer().getSummon() : null;
                break;
            }
            case AURA:
            case AURA_CORPSE_MOB:
            case FRONT_AURA:
            case BEHIND_AURA:
            case GROUND:
            case AURA_FRIENDLY: {
                target = this;
                break;
            }
            case SELF:
            case PET:
            case SERVITOR:
            case SUMMON:
            case OWNER_PET:
            case PARTY:
            case CLAN:
            case PARTY_CLAN:
            case COMMAND_CHANNEL: {
                doit = true;
                // Fallthrough.
            }
            default: {
                if (targets.isEmpty()) {
                    if (simultaneously) {
                        setCastingSimultaneouslyNow(false);
                    } else {
                        setCastingNow(false);
                    }

                    if (isPlayer()) {
                        sendPacket(ActionFailed.STATIC_PACKET);
                        getAI().setIntentionActive();
                    }
                    return;
                }

                if ((skill.isContinuous() && !skill.isDebuff())
                        || skill.hasEffectType(EffectType.CPHEAL, EffectType.HEAL)) {
                    doit = true;
                }

                if (doit) {
                    target = targets.get(0).asCreature();
                } else {
                    target = this.target != null ? this.target.asCreature() : null;
                }
            }
        }

        beginCast(skill, simultaneously, target, targets);
    }

    private void beginCast(Skill skill, boolean simultaneously, Creature target, List<WorldObject> targets) {
        if (target == null) {
            if (simultaneously) {
                setCastingSimultaneouslyNow(false);
            } else {
                setCastingNow(false);
            }

            if (isPlayer()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                getAI().setIntentionActive();
            }
            return;
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_SKILL_USE, this)) {
            if (onCreatureSkillUse == null) {
                onCreatureSkillUse = new OnCreatureSkillUse();
            }

            onCreatureSkillUse.setCaster(this);
            onCreatureSkillUse.setSkill(skill);
            onCreatureSkillUse.setSimultaneously(simultaneously);
            onCreatureSkillUse.setTarget(target);
            onCreatureSkillUse.setTargets(targets);
            final TerminateReturn term =
                    EventDispatcher.getInstance().notifyEvent(onCreatureSkillUse, this, TerminateReturn.class);
            if ((term != null) && term.terminate()) {
                if (simultaneously) {
                    setCastingSimultaneouslyNow(false);
                } else {
                    setCastingNow(false);
                }

                if (isPlayer()) {
                    sendPacket(ActionFailed.STATIC_PACKET);
                    getAI().setIntentionActive();
                }
                return;
            }
        }

        // TODO: Unhardcode using event listeners!
        if (skill.hasEffectType(EffectType.RESURRECTION)
                && (isResurrectionBlocked() || target.isResurrectionBlocked())) {
            sendPacket(SystemMessageId.REJECT_RESURRECTION);
            target.sendPacket(SystemMessageId.REJECT_RESURRECTION);
            if (simultaneously) {
                setCastingSimultaneouslyNow(false);
            } else {
                setCastingNow(false);
            }

            if (isPlayer()) {
                getAI().setIntentionActive();
                sendPacket(ActionFailed.STATIC_PACKET);
            }
            return;
        }

        int skillTime = (skill.getHitTime() + skill.getCoolTime());
        if (!skill.isChanneling() || (skill.getChannelingSkillId() == 0)) {
            if (!skill.isStatic()) {
                skillTime = Formulas.calcAtkSpd(this, skill, skillTime);
            }

            if (skill.isMagic()
                    && (isChargedShot(ShotType.SPIRITSHOTS) || isChargedShot(ShotType.BLESSED_SPIRITSHOTS))) {
                skillTime = (int) (0.6 * skillTime);
            }
        }

        // Client can't handle less than 550ms Casting Animation in Magic Skills with more than 550ms base.
        if (skill.isMagic() && ((skill.getHitTime() + skill.getCoolTime()) > 550) && (skillTime < 550)) {
            skillTime = 550;
        }
        // Client can't handle less than 500ms Casting Animation in Physical Skills with 500ms base or more.
        else if (!skill.isStatic() && ((skill.getHitTime() + skill.getCoolTime()) >= 500) && (skillTime < 500)) {
            skillTime = 500;
        }

        if (castingSimultaneouslyNow && simultaneously) {
            WorldPartition.loop()
                    .schedule(() -> beginCast(skill, simultaneously, target, targets), 100, TimeUnit.MILLISECONDS);
            return;
        }

        if (simultaneously) {
            setCastingSimultaneouslyNow(true);
        } else {
            setCastingNow(true);
        }

        if (!simultaneously) {
            castInterruptTime = -2
                    + GameTimeTaskManager.getInstance().getGameTicks()
                    + (skillTime / GameTimeTaskManager.MILLIS_IN_TICK);
            setLastSkillCast(skill);
        } else {
            setLastSimultaneousSkillCast(skill);
        }

        int reuseDelay;
        if (skill.isStaticReuse() || skill.isStatic()) {
            reuseDelay = (skill.getReuseDelay());
        } else if (skill.isMagic()) {
            reuseDelay = (int) (skill.getReuseDelay() * calcStat(Stat.MAGIC_REUSE_RATE, 1, null, null));
        } else if (skill.isPhysical()) {
            reuseDelay = (int) (skill.getReuseDelay() * calcStat(Stat.P_REUSE, 1, null, null));
        } else {
            reuseDelay = (int) (skill.getReuseDelay() * calcStat(Stat.DANCE_REUSE, 1, null, null));
        }

        if (isPlayable()) {
            reuseDelay = (int) (reuseDelay
                    * ClassBalanceConfig.SKILL_REUSE_MULTIPLIERS[
                            asPlayer().getPlayerClass().getId()]);
        }

        final int initmpcons = stat.getMpInitialConsume(skill);
        if (initmpcons > 0) {
            status.reduceMp(initmpcons);
            final StatusUpdate su = new StatusUpdate(this);
            su.addAttribute(StatusUpdate.CUR_MP, (int) status.getCurrentMp());
            sendPacket(su);
        }

        if (reuseDelay > 10) {
            if (Formulas.calcSkillMastery(this, skill)) {
                reuseDelay = 100;
                if (isPlayable()) {
                    sendPacket(SystemMessageId.A_SKILL_IS_READY_TO_BE_USED_AGAIN);
                }
            }

            if (reuseDelay > 1000) {
                addTimeStamp(skill, reuseDelay);
            } else {
                disableSkill(skill, reuseDelay);
            }
        }

        if (target != this) {
            setHeading(LocationUtil.calculateHeadingFrom(this, target));
            broadcastPacket(new ExRotation(getObjectId(), getHeading()));
        }

        if (isPlayable()) {
            if ((skill.getItemConsumeId() > 0)
                    && !destroyItemByItemId(null, skill.getItemConsumeId(), skill.getItemConsumeCount(), null, true)) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
                abortCast();
                return;
            }

            if ((skill.getReferenceItemId() > 0)
                    && (ItemData.getInstance()
                                    .getTemplate(skill.getReferenceItemId())
                                    .getBodyPart()
                            == BodyPart.DECO)) {
                for (Item item : getInventory().getAllItemsByItemId(skill.getReferenceItemId())) {
                    if (item.isEquipped()) {
                        if (item.getMana() < item.useSkillDisTime()) {
                            abortCast();
                            return;
                        }

                        item.decreaseMana(false, item.useSkillDisTime());
                        break;
                    }
                }
            }
        }

        if (skill.getFlyType() != null) {
            broadcastPacket(new FlyToLocation(this, target, skill.getFlyType()));
            setXYZ(target.getX(), target.getY(), target.getZ());
        }

        if (!skill.isToggle()) {
            broadcastSkillPacket(
                    new MagicSkillUse(
                            this, target, skill.getDisplayId(), skill.getDisplayLevel(), skillTime, reuseDelay),
                    target);
            broadcastSkillPacket(
                    new MagicSkillLaunched(this, skill.getDisplayId(), skill.getDisplayLevel(), targets), targets);
        }

        if (isPlayer() && !skill.isAbnormalInstant()) {
            SystemMessage sm = null;
            switch (skill.getId()) {
                case 1312: // Fishing
                {
                    // Done in Player.startFishing()
                    break;
                }
                case 2046: // Wolf Collar
                {
                    sm = new SystemMessage(SystemMessageId.SUMMONING_YOUR_PET);
                    break;
                }
                default: {
                    sm = new SystemMessage(SystemMessageId.USE_S1);
                    sm.addSkillName(skill);
                    break;
                }
            }

            if (sm != null) {
                sendPacket(sm);
            }
        }

        if (skill.hasEffects(EffectScope.START)) {
            skill.applyEffectScope(EffectScope.START, new BuffInfo(this, target, skill), true, false);
        }

        final MagicUseTask mut = new MagicUseTask(this, targets, skill, skillTime, simultaneously);

        if (skillTime > 0) {
            if (isPlayer() && !simultaneously) {
                sendPacket(new SetupGauge(getObjectId(), SetupGauge.BLUE, skillTime));
            }

            if (skill.isChanneling() && (skill.getChannelingSkillId() > 0)) {
                getSkillChannelizer().startChanneling(skill);
            }

            if (simultaneously) {
                final Future<?> future = skillCast2;
                if (future != null) {
                    future.cancel(true);
                    skillCast2 = null;
                }

                // 400 ms early for client animation (party buffs especially).
                skillCast2 = WorldPartition.loop().schedule(mut, Math.max(0, skillTime - 400), TimeUnit.MILLISECONDS);
            } else {
                final Future<?> future = skillCast;
                if (future != null) {
                    future.cancel(true);
                    skillCast = null;
                }

                skillCast = WorldPartition.loop().schedule(mut, Math.max(0, skillTime - 400), TimeUnit.MILLISECONDS);
            }
        } else {
            mut.setSkillTime(0);
            onMagicLaunchedTimer(mut);
        }
    }

    public boolean checkDoCastConditions(Skill skill) {
        if ((skill == null)
                || isSkillDisabled(skill)
                || ((skill.getFlyType() == FlyType.CHARGE) && isMovementDisabled())) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (status.getCurrentMp() < (stat.getMpConsume(skill) + stat.getMpInitialConsume(skill))) {
            sendPacket(SystemMessageId.NOT_ENOUGH_MP);

            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (status.getCurrentHp() <= skill.getHpConsume()) {
            sendPacket(SystemMessageId.NOT_ENOUGH_HP);

            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (!skill.isStatic()) {
            if (skill.isMagic()) {
                if (isMuted()) {
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return false;
                }
            } else {
                if (isPhysicalMuted()) {
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return false;
                }
            }
        }

        if (skill.isChanneling() && (skill.getChannelingSkillId() > 0) && !isInInstance()) {
            final ZoneRegion zoneRegion = ZoneManager.getInstance().getRegion(this);
            boolean canCast = true;
            if ((skill.getTargetType() == TargetType.GROUND) && isPlayer()) {
                final Location wp = asPlayer().getCurrentSkillWorldPosition();
                if (!zoneRegion.checkEffectRangeInsidePeaceZone(skill, wp.getX(), wp.getY(), wp.getZ())) {
                    canCast = false;
                }
            } else if (!zoneRegion.checkEffectRangeInsidePeaceZone(skill, getX(), getY(), getZ())) {
                canCast = false;
            }

            if (!canCast) {
                final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
                sm.addSkillName(skill);
                sendPacket(sm);
                return false;
            }
        }

        if (getActiveWeaponItem() != null) {
            final Weapon wep = getActiveWeaponItem();
            if (wep.useWeaponSkillsOnly() && !isGM() && wep.hasSkills()) {
                boolean found = false;
                for (SkillHolder sh : wep.getSkills()) {
                    if (sh.getSkillId() == skill.getId()) {
                        found = true;
                    }
                }

                if (!found) {
                    if (asPlayer() != null) {
                        sendPacket(SystemMessageId.THAT_WEAPON_CANNOT_USE_ANY_OTHER_SKILL_EXCEPT_THE_WEAPON_S_SKILL);
                    }

                    return false;
                }
            }
        }

        // TODO: combine check and consume
        if ((skill.getItemConsumeId() > 0) && (getInventory() != null)) {
            final Item requiredItems = getInventory().getItemByItemId(skill.getItemConsumeId());

            if ((requiredItems == null) || (requiredItems.getCount() < skill.getItemConsumeCount())) {
                // Checked: when a summon skill failed, server show required consume item count.
                if (skill.hasEffectType(EffectType.SUMMON)) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.SUMMONING_A_SERVITOR_COSTS_S2_S1);
                    sm.addItemName(skill.getItemConsumeId());
                    sm.addInt(skill.getItemConsumeCount());
                    sendPacket(sm);
                } else {
                    sendPacket(SystemMessageId.THERE_ARE_NOT_ENOUGH_NECESSARY_ITEMS_TO_USE_THE_SKILL);
                }

                return false;
            }
        }

        return true;
    }

    public void addTimeStampItem(Item item, long reuse) {
        addTimeStampItem(item, reuse, -1);
    }

    public void addTimeStampItem(Item item, long reuse, long endsAt) {
        itemReuseTimeStamps.put(item.getObjectId(), new TimeStamp(item, reuse, endsAt));
    }

    /** @return the remaining time, or -1 without a time stamp. */
    public long getItemRemainingReuseTime(int itemObjId) {
        final TimeStamp reuseStamp = itemReuseTimeStamps.get(itemObjId);
        return reuseStamp != null ? reuseStamp.getRemaining() : -1;
    }

    /** @return the remaining time, or -1 without a time stamp. */
    public long getReuseDelayOnGroup(int group) {
        if ((group > 0) && !itemReuseTimeStamps.isEmpty()) {
            final long currentTime = ClockBridge.gameMillis();
            for (TimeStamp ts : itemReuseTimeStamps.values()) {
                if (ts.getSharedReuseGroup() == group) {
                    final long stamp = ts.getStamp();
                    if (currentTime < stamp) {
                        return Math.max(stamp - currentTime, 0);
                    }
                }
            }
        }

        return -1;
    }

    public void addTimeStamp(Skill skill, long reuse) {
        addTimeStamp(skill, reuse, -1);
    }

    public void addTimeStamp(Skill skill, long reuse, long endsAt) {
        skillReuseTimeStamps.put(skill.getReuseHashCode(), new TimeStamp(skill, reuse, endsAt));
    }

    public void removeTimeStamp(Skill skill) {
        skillReuseTimeStamps.remove(skill.getReuseHashCode());
    }

    public void resetTimeStamps() {
        skillReuseTimeStamps.clear();
    }

    /** @return the remaining time, or -1 without a time stamp. */
    public long getSkillRemainingReuseTime(int hashCode) {
        final TimeStamp reuseStamp = skillReuseTimeStamps.get(hashCode);
        return reuseStamp != null ? reuseStamp.getRemaining() : -1;
    }

    public boolean hasSkillReuse(int hashCode) {
        final TimeStamp reuseStamp = skillReuseTimeStamps.get(hashCode);
        return (reuseStamp != null) && reuseStamp.hasNotPassed();
    }

    public TimeStamp getSkillReuseTimeStamp(int hashCode) {
        return skillReuseTimeStamps.get(hashCode);
    }

    public void enableSkill(Skill skill) {
        if (skill == null) {
            return;
        }

        disabledSkills.remove(skill.getReuseHashCode());
    }

    /** Disables the skill for {@code delay} ms; zero or less disables it indefinitely. */
    public void disableSkill(Skill skill, long delay) {
        if (skill == null) {
            return;
        }

        disabledSkills.put(skill.getReuseHashCode(), delay > 0 ? ClockBridge.gameMillis() + delay : Long.MAX_VALUE);
    }

    public void resetDisabledSkills() {
        disabledSkills.clear();
    }

    public boolean isSkillDisabled(Skill skill) {
        if (skill == null) {
            return false;
        }

        if (isAllSkillsDisabled()) {
            return true;
        }

        final int hashCode = skill.getReuseHashCode();
        if (hasSkillReuse(hashCode)) {
            return true;
        }

        if (disabledSkills.isEmpty()) {
            return false;
        }

        final Long stamp = disabledSkills.get(hashCode);
        if (stamp == null) {
            return false;
        }

        if (stamp < ClockBridge.gameMillis()) {
            disabledSkills.remove(hashCode);
            return false;
        }

        return true;
    }

    public void disableAllSkills() {
        allSkillsDisabled = true;
    }

    public void enableAllSkills() {
        allSkillsDisabled = false;
    }

    /** @return {@code false} if the creature was already dead. */
    public boolean doDie(Creature killer) {
        if (CaptchaConfig.ENABLE_CAPTCHA) {
            CaptchaManager.getInstance().updateCounter(killer, this);
        }

        synchronized (this) {
            if (dead) {
                return false;
            }

            setCurrentHp(0);
            setDead(true);
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_DEATH, this)) {
            EventDispatcher.getInstance()
                    .notifyEvent(
                            OnCreatureDeath.builder()
                                    .attacker(killer)
                                    .target(this)
                                    .build(),
                            this);
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_KILLED, this)) {
            final TerminateReturn returnBack = EventDispatcher.getInstance()
                    .notifyEvent(
                            OnCreatureKilled.builder()
                                    .attacker(killer)
                                    .target(this)
                                    .build(),
                            this,
                            TerminateReturn.class);
            if ((returnBack != null) && returnBack.terminate()) {
                return false;
            }
        }

        final Creature mainDamageDealer = isMonster() ? asMonster().getMainDamageDealer() : null;
        calculateRewards(mainDamageDealer != null ? mainDamageDealer : killer);

        setTarget(null);

        stopMove(null);

        status.stopHpMpRegeneration();

        if (isAttackable()) {
            final Spawn spawn = asNpc().getSpawn();
            if ((spawn != null) && spawn.isRespawnEnabled()) {
                stopAllEffects();
            } else {
                effectList.stopAllEffectsWithoutExclusions(true, true);
            }

            if ((killer != null) && killer.isPlayable() && !killer.asPlayer().isGM()) {
                final NpcTemplate template = asAttackable().getTemplate();
                final Set<Integer> clans = template.getClans();
                if ((clans != null) && !clans.isEmpty()) {
                    World.forEachVisibleObjectInRange(this, Attackable.class, template.getClanHelpRange(), called -> {
                        if (called.isDead() || !called.hasAI() || (Math.abs(killer.getZ() - called.getZ()) > 600)) {
                            return;
                        }

                        if ((ClockBridge.gameMillis() - called.getSpawnTime()) < 7000) {
                            return;
                        }

                        if ((called.getAI().getIntention() != Intention.IDLE)
                                && (called.getAI().getIntention() != Intention.ACTIVE)) {
                            return;
                        }

                        if (!template.isClan(called.getTemplate().getClans())) {
                            return;
                        }

                        called.getAI().notifyActionAggression(killer, 1);

                        if (EventDispatcher.getInstance().hasListener(EventType.ON_ATTACKABLE_FACTION_CALL, called)) {
                            EventDispatcher.getInstance()
                                    .notifyEventAsync(
                                            OnAttackableFactionCall.builder()
                                                    .npc(called)
                                                    .caller(asAttackable())
                                                    .attacker(killer.asPlayer())
                                                    .summon(killer.isSummon())
                                                    .build(),
                                            called);
                        }
                    });
                }
            }
        } else {
            stopAllEffectsExceptThoseThatLastThroughDeath();
        }

        broadcastStatusUpdate();

        if (hasAI()) {
            getAI().notifyActionDeath();
        }

        ZoneManager.getInstance().getRegion(this).onDeath(this);

        getAttackByList().clear();

        if (isChannelized()) {
            getSkillChannelized().abortChannelization();
        }

        if (this instanceof GrandBoss) {
            if (BossAnnouncementsConfig.GRANDBOSS_DEFEAT_ANNOUNCEMENTS
                    && (!isInInstance() || BossAnnouncementsConfig.GRANDBOSS_INSTANCE_ANNOUNCEMENTS)
                    && !isMinion()
                    && !isRaidMinion()) {
                final String name = NpcData.getInstance().getTemplate(getId()).getName();
                if ((name != null)
                        && !BossAnnouncementsConfig.RAIDBOSSES_EXCLUDED_FROM_DEFEAT_ANNOUNCEMENTS.contains(getId())) {
                    World.broadcastToAllOnlinePlayers(name + " has been defeated!");
                    World.broadcastToAllOnlinePlayersOnScreen(name + " has been defeated!");
                }
            }
        } else if (isRaid()
                && BossAnnouncementsConfig.RAIDBOSS_DEFEAT_ANNOUNCEMENTS
                && (!isInInstance() || BossAnnouncementsConfig.RAIDBOSS_INSTANCE_ANNOUNCEMENTS)
                && !isMinion()
                && !isRaidMinion()) {
            final String name = NpcData.getInstance().getTemplate(getId()).getName();
            if ((name != null)
                    && !BossAnnouncementsConfig.RAIDBOSSES_EXCLUDED_FROM_DEFEAT_ANNOUNCEMENTS.contains(getId())) {
                World.broadcastToAllOnlinePlayers(name + " has been defeated!");
                World.broadcastToAllOnlinePlayersOnScreen(name + " has been defeated!");
            }
        }

        return true;
    }

    @Override
    public boolean decayMe() {
        if (hasAI()) {
            if (isAttackable()) {
                getAttackByList().clear();
                asAttackable().clearAggroList();
                getAI().setIntentionIdle();
            }

            getAI().stopAITask();
        }

        coreAIDisabled = false;

        onCreatureSkillUse = null;

        return super.decayMe();
    }

    public boolean deleteMe() {
        if (hasAI()) {
            getAI().stopAITask();
        }

        effectList.stopAllEffectsWithoutExclusions(false, false);

        if (seenCreatures != null) {
            CreatureSeeTaskManager.getInstance().remove(this);
            seenCreatures.clear();
        }

        buffFinishTask.stop();

        setWorldRegion(null);

        return true;
    }

    public void detachAI() {
        if (isWalker()) {
            return;
        }

        setAI(null);
    }

    protected void calculateRewards(Creature killer) {}

    public void doRevive() {
        if (!dead) {
            return;
        }

        if (!teleporting) {
            setIsPendingRevive(false);
            setDead(false);

            if ((PlayerConfig.RESPAWN_RESTORE_CP > 0)
                    && (status.getCurrentCp() < (stat.getMaxCp() * PlayerConfig.RESPAWN_RESTORE_CP))) {
                status.setCurrentCp(stat.getMaxCp() * PlayerConfig.RESPAWN_RESTORE_CP);
            }

            if ((PlayerConfig.RESPAWN_RESTORE_HP > 0)
                    && (status.getCurrentHp() < (stat.getMaxHp() * PlayerConfig.RESPAWN_RESTORE_HP))) {
                status.setCurrentHp(stat.getMaxHp() * PlayerConfig.RESPAWN_RESTORE_HP);
            }

            if ((PlayerConfig.RESPAWN_RESTORE_MP > 0)
                    && (status.getCurrentMp() < (stat.getMaxMp() * PlayerConfig.RESPAWN_RESTORE_MP))) {
                status.setCurrentMp(stat.getMaxMp() * PlayerConfig.RESPAWN_RESTORE_MP);
            }

            broadcastPacket(new Revive(this));
            ZoneManager.getInstance().getRegion(this).onRevive(this);
        } else {
            setIsPendingRevive(true);
        }
    }

    public void doRevive(double revivePower) {
        doRevive();
    }

    public CreatureAI getAI() {
        CreatureAI ai = this.ai;
        if (ai == null) {
            synchronized (this) {
                ai = this.ai;
                if (ai == null) {
                    this.ai = ai = initAI();
                }
            }
        }

        return ai;
    }

    protected CreatureAI initAI() {
        return new CreatureAI(this);
    }

    public void setAI(CreatureAI newAI) {
        final CreatureAI oldAI = ai;
        if ((oldAI != null) && (oldAI != newAI) && (oldAI instanceof AttackableAI)) {
            oldAI.stopAITask();
        }

        ai = newAI;
    }

    public boolean hasAI() {
        return ai != null;
    }

    public boolean isRaid() {
        return false;
    }

    public boolean isMinion() {
        return false;
    }

    public boolean isRaidMinion() {
        return false;
    }

    public boolean isControlBlocked() {
        return isAffected(EffectFlag.FEAR);
    }

    public boolean isAllSkillsDisabled() {
        return allSkillsDisabled || isStunned() || isSleeping() || isParalyzed();
    }

    public boolean isAttackDisabled() {
        return isAttackingNow() || isDisabled();
    }

    public boolean isDisabled() {
        return coreAIDisabled
                || isAlikeDead()
                || isPhysicalAttackMuted()
                || isStunned()
                || isSleeping()
                || isParalyzed();
    }

    public boolean isConfused() {
        return isAffected(EffectFlag.CONFUSED);
    }

    public boolean isAlikeDead() {
        return dead;
    }

    public boolean isMuted() {
        return isAffected(EffectFlag.MUTED);
    }

    public boolean isPhysicalMuted() {
        return isAffected(EffectFlag.PSYCHICAL_MUTED);
    }

    public boolean isPhysicalAttackMuted() {
        return isAffected(EffectFlag.PSYCHICAL_ATTACK_MUTED);
    }

    public boolean isMovementDisabled() {
        // check for isTeleporting to prevent teleport cheating (if appear packet not received)
        return isStunned()
                || isRooted()
                || isSleeping()
                || overloaded
                || isParalyzed()
                || immobilized
                || isAlikeDead()
                || teleporting;
    }

    public boolean isOutOfControl() {
        return isConfused() || isControlBlocked();
    }

    public boolean isParalyzed() {
        return paralyzed || isAffected(EffectFlag.PARALYZED);
    }

    public boolean isPendingRevive() {
        return dead && pendingRevive;
    }

    public void setIsPendingRevive(boolean value) {
        pendingRevive = value;
    }

    public boolean isDisarmed() {
        return isAffected(EffectFlag.DISARMED);
    }

    public boolean isRooted() {
        return isAffected(EffectFlag.ROOTED);
    }

    private void setRunning(boolean value) {
        if (running == value) {
            return;
        }

        running = value;
        if (stat.getRunSpeed() != 0) {
            broadcastPacket(new ChangeMoveType(this));
        }

        if (isPlayer()) {
            asPlayer().broadcastUserInfo();
        } else if (isSummon()) {
            broadcastStatusUpdate();
        } else if (isNpc()) {
            World.forEachVisibleObject(this, Player.class, player -> {
                if (!isVisibleFor(player)) {
                    return;
                }

                if (isFakePlayer()) {
                    player.sendPacket(new FakePlayerInfo(asNpc()));
                } else if (stat.getRunSpeed() == 0) {
                    player.sendPacket(new ServerObjectInfo(asNpc(), player));
                } else {
                    player.sendPacket(new AbstractNpcInfo.NpcInfo(asNpc(), player));
                }
            });
        }
    }

    public void setRunning() {
        setRunning(true);
    }

    public boolean isSleeping() {
        return isAffected(EffectFlag.SLEEP);
    }

    public boolean isStunned() {
        return isAffected(EffectFlag.STUNNED);
    }

    public boolean isBetrayed() {
        return isAffected(EffectFlag.BETRAYED);
    }

    public boolean isInvul() {
        return invul || teleporting || isAffected(EffectFlag.INVUL);
    }

    public boolean isUndead() {
        return false;
    }

    public boolean isResurrectionBlocked() {
        return isAffected(EffectFlag.BLOCK_RESURRECTION);
    }

    public void initCharStat() {
        stat = new CreatureStat(this);
    }

    public void initCharStatus() {
        status = new CreatureStatus(this);
    }

    public void setTitle(String value) {
        if (value == null) {
            title = "";
        } else {
            title = isPlayer() && (value.length() > 21) ? value.substring(0, 20) : value;
        }
    }

    public void setWalking() {
        setRunning(false);
    }

    public boolean hasAbnormalVisualEffect(AbnormalVisualEffect ave) {
        if (ave.isEvent()) {
            return (abnormalVisualEffectEvent & ave.getMask()) == ave.getMask();
        }

        if (ave.isSpecial()) {
            return (abnormalVisualEffectSpecial & ave.getMask()) == ave.getMask();
        }

        return (abnormalVisualEffects & ave.getMask()) == ave.getMask();
    }

    public void startAbnormalVisualEffect(boolean update, AbnormalVisualEffect... aves) {
        for (AbnormalVisualEffect ave : aves) {
            if (ave.isEvent()) {
                abnormalVisualEffectEvent |= ave.getMask();
            } else if (ave.isSpecial()) {
                abnormalVisualEffectSpecial |= ave.getMask();
            } else {
                abnormalVisualEffects |= ave.getMask();
            }
        }

        if (update) {
            updateAbnormalEffect();
        }
    }

    public void stopAbnormalVisualEffect(boolean update, AbnormalVisualEffect... aves) {
        for (AbnormalVisualEffect ave : aves) {
            if (ave.isEvent()) {
                abnormalVisualEffectEvent &= ~ave.getMask();
            } else if (ave.isSpecial()) {
                abnormalVisualEffectSpecial &= ~ave.getMask();
            } else {
                abnormalVisualEffects &= ~ave.getMask();
            }
        }

        if (update) {
            updateAbnormalEffect();
        }
    }

    public void startFakeDeath() {
        if (!isPlayer()) {
            return;
        }

        asPlayer().setFakeDeath(true);

        abortAttack();
        abortCast();
        stopMove(null);
        getAI().notifyActionFakeDeath();
        broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_START_FAKEDEATH));

        if (PlayerConfig.FAKE_DEATH_UNTARGET) {
            World.forEachVisibleObject(this, Creature.class, c -> {
                if (c.getTarget() == this) {
                    c.setTarget(null);
                }
            });
        }
    }

    public void startStunning() {
        abortAttack();
        abortCast();
        stopMove(null);
        getAI().notifyActionStunned();
        if (!isSummon()) {
            getAI().setIntentionIdle();
        }

        updateAbnormalEffect();
    }

    public void startParalyze() {
        abortAttack();
        abortCast();
        stopMove(null);
        getAI().notifyActionParalyzed();
    }

    public void stopAllEffects() {
        effectList.stopAllEffects();
    }

    /** Stops all effects except those that last through death. */
    public void stopAllEffectsExceptThoseThatLastThroughDeath() {
        effectList.stopAllEffectsExceptThoseThatLastThroughDeath();
    }

    public void stopSkillEffects(SkillFinishType type, int skillId) {
        effectList.stopSkillEffects(type, skillId);
    }

    public void stopEffects(EffectType type) {
        effectList.stopEffects(type);
    }

    /** Runs on any action except movement (attack, cast). */
    public void stopEffectsOnAction() {
        effectList.stopEffectsOnAction();
    }

    /** Runs on HP decrease and mana burn. */
    public void stopEffectsOnDamage(boolean awake) {
        effectList.stopEffectsOnDamage(awake);
    }

    public void stopFakeDeath(boolean removeEffects) {
        if (removeEffects) {
            stopEffects(EffectType.FAKE_DEATH);
        }

        // recentFakeDeath grants a grace period against mobs only.
        if (isPlayer()) {
            final Player player = asPlayer();
            player.setFakeDeath(false);
            player.setRecentFakeDeath(true);
        }

        broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_STOP_FAKEDEATH));

        // TODO: Temp hack: players see FD on ppl that are moving: Teleport to someone who uses FD - if he gets up he
        // will fall down again for that client -
        // even tho he is actually standing... Probably bad info in CharInfo packet?
        broadcastPacket(new Revive(this));
    }

    public void stopStunning(boolean removeEffects) {
        if (removeEffects) {
            stopEffects(EffectType.STUN);
        }

        if (!isPlayer()) {
            getAI().notifyActionThink();
        }

        updateAbnormalEffect();
    }

    public void stopTransformation(boolean removeEffects) {
        if (removeEffects) {
            effectList.stopSkillEffects(SkillFinishType.NORMAL, AbnormalType.TRANSFORM);
        }

        if (isPlayer() && (getTransformation() != null)) {
            untransform();
        }

        if (!isPlayer()) {
            getAI().notifyActionThink();
        }

        updateAbnormalEffect();
    }

    public abstract void updateAbnormalEffect();

    /** Updates only this player's client, not the party's. */
    public void updateEffectIcons() {
        updateEffectIcons(false);
    }

    public void updateEffectIcons(boolean partyOnly) {}

    public boolean isAffectedBySkill(int skillId) {
        return effectList.isAffectedBySkill(skillId);
    }

    public static class MoveData {
        // When we retrieve x/y/z we use GameTimeControl.getGameTicks().
        // If we are moving, but move timestamp==gameticks, we don't need to recalculate position.
        public int moveStartTime;
        public int moveTimestamp;
        public int xDestination;
        public int yDestination;
        public int zDestination;
        public double xAccurate; // Otherwise there would be rounding errors.
        public double yAccurate;
        public double zAccurate;
        public int heading;

        public boolean disregardingGeodata;
        public int onGeodataPathIndex;
        public List<GeoLocation> geoPath;
        public int geoPathAccurateTx;
        public int geoPathAccurateTy;
        public int geoPathGtx;
        public int geoPathGty;

        public int lastBroadcastTime;
    }

    /** NPCs without skills share NPC_STD_CALCULATOR, so it is copied before the first modification. */
    public void addStatFunc(AbstractFunction function) {
        if (function == null) {
            return;
        }

        synchronized (this) {
            if (calculators == NPC_STD_CALCULATOR) {
                calculators = new Calculator[Stat.NUM_STATS];
                for (int i = 0; i < Stat.NUM_STATS; i++) {
                    if (NPC_STD_CALCULATOR[i] != null) {
                        calculators[i] = new Calculator(NPC_STD_CALCULATOR[i]);
                    }
                }
            }

            final int stat = function.getStat().ordinal();
            if (calculators[stat] == null) {
                calculators[stat] = new Calculator();
            }

            calculators[stat].addFunc(function);
        }
    }

    public void addStatFuncs(List<AbstractFunction> functions) {
        final List<Stat> modifiedStats = new ArrayList<>();
        for (AbstractFunction f : functions) {
            modifiedStats.add(f.getStat());
            addStatFunc(f);
        }

        broadcastModifiedStats(modifiedStats);
    }

    /** Re-links an NPC to the shared NPC_STD_CALCULATOR once its calculators match it again. */
    public void removeStatFunc(AbstractFunction function) {
        if (function == null) {
            return;
        }

        final int stat = function.getStat().ordinal();

        synchronized (this) {
            if (calculators[stat] == null) {
                return;
            }

            calculators[stat].removeFunc(function);

            if (calculators[stat].size() == 0) {
                calculators[stat] = null;
            }

            if (isNpc()) {
                int i = 0;
                for (; i < Stat.NUM_STATS; i++) {
                    if (!Calculator.equalsCals(calculators[i], NPC_STD_CALCULATOR[i])) {
                        break;
                    }
                }

                if (i >= Stat.NUM_STATS) {
                    calculators = NPC_STD_CALCULATOR;
                }
            }
        }
    }

    public void removeStatFuncs(AbstractFunction[] functions) {
        final List<Stat> modifiedStats = new ArrayList<>();
        for (AbstractFunction f : functions) {
            modifiedStats.add(f.getStat());
            removeStatFunc(f);
        }

        broadcastModifiedStats(modifiedStats);
    }

    public void removeStatsOwner(Object owner) {
        List<Stat> modifiedStats = null;
        int i = 0;

        synchronized (this) {
            for (Calculator calc : calculators) {
                if (calc != null) {
                    if (modifiedStats != null) {
                        modifiedStats.addAll(calc.removeOwner(owner));
                    } else {
                        modifiedStats = calc.removeOwner(owner);
                    }

                    if (calc.size() == 0) {
                        calculators[i] = null;
                    }
                }

                i++;
            }

            if (isNpc()) {
                i = 0;
                for (; i < Stat.NUM_STATS; i++) {
                    if (!Calculator.equalsCals(calculators[i], NPC_STD_CALCULATOR[i])) {
                        break;
                    }
                }

                if (i >= Stat.NUM_STATS) {
                    calculators = NPC_STD_CALCULATOR;
                }
            }

            if (isSummon()) {
                if (getCurrentHp() > getMaxHp()) {
                    setCurrentHp(getMaxHp());
                }

                if (getCurrentMp() > getMaxMp()) {
                    setCurrentMp(getMaxMp());
                }
            }

            broadcastModifiedStats(modifiedStats);
        }
    }

    protected void broadcastModifiedStats(List<Stat> stats) {
        if (!isSpawned()) {
            return;
        }

        if ((stats == null) || stats.isEmpty()) {
            return;
        }

        if (isSummon()) {
            final Summon summon = asSummon();
            if (summon.getOwner() != null) {
                summon.updateAndBroadcastStatus(1);
            }
        } else {
            boolean broadcastFull = false;
            final StatusUpdate su = new StatusUpdate(this);
            for (Stat stat : stats) {
                if (stat == Stat.POWER_ATTACK_SPEED) {
                    su.addAttribute(StatusUpdate.ATK_SPD, (int) this.stat.getPAtkSpd());
                } else if (stat == Stat.MAGIC_ATTACK_SPEED) {
                    su.addAttribute(StatusUpdate.CAST_SPD, this.stat.getMAtkSpd());
                } else if (stat == Stat.MOVE_SPEED) {
                    broadcastFull = true;
                }
            }

            if (isPlayer()) {
                final Player player = asPlayer();
                if (broadcastFull) {
                    player.broadcastUserInfo();
                } else {
                    player.updateUserInfo();
                    if (su.hasAttributes()) {
                        broadcastPacket(su);
                    }
                }

                final Summon summon = player.getSummon();
                if ((summon != null) && isAffected(EffectFlag.SERVITOR_SHARE)) {
                    summon.broadcastStatusUpdate();
                }
            } else if (isNpc()) {
                if (broadcastFull) {
                    World.forEachVisibleObject(this, Player.class, player -> {
                        if (!isVisibleFor(player)) {
                            return;
                        }

                        if (isFakePlayer()) {
                            player.sendPacket(new FakePlayerInfo(asNpc()));
                        } else if (this.stat.getRunSpeed() == 0) {
                            player.sendPacket(new ServerObjectInfo(asNpc(), player));
                        } else {
                            player.sendPacket(new AbstractNpcInfo.NpcInfo(asNpc(), player));
                        }
                    });
                } else if (su.hasAttributes()) {
                    broadcastPacket(su);
                }
            } else if (su.hasAttributes()) {
                broadcastPacket(su);
            }
        }
    }

    /** Destination X while moving, current X otherwise. */
    public int getXdestination() {
        final MoveData move = this.move;
        if (move != null) {
            return move.xDestination;
        }

        return getX();
    }

    /** Destination Y while moving, current Y otherwise. */
    public int getYdestination() {
        final MoveData move = this.move;
        if (move != null) {
            return move.yDestination;
        }

        return getY();
    }

    /** Destination Z while moving, current Z otherwise. */
    public int getZdestination() {
        final MoveData move = this.move;
        if (move != null) {
            return move.zDestination;
        }

        return getZ();
    }

    public boolean isInCombat() {
        return hasAI() && ((getAI().getAttackTarget() != null) || getAI().isAutoAttacking());
    }

    public boolean isMoving() {
        return move != null;
    }

    public boolean isOnGeodataPath() {
        final MoveData move = this.move;
        if (move == null) {
            return false;
        }

        return isOnGeodataPath(move);
    }

    public boolean isOnGeodataPath(MoveData move) {
        if (move.onGeodataPathIndex == -1) {
            return false;
        }

        if (move.onGeodataPathIndex == (move.geoPath.size() - 1)) {
            return false;
        }

        return true;
    }

    /** @return the geo path of the current move, or null when there is no move. */
    public List<GeoLocation> getGeoPath() {
        final MoveData move = this.move;
        if (move != null) {
            return move.geoPath;
        }

        return null;
    }

    public boolean canAbortCast() {
        return castInterruptTime > GameTimeTaskManager.getInstance().getGameTicks();
    }

    public boolean isAttackingOrCastingNow() {
        return isAttackingNow() || isRangeAttackingNow() || isCastingNow() || isCastingSimultaneouslyNow();
    }

    public boolean isAttackingNow() {
        return attackEndTime > ClockBridge.gameMillis();
    }

    public boolean isRangeAttackingNow() {
        return bowAttackEndTime > GameTimeTaskManager.getInstance().getGameTicks();
    }

    public void abortAttack() {
        if (isAttackingNow()) {
            sendPacket(ActionFailed.STATIC_PACKET);
        }
    }

    public void abortCast() {
        if (castingNow || castingSimultaneouslyNow) {
            Future<?> future = skillCast;

            if (future != null) {
                future.cancel(true);
                skillCast = null;
            }

            future = skillCast2;
            if (future != null) {
                future.cancel(true);
                skillCast2 = null;
            }

            // TODO: Handle removing spawned npc.
            if (isChanneling()) {
                getSkillChannelizer().stopChanneling();
            }

            if (allSkillsDisabled) {
                enableAllSkills(); // this remains for forced skill use, e.g. scroll of escape
            }

            setCastingNow(false);
            setCastingSimultaneouslyNow(false);

            castInterruptTime = 0;
            if (isPlayer()) {
                getAI().notifyActionFinishCasting();
            }

            broadcastPacket(new MagicSkillCanceled(getObjectId()));
            sendPacket(ActionFailed.STATIC_PACKET);
        }
    }

    /**
     * @return true when the move is finished. X and Y are computed server-side so players cannot
     * tamper with their speed; Z is taken from the client's ValidatePosition packet.
     */
    public boolean updatePosition() {
        if (!isSpawned()) {
            this.move = null;
            return true;
        }

        final MoveData move = this.move;
        if (move == null) {
            return true;
        }

        if (move.moveTimestamp == 0) {
            move.moveTimestamp = move.moveStartTime;
            move.xAccurate = getX();
            move.yAccurate = getY();
        }

        final int gameTicks = GameTimeTaskManager.getInstance().getGameTicks();
        if (move.moveTimestamp == gameTicks) {
            return false;
        }

        final int xPrev = getX();
        final int yPrev = getY();
        final int zPrev = getZ(); // The z coordinate may be modified by coordinate synchronizations.
        double dx = move.xDestination - move.xAccurate;
        double dy = move.yDestination - move.yAccurate;
        double dz = move.zDestination - zPrev;

        if (isPlayer() && !flying) {
            if (cursorKeyMovement) {
                final double angle = LocationUtil.convertHeadingToDegree(getHeading());
                final double radian = Math.toRadians(angle);
                final double course = Math.toRadians(180);
                final double frontDistance = 10 * (stat.getMoveSpeed() / 100);
                final int x1 = (int) (Math.cos(Math.PI + radian + course) * frontDistance);
                final int y1 = (int) (Math.sin(Math.PI + radian + course) * frontDistance);
                final int x = xPrev + x1;
                final int y = yPrev + y1;
                if (!GeoEngine.getInstance().canMoveToTarget(xPrev, yPrev, zPrev, x, y, zPrev, getInstanceId())) {
                    // Stop at the current position, not lastServerPosition (which is where the move started).
                    // Snapping back to lastServerPosition while the client renders the player at the wall causes a
                    // large WASD desync that grows with each pivot against geometry.
                    move.onGeodataPathIndex = -1;
                    stopMove(getLocation());
                    return true;
                }
            } else {
                final double distance = Math.sqrt((dx * dx) + (dy * dy));
                if (distance > 3000) {
                    final double angle = LocationUtil.convertHeadingToDegree(getHeading());
                    final double radian = Math.toRadians(angle);
                    final double course = Math.toRadians(180);
                    final double frontDistance = 10 * (stat.getMoveSpeed() / 100);
                    final int x1 = (int) (Math.cos(Math.PI + radian + course) * frontDistance);
                    final int y1 = (int) (Math.sin(Math.PI + radian + course) * frontDistance);
                    final int x = xPrev + x1;
                    final int y = yPrev + y1;
                    if (!GeoEngine.getInstance().canMoveToTarget(xPrev, yPrev, zPrev, x, y, zPrev, getInstanceId())) {
                        move.onGeodataPathIndex = -1;
                        if (hasAI()) {
                            if (getAI().isFollowing()) {
                                getAI().stopFollow();
                            }

                            getAI().setIntentionIdle();
                        }

                        return true;
                    }
                } else {
                    if (hasAI()
                            && (getAI().getIntention() == Intention.ATTACK)) // Retail: players attacking move directly.
                    {
                        final double angle = LocationUtil.convertHeadingToDegree(getHeading());
                        final double radian = Math.toRadians(angle);
                        final double course = Math.toRadians(180);
                        final double frontDistance = 10 * (stat.getMoveSpeed() / 100);
                        final int x1 = (int) (Math.cos(Math.PI + radian + course) * frontDistance);
                        final int y1 = (int) (Math.sin(Math.PI + radian + course) * frontDistance);
                        final int x = xPrev + x1;
                        final int y = yPrev + y1;
                        if (!GeoEngine.getInstance()
                                .canMoveToTarget(xPrev, yPrev, zPrev, x, y, zPrev, getInstanceId())) {
                            move.onGeodataPathIndex = -1;
                            broadcastPacket(new StopMove(this));
                            return true;
                        }
                    } else {
                        final WorldRegion region = getWorldRegion();
                        if (region != null) {
                            final boolean hasDoors = !region.getDoors().isEmpty();
                            final boolean hasFences = !region.getFences().isEmpty();
                            if (hasDoors || hasFences) {
                                final double angle = LocationUtil.convertHeadingToDegree(getHeading());
                                final double radian = Math.toRadians(angle);
                                final double course = Math.toRadians(180);
                                final double frontDistance = 10 * (stat.getMoveSpeed() / 100);
                                final int x1 = (int) (Math.cos(Math.PI + radian + course) * frontDistance);
                                final int y1 = (int) (Math.sin(Math.PI + radian + course) * frontDistance);
                                final int x = xPrev + x1;
                                final int y = yPrev + y1;
                                if ((hasDoors
                                                && DoorData.getInstance()
                                                        .checkIfDoorsBetween(
                                                                xPrev,
                                                                yPrev,
                                                                zPrev,
                                                                x,
                                                                y,
                                                                zPrev,
                                                                getInstanceId(),
                                                                false)) //
                                        || (hasFences
                                                && FenceData.getInstance()
                                                        .checkIfFenceBetween(
                                                                xPrev, yPrev, zPrev, x, y, zPrev, getInstanceId()))) {
                                    move.onGeodataPathIndex = -1;
                                    if (hasAI()) {
                                        if (getAI().isFollowing()) {
                                            getAI().stopFollow();
                                        }

                                        getAI().setIntentionIdle();
                                    }

                                    stopMove(null);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        double delta = (dx * dx) + (dy * dy);
        final boolean isFloating = flying || (isInsideZone(ZoneId.WATER) && !isInsideZone(ZoneId.CASTLE));
        if (!isFloating
                && (delta < 10000)
                && ((dz * dz) > 2500)) // Close enough: tolerates client/server geodata mismatch.
        {
            delta = Math.sqrt(delta);
        } else {
            delta = Math.sqrt(delta + (dz * dz));
        }

        final double collision;
        final WorldObject target = this.target;
        if ((target != null) && target.isCreature() && hasAI() && (getAI().getIntention() == Intention.ATTACK)) {
            collision = target.asCreature().getTemplate().getCollisionRadius();
        } else {
            collision = getTemplate().getCollisionRadius();
        }

        delta = Math.max(0.00001, delta - collision);

        double distFraction = Double.MAX_VALUE;
        if (delta > 1) {
            final double distPassed =
                    (stat.getMoveSpeed() * (gameTicks - move.moveTimestamp)) / GameTimeTaskManager.TICKS_PER_SECOND;
            distFraction = distPassed / delta;
        }

        final boolean arrived = distFraction > 1;
        if (arrived) {
            super.setXYZ(move.xDestination, move.yDestination, move.zDestination);
        } else {
            final int newZ = zPrev + (int) ((dz * distFraction) + 0.5);
            move.xAccurate += dx * distFraction;
            move.yAccurate += dy * distFraction;

            // Prevent attackables teleporting to another ground layer while moving.
            if (isAttackable() && !isFloating && (Math.abs(newZ - zPrev) > 300)) {
                final Spawn spawn = asAttackable().getSpawn();
                if (spawn != null) {
                    teleToLocation(spawn, getInstanceId(), 0);
                    getAttackByList().clear();
                    asAttackable().clearAggroList();
                    getAI().setIntentionIdle();
                }

                return true;
            }

            super.setXYZ((int) move.xAccurate, (int) move.yAccurate, newZ);
        }

        revalidateZone(false);

        move.moveTimestamp = gameTicks;

        if (arrived && !isOnGeodataPath()) {
            broadcastMoveToLocation(true);
        } else if (isAttackable() && (target != null)) {
            broadcastMoveToLocation();
        }

        return arrived;
    }

    public void revalidateZone(boolean force) {
        // This function is called too often from movement code.
        if (!force
                && (calculateDistance3D(lastZoneValidateLocation)
                        < (isNpc() && !isInCombat() ? NpcConfig.MAX_DRIFT_RANGE : 100))) {
            return;
        }

        lastZoneValidateLocation.setXYZ(this);

        final ZoneRegion region = ZoneManager.getInstance().getRegion(this);
        if (region != null) {
            region.revalidateZones(this);
        } else {
            World.disposeOutOfBoundsObject(this);
        }
    }

    /** Sends no StopMove/StopRotation packet. */
    public void stopMove(Location loc) {
        move = null;

        if (loc != null) {
            setXYZ(loc.getX(), loc.getY(), loc.getZ());
            setHeading(loc.getHeading());
            revalidateZone(true);
        }

        broadcastPacket(new StopMove(this));
    }

    public void setTarget(WorldObject object) {
        if ((object != null) && !object.isSpawned()) {
            target = null;
            return;
        }

        target = object;
    }

    /** @return the target's object id, or -1 without a target. */
    public int getTargetId() {
        if (target != null) {
            return target.getObjectId();
        }

        return 0;
    }

    /** Only sets up the move; sends no MoveToPawn/MoveToLocation packet. */
    public void moveToLocation(int xValue, int yValue, int zValue, int offsetValue) {
        final double speed = stat.getMoveSpeed();
        if ((speed <= 0) || isMovementDisabled()) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        int x = xValue;
        int y = yValue;
        int z = zValue;
        int offset = offsetValue;

        final int curX = getX();
        final int curY = getY();
        final int curZ = getZ();

        double dx = (x - curX);
        double dy = (y - curY);
        double dz = (z - curZ);
        double distance = Math.sqrt((dx * dx) + (dy * dy));

        final boolean verticalMovementOnly = flying && (distance == 0) && (dz != 0);
        if (verticalMovementOnly) {
            distance = Math.abs(dz);
        }

        // Water moves stay short and skip geodata checks: a click distance can easily exceed 3000.
        final boolean isInWater = isInsideZone(ZoneId.WATER) && !isInsideZone(ZoneId.CASTLE);
        if (isInWater && (distance > 700)) {
            final double divider = 700 / distance;
            x = curX + (int) (divider * dx);
            y = curY + (int) (divider * dy);
            z = curZ + (int) (divider * dz);
            dx = (x - curX);
            dy = (y - curY);
            dz = (z - curZ);
            distance = Math.sqrt((dx * dx) + (dy * dy));
        }

        // @formatter:off
        // Define movement angles needed.
        // ^
        // |    X (x,y)
        // |   /
        // |  / distance
        // | /
        // |/ angle
        // X ---------->
        // (curx,cury)
        // @formatter:on

        double cos;
        double sin;

        if ((offset > 0) || (distance < 1)) {
            // approximation for moving closer when z coordinates are different
            // TODO: handle Z axis movement better
            offset -= Math.abs(dz);
            if (offset < 5) {
                offset = 5;
            }

            if ((distance < 1) || ((distance - offset) <= 0)) {
                getAI().notifyActionArrived();
                sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }

            sin = dy / distance;
            cos = dx / distance;
            distance -= (offset - 5); // Due to rounding error, we have to move a bit closer to be in range.

            x = curX + (int) (distance * cos);
            y = curY + (int) (distance * sin);
        } else {
            sin = dy / distance;
            cos = dx / distance;
        }

        final MoveData move = new MoveData();

        final WorldRegion region = getWorldRegion();
        move.disregardingGeodata = (region == null) || !region.areNeighborsActive();
        move.onGeodataPathIndex = -1;
        if (!move.disregardingGeodata && !flying && !isInWater && !isVehicle() && !cursorKeyMovement) {
            final boolean isInVehicle = isPlayer() && (asPlayer().getVehicle() != null);
            if (isInVehicle) {
                move.disregardingGeodata = true;
            }

            if ((GeoEngineConfig.PATHFINDING > 0) && !(this instanceof QuestGuard)) {
                int originalX = x;
                int originalY = y;
                final int originalZ = z;
                final double originalDistance = distance;
                final int gtx = (originalX - World.WORLD_X_MIN) >> 4;
                final int gty = (originalY - World.WORLD_Y_MIN) >> 4;
                final MoveData currentMove = this.move;
                if ((currentMove != null) && isOnGeodataPath(currentMove)) {
                    if ((gtx == currentMove.geoPathGtx) && (gty == currentMove.geoPathGty)) {
                        sendPacket(ActionFailed.STATIC_PACKET);
                        return;
                    }

                    currentMove.onGeodataPathIndex = -1;
                }

                final boolean directMove = isPlayer() && hasAI() && (getAI().getIntention() == Intention.ATTACK);

                final boolean isDestinationInWater =
                        isPlayer() && (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null);

                if (directMove //
                        || (!isInVehicle
                                && !isDestinationInWater
                                && !(isPlayer() && (distance > 3000))
                                && !(isMonster()
                                        && (Math.abs(dz) > 100)
                                        && GeoEngine.getInstance()
                                                .canSeeTarget(this, target)) // Monsters can move on ledges.
                                && !(((curZ - z) > 300)
                                        && (distance
                                                < 300)))) // Prohibit correcting destination if character wants to fall.
                {
                    final Location destination =
                            GeoEngine.getInstance().getValidLocation(curX, curY, curZ, x, y, z, getInstanceId());
                    x = destination.getX();
                    y = destination.getY();
                    if (!isPlayer()) {
                        z = destination.getZ();
                    }

                    dx = x - curX;
                    dy = y - curY;
                    dz = z - curZ;
                    distance = verticalMovementOnly ? (dz * dz) : Math.sqrt((dx * dx) + (dy * dy));
                }

                if (!directMove && ((originalDistance - distance) > 30) && !isControlBlocked() && !isInVehicle) {
                    move.geoPath = PathFinding.getInstance()
                            .findPath(curX, curY, curZ, originalX, originalY, originalZ, getInstanceId(), isPlayer());
                    boolean found = (move.geoPath != null) && (move.geoPath.size() > 1);

                    if (!found && isAttackable()) {
                        int xMin = Math.min(curX, originalX);
                        int xMax = Math.max(curX, originalX);
                        int yMin = Math.min(curY, originalY);
                        int yMax = Math.max(curY, originalY);
                        final int maxDiff = Math.min(Math.max(xMax - xMin, yMax - yMin), 500);
                        xMin -= maxDiff;
                        xMax += maxDiff;
                        yMin -= maxDiff;
                        yMax += maxDiff;
                        int destinationX = 0;
                        int destinationY = 0;
                        double shortDistance = Double.MAX_VALUE;
                        double tempDistance;
                        List<GeoLocation> tempPath;
                        for (int sX = xMin; sX < xMax; sX += 500) {
                            for (int sY = yMin; sY < yMax; sY += 500) {
                                final double tdx = sX - originalX;
                                final double tdy = sY - originalY;
                                tempDistance = Math.sqrt((tdx * tdx) + (tdy * tdy));
                                if (tempDistance < shortDistance) {
                                    tempPath = PathFinding.getInstance()
                                            .findPath(curX, curY, curZ, sX, sY, originalZ, getInstanceId(), false);
                                    found = (tempPath != null) && (tempPath.size() > 1);
                                    if (found) {
                                        shortDistance = tempDistance;
                                        move.geoPath = tempPath;
                                        destinationX = sX;
                                        destinationY = sY;
                                    }
                                }
                            }
                        }

                        found = (move.geoPath != null) && (move.geoPath.size() > 1);
                        if (found) {
                            originalX = destinationX;
                            originalY = destinationY;
                        }
                    }

                    if (found) {
                        move.onGeodataPathIndex = 0;
                        move.geoPathGtx = gtx;
                        move.geoPathGty = gty;
                        move.geoPathAccurateTx = originalX;
                        move.geoPathAccurateTy = originalY;
                        x = move.geoPath.get(move.onGeodataPathIndex).getX();
                        y = move.geoPath.get(move.onGeodataPathIndex).getY();
                        z = move.geoPath.get(move.onGeodataPathIndex).getZ();
                        dx = x - curX;
                        dy = y - curY;
                        dz = z - curZ;
                        distance = verticalMovementOnly ? (dz * dz) : Math.sqrt((dx * dx) + (dy * dy));
                        sin = dy / distance;
                        cos = dx / distance;
                    } else {
                        // No path found: move directly, as retail does.

                        move.disregardingGeodata = true;
                        x = originalX;
                        y = originalY;
                        z = originalZ;
                        distance = originalDistance;
                    }
                }

                if (isPlayable()
                        && !cursorKeyMovement
                        && (move.geoPath == null) //
                        && !isInVehicle
                        && (distance < 3000)
                        && !(((curZ - z) > 300)
                                && (distance < 300))) // Prohibit correcting destination if character wants to fall.
                {
                    final Location destination =
                            GeoEngine.getInstance().getValidLocation(curX, curY, curZ, x, y, z, getInstanceId());
                    x = destination.getX();
                    y = destination.getY();
                    z = destination.getZ();
                    dx = x - curX;
                    dy = y - curY;
                    dz = z - curZ;
                    distance = verticalMovementOnly ? (dz * dz) : Math.sqrt((dx * dx) + (dy * dy));
                }
            }

            if ((distance < 1) && ((GeoEngineConfig.PATHFINDING > 0) || isPlayable())) {
                // An attack or cast already in flight has to survive this, cancelling it is what strands melee.
                final Intention intention = hasAI() ? getAI().getIntention() : null;
                final boolean pendingTargetAction = (intention == Intention.ATTACK) || (intention == Intention.CAST);
                if (isSummon() && !pendingTargetAction) {
                    if (getAI().getFollowTarget() != asPlayer()) {
                        asSummon().setFollowStatus(false);
                        getAI().setIntentionIdle();
                    }
                } else if (!pendingTargetAction) {
                    getAI().setIntentionIdle();
                }

                sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }
        }

        if ((flying || isInWater) && !verticalMovementOnly) {
            distance = Math.sqrt((distance * distance) + (dz * dz));
        }

        final int ticksToMove = (int) ((GameTimeTaskManager.TICKS_PER_SECOND * distance) / speed);
        move.xDestination = x;
        move.yDestination = y;
        move.zDestination = z;

        move.heading = 0; // Initial value for coordinate sync.

        // Does not break heading on vertical movements.
        if (!verticalMovementOnly) {
            setHeading(LocationUtil.calculateHeadingFrom(cos, sin));
        }

        move.moveStartTime = GameTimeTaskManager.getInstance().getGameTicks();

        this.move = move;

        Movement.register(this);

        if ((ticksToMove * GameTimeTaskManager.MILLIS_IN_TICK) > 3000) {
            WorldPartition.loop().schedule(() -> getAI().notifyActionArrivedRevalidate(), 2000, TimeUnit.MILLISECONDS);
        }
    }

    public boolean moveToNextRoutePoint() {
        final MoveData move = this.move;
        if (move == null) {
            return false;
        }

        if (!isOnGeodataPath(move)) {
            this.move = null;
            return false;
        }

        final double speed = stat.getMoveSpeed();
        if ((speed <= 0) || isMovementDisabled()) {
            this.move = null;
            return false;
        }

        final int curX = getX();
        final int curY = getY();

        final MoveData newMove = new MoveData();

        newMove.onGeodataPathIndex = move.onGeodataPathIndex + 1;
        newMove.geoPath = move.geoPath;
        newMove.geoPathGtx = move.geoPathGtx;
        newMove.geoPathGty = move.geoPathGty;
        newMove.geoPathAccurateTx = move.geoPathAccurateTx;
        newMove.geoPathAccurateTy = move.geoPathAccurateTy;
        if (move.onGeodataPathIndex == (move.geoPath.size() - 2)) {
            newMove.xDestination = move.geoPathAccurateTx;
            newMove.yDestination = move.geoPathAccurateTy;
            newMove.zDestination = move.geoPath.get(newMove.onGeodataPathIndex).getZ();
        } else {
            newMove.xDestination = move.geoPath.get(newMove.onGeodataPathIndex).getX();
            newMove.yDestination = move.geoPath.get(newMove.onGeodataPathIndex).getY();
            newMove.zDestination = move.geoPath.get(newMove.onGeodataPathIndex).getZ();
        }

        final double ndx = newMove.xDestination - curX;
        final double ndy = newMove.yDestination - curY;
        final double distance = Math.sqrt((ndx * ndx) + (ndy * ndy));
        if (distance != 0) {
            setHeading(LocationUtil.calculateHeadingFrom(curX, curY, newMove.xDestination, newMove.yDestination));
        }

        final int ticksToMove = (int) ((GameTimeTaskManager.TICKS_PER_SECOND * distance) / speed);
        newMove.heading = 0;
        newMove.moveStartTime = GameTimeTaskManager.getInstance().getGameTicks();

        this.move = newMove;

        Movement.register(this);

        if ((ticksToMove * GameTimeTaskManager.MILLIS_IN_TICK) > 3000) {
            WorldPartition.loop().schedule(() -> getAI().notifyActionArrivedRevalidate(), 2000, TimeUnit.MILLISECONDS);
        }

        broadcastMoveToLocation(true);
        return true;
    }

    public boolean validateMovementHeading(int heading) {
        final MoveData move = this.move;
        if (move == null) {
            return true;
        }

        boolean result = true;
        if (move.heading != heading) {
            result = (move.heading == 0);
            move.heading = heading;
        }

        return result;
    }

    public boolean isInsideRadius2D(ILocational loc, int radius) {
        return isInsideRadius2D(loc.getX(), loc.getY(), loc.getZ(), radius);
    }

    public boolean isInsideRadius2D(int x, int y, int z, int radius) {
        return calculateDistance2D(x, y, z) < radius;
    }

    public boolean isInsideRadius3D(ILocational loc, int radius) {
        return isInsideRadius3D(loc.getX(), loc.getY(), loc.getZ(), radius);
    }

    public boolean isInsideRadius3D(int x, int y, int z, int radius) {
        return calculateDistance3D(x, y, z) < radius;
    }

    protected boolean checkAndEquipArrows() {
        return true;
    }

    protected boolean checkAndEquipBolts() {
        return true;
    }

    public synchronized void addExpAndSp(double addToExp, double addToSp) {}

    public abstract Item getActiveWeaponInstance();

    public abstract Weapon getActiveWeaponItem();

    public abstract Item getSecondaryWeaponInstance();

    public abstract ItemTemplate getSecondaryWeaponItem();

    public void onHitTimer(
            Creature target,
            int damageValue,
            boolean crit,
            boolean miss,
            byte shld,
            boolean soulshot,
            boolean rechargeShots) {
        if ((target == null) || isAlikeDead()) {
            getAI().notifyActionCancel();
            return;
        }

        if (isFakePlayer() && !FakePlayersConfig.FAKE_PLAYER_AGGRO_FPC && target.isFakePlayer()) {
            return;
        }

        if ((isNpc() && target.isAlikeDead()) || target.isDead() || (!isInSurroundingRegion(target) && !isDoor())) {
            // The target can die before the hit lands; recharge shots for the next attack.
            if (rechargeShots) {
                rechargeShots(true, false);
            }

            getAI().notifyActionCancel();
            sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (miss) {
            if (target.hasAI()) {
                target.getAI().notifyActionEvaded(this);
            }

            notifyAttackAvoid(target, false);
        }

        int damage = damageValue;
        sendDamageMessage(target, damage, false, crit, miss);

        // Check Raidboss attack Creature will be petrified if attacking a raid that's more than 8 levels lower
        if (target.isRaid()
                && target.giveRaidCurse()
                && !NpcConfig.RAID_DISABLE_CURSE
                && (getLevel() > (target.getLevel() + 8))) {
            final Skill skill = CommonSkill.RAID_CURSE2.getSkill();
            if (skill != null) {
                abortAttack();
                abortCast();
                getAI().setIntentionIdle();
                skill.applyEffects(target, this);
            } else {
                log.warn("Skill 4515 at level 1 is missing in DP.");
            }

            damage = 0; // prevents messing up drop calculation
        }

        if (target.isPlayer()) {
            final Player enemy = target.asPlayer();
            enemy.getAI().clientStartAutoAttack();
        }

        if (!miss && (damage > 0)) {
            final Weapon weapon = getActiveWeaponItem();
            final boolean isBow = ((weapon != null)
                    && ((weapon.getItemType() == WeaponType.BOW) || (weapon.getItemType() == WeaponType.CROSSBOW)));
            int reflectedDamage = 0;
            if (!isBow && !target.isInvul()) {
                // quick fix for no drop from raid if boss attack high-level char with damage reflection
                if (!target.isRaid() || (asPlayer() == null) || (asPlayer().getLevel() <= (target.getLevel() + 8))) {
                    final double reflectPercent = target.getStat().calcStat(Stat.REFLECT_DAMAGE_PERCENT, 0, null, null);
                    if (reflectPercent > 0) {
                        reflectedDamage = (int) ((reflectPercent / 100.) * damage);
                        if (reflectedDamage > target.getMaxHp()) {
                            reflectedDamage = target.getMaxHp();
                        }
                    }
                }
            }

            target.reduceCurrentHp(damage, this, null);
            target.notifyDamageReceived(damage, this, null, crit, false);
            if (reflectedDamage > 0) {
                reduceCurrentHp(reflectedDamage, target, true, false, null);
                notifyDamageReceived(reflectedDamage, target, null, crit, false);
            }

            if (!isBow) {
                double absorbPercent = stat.calcStat(Stat.ABSORB_DAMAGE_PERCENT, 0, null, null);
                if (absorbPercent > 0) {
                    final int maxCanAbsorb = (int) (stat.getMaxRecoverableHp() - status.getCurrentHp());
                    int absorbDamage = (int) ((absorbPercent / 100.) * damage);
                    if (absorbDamage > maxCanAbsorb) {
                        absorbDamage = maxCanAbsorb;
                    }

                    if (absorbDamage > 0) {
                        setCurrentHp(status.getCurrentHp() + absorbDamage);
                    }
                }

                absorbPercent = stat.calcStat(Stat.ABSORB_MANA_DAMAGE_PERCENT, 0, null, null);
                if (absorbPercent > 0) {
                    final int maxCanAbsorb = (int) (stat.getMaxRecoverableMp() - status.getCurrentMp());
                    int absorbDamage = (int) ((absorbPercent / 100.) * damage);
                    if (absorbDamage > maxCanAbsorb) {
                        absorbDamage = maxCanAbsorb;
                    }

                    if (absorbDamage > 0) {
                        setCurrentMp(status.getCurrentMp() + absorbDamage);
                    }
                }
            }

            if (target.hasAI()) {
                target.getAI().notifyActionAttacked(this);
            }

            getAI().clientStartAutoAttack();
            if (isSummon()) {
                final Player owner = asSummon().getOwner();
                if (owner != null) {
                    owner.getAI().clientStartAutoAttack();
                }
            }

            if (!target.isRaid() && Formulas.calcAtkBreak(target, damage)) {
                target.breakAttack();
                target.breakCast();
            }

            if (!triggerSkills.isEmpty()) {
                for (OptionSkillHolder holder : triggerSkills.values()) {
                    if (((!crit && (holder.skillType() == OptionSkillType.ATTACK))
                                    || ((holder.skillType() == OptionSkillType.CRITICAL) && crit))
                            && (ClockBridge.random().nextInt(100) < holder.chance())) {
                        makeTriggerCast(holder.skill(), target);
                    }
                }
            }

            if (crit && (weapon != null)) {
                weapon.castOnCriticalSkill(this, target);
            }
        }

        if (rechargeShots) {
            rechargeShots(true, false);
        }
    }

    public void breakAttack() {
        if (isAttackingNow()) {
            abortAttack();
            if (isPlayer()) {
                sendPacket(SystemMessageId.YOUR_ATTACK_HAS_FAILED);
            }
        }
    }

    public void breakCast() {
        // damage can only cancel magical & static skills
        if (castingNow
                && canAbortCast()
                && (lastSkillCast != null)
                && (lastSkillCast.isMagic() || lastSkillCast.isStatic())) {
            abortCast();

            if (isPlayer()) {
                sendPacket(SystemMessageId.YOUR_CASTING_HAS_BEEN_INTERRUPTED);
            }
        }
    }

    protected void reduceArrowCount(boolean bolts) {}

    @Override
    public void onForcedAttack(Player player) {
        if (isInsidePeaceZone(player)) {
            player.sendPacket(SystemMessageId.YOU_MAY_NOT_ATTACK_THIS_TARGET_IN_A_PEACEFUL_ZONE);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isInOlympiadMode()
                && (player.getTarget() != null)
                && player.getTarget().isPlayable()) {
            Player target = null;
            final WorldObject object = player.getTarget();
            if ((object != null) && object.isPlayable()) {
                target = object.asPlayer();
            }

            if ((target == null)
                    || (target.isInOlympiadMode()
                            && (!player.isOlympiadStart()
                                    || (player.getOlympiadGameId() != target.getOlympiadGameId())))) {
                player.sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }
        }

        if ((player.getTarget() != null)
                && !player.getTarget().canBeAttacked()
                && !player.getAccessLevel().allowPeaceAttack()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isConfused()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.getBlockCheckerArena() != -1) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        player.getAI().setIntentionAttack(this);
    }

    public boolean isInsidePeaceZone(Player attacker) {
        return isInsidePeaceZone(attacker, this);
    }

    public boolean isInsidePeaceZone(Player attacker, WorldObject target) {
        return (!attacker.getAccessLevel().allowPeaceAttack() && isInsidePeaceZone((WorldObject) attacker, target));
    }

    public boolean isInsidePeaceZone(WorldObject attacker, WorldObject target) {
        if ((target == null) || !((target.isPlayable() || target.isFakePlayer()) && attacker.isPlayable())) {
            return false;
        }

        if (InstanceManager.getInstance().getInstance(getInstanceId()).isPvP()) {
            return false;
        }

        if (TerritoryWarManager.PLAYER_WITH_WARD_CAN_BE_KILLED_IN_PEACEZONE
                && TerritoryWarManager.getInstance().isTWInProgress()
                && target.isPlayer()
                && target.asPlayer().isCombatFlagEquipped()) {
            return false;
        }

        if (PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_BE_KILLED_IN_PEACEZONE) {
            // Allows red to be attacked and red to attack flagged players.
            final Player player = asPlayer();
            if ((player != null) && (player.getKarma() > 0)) {
                return false;
            }

            final Player attackerPlayer = attacker.asPlayer();
            if ((attackerPlayer != null)
                    && (attackerPlayer.getKarma() > 0)
                    && (player != null)
                    && (player.getPvpFlag() > 0)) {
                return false;
            }
        }

        return (target.isInsideZone(ZoneId.PEACE)
                || attacker.isInsideZone(ZoneId.PEACE)
                || target.isInsideZone(ZoneId.NO_PVP)
                || attacker.isInsideZone(ZoneId.NO_PVP));
    }

    public boolean isInActiveRegion() {
        final WorldRegion region = getWorldRegion();
        return ((region != null) && (region.isActive()));
    }

    public boolean isInParty() {
        return false;
    }

    public Party getParty() {
        return null;
    }

    /** Delay in milliseconds before the next attack. */
    public int calculateTimeBetweenAttacks() {
        return (int) (500000 / stat.getPAtkSpd());
    }

    public int calculateReuseTime(Weapon weapon) {
        if (isTransformed()) {
            switch (getAttackType()) {
                case BOW: {
                    return (int) ((1500 * 333 * stat.getWeaponReuseModifier(null)) / stat.getPAtkSpd());
                }
                case CROSSBOW: {
                    return (int) ((1200 * 333 * stat.getWeaponReuseModifier(null)) / stat.getPAtkSpd());
                }
            }
        }

        if ((weapon == null) || (weapon.getReuseDelay() == 0)) {
            return 0;
        }

        return (int) ((weapon.getReuseDelay() * 333) / stat.getPAtkSpd());
    }

    /** @return the replaced skill, or null if the skill is new. */
    public Skill addSkill(Skill newSkill) {
        Skill oldSkill = null;
        if (newSkill != null) {
            oldSkill = skills.put(newSkill.getId(), newSkill);

            if (oldSkill != null) {
                removeStatsOwner(oldSkill);

                if (oldSkill.isPassive()) {
                    stopSkillEffects(SkillFinishType.NORMAL, oldSkill.getId());
                }
            }

            addStatFuncs(newSkill.getStatFuncs(null, this));
            if (newSkill.isPassive()) {
                newSkill.applyEffects(this, this, false, true, false, 0);
            }
        }

        return oldSkill;
    }

    public Skill removeSkill(Skill skill, boolean cancelEffect) {
        return (skill != null) ? removeSkill(skill.getId(), cancelEffect) : null;
    }

    public Skill removeSkill(int skillId) {
        return removeSkill(skillId, true);
    }

    public Skill removeSkill(int skillId, boolean cancelEffect) {
        final Skill oldSkill = skills.remove(skillId);

        if (oldSkill != null) {
            if ((lastSkillCast != null) && castingNow && (oldSkill.getId() == lastSkillCast.getId())) {
                abortCast();
            }

            if ((lastSimultaneousSkillCast != null)
                    && castingSimultaneouslyNow
                    && (oldSkill.getId() == lastSimultaneousSkillCast.getId())) {
                abortCast();
            }

            if (cancelEffect || oldSkill.isToggle() || oldSkill.isPassive()) {
                removeStatsOwner(oldSkill);
                stopSkillEffects(SkillFinishType.REMOVED, oldSkill.getId());
            }
        }

        return oldSkill;
    }

    public Collection<Skill> getAllSkills() {
        return skills.values();
    }

    public int getSkillLevel(int skillId) {
        final Skill skill = getKnownSkill(skillId);
        return (skill == null) ? 0 : skill.getLevel();
    }

    public Skill getKnownSkill(int skillId) {
        return skills.get(skillId);
    }

    public int getBuffCount() {
        return effectList.getBuffCount();
    }

    public int getDanceCount() {
        return effectList.getDanceCount();
    }

    /** Requires a cast in progress. */
    public void onMagicLaunchedTimer(MagicUseTask mut) {
        final Skill skill = mut.getSkill();
        final List<WorldObject> targets = mut.getTargets();
        if ((skill == null) || (targets == null)) {
            abortCast();
            return;
        }

        if (targets.isEmpty()) {
            switch (skill.getTargetType()) {
                // only AURA-type skills can be cast without target
                case AURA:
                case FRONT_AURA:
                case BEHIND_AURA:
                case AURA_CORPSE_MOB:
                case AURA_FRIENDLY: {
                    break;
                }
                default: {
                    abortCast();
                    return;
                }
            }
        }

        // Escape-from-radius and peace-zone check; imprecise for AoE skills.
        int escapeRange = 0;
        if (skill.getEffectRange() > escapeRange) {
            escapeRange = skill.getEffectRange();
        } else if ((skill.getCastRange() < 0) && (skill.getAffectRange() > 80)) {
            escapeRange = skill.getAffectRange();
        }

        if (!targets.isEmpty() && (escapeRange > 0)) {
            int skipRange = 0;
            int skipLOS = 0;
            int skipPeaceZone = 0;
            final List<WorldObject> targetList = new ArrayList<>();
            for (WorldObject target : targets) {
                if (target.isCreature()) {
                    if (!isInsideRadius3D(
                            target.getX(), target.getY(), target.getZ(), escapeRange + template.getCollisionRadius())) {
                        skipRange++;
                        continue;
                    }

                    // Healing party members should ignore LOS.
                    if (((skill.getTargetType() != TargetType.PARTY) || !skill.hasEffectType(EffectType.HEAL)) //
                            && (mut.getSkillTime() > 550)
                            && !GeoEngine.getInstance().canSeeTarget(this, target)) {
                        skipLOS++;
                        continue;
                    }

                    if (skill.hasNegativeEffect()) {
                        if (isPlayer()) {
                            if (target.asCreature().isInsidePeaceZone(asPlayer())) {
                                skipPeaceZone++;
                                continue;
                            }
                        } else {
                            if (target.asCreature().isInsidePeaceZone(this, target)) {
                                skipPeaceZone++;
                                continue;
                            }
                        }
                    }

                    targetList.add(target);
                }
            }

            if (targetList.isEmpty()) {
                if (isPlayer()) {
                    if (skipRange > 0) {
                        sendPacket(SystemMessageId.THE_DISTANCE_IS_TOO_FAR_AND_SO_THE_CASTING_HAS_BEEN_STOPPED);
                    } else if (skipLOS > 0) {
                        sendPacket(SystemMessageId.CANNOT_SEE_TARGET);
                    } else if (skipPeaceZone > 0) {
                        sendPacket(SystemMessageId.A_MALICIOUS_SKILL_CANNOT_BE_USED_IN_A_PEACE_ZONE);
                    }
                }

                abortCast();
                return;
            }

            mut.setTargets(targetList);
        }

        // Static skills can be used while faking death.
        if ((mut.isSimultaneous() && !castingSimultaneouslyNow)
                || (!mut.isSimultaneous() && !castingNow)
                || (isAlikeDead() && !skill.isStatic())) {
            getAI().notifyActionCancel();
            return;
        }

        mut.setPhase(2);
        if (mut.getSkillTime() == 0) {
            onMagicHitTimer(mut);
        } else {
            skillCast = WorldPartition.loop().schedule(mut, 400, TimeUnit.MILLISECONDS);
        }
    }

    public void onMagicHitTimer(MagicUseTask mut) {
        final Skill skill = mut.getSkill();
        final List<WorldObject> targets = mut.getTargets();
        if ((skill == null) || (targets == null)) {
            abortCast();
            return;
        }

        try {
            for (WorldObject tgt : targets) {
                if (tgt.isPlayable()) {
                    if (isPlayer() && tgt.isSummon()) {
                        tgt.asSummon().updateAndBroadcastStatus(1);
                    }
                } else if (isPlayable() && tgt.isAttackable()) {
                    final Creature target = tgt.asCreature();
                    if (skill.getEffectPoint() > 0) {
                        target.asAttackable().reduceHate(this, skill.getEffectPoint());
                    } else if (skill.getEffectPoint() < 0) {
                        target.asAttackable().addDamageHate(this, 0, -skill.getEffectPoint());
                    }
                }
            }

            rechargeShots(skill.useSoulShot(), skill.useSpiritShot());

            final StatusUpdate su = new StatusUpdate(this);
            boolean isSendStatus = false;

            final double mpConsume = stat.getMpConsume(skill);
            if (mpConsume > 0) {
                if (mpConsume > status.getCurrentMp()) {
                    sendPacket(SystemMessageId.NOT_ENOUGH_MP);
                    abortCast();
                    return;
                }

                status.reduceMp(mpConsume);
                su.addAttribute(StatusUpdate.CUR_MP, (int) status.getCurrentMp());
                isSendStatus = true;
            }

            if (skill.getHpConsume() > 0) {
                final double consumeHp = skill.getHpConsume();
                if (consumeHp >= status.getCurrentHp()) {
                    sendPacket(SystemMessageId.NOT_ENOUGH_HP);
                    abortCast();
                    return;
                }

                status.reduceHp(consumeHp, this, true);
                su.addAttribute(StatusUpdate.CUR_HP, (int) status.getCurrentHp());
                isSendStatus = true;
            }

            if (isSendStatus) {
                sendPacket(su);
            }

            if (isPlayer()) {
                final Player player = asPlayer();
                if (skill.getChargeConsumeCount() > 0) {
                    player.decreaseCharges(skill.getChargeConsumeCount());
                }

                if ((skill.getMaxSoulConsumeCount() > 0) && !player.decreaseSouls(skill.getMaxSoulConsumeCount())) {
                    abortCast();
                    return;
                }
            }

            callSkill(mut.getSkill(), mut.getTargets());
        } catch (NullPointerException e) {
            log.warn("onMagicHitTimer: ", e);
        }

        if (mut.getSkillTime() > 0) {
            mut.setCount(mut.getCount() + 1);
        }

        mut.setPhase(3);
        if (mut.getSkillTime() == 0) {
            onMagicFinalizer(mut);
        } else {
            if (mut.isSimultaneous()) {
                skillCast2 = WorldPartition.loop().schedule(mut, 0, TimeUnit.MILLISECONDS);
            } else {
                skillCast = WorldPartition.loop().schedule(mut, 0, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void onMagicFinalizer(MagicUseTask mut) {
        if (mut.isSimultaneous()) {
            skillCast2 = null;
            setCastingSimultaneouslyNow(false);
            return;
        }

        skillCast = null;
        castInterruptTime = 0;

        if (mut.getCount() > 0) {
            rechargeShots(mut.getSkill().useSoulShot(), mut.getSkill().useSpiritShot());
        }

        setCastingNow(false);
        setCastingSimultaneouslyNow(false);

        final Skill skill = mut.getSkill();
        final WorldObject target =
                !mut.getTargets().isEmpty() ? mut.getTargets().get(0) : null;

        if (skill.nextActionIsAttack()
                && (this.target != this)
                && (target != null)
                && (this.target == target)
                && this.target.isCreature()
                && target.canBeAttacked()
                && (!isPlayer() || !asPlayer().isAutoPlaying())) {
            final Intention nextIntention = getAI().getNextIntention();
            if (nextIntention != Intention.MOVE_TO) {
                if (isPlayer()) {
                    final SkillUseHolder currSkill = asPlayer().getCurrentSkill();
                    if ((currSkill == null) || !currSkill.isShiftPressed()) {
                        WorldPartition.loop()
                                .schedule(
                                        () -> {
                                            if (!isDisabled() && !isAttackingOrCastingNow()) {
                                                getAI().setIntentionAttack(target);
                                            }
                                        },
                                        333,
                                        TimeUnit.MILLISECONDS); // Wait for skill land animation.
                    }
                } else {
                    getAI().setIntentionAttack(target);
                }
            } else if (isPlayer()) {
                WorldPartition.loop().schedule(() -> completeMagicFinalizer(skill, target), 333, TimeUnit.MILLISECONDS);
                return;
            }
        }

        completeMagicFinalizer(skill, target);
    }

    private void completeMagicFinalizer(Skill skill, WorldObject target) {
        if (skill.hasNegativeEffect() && (skill.getTargetType() != TargetType.UNLOCKABLE)) {
            getAI().clientStartAutoAttack();
        }

        getAI().notifyActionFinishCasting();

        notifyQuestEventSkillFinished(skill, target);

        if (isPlayer()) {
            final Player player = asPlayer();
            final SkillUseHolder queuedSkill = player.getQueuedSkill();
            player.setCurrentSkill(null, false, false);
            if (queuedSkill != null) {
                player.setQueuedSkill(null, false, false);

                WorldPartition.loop()
                        .execute(new QueuedMagicUseTask(
                                player,
                                queuedSkill.getSkill(),
                                queuedSkill.isCtrlPressed(),
                                queuedSkill.isShiftPressed()));
            }
        }

        if (isChanneling()) {
            getSkillChannelizer().stopChanneling();
        }
    }

    protected void notifyQuestEventSkillFinished(Skill skill, WorldObject target) {}

    public void callSkill(Skill skill, List<WorldObject> targets) {
        try {
            if (skill.isToggle() && isAffectedBySkill(skill.getId())) {
                return;
            }

            if ((targets == null) || targets.isEmpty()) {
                return;
            }

            // Block RaidBoss from receiving heals/buffs from players (and their pet/summon).
            if (GrandBossConfig.DISABLE_RAIDBOSS_HEAL_FROM_PLAYERS
                    || GrandBossConfig.DISABLE_RAIDBOSS_BUFF_FROM_PLAYERS) {
                final Player actingPlayer = isPlayer()
                        ? asPlayer()
                        : (isSummon() ? asSummon().getOwner() : (isPet() ? asPet().getOwner() : null));
                if (actingPlayer != null) {
                    final boolean isHealSkill = skill.hasEffectType(
                            EffectType.HEAL,
                            EffectType.CPHEAL,
                            EffectType.MANAHEAL_BY_LEVEL,
                            EffectType.MANAHEAL_PERCENT,
                            EffectType.REBALANCE_HP);
                    final boolean isBuffSkill = !isHealSkill
                            && (skill.hasEffectType(
                                            EffectType.BUFF,
                                            EffectType.NOBLESSE_BLESSING,
                                            EffectType.CHARM_OF_LUCK,
                                            EffectType.RELAXING)
                                    || (skill.isContinuous() && !skill.isDebuff()));
                    final boolean blockRaidBoss = (isHealSkill && GrandBossConfig.DISABLE_RAIDBOSS_HEAL_FROM_PLAYERS)
                            || (isBuffSkill && GrandBossConfig.DISABLE_RAIDBOSS_BUFF_FROM_PLAYERS);

                    if (blockRaidBoss) {
                        boolean hasRaidBossTarget = false;
                        for (WorldObject obj : targets) {
                            if (obj instanceof RaidBoss) {
                                hasRaidBossTarget = true;
                                break;
                            }
                        }

                        if (hasRaidBossTarget) {
                            if (targets.size() == 1) {
                                return;
                            }

                            targets.removeIf(obj -> obj instanceof RaidBoss);

                            if (targets.isEmpty()) {
                                return;
                            }
                        }
                    }
                }
            }

            for (WorldObject obj : targets) {
                if ((obj == null) || !obj.isCreature()) {
                    continue;
                }

                final Creature target = obj.asCreature();

                Creature targetsAttackTarget = null;
                Creature targetsCastTarget = null;
                if (target.hasAI()) {
                    targetsAttackTarget = target.getAI().getAttackTarget();
                    targetsCastTarget = target.getAI().getCastTarget();
                }

                if (!NpcConfig.RAID_DISABLE_CURSE
                        && ((target.isRaid() && target.giveRaidCurse() && (getLevel() > (target.getLevel() + 8)))
                                || (!skill.hasNegativeEffect()
                                        && (targetsAttackTarget != null)
                                        && targetsAttackTarget.isRaid()
                                        && targetsAttackTarget.giveRaidCurse()
                                        && targetsAttackTarget.getAttackByList().contains(target)
                                        && (getLevel() > (targetsAttackTarget.getLevel() + 8)))
                                || (!skill.hasNegativeEffect()
                                        && (targetsCastTarget != null)
                                        && targetsCastTarget.isRaid()
                                        && targetsCastTarget.giveRaidCurse()
                                        && targetsCastTarget.getAttackByList().contains(target)
                                        && (getLevel() > (targetsCastTarget.getLevel() + 8))))) {
                    final CommonSkill curse = skill.isMagic() ? CommonSkill.RAID_CURSE : CommonSkill.RAID_CURSE2;
                    final Skill curseSkill = curse.getSkill();
                    if (curseSkill != null) {
                        abortAttack();
                        abortCast();
                        getAI().setIntentionIdle();
                        curseSkill.applyEffects(target, this);
                    } else {
                        log.warn("Skill ID {} level {} is missing in DP!", curse.getId(), curse.getLevel());
                    }
                    return;
                }

                if (skill.isOverhit() && target.isAttackable()) {
                    target.asAttackable().overhitEnabled(true);
                }

                if (!skill.isStatic()) {
                    final Weapon activeWeapon = getActiveWeaponItem();
                    if ((activeWeapon != null) && !target.isDead()) {
                        activeWeapon.castOnMagicSkill(this, target, skill);
                    }

                    if (!triggerSkills.isEmpty()) {
                        for (OptionSkillHolder holder : triggerSkills.values()) {
                            if (((skill.isMagic() && (holder.skillType() == OptionSkillType.MAGIC))
                                            || (skill.isPhysical() && (holder.skillType() == OptionSkillType.ATTACK)))
                                    && (ClockBridge.random().nextInt(100) < holder.chance())) {
                                makeTriggerCast(holder.skill(), target);
                            }
                        }
                    }
                }
            }

            skill.activateSkill(this, targets);

            if ((skill.getEffectPoint() <= 0)
                    && skill.hasNegativeEffect()
                    && (PvpConfig.FLAG_PLAYER_ON_RAID_ATTACK || PvpConfig.FLAG_PLAYER_ON_CHAMPION_ATTACK)) {
                final Player actingPlayer;
                if (isPlayer()) {
                    actingPlayer = asPlayer();
                } else if (PvpConfig.FLAG_PLAYER_ON_SUMMON_PET_RAID_CHAMPION_ATTACK && (isSummon() || isPet())) {
                    actingPlayer = isSummon() ? asSummon().getOwner() : asPet().getOwner();
                } else {
                    actingPlayer = null;
                }

                if (actingPlayer != null) {
                    for (WorldObject target : targets) {
                        if (target.isCreature()) {
                            final Creature targetCreature = target.asCreature();
                            if (((targetCreature.isRaid() && PvpConfig.FLAG_PLAYER_ON_RAID_ATTACK)
                                    || (targetCreature.isChampion() && PvpConfig.FLAG_PLAYER_ON_CHAMPION_ATTACK))) {
                                actingPlayer.updatePvPStatus();
                                break;
                            }
                        }
                    }
                }
            }

            final Player player = asPlayer();
            if (player != null) {
                for (WorldObject target : targets) {
                    if (target.isCreature()) {
                        if (skill.getEffectPoint() <= 0) {
                            if ((target.isPlayable() || target.isTrap()) && skill.hasNegativeEffect()) {
                                if (!target.equals(this)) {
                                    if (target.isPlayer()) {
                                        target.asPlayer().getAI().clientStartAutoAttack();
                                    } else if (target.isSummon()
                                            && target.asCreature().hasAI()) {
                                        final Player owner = target.asSummon().getOwner();
                                        if (owner != null) {
                                            owner.getAI().clientStartAutoAttack();
                                        }
                                    }

                                    // attack of the own pet does not flag player
                                    // triggering trap not flag trap owner
                                    if ((player.getSummon() != target)
                                            && !isTrap()
                                            && !((skill.getEffectPoint() == 0) && (skill.getAffectRange() > 0))) {
                                        player.updatePvPStatus(target.asCreature());
                                    }
                                }
                            } else if (target.isAttackable()) {
                                switch (skill.getId()) {
                                    case 51: // Lure
                                    case 511: // Temptation
                                    {
                                        break;
                                    }
                                    default: {
                                        target.asCreature().addAttackerToAttackByList(this);
                                    }
                                }
                            }

                            if (target.asCreature().hasAI()
                                    && skill.hasNegativeEffect()
                                    && !skill.hasEffectType(EffectType.HATE)
                                    && (skill.getAbnormalType() != AbnormalType.TURN_PASSIVE)) {
                                target.asCreature().getAI().notifyActionAttacked(this);
                            }
                        } else {
                            if (target.isPlayer()) {
                                if (!(target.equals(this) || target.equals(player))
                                        && ((target.asPlayer().getPvpFlag() > 0)
                                                || (target.asPlayer().getKarma() > 0))) {
                                    player.updatePvPStatus();
                                }
                            } else if (target.isAttackable()) {
                                player.updatePvPStatus();
                            }
                        }
                    }

                    if (target.isFakePlayer() && !FakePlayersConfig.FAKE_PLAYER_AUTO_ATTACKABLE) {
                        player.updatePvPStatus();
                    }
                }

                World.forEachVisibleObjectInRange(player, Npc.class, 1000, npc -> {
                    if (EventDispatcher.getInstance().hasListener(EventType.ON_NPC_SKILL_SEE, npc)) {
                        EventDispatcher.getInstance()
                                .notifyEventAsync(new OnNpcSkillSee(npc, player, skill, targets, isSummon()), npc);
                    }

                    if (npc.isAttackable()) {
                        final Attackable attackable = npc.asAttackable();
                        int skillEffectPoint = skill.getEffectPoint();
                        if (player.hasSummon() && (targets.size() == 1) && targets.contains(player.getSummon())) {
                            skillEffectPoint = 0;
                        }

                        if ((skillEffectPoint > 0)
                                && attackable.hasAI()
                                && (attackable.getAI().getIntention() == Intention.ATTACK)) {
                            final WorldObject npcTarget = attackable.getTarget();
                            for (WorldObject skillTarget : targets) {
                                if ((npcTarget == skillTarget) || (npc == skillTarget)) {
                                    final Creature originalCaster = isSummon() ? this : player;
                                    attackable.addDamageHate(originalCaster, 0, (long)
                                            (((skillEffectPoint * 150) / (attackable.getLevel() + 7))
                                                    * attackable.getHateRatio(originalCaster)));
                                }
                            }
                        }

                        // Players 9+ levels above a Raid Boss casting nearby are silenced with the Raid Curse skill;
                        // static skills (recipe books, potions, item skills) are exempt.
                        if (!NpcConfig.RAID_DISABLE_CURSE
                                && !skill.isStatic()
                                && attackable.isRaid()
                                && attackable.giveRaidCurse()
                                && attackable.isInCombat()
                                && ((player.getLevel() - attackable.getLevel()) > 8)) {
                            player.abortAttack();
                            player.abortCast();
                            player.getAI().setIntentionIdle();

                            final CommonSkill curse =
                                    skill.hasNegativeEffect() ? CommonSkill.RAID_CURSE2 : CommonSkill.RAID_CURSE;
                            curse.getSkill().applyEffects(attackable, player);
                        }
                    }
                });
            }

            if (skill.hasNegativeEffect() && !skill.hasEffectType(EffectType.HATE)) {
                for (WorldObject target : targets) {
                    if (target.isCreature()) {
                        final Creature creature = target.asCreature();
                        if (creature.hasAI()) {
                            creature.getAI().notifyActionAttacked(this);
                        }
                    }

                    if (isFakePlayer()) {
                        if (target.isPlayable() || target.isFakePlayer()) {
                            final Npc npc = asNpc();
                            if (!npc.isScriptValue(1)) {
                                npc.setScriptValue(1);
                                npc.broadcastInfo();
                                ScriptManager.getInstance()
                                        .getScript("PvpFlaggingStopTask")
                                        .notifyEvent("FLAG_CHECK", npc, null);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn("CallSkill() failed.", e);
        }
    }

    /** (level + 89) / 100. */
    public double getLevelMod() {
        return ((getLevel() + 89) / 100d);
    }

    public void forceIsCasting(int newSkillCastEndTick) {
        setCastingNow(true);

        // for interrupt -400 ms
        castInterruptTime = newSkillCastEndTick - 4;
    }

    @Getter
    private boolean coreAIDisabled = false;

    public void updatePvPFlag(int value) {}

    public double getRandomDamageMultiplier() {
        int random;
        if (getActiveWeaponItem() != null) {
            random = (int) stat.calcStat(Stat.RANDOM_DAMAGE, 0);
        } else {
            random = 5 + (int) Math.sqrt(getLevel());
        }

        return (1 + ((double) (random > 0 ? ClockBridge.random().nextInt(-random, random + 1) : -random) / 100));
    }

    public abstract int getLevel();

    public double calcStat(Stat stat, double init) {
        return this.stat.calcStat(stat, init, null, null);
    }

    // TODO: remove once CreatureStatus is complete
    public double calcStat(Stat stat, double init, Creature target, Skill skill) {
        return this.stat.calcStat(stat, init, target, skill);
    }

    public int getAccuracy() {
        return stat.getAccuracy();
    }

    public float getAttackSpeedMultiplier() {
        return stat.getAttackSpeedMultiplier();
    }

    public double getCriticalDmg(Creature target, double init) {
        return stat.getCriticalDmg(target, init);
    }

    public int getCriticalHit(Creature target, Skill skill) {
        return stat.getCriticalHit(target, skill);
    }

    public int getEvasionRate(Creature target) {
        return stat.getEvasionRate(target);
    }

    public int getMagicalAttackRange(Skill skill) {
        return stat.getMagicalAttackRange(skill);
    }

    public int getMaxCp() {
        return stat.getMaxCp();
    }

    public int getMaxRecoverableCp() {
        return stat.getMaxRecoverableCp();
    }

    public double getMAtk(Creature target, Skill skill) {
        return stat.getMAtk(target, skill);
    }

    public int getMAtkSpd() {
        return stat.getMAtkSpd();
    }

    public int getMaxMp() {
        return stat.getMaxMp();
    }

    public int getMaxRecoverableMp() {
        return stat.getMaxRecoverableMp();
    }

    public int getMaxHp() {
        return stat.getMaxHp();
    }

    public int getMaxRecoverableHp() {
        return stat.getMaxRecoverableHp();
    }

    public int getMCriticalHit(Creature target, Skill skill) {
        return stat.getMCriticalHit(target, skill);
    }

    public double getMDef(Creature target, Skill skill) {
        return stat.getMDef(target, skill);
    }

    public double getMReuseRate(Skill skill) {
        return stat.getMReuseRate(skill);
    }

    public double getPAtk(Creature target) {
        return stat.getPAtk(target);
    }

    public double getPAtkSpd() {
        return stat.getPAtkSpd();
    }

    public double getPDef(Creature target) {
        return stat.getPDef(target);
    }

    public int getPhysicalAttackRange() {
        return stat.getPhysicalAttackRange();
    }

    public double getMovementSpeedMultiplier() {
        return stat.getMovementSpeedMultiplier();
    }

    public double getRunSpeed() {
        return stat.getRunSpeed();
    }

    public double getWalkSpeed() {
        return stat.getWalkSpeed();
    }

    public double getSwimRunSpeed() {
        return stat.getSwimRunSpeed();
    }

    public double getSwimWalkSpeed() {
        return stat.getSwimWalkSpeed();
    }

    public double getMoveSpeed() {
        return stat.getMoveSpeed();
    }

    public int getShldDef() {
        return stat.getShldDef();
    }

    public int getSTR() {
        return stat.getSTR();
    }

    public int getDEX() {
        return stat.getDEX();
    }

    public int getCON() {
        return stat.getCON();
    }

    public int getINT() {
        return stat.getINT();
    }

    public int getWIT() {
        return stat.getWIT();
    }

    public int getMEN() {
        return stat.getMEN();
    }

    // TODO: remove once CreatureStatus is complete
    public void addStatusListener(Creature object) {
        status.addStatusListener(object);
    }

    public void reduceCurrentHp(double amount, Creature attacker, Skill skill) {
        reduceCurrentHp(amount, attacker, true, false, skill);
    }

    public void reduceCurrentHpByDOT(double amount, Creature attacker, Skill skill) {
        reduceCurrentHp(amount, attacker, !skill.isToggle(), true, skill);
    }

    public void reduceCurrentHp(double amount, Creature attacker, boolean awake, boolean isDOT, Skill skill) {
        if (ChampionMonstersConfig.CHAMPION_ENABLE && isChampion() && (ChampionMonstersConfig.CHAMPION_HP != 0)) {
            status.reduceHp(amount / ChampionMonstersConfig.CHAMPION_HP, attacker, awake, isDOT, false);
        } else {
            if (isPlayer()
                    && !isDOT
                    && (skill != null)
                    && (skill.getCastRange() > 0)
                    && (attacker != null)
                    && !GeoEngine.getInstance().canSeeTarget(attacker, this)) {
                amount = 0;
            }

            status.reduceHp(amount, attacker, awake, isDOT, false);
        }
    }

    public void reduceCurrentMp(double amount) {
        status.reduceMp(amount);
    }

    @Override
    public void removeStatusListener(Creature object) {
        status.removeStatusListener(object);
    }

    protected void stopHpMpRegeneration() {
        status.stopHpMpRegeneration();
    }

    public double getCurrentCp() {
        return status.getCurrentCp();
    }

    public int getCurrentCpPercent() {
        return (int) ((status.getCurrentCp() * 100) / stat.getMaxCp());
    }

    public void setCurrentCp(double newCp) {
        status.setCurrentCp(newCp);
    }

    public double getCurrentHp() {
        return status.getCurrentHp();
    }

    public int getCurrentHpPercent() {
        return (int) ((status.getCurrentHp() * 100) / stat.getMaxHp());
    }

    public void setCurrentHp(double newHp) {
        status.setCurrentHp(newHp);
    }

    public void setCurrentHpMp(double newHp, double newMp) {
        status.setCurrentHpMp(newHp, newMp);
    }

    public double getCurrentMp() {
        return status.getCurrentMp();
    }

    public int getCurrentMpPercent() {
        return (int) ((status.getCurrentMp() * 100) / stat.getMaxMp());
    }

    public void setCurrentMp(double newMp) {
        status.setCurrentMp(newMp);
    }

    public void fullRestore() {
        status.setCurrentHp(getMaxHp());
        status.setCurrentMp(getMaxMp(), isPlayable());
    }

    public int getMaxLoad() {
        if (isPlayer() || isPet()) {
            // Weight limit = (CON modifier * 69000) * skills.
            final double baseLoad = Math.floor(BaseStat.CON.calcBonus(this) * 69000 * PlayerConfig.ALT_WEIGHT_LIMIT);
            return (int) calcStat(Stat.WEIGHT_LIMIT, baseLoad, this, null);
        }

        return 0;
    }

    public int getBonusWeightPenalty() {
        if (isPlayer() || isPet()) {
            return (int) calcStat(Stat.WEIGHT_PENALTY, 1, this, null);
        }

        return 0;
    }

    public int getCurrentLoad() {
        if (isPlayer() || isPet()) {
            final Inventory inventory = getInventory();
            return (inventory != null) ? inventory.getTotalWeight() : 0;
        }

        return 0;
    }

    public boolean isChampion() {
        return false;
    }

    public void sendDamageMessage(Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss) {
        if (miss && target.isPlayer()) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_EVADED_C2_S_ATTACK);
            sm.addPcName(target.asPlayer());
            sm.addString(getName());
            target.sendPacket(sm);
        }
    }

    public byte getAttackElement() {
        return stat.getAttackElement();
    }

    public int getAttackElementValue(byte attackAttribute) {
        return stat.getAttackElementValue(attackAttribute);
    }

    public int getDefenseElementValue(byte defenseAttribute) {
        return stat.getDefenseElementValue(defenseAttribute);
    }

    public void disableCoreAI(boolean value) {
        coreAIDisabled = value;
    }

    public boolean giveRaidCurse() {
        return true;
    }

    public boolean isAffected(EffectFlag flag) {
        return effectList.isAffected(flag);
    }

    public boolean isAffectedByAbnormalType(AbnormalType type) {
        return effectList.isAffectedByAbnormalType(type);
    }

    public void addTriggerSkill(OptionSkillHolder holder) {
        getTriggerSkills().put(holder.skill().getId(), holder);
    }

    public void removeTriggerSkill(OptionSkillHolder holder) {
        getTriggerSkills().remove(holder.skill().getId());
    }

    public void makeTriggerCast(Skill skill, Creature target, boolean ignoreTargetType) {
        try {
            if (skill == null) {
                return;
            }

            if (skill.checkCondition(this, target, false)) {
                if (isSkillDisabled(skill)) {
                    return;
                }

                if (skill.getReuseDelay() > 0) {
                    disableSkill(skill, skill.getReuseDelay());
                }

                final List<WorldObject> targets = !ignoreTargetType
                        ? skill.getTargetList(this, false, target)
                        : Collections.singletonList(target);
                if (targets.isEmpty()) {
                    return;
                }

                Creature skillTarget = target;
                for (WorldObject obj : targets) {
                    if ((obj != null) && obj.isCreature()) {
                        skillTarget = obj.asCreature();
                        break;
                    }
                }

                if (PlayerConfig.ALT_VALIDATE_TRIGGER_SKILLS
                        && isPlayable()
                        && (skillTarget != null)
                        && skillTarget.isPlayable()) {
                    final Player player = asPlayer();
                    if (!player.checkPvpSkill(skillTarget, skill)) {
                        return;
                    }
                }

                broadcastSkillPacket(
                        new MagicSkillUse(this, skillTarget, skill.getDisplayId(), skill.getLevel(), 0, 0),
                        skillTarget);
                broadcastSkillPacket(
                        new MagicSkillLaunched(this, skill.getDisplayId(), skill.getLevel(), targets), targets);

                skill.activateSkill(this, targets);
            }
        } catch (Exception e) {
            log.warn("makeTriggerCast: ", e);
        }
    }

    public void makeTriggerCast(Skill skill, Creature target) {
        makeTriggerCast(skill, target, false);
    }

    public boolean canRevive() {
        return true;
    }

    public void setCanRevive(boolean value) {}

    public boolean isSweepActive() {
        return false;
    }

    public int getClanId() {
        return 0;
    }

    public Clan getClan() {
        return null;
    }

    public boolean isAcademyMember() {
        return false;
    }

    public int getPledgeType() {
        return 0;
    }

    public int getAllyId() {
        return 0;
    }

    public void notifyDamageReceived(
            double damage, Creature attacker, Skill skill, boolean critical, boolean damageOverTime) {
        if (isPlayer() && asPlayer().isFakeDeath() && PlayerConfig.FAKE_DEATH_DAMAGE_STAND && (damage > 0)) {
            stopFakeDeath(true);
        }

        if ((attacker != null)
                && EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_DAMAGE_DEALT, attacker)) {
            EventDispatcher.getInstance()
                    .notifyEvent(
                            OnCreatureDamageDealt.builder()
                                    .attacker(attacker)
                                    .target(this)
                                    .damage(damage)
                                    .skill(skill)
                                    .critical(critical)
                                    .damageOverTime(damageOverTime)
                                    .build(),
                            attacker);
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_DAMAGE_RECEIVED, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnCreatureDamageReceived.builder()
                                    .attacker(attacker)
                                    .target(this)
                                    .damage(damage)
                                    .skill(skill)
                                    .critical(critical)
                                    .damageOverTime(damageOverTime)
                                    .build(),
                            this);
        }
    }

    public void notifyAttackAvoid(Creature target, boolean isDot) {
        if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_ATTACK_AVOID, target)) {
            EventDispatcher.getInstance()
                    .notifyEvent(
                            OnCreatureAttackAvoid.builder()
                                    .attacker(this)
                                    .target(target)
                                    .damageOverTime(isDot)
                                    .build(),
                            target);
        }
    }

    public WeaponType getAttackType() {
        if (isTransformed()) {
            final TransformTemplate template = getTransformation().getTemplate(asPlayer());
            if (template != null) {
                return template.getBaseAttackType();
            }
        }

        final Weapon weapon = getActiveWeaponItem();
        if (weapon != null) {
            return weapon.getItemType();
        }

        return this.template.getBaseAttackType();
    }

    public boolean isInCategory(CategoryType type) {
        return CategoryData.getInstance().isInCategory(type, getId());
    }

    @Override
    public boolean isCreature() {
        return true;
    }

    @Override
    public Creature asCreature() {
        return this;
    }

    public boolean isChanneling() {
        return (channelizer != null) && channelizer.isChanneling();
    }

    public SkillChannelizer getSkillChannelizer() {
        if (channelizer == null) {
            channelizer = new SkillChannelizer(this);
        }

        return channelizer;
    }

    public boolean isChannelized() {
        return (channelized != null) && !channelized.isChannelized();
    }

    public SkillChannelized getSkillChannelized() {
        if (channelized == null) {
            channelized = new SkillChannelized();
        }

        return channelized;
    }

    public void addInvulAgainst(SkillHolder holder) {
        final InvulSkillHolder invulHolder = getInvulAgainstSkills().get(holder.getSkillId());
        if (invulHolder != null) {
            invulHolder.increaseInstances();
            return;
        }

        getInvulAgainstSkills().put(holder.getSkillId(), new InvulSkillHolder(holder));
    }

    public void removeInvulAgainst(SkillHolder holder) {
        final InvulSkillHolder invulHolder = getInvulAgainstSkills().get(holder.getSkillId());
        if ((invulHolder != null) && (invulHolder.decreaseInstances() < 1)) {
            getInvulAgainstSkills().remove(holder.getSkillId());
        }
    }

    public boolean isInvulAgainst(int skillId, int skillLevel) {
        if (!invulAgainstSkills.isEmpty()) {
            final SkillHolder holder = getInvulAgainstSkills().get(skillId);
            return ((holder != null) && ((holder.getSkillLevel() < 1) || (holder.getSkillLevel() == skillLevel)));
        }

        return false;
    }

    @Override
    public Collection<AbstractEventListener> getListeners(EventType type) {
        final Collection<AbstractEventListener> objectListeners = super.getListeners(type);
        final Collection<AbstractEventListener> templateListeners = template.getListeners(type);
        final Collection<AbstractEventListener> globalListeners = isMonster()
                ? Containers.Monsters().getListeners(type)
                : isNpc()
                        ? Containers.Npcs().getListeners(type)
                        : isPlayer() ? Containers.Players().getListeners(type) : Collections.emptyList();

        if (objectListeners.isEmpty() && templateListeners.isEmpty() && globalListeners.isEmpty()) {
            return Collections.emptyList();
        } else if (!objectListeners.isEmpty() && templateListeners.isEmpty() && globalListeners.isEmpty()) {
            return objectListeners;
        } else if (!templateListeners.isEmpty() && objectListeners.isEmpty() && globalListeners.isEmpty()) {
            return templateListeners;
        } else if (!globalListeners.isEmpty() && objectListeners.isEmpty() && templateListeners.isEmpty()) {
            return globalListeners;
        }

        final Collection<AbstractEventListener> allListeners =
                new ArrayList<>(objectListeners.size() + templateListeners.size() + globalListeners.size());
        allListeners.addAll(objectListeners);
        allListeners.addAll(templateListeners);
        allListeners.addAll(globalListeners);
        return allListeners;
    }

    public Race getRace() {
        return template.getRace();
    }

    @Override
    public void setXYZ(int newX, int newY, int newZ) {
        // 0, 0 is not a valid location.
        if ((newX == 0) && (newY == 0)) {
            return;
        }

        final ZoneRegion newZoneRegion = ZoneManager.getInstance().getRegion(newX, newY);
        // Prevent moving to nonexistent regions.
        if (newZoneRegion == null) {
            return;
        }

        final ZoneRegion oldZoneRegion = ZoneManager.getInstance().getRegion(this);
        if (oldZoneRegion != newZoneRegion) {
            oldZoneRegion.removeFromZones(this);
            newZoneRegion.revalidateZones(this);
        }

        super.setXYZ(newX, newY, newZ);
    }

    public boolean isInDuel() {
        return false;
    }

    public int getDuelId() {
        return 0;
    }

    public byte getSiegeState() {
        return 0;
    }

    public int getSiegeSide() {
        return 0;
    }

    protected void initSeenCreatures() {
        if (seenCreatures == null) {
            synchronized (this) {
                if (seenCreatures == null) {
                    if (isNpc()) {
                        final NpcTemplate template = asNpc().getTemplate();
                        if ((template != null) && (template.getAggroRange() > 0)) {
                            seenCreatureRange = template.getAggroRange();
                        }
                    }

                    seenCreatures = ConcurrentHashMap.newKeySet(1);
                }
            }
        }

        CreatureSeeTaskManager.getInstance().add(this);
    }

    public void updateSeenCreatures() {
        if ((seenCreatures == null) || dead || !isSpawned()) {
            return;
        }

        final WorldRegion region = getWorldRegion();
        if ((region == null) || !region.areNeighborsActive()) {
            return;
        }

        World.forEachVisibleObjectInRange(this, Creature.class, seenCreatureRange, creature -> {
            if (!creature.isInvisible()
                    && seenCreatures.add(creature)
                    && EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_SEE, this)) {
                EventDispatcher.getInstance()
                        .notifyEventAsync(
                                OnCreatureSee.builder()
                                        .creature(this)
                                        .seen(creature)
                                        .build(),
                                this);
            }
        });
    }

    public void removeSeenCreature(WorldObject worldObject) {
        if (seenCreatures == null) {
            return;
        }

        seenCreatures.remove(worldObject);
    }

    public int getMinShopDistance() {
        return 0;
    }

    /**
     * Adopts a client-reported position mid-move, also updating MoveData's accurate coords so the next
     * tick does not snap back to the stale xAccurate / yAccurate.
     */
    public void setSyncedXYZ(int x, int y, int z) {
        setXYZ(x, y, z);

        final MoveData move = this.move;
        if (move != null) {
            move.xAccurate = x;
            move.yAccurate = y;
        }
    }

    public void addBuffInfoTime(BuffInfo info) {
        buffFinishTask.addBuffInfo(info);
    }

    public void removeBuffInfoTime(BuffInfo info) {
        buffFinishTask.removeBuffInfo(info);
    }
}
