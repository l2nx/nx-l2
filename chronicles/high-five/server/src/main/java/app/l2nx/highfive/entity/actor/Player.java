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

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.highfive.LoginServerThread;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.ai.Intention;
import app.l2nx.highfive.ai.PlayerAI;
import app.l2nx.highfive.ai.SummonAI;
import app.l2nx.highfive.cache.RelationCache;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.communitybbs.BB.Forum;
import app.l2nx.highfive.communitybbs.Manager.ForumsBBSManager;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.PvpConfig;
import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.config.custom.AutoPlayConfig;
import app.l2nx.highfive.config.custom.DualboxCheckConfig;
import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.config.custom.FakePlayersConfig;
import app.l2nx.highfive.config.custom.FreeMountsConfig;
import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.config.custom.PrivateStoreRangeConfig;
import app.l2nx.highfive.config.custom.PvpAnnounceConfig;
import app.l2nx.highfive.config.custom.PvpRewardItemConfig;
import app.l2nx.highfive.config.custom.PvpTitleColorConfig;
import app.l2nx.highfive.config.custom.RebirthConfig;
import app.l2nx.highfive.data.enums.CategoryType;
import app.l2nx.highfive.data.holders.AccessLevel;
import app.l2nx.highfive.data.holders.PetLevelData;
import app.l2nx.highfive.data.holders.SellBuffHolder;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.sql.CharSummonTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.data.sql.OfflinePlayTable;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.data.sql.StoredEffectRows;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.data.xml.CategoryData;
import app.l2nx.highfive.data.xml.ClassListData;
import app.l2nx.highfive.data.xml.EnchantSkillGroupsData;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.data.xml.ExperienceLossData;
import app.l2nx.highfive.data.xml.FishData;
import app.l2nx.highfive.data.xml.HennaData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.NpcNameLocalisationData;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.data.xml.PlayerTemplateData;
import app.l2nx.highfive.data.xml.RecipeData;
import app.l2nx.highfive.data.xml.SendMessageLocalisationData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.appearance.PlayerAppearance;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.enums.player.IllegalActionPunishmentType;
import app.l2nx.highfive.entity.actor.enums.player.MountType;
import app.l2nx.highfive.entity.actor.enums.player.PlayerAction;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.holders.creature.TimeStamp;
import app.l2nx.highfive.entity.actor.holders.player.ArenaParticipantsHolder;
import app.l2nx.highfive.entity.actor.holders.player.AutoPlaySettingsHolder;
import app.l2nx.highfive.entity.actor.holders.player.AutoUseSettingsHolder;
import app.l2nx.highfive.entity.actor.holders.player.BlockList;
import app.l2nx.highfive.entity.actor.holders.player.ContactList;
import app.l2nx.highfive.entity.actor.holders.player.Duel;
import app.l2nx.highfive.entity.actor.holders.player.Macro;
import app.l2nx.highfive.entity.actor.holders.player.MacroList;
import app.l2nx.highfive.entity.actor.holders.player.MovieHolder;
import app.l2nx.highfive.entity.actor.holders.player.Nevit;
import app.l2nx.highfive.entity.actor.holders.player.Radar;
import app.l2nx.highfive.entity.actor.holders.player.Shortcut;
import app.l2nx.highfive.entity.actor.holders.player.Shortcuts;
import app.l2nx.highfive.entity.actor.holders.player.SubClassHolder;
import app.l2nx.highfive.entity.actor.holders.player.TeleportBookmark;
import app.l2nx.highfive.entity.actor.instance.AirShip;
import app.l2nx.highfive.entity.actor.instance.Boat;
import app.l2nx.highfive.entity.actor.instance.ControlTower;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.entity.actor.instance.Decoy;
import app.l2nx.highfive.entity.actor.instance.Defender;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.EventMonster;
import app.l2nx.highfive.entity.actor.instance.FriendlyMob;
import app.l2nx.highfive.entity.actor.instance.Guard;
import app.l2nx.highfive.entity.actor.instance.TamedBeast;
import app.l2nx.highfive.entity.actor.instance.Trap;
import app.l2nx.highfive.entity.actor.stat.PlayerStat;
import app.l2nx.highfive.entity.actor.status.PlayerStatus;
import app.l2nx.highfive.entity.actor.tasks.player.DismountTask;
import app.l2nx.highfive.entity.actor.tasks.player.FameTask;
import app.l2nx.highfive.entity.actor.tasks.player.InventoryEnableTask;
import app.l2nx.highfive.entity.actor.tasks.player.LookingForFishTask;
import app.l2nx.highfive.entity.actor.tasks.player.PetFeedTask;
import app.l2nx.highfive.entity.actor.tasks.player.RecoBonusTaskEnd;
import app.l2nx.highfive.entity.actor.tasks.player.RecoGiveTask;
import app.l2nx.highfive.entity.actor.tasks.player.RentPetTask;
import app.l2nx.highfive.entity.actor.tasks.player.ResetChargesTask;
import app.l2nx.highfive.entity.actor.tasks.player.ResetSoulsTask;
import app.l2nx.highfive.entity.actor.tasks.player.SitDownTask;
import app.l2nx.highfive.entity.actor.tasks.player.StandUpTask;
import app.l2nx.highfive.entity.actor.tasks.player.TeleportWatchdogTask;
import app.l2nx.highfive.entity.actor.tasks.player.VitalityTask;
import app.l2nx.highfive.entity.actor.tasks.player.WarnUserTakeBreakTask;
import app.l2nx.highfive.entity.actor.tasks.player.WaterTask;
import app.l2nx.highfive.entity.actor.templates.PlayerTemplate;
import app.l2nx.highfive.entity.actor.transform.AdditionalSkillHolder;
import app.l2nx.highfive.entity.actor.transform.Transform;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.entity.clan.ClanPrivileges;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.groups.PartyDistributionType;
import app.l2nx.highfive.entity.groups.PartyMessageType;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoom;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoomList;
import app.l2nx.highfive.entity.groups.matching.PartyMatchWaitingList;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.entity.item.Armor;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.Henna;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.item.holders.PremiumItem;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.recipe.ManufactureItem;
import app.l2nx.highfive.entity.item.recipe.RecipeList;
import app.l2nx.highfive.entity.item.type.ActionType;
import app.l2nx.highfive.entity.item.type.ArmorType;
import app.l2nx.highfive.entity.item.type.EtcItemType;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.itemcontainer.PetInventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerFreight;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerRefund;
import app.l2nx.highfive.entity.itemcontainer.PlayerWarehouse;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneRegion;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.entity.zone.type.BossZone;
import app.l2nx.highfive.entity.zone.type.WaterZone;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.handler.IItemHandler;
import app.l2nx.highfive.handler.ItemHandler;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.managers.AntiFeedManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.CoupleManager;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.managers.DimensionalRiftManager;
import app.l2nx.highfive.managers.DuelManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.GrandBossManager;
import app.l2nx.highfive.managers.HandysBlockCheckerManager;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.ItemsOnGroundManager;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.managers.RebirthManager;
import app.l2nx.highfive.managers.RecipeManager;
import app.l2nx.highfive.managers.ScriptManager;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.effects.EffectFlag;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.playable.OnPlayableExpChanged;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerFameChanged;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerHennaRemove;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerItemEquip;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerKarmaChanged;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLogin;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLogout;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerPKChanged;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerProfessionCancel;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerProfessionChange;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerPvPChanged;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerPvPKill;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerSit;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerTransform;
import app.l2nx.highfive.mechanics.events.listeners.FunctionEventListener;
import app.l2nx.highfive.mechanics.events.returns.TerminateReturn;
import app.l2nx.highfive.mechanics.fishing.Fish;
import app.l2nx.highfive.mechanics.fishing.Fishing;
import app.l2nx.highfive.mechanics.krateisCube.KrateiArena;
import app.l2nx.highfive.mechanics.multisell.PreparedListContainer;
import app.l2nx.highfive.mechanics.olympiad.Hero;
import app.l2nx.highfive.mechanics.olympiad.OlympiadGameManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadGameTask;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.QuestTimer;
import app.l2nx.highfive.mechanics.script.timers.TimerHolder;
import app.l2nx.highfive.mechanics.sevensigns.SevenSigns;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.Siege;
import app.l2nx.highfive.mechanics.siege.TerritoryWard;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.CommonSkill;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.StoredEffect;
import app.l2nx.highfive.mechanics.skill.enums.FlyType;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.mechanics.skill.holders.EnchantSkillLearn;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.mechanics.skill.holders.SkillUseHolder;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCTeam;
import app.l2nx.highfive.mechanics.variables.AccountVariables;
import app.l2nx.highfive.mechanics.variables.PlayerVariables;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.enums.HtmlActionScope;
import app.l2nx.highfive.network.holders.RequestPartner;
import app.l2nx.highfive.network.holders.TradeList;
import app.l2nx.highfive.network.serverpackets.AbstractHtmlPacket;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.ChangeWaitType;
import app.l2nx.highfive.network.serverpackets.CharInfo;
import app.l2nx.highfive.network.serverpackets.ConfirmDlg;
import app.l2nx.highfive.network.serverpackets.CreatureSay;
import app.l2nx.highfive.network.serverpackets.EtcStatusUpdate;
import app.l2nx.highfive.network.serverpackets.ExAutoSoulShot;
import app.l2nx.highfive.network.serverpackets.ExBrExtraUserInfo;
import app.l2nx.highfive.network.serverpackets.ExDuelUpdateUserInfo;
import app.l2nx.highfive.network.serverpackets.ExFishingEnd;
import app.l2nx.highfive.network.serverpackets.ExFishingStart;
import app.l2nx.highfive.network.serverpackets.ExGetBookMarkInfoPacket;
import app.l2nx.highfive.network.serverpackets.ExGetOnAirShip;
import app.l2nx.highfive.network.serverpackets.ExOlympiadMode;
import app.l2nx.highfive.network.serverpackets.ExPrivateStoreSetWholeMsg;
import app.l2nx.highfive.network.serverpackets.ExSetCompassZoneCode;
import app.l2nx.highfive.network.serverpackets.ExStartScenePlayer;
import app.l2nx.highfive.network.serverpackets.ExStorageMaxCount;
import app.l2nx.highfive.network.serverpackets.ExUseSharedGroupItem;
import app.l2nx.highfive.network.serverpackets.ExVoteSystemInfo;
import app.l2nx.highfive.network.serverpackets.FriendStatusPacket;
import app.l2nx.highfive.network.serverpackets.GetOnVehicle;
import app.l2nx.highfive.network.serverpackets.HennaInfo;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.serverpackets.ItemList;
import app.l2nx.highfive.network.serverpackets.LeaveWorld;
import app.l2nx.highfive.network.serverpackets.MagicSkillUse;
import app.l2nx.highfive.network.serverpackets.MyTargetSelected;
import app.l2nx.highfive.network.serverpackets.NicknameChanged;
import app.l2nx.highfive.network.serverpackets.ObservationMode;
import app.l2nx.highfive.network.serverpackets.ObservationReturn;
import app.l2nx.highfive.network.serverpackets.PartySmallWindowUpdate;
import app.l2nx.highfive.network.serverpackets.PetInventoryUpdate;
import app.l2nx.highfive.network.serverpackets.PlaySound;
import app.l2nx.highfive.network.serverpackets.PledgeShowMemberListDelete;
import app.l2nx.highfive.network.serverpackets.PledgeShowMemberListUpdate;
import app.l2nx.highfive.network.serverpackets.PrivateStoreListBuy;
import app.l2nx.highfive.network.serverpackets.PrivateStoreListSell;
import app.l2nx.highfive.network.serverpackets.PrivateStoreManageListBuy;
import app.l2nx.highfive.network.serverpackets.PrivateStoreManageListSell;
import app.l2nx.highfive.network.serverpackets.PrivateStoreMsgBuy;
import app.l2nx.highfive.network.serverpackets.PrivateStoreMsgSell;
import app.l2nx.highfive.network.serverpackets.RecipeShopMsg;
import app.l2nx.highfive.network.serverpackets.RecipeShopSellList;
import app.l2nx.highfive.network.serverpackets.RelationChanged;
import app.l2nx.highfive.network.serverpackets.Ride;
import app.l2nx.highfive.network.serverpackets.ServerPacket;
import app.l2nx.highfive.network.serverpackets.SetupGauge;
import app.l2nx.highfive.network.serverpackets.ShortcutInit;
import app.l2nx.highfive.network.serverpackets.SkillCoolTime;
import app.l2nx.highfive.network.serverpackets.SkillList;
import app.l2nx.highfive.network.serverpackets.Snoop;
import app.l2nx.highfive.network.serverpackets.SocialAction;
import app.l2nx.highfive.network.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.serverpackets.StopMove;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.network.serverpackets.TargetSelected;
import app.l2nx.highfive.network.serverpackets.TargetUnselected;
import app.l2nx.highfive.network.serverpackets.TradeDone;
import app.l2nx.highfive.network.serverpackets.TradeOtherDone;
import app.l2nx.highfive.network.serverpackets.TradeStart;
import app.l2nx.highfive.network.serverpackets.UserInfo;
import app.l2nx.highfive.network.serverpackets.ValidateLocation;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import app.l2nx.highfive.taskmanagers.AutoPlayTaskManager;
import app.l2nx.highfive.taskmanagers.AutoUseTaskManager;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.taskmanagers.ItemsAutoDestroyTaskManager;
import app.l2nx.highfive.taskmanagers.PlayerAutoSaveTaskManager;
import app.l2nx.highfive.taskmanagers.PvpFlagTaskManager;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.LocationUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class represents all player characters in the world.<br>
 * There is always a client-thread connected to this (except if a player-store is activated upon logout).
 */
@Slf4j
public class Player extends Playable {
    // Character Skill SQL String Definitions:
    private static final String RESTORE_SKILLS_FOR_CHAR =
            "SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=?";
    private static final String UPDATE_CHARACTER_SKILL_LEVEL =
            "UPDATE character_skills SET skill_level=? WHERE skill_id=? AND charId=? AND class_index=?";
    private static final String ADD_NEW_SKILLS =
            "REPLACE INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,?,?)";
    private static final String DELETE_SKILL_FROM_CHAR =
            "DELETE FROM character_skills WHERE skill_id=? AND charId=? AND class_index=?";
    private static final String DELETE_CHAR_SKILLS = "DELETE FROM character_skills WHERE charId=? AND class_index=?";

    // Character Skill Save SQL String Definitions:
    private static final String ADD_SKILL_SAVE =
            "REPLACE INTO character_skills_save (charId,skill_id,skill_level,remaining_ms,tick_phase_ms,reuse_delay,reuse_ends_at,restore_type,class_index,buff_index) VALUES (?,?,?,?,?,?,?,?,?,?)";
    private static final String RESTORE_SKILL_SAVE =
            "SELECT skill_id,skill_level,remaining_ms,tick_phase_ms,reuse_delay,reuse_ends_at,restore_type FROM character_skills_save WHERE charId=? AND class_index=? ORDER BY buff_index ASC";
    private static final String DELETE_SKILL_SAVE =
            "DELETE FROM character_skills_save WHERE charId=? AND class_index=?";

    // Character Item Reuse Time String Definition:
    private static final String ADD_ITEM_REUSE_SAVE =
            "INSERT INTO character_item_reuse_save (charId,itemId,itemObjId,reuseDelay,reuse_ends_at) VALUES (?,?,?,?,?)";
    private static final String RESTORE_ITEM_REUSE_SAVE =
            "SELECT charId,itemId,itemObjId,reuseDelay,reuse_ends_at FROM character_item_reuse_save WHERE charId=?";
    private static final String DELETE_ITEM_REUSE_SAVE = "DELETE FROM character_item_reuse_save WHERE charId=?";

    // Character Character SQL String Definitions:
    private static final String INSERT_CHARACTER =
            "INSERT INTO characters (account_name,charId,char_name,level,maxHp,curHp,maxCp,curCp,maxMp,curMp,face,hairStyle,hairColor,sex,exp,sp,karma,fame,pvpkills,pkkills,clanid,race,classid,deletetime,cancraft,title,title_color,accesslevel,online,isin7sdungeon,clan_privs,wantspeace,base_class,newbie,nobless,power_grade,createDate,lastAccess) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_CHARACTER =
            "UPDATE characters SET level=?,maxHp=?,curHp=?,maxCp=?,curCp=?,maxMp=?,curMp=?,face=?,hairStyle=?,hairColor=?,sex=?,heading=?,x=?,y=?,z=?,exp=?,expBeforeDeath=?,sp=?,karma=?,fame=?,pvpkills=?,pkkills=?,clanid=?,race=?,classid=?,deletetime=?,title=?,title_color=?,accesslevel=?,online=?,isin7sdungeon=?,clan_privs=?,wantspeace=?,base_class=?,onlinetime=?,newbie=?,nobless=?,power_grade=?,subpledge=?,lvl_joined_academy=?,apprentice=?,sponsor=?,clan_join_expiry_time=?,clan_create_expiry_time=?,char_name=?,death_penalty_level=?,bookmarkslot=?,vitality_points=?,language=?,faction=?,pccafe_points=? WHERE charId=?";
    private static final String RESTORE_CHARACTER = "SELECT * FROM characters WHERE charId=?";

    // Character Teleport Bookmark:
    private static final String INSERT_TP_BOOKMARK =
            "INSERT INTO character_tpbookmark (charId,Id,x,y,z,icon,tag,name) values (?,?,?,?,?,?,?,?)";
    private static final String UPDATE_TP_BOOKMARK =
            "UPDATE character_tpbookmark SET icon=?,tag=?,name=? where charId=? AND Id=?";
    private static final String RESTORE_TP_BOOKMARK =
            "SELECT Id,x,y,z,icon,tag,name FROM character_tpbookmark WHERE charId=?";
    private static final String DELETE_TP_BOOKMARK = "DELETE FROM character_tpbookmark WHERE charId=? AND Id=?";

    // Character Subclass SQL String Definitions:
    private static final String RESTORE_CHAR_SUBCLASSES =
            "SELECT class_id,exp,sp,level,class_index FROM character_subclasses WHERE charId=? ORDER BY class_index ASC";
    private static final String ADD_CHAR_SUBCLASS =
            "INSERT INTO character_subclasses (charId,class_id,exp,sp,level,class_index) VALUES (?,?,?,?,?,?)";
    private static final String UPDATE_CHAR_SUBCLASS =
            "UPDATE character_subclasses SET exp=?,sp=?,level=?,class_id=? WHERE charId=? AND class_index =?";
    private static final String DELETE_CHAR_SUBCLASS =
            "DELETE FROM character_subclasses WHERE charId=? AND class_index=?";

    // Character Henna SQL String Definitions:
    private static final String RESTORE_CHAR_HENNAS =
            "SELECT slot,symbol_id FROM character_hennas WHERE charId=? AND class_index=?";
    private static final String ADD_CHAR_HENNA =
            "INSERT INTO character_hennas (charId,symbol_id,slot,class_index) VALUES (?,?,?,?)";
    private static final String DELETE_CHAR_HENNA =
            "DELETE FROM character_hennas WHERE charId=? AND slot=? AND class_index=?";

    // Character Shortcut SQL String Definitions:
    private static final String DELETE_CHAR_SHORTCUTS =
            "DELETE FROM character_shortcuts WHERE charId=? AND class_index=?";

    // Character Recipe List Save
    private static final String DELETE_CHAR_RECIPE_SHOP = "DELETE FROM character_recipeshoplist WHERE charId=?";
    private static final String INSERT_CHAR_RECIPE_SHOP =
            "REPLACE INTO character_recipeshoplist (`charId`, `recipeId`, `price`, `index`) VALUES (?, ?, ?, ?)";
    private static final String RESTORE_CHAR_RECIPE_SHOP =
            "SELECT * FROM character_recipeshoplist WHERE charId=? ORDER BY `index`";

    public static final String NEWBIE_KEY = "NEWBIE";

    public static final int ID_NONE = -1;

    public static final int REQUEST_TIMEOUT = 15;

    @Getter
    private int pcCafePoints = 0;

    // @formatter:off
    private static final int[][] NEVIT_HOURGLASS_BONUS = {
        {25, 50, 50, 50, 50, 50, 50, 50, 50, 50},
        {16, 33, 50, 50, 50, 50, 50, 50, 50, 50},
        {12, 25, 37, 50, 50, 50, 50, 50, 50, 50},
        {10, 20, 30, 40, 50, 50, 50, 50, 50, 50},
        {8, 16, 25, 33, 41, 50, 50, 50, 50, 50},
        {7, 14, 21, 28, 35, 42, 50, 50, 50, 50},
        {6, 12, 18, 25, 31, 37, 43, 50, 50, 50},
        {5, 11, 16, 22, 27, 33, 38, 44, 50, 50},
        {5, 10, 15, 20, 25, 30, 35, 40, 45, 50},
    };
    // @formatter:on

    private static final int NEVIT_HOURGLASS_ACTIVE = 0;
    private static final int NEVIT_HOURGLASS_PAUSED = 1;
    private static final int NEVIT_HOURGLASS_MAINTAIN = 10;

    @Getter
    private GameClient client;

    private String ipAddress = "N/A";

    @Getter
    private final String accountNamePlayer;

    @Getter
    @Setter
    private long deleteTimer;

    @Getter
    @Setter
    private Calendar createDate = ClockBridge.calendar();

    @Getter
    private String lang = null;

    private String htmlPrefix = "";

    @Getter
    private volatile boolean online = false;

    @Getter
    @Setter
    private boolean offlinePlay = false;

    private boolean enteredWorld = false;
    private long onlineTime;
    private long onlineBeginTime;

    @Getter
    @Setter(lombok.AccessLevel.PROTECTED)
    private long lastAccess;

    @Setter
    private long uptime;

    private final InventoryUpdate inventoryUpdate = new InventoryUpdate();
    private ScheduledFuture<?> inventoryUpdateTask;
    private ScheduledFuture<?> itemListTask;
    private ScheduledFuture<?> skillListTask;
    private ScheduledFuture<?> updateAndBroadcastStatusTask;
    private ScheduledFuture<?> broadcastCharInfoTask;
    private ScheduledFuture<?> broadcastStatusUpdateTask;

    @Getter
    private boolean changingClass = false;

    @Getter
    protected int baseClass;

    @Getter
    protected int activeClass;

    @Getter
    protected int classIndex = 0;

    /** Data for mounted pets. */
    private int controlItemId;

    private PetLevelData leveldata;

    @Getter
    private int currentFeed;

    protected Future<?> mountFeedTask;
    private ScheduledFuture<?> dismountTask;
    private boolean petItems = false;

    /** The list of sub-classes this character has. */
    @Getter
    private final Map<Integer, SubClassHolder> subClasses = new ConcurrentHashMap<>();

    @Getter
    private final PlayerAppearance appearance;

    /** The Experience of the Player before the last Death Penalty */
    @Getter
    @Setter
    private long expBeforeDeath;

    /** The number of player killed during a PvP (the player killed was PvP Flagged) */
    @Getter
    private int pvpKills;

    /** The PK counter of the Player (= Number of non PvP Flagged player killed) */
    @Getter
    private int pkKills;

    /** The PvP Flag state of the Player (0=White, 1=Purple) */
    @Getter
    private byte pvpFlag;

    /** The Fame of this Player */
    @Getter
    private int fame;

    private ScheduledFuture<?> fameTask;

    /** Vitality recovery task */
    private ScheduledFuture<?> vitalityTask;

    private ScheduledFuture<?> teleportWatchdog;

    /** The Siege state of the Player */
    @Getter
    @Setter
    private byte siegeState = 0;

    /** The id of castle/fort which the Player is registered for siege */
    @Getter
    @Setter
    private int siegeSide = 0;

    private int curWeightPenalty = 0;

    private int lastCompassZone; // The last compass zone update send to the client.

    @Getter
    @Setter
    private boolean in7sDungeon = false;

    @Getter
    private final ContactList contactList = new ContactList(this);

    @Getter
    private int bookmarkSlot = 0; // The Teleport Bookmark Slot

    private final Map<Integer, TeleportBookmark> tpbookmarks = new ConcurrentHashMap<>();

    private boolean canFeed;

    @Getter
    @Setter
    private boolean inSiege;

    @Getter
    @Setter
    private boolean inHideoutSiege = false;

    /** Olympiad */
    @Getter
    @Setter
    private boolean inOlympiadMode = false;

    @Getter
    @Setter
    private boolean olympiadStart = false;

    @Getter
    @Setter
    private int olympiadGameId = -1;

    @Getter
    @Setter
    private int olympiadSide = -1;
    /** Olympiad buff count. */
    @Getter
    @Setter
    private int olympiadBuffCount = 0;

    /** Duel */
    @Getter
    private boolean inDuel = false;

    private boolean startingDuel = false;

    @Getter
    @Setter
    private int duelState = Duel.DUELSTATE_NODUEL;

    @Getter
    private int duelId = 0;

    private SystemMessageId noDuelReason = SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL;

    /** Boat and AirShip */
    @Getter
    private Vehicle vehicle = null;

    @Getter
    @Setter
    private Location inVehiclePosition;

    public ScheduledFuture<?> taskForFish;

    @Getter
    private MountType mountType = MountType.NONE;

    @Getter
    private int mountNpcId;

    @Getter
    private int mountLevel;
    /** Store object used to summon the strider you are mounting **/
    @Getter
    @Setter
    private int mountObjectID = 0;

    @Getter
    @Setter
    public int teleMode = 0;

    @Getter
    @Setter
    private boolean inCrystallize;

    @Getter
    @Setter
    private boolean crafting;

    @Getter
    @Setter
    private long offlineStartTime = 0;

    @Getter
    private Transform transformation;

    private final Map<Integer, Skill> transformSkills = new ConcurrentHashMap<>();

    /** The table containing all RecipeList of the Player */
    private final Map<Integer, RecipeList> dwarvenRecipeBook = new ConcurrentHashMap<>();

    private final Map<Integer, RecipeList> commonRecipeBook = new ConcurrentHashMap<>();

    /** Premium Items */
    @Getter
    private final Map<Integer, PremiumItem> premiumItemList = new ConcurrentHashMap<>();

    /** True if the Player is sitting */
    @Getter
    private boolean sitting;

    private boolean sittingInProgress;

    /** Location before entering Observer Mode */
    @Getter
    private final Location lastLocation = new Location(0, 0, 0);

    private boolean observerMode = false;

    /** Stored from last ValidatePosition **/
    @Getter
    private final Location lastServerPosition = new Location(0, 0, 0);

    private final AtomicBoolean blinkActive = new AtomicBoolean();

    /** The number of recommendation obtained by the Player */
    @Getter
    private int recomHave; // How much I was recommended by others.
    /** The number of recommendation that the Player can give */
    @Getter
    private int recomLeft; // How many recommendations I can give to others.
    /** Recommendation Bonus task **/
    private ScheduledFuture<?> nevitHourglassTask;
    /** Recommendation task **/
    private ScheduledFuture<?> recoGiveTask;
    /** Recommendation Two Hours bonus **/
    @Getter
    @Setter
    protected boolean recoTwoHoursGiven = false;

    @Getter
    private final PlayerInventory inventory = new PlayerInventory(this);

    @Getter
    private final PlayerFreight freight = new PlayerFreight(this);

    @Getter
    private final PlayerWarehouse warehouse = new PlayerWarehouse(this);

    private PlayerRefund refund;

    @Getter
    private PrivateStoreType privateStoreType = PrivateStoreType.NONE;

    @Getter
    @Setter
    private TradeList activeTradeList;

    @Getter
    @Setter
    private ItemContainer activeWarehouse;

    private Map<Integer, ManufactureItem> manufactureItems;

    @Getter
    private String storeName = "";

    private TradeList sellList;
    private TradeList buyList;

    // Multisell
    @Getter
    @Setter
    private PreparedListContainer multiSell = null;

    /** Bitmask used to keep track of one-time/newbie quest rewards */
    @Getter
    @Setter
    private int newbie;

    @Getter
    private boolean noble = false;

    @Getter
    private boolean hero = false;

    /** Premium System */
    @Setter
    private boolean premiumStatus = false;

    /** Faction System */
    @Getter
    private boolean good = false;

    @Getter
    private boolean evil = false;

    /** The Npc corresponding to the last Folk which one the player talked. */
    @Getter
    @Setter
    private Npc lastFolkNPC = null;

    /** Last NPC Id talked on a quest */
    @Getter
    @Setter
    private int lastQuestNpcObject = 0;

    /** The table containing all Quests began by the Player */
    private final Map<String, QuestState> quests = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    /** The list containing all shortcuts of this player. */
    private final Shortcuts shortcuts = new Shortcuts(this);

    /** The list containing all macros of this player. */
    @Getter
    private final MacroList macros = new MacroList(this);

    private final Set<Player> snoopListener = ConcurrentHashMap.newKeySet(1);
    private final Set<Player> snoopedPlayer = ConcurrentHashMap.newKeySet(1);

    /** Hennas */
    @Getter
    private final Henna[] hennaList = new Henna[3];

    @Getter
    private int hennaStatSTR;

    @Getter
    private int hennaStatINT;

    @Getter
    private int hennaStatDEX;

    @Getter
    private int hennaStatMEN;

    @Getter
    private int hennaStatWIT;

    @Getter
    private int hennaStatCON;

    /** The Summon of the Player */
    private Summon summon = null;
    /** The Decoy of the Player */
    @Getter
    @Setter
    private Decoy decoy = null;
    /** The Trap of the Player */
    @Getter
    @Setter
    private Trap trap = null;
    /** The Agathion of the Player */
    @Getter
    @Setter
    private int agathionId = 0;

    // Apparently, a Player CAN have both a summon AND a tamed beast at the same time!!
    // After Freya players can control more than one tamed beast.
    @Getter
    private Collection<TamedBeast> trainedBeasts = null;

    @Getter
    @Setter
    private boolean minimapAllowed = false;

    // client radar
    // TODO: This needs to be better integrated and saved/loaded
    @Getter
    private final Radar radar;

    private ScheduledFuture<?> taskWarnUserTakeBreak;

    // Party matching
    // private int _partymatching = 0;
    @Getter
    @Setter
    private int partyRoom = 0;
    // private int _partywait = 0;

    // Clan related attributes
    /** The Clan Identifier of the Player */
    @Getter
    private int clanId;

    /** The Clan object of the Player */
    @Getter
    private Clan clan;

    /** Apprentice and Sponsor IDs */
    @Getter
    @Setter
    private int apprentice = 0;

    @Getter
    @Setter
    private int sponsor = 0;

    @Getter
    @Setter
    private long clanJoinExpiryTime;

    @Getter
    @Setter
    private long clanCreateExpiryTime;

    @Getter
    @Setter
    private int powerGrade = 0;

    @Getter
    private ClanPrivileges clanPrivileges = new ClanPrivileges();

    /** Player's pledge class (knight, Baron, etc.) */
    @Getter
    private int pledgeClass = 0;

    @Getter
    @Setter
    private int pledgeType = 0;

    /** Level at which the player joined the clan as an academy member */
    @Getter
    @Setter
    private int lvlJoinedAcademy = 0;

    @Getter
    @Setter
    private int wantsPeace = 0;

    // Death Penalty Buff Level
    @Getter
    @Setter
    private int deathPenaltyBuffLevel = 0;

    // charges
    private final AtomicInteger charges = new AtomicInteger();
    private ScheduledFuture<?> chargeTask = null;

    // Absorbed Souls
    @Getter
    private int chargedSouls = 0;

    private ScheduledFuture<?> soulTask = null;

    // WorldPosition used by TARGET_SIGNET_GROUND
    @Getter
    @Setter
    private Location currentSkillWorldPosition;

    private AccessLevel accessLevel;

    private boolean messageRefusal = false; // message refusal mode

    @Getter
    private boolean silenceMode = false; // silence mode

    private List<Integer> silenceModeExcluded; // silence mode

    @Setter
    private boolean dietMode = false; // ignore weight penalty

    @Setter
    private boolean tradeRefusal = false; // Trade refusal

    @Setter
    private boolean exchangeRefusal = false; // Exchange refusal

    @Getter
    @Setter
    private Party party;

    @Getter
    @Setter
    PartyDistributionType partyDistributionType;

    // This is needed to find the inviting player for Party response.
    // There can only be one active party request at once.
    @Setter
    private Player activeRequester;

    private long requestExpireTime = 0;

    @Getter
    private final RequestPartner request = new RequestPartner(this);

    private Item arrowItem;
    private Item boltItem;

    // Used for protection after teleport.
    private long spawnProtectEndTime = 0;
    private long teleportProtectEndTime = 0;

    // Protects a char from aggro mobs when getting up from fake death.
    private long recentFakeDeathEndTime = 0;

    @Getter
    @Setter
    private boolean fakeDeath;

    /** The fists Weapon of the Player (used when no weapon is equipped) */
    @Getter
    @Setter
    private Weapon fistsWeaponItem;

    @Getter
    private final Map<Integer, String> accountChars = new LinkedHashMap<>();

    // private byte _updateKnownCounter = 0;

    @Getter
    private int expertiseArmorPenalty = 0;

    @Getter
    private int expertiseWeaponPenalty = 0;

    @Getter
    @Setter
    private int expertisePenaltyBonus = 0;

    @Getter
    @Setter
    private boolean enchanting = false;

    @Getter
    private int activeEnchantItemId = ID_NONE;

    @Getter
    @Setter
    private int activeEnchantSupportItemId = ID_NONE;

    @Getter
    @Setter
    private int activeEnchantAttrItemId = ID_NONE;

    @Getter
    @Setter
    private long activeEnchantTimestamp = 0;

    @Getter
    protected boolean inventoryDisabled = false;
    /** Player's cubics. */
    private final Map<Integer, Cubic> cubics = new ConcurrentSkipListMap<>();

    private final ConcurrentLinkedDeque<Integer> cubicOrder = new ConcurrentLinkedDeque<>();
    /** Active shots. */
    @Getter
    protected Set<Integer> autoSoulShot = ConcurrentHashMap.newKeySet(1);

    /** Event parameters */
    @Setter
    private boolean registeredOnEvent = false;

    @Getter
    @Setter
    private boolean onSoloEvent = false;

    @Getter
    @Setter
    private boolean onEvent = false;

    @Setter
    private byte blockCheckerArena = -1;

    @Getter
    @Setter
    private KrateiArena krateiArena = null;

    private int ucKills = 0;
    private int ucDeaths = 0;
    private int ucState = 0;
    public static final int UC_STATE_NONE = 0;
    public static final int UC_STATE_POINT = 1;
    public static final int UC_STATE_ARENA = 2;

    /** new loto ticket **/
    private final int[] loto = new int[5];
    /** New race ticket. */
    private final int[] raceTickets = new int[2];

    @Getter
    private final BlockList blockList = new BlockList(this);

    private Fish fish;

    @Getter
    @Setter
    private Item lure;

    @Getter
    private Fishing fishCombat;

    @Getter
    @Setter
    private boolean fishing = false;

    @Getter
    private int fishX = 0;

    @Getter
    private int fishY = 0;

    @Getter
    private int fishZ = 0;

    private ScheduledFuture<?> taskRentPet;
    private ScheduledFuture<?> taskWater;

    /** Last Html Npcs, 0 = last html was not bound to an npc */
    private final int[] htmlActionOriginObjectIds = new int[HtmlActionScope.values().length];
    /**
     * Origin of the last incoming html action request.<br>
     * This can be used for htmls continuing the conversation with an npc.
     */
    @Getter
    private int lastHtmlActionOriginId;

    /** Bypass validations */
    @SuppressWarnings("unchecked")
    private final List<String>[] htmlActionCaches = new List[HtmlActionScope.values().length];

    private Forum forumMail;
    private Forum forumMemo;

    /** Current skill in use. Note that Creature has _lastSkillCast, but this has the button presses */
    @Getter
    private SkillUseHolder currentSkill;

    @Getter
    private SkillUseHolder currentPetSkill;

    /** Skills queued because a skill is already in progress */
    @Getter
    private SkillUseHolder queuedSkill;

    @Getter
    @Setter
    private int cursedWeaponEquippedId = 0;

    @Getter
    @Setter
    private boolean combatFlagEquipped = false;

    @Setter
    private boolean canRevive = true;

    private int reviveRequested = 0;
    private double revivePower = 0;

    @Getter
    private boolean revivingPet = false;

    private double cpUpdateIncCheck = .0;
    private double cpUpdateDecCheck = .0;
    private double cpUpdateInterval = .0;
    private double mpUpdateIncCheck = .0;
    private double mpUpdateDecCheck = .0;
    private double mpUpdateInterval = .0;

    private double originalCp = .0;
    private double originalHp = .0;
    private double originalMp = .0;

    /** Char Coords from Client */
    @Getter
    @Setter
    private int clientX;

    @Getter
    @Setter
    private int clientY;

    @Getter
    @Setter
    private int clientZ;

    @Getter
    @Setter
    private int clientHeading;

    // During fall validations will be disabled for 1000 ms.
    private static final int FALLING_VALIDATION_DELAY = 1000;
    private volatile long fallingTimestamp = 0;
    private volatile int fallingDamage = 0;
    private Future<?> fallingDamageTask = null;

    @Getter
    private int multiSocialTarget = 0;

    @Getter
    private int multiSociaAction = 0;

    @Getter
    @Setter
    private MovieHolder movieHolder = null;

    @Getter
    @Setter
    private String adminConfirmCmd = null;

    private volatile long lastItemAuctionInfoRequest = 0;

    @Getter
    @Setter
    private long pvpFlagLasts;

    @Getter
    private long notMoveUntil = 0;

    /** Map containing all custom skills of this player. */
    private Map<Integer, Skill> customSkills = null;

    private volatile int actionMask;

    @Setter
    private Map<Stat, Double> servitorShare;

    /**
     * Creates a player.
     * @param objectId the object ID
     * @param template the player template
     * @param accountName the account name
     * @param app the player appearance
     */
    private Player(int objectId, PlayerTemplate template, String accountName, PlayerAppearance app) {
        super(objectId, template);
        setInstanceType(InstanceType.Player);
        initCharStatusUpdateValues();
        initPcStatusUpdateValues();

        for (int i = 0; i < htmlActionCaches.length; ++i) {
            htmlActionCaches[i] = new ArrayList<>();
        }

        accountNamePlayer = accountName;
        app.setOwner(this);
        appearance = app;

        // Create an AI.
        getAI();

        // Create a Radar object.
        radar = new Radar(this);
        startVitalityTask();
    }

    /**
     * Creates a player.
     * @param template the player template
     * @param accountName the account name
     * @param app the player appearance
     */
    private Player(PlayerTemplate template, String accountName, PlayerAppearance app) {
        this(IdManager.getInstance().getNextId(), template, accountName, app);
    }

    public void startPvPFlag() {
        updatePvPFlag(1);
        PvpFlagTaskManager.getInstance().add(this);
    }

    public void stopPvpRegTask() {
        PvpFlagTaskManager.getInstance().remove(this);
    }

    public void stopPvPFlag() {
        stopPvpRegTask();
        updatePvPFlag(0);
    }

    // L2JMOD Wedding
    @Getter
    @Setter
    private boolean married = false;

    @Getter
    @Setter
    private int partnerId = 0;

    @Getter
    @Setter
    private int coupleId = 0;

    @Getter
    private boolean engageRequest = false;

    @Getter
    private int engageId = 0;

    @Getter
    @Setter
    private boolean marryRequest = false;

    @Getter
    @Setter
    private boolean marryAccepted = false;

    // Item Mall
    private static final String GAME_POINTS_VAR = "PRIME_POINTS"; // Keep compatibility with later clients.

    // Save responder name for log it.
    @Getter
    @Setter
    private String lastPetitionGmName = null;

    private boolean hasCharmOfCourage = false;

    @Getter
    private final AutoPlaySettingsHolder autoPlaySettings = new AutoPlaySettingsHolder();

    @Getter
    private final AutoUseSettingsHolder autoUseSettings = new AutoUseSettingsHolder();

    private final AtomicBoolean autoPlaying = new AtomicBoolean();

    private final List<QuestTimer> questTimers = new ArrayList<>();
    private final List<TimerHolder<?>> timerHolders = new ArrayList<>();

    // Selling buffs system
    @Setter
    private boolean sellingBuffs = false;

    private List<SellBuffHolder> sellBuffs = null;

    public boolean isSellingBuffs() {
        return sellingBuffs;
    }

    public List<SellBuffHolder> getSellingBuffs() {
        if (sellBuffs == null) {
            sellBuffs = new ArrayList<>();
        }

        return sellBuffs;
    }

    /**
     * Create a new Player and add it in the characters table of the database.<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Create a new Player with an account name</li>
     * <li>Set the name, the Hair Style, the Hair Color and the Face type of the Player</li>
     * <li>Add the player in the characters table of the database</li>
     * </ul>
     * @param template The PlayerTemplate to apply to the Player
     * @param accountName The name of the Player
     * @param name The name of the Player
     * @param app the player's appearance
     * @return The Player added to the database or null
     */
    public static Player create(PlayerTemplate template, String accountName, String name, PlayerAppearance app) {
        // Create a new Player with an account name.
        final Player player = new Player(template, accountName, app);

        // Set the name of the Player.
        player.setName(name);

        // Set Character's create time.
        player.setCreateDate(ClockBridge.calendar());

        // Set the base class ID to that of the actual class ID.
        player.setBaseClass(player.getPlayerClass());

        // Kept for backwards compatibility.
        player.setNewbie(1);

        // Give 20 recommendations.
        player.setRecomLeft(20);

        // Add the player in the characters table of the database.
        return player.createDb() ? player : null;
    }

    public String getAccountName() {
        return client == null ? accountNamePlayer : client.getAccountName();
    }

    public int getRelation(Player target) {
        int result = 0;
        if (clan != null) {
            result |= RelationChanged.RELATION_CLAN_MEMBER;
            if (getClan() == target.getClan()) {
                result |= RelationChanged.RELATION_CLAN_MATE;
            }

            if (getAllyId() != 0) {
                result |= RelationChanged.RELATION_ALLY_MEMBER;
            }
        }

        if (isClanLeader()) {
            result |= RelationChanged.RELATION_LEADER;
        }

        if ((getParty() != null) && (getParty() == target.getParty())) {
            result |= RelationChanged.RELATION_HAS_PARTY;
            for (int i = 0; i < party.getMembers().size(); i++) {
                if (party.getMembers().get(i) != this) {
                    continue;
                }

                switch (i) {
                    case 0: {
                        result |= RelationChanged.RELATION_PARTYLEADER; // 0x10
                        break;
                    }
                    case 1: {
                        result |= RelationChanged.RELATION_PARTY4; // 0x8
                        break;
                    }
                    case 2: {
                        result |= RelationChanged.RELATION_PARTY3
                                + RelationChanged.RELATION_PARTY2
                                + RelationChanged.RELATION_PARTY1; // 0x7
                        break;
                    }
                    case 3: {
                        result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY2; // 0x6
                        break;
                    }
                    case 4: {
                        result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY1; // 0x5
                        break;
                    }
                    case 5: {
                        result |= RelationChanged.RELATION_PARTY3; // 0x4
                        break;
                    }
                    case 6: {
                        result |= RelationChanged.RELATION_PARTY2 + RelationChanged.RELATION_PARTY1; // 0x3
                        break;
                    }
                    case 7: {
                        result |= RelationChanged.RELATION_PARTY2; // 0x2
                        break;
                    }
                    case 8: {
                        result |= RelationChanged.RELATION_PARTY1; // 0x1
                        break;
                    }
                }
            }
        }

        if (siegeState != 0) {
            if (TerritoryWarManager.getInstance().getRegisteredTerritoryId(this) != 0) {
                result |= RelationChanged.RELATION_TERRITORY_WAR;
            } else {
                result |= RelationChanged.RELATION_INSIEGE;
                if (getSiegeState() != target.getSiegeState()) {
                    result |= RelationChanged.RELATION_ENEMY;
                } else {
                    result |= RelationChanged.RELATION_ALLY;
                }

                if (siegeState == 1) {
                    result |= RelationChanged.RELATION_ATTACKER;
                }
            }
        }

        if ((getClan() != null) && (target.getClan() != null)) {
            if ((target.getPledgeType() != Clan.SUBUNIT_ACADEMY)
                    && (getPledgeType() != Clan.SUBUNIT_ACADEMY)
                    && target.getClan().isAtWarWith(getClan().getId())) {
                result |= RelationChanged.RELATION_1SIDED_WAR;
                if (getClan().isAtWarWith(target.getClan().getId())) {
                    result |= RelationChanged.RELATION_MUTUAL_WAR;
                }
            }
        }

        if (blockCheckerArena != -1) {
            result |= RelationChanged.RELATION_INSIEGE;
            final ArenaParticipantsHolder holder =
                    HandysBlockCheckerManager.getInstance().getHolder(getBlockCheckerArena());
            if (holder.getPlayerTeam(this) == 0) {
                result |= RelationChanged.RELATION_ENEMY;
            } else {
                result |= RelationChanged.RELATION_ALLY;
            }

            result |= RelationChanged.RELATION_ATTACKER;
        }

        return result;
    }

    /**
     * Retrieve a Player from the characters table of the database and add it in _allObjects of the L2world (call restore method).<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Retrieve the Player from the characters table of the database</li>
     * <li>Add the Player object in _allObjects</li>
     * <li>Set the x,y,z position of the Player and make it invisible</li>
     * <li>Update the overloaded status of the Player</li>
     * </ul>
     * @param objectId Identifier of the object to initialized
     * @return The Player loaded from the database
     */
    public static Player load(int objectId) {
        return restore(objectId);
    }

    private void initPcStatusUpdateValues() {
        cpUpdateInterval = getMaxCp() / 352.0;
        cpUpdateIncCheck = getMaxCp();
        cpUpdateDecCheck = getMaxCp() - cpUpdateInterval;
        mpUpdateInterval = getMaxMp() / 352.0;
        mpUpdateIncCheck = getMaxMp();
        mpUpdateDecCheck = getMaxMp() - mpUpdateInterval;
    }

    @Override
    public PlayerStat getStat() {
        return (PlayerStat) super.getStat();
    }

    @Override
    public void initCharStat() {
        setStat(new PlayerStat(this));
    }

    @Override
    public PlayerStatus getStatus() {
        return (PlayerStatus) super.getStatus();
    }

    @Override
    public void initCharStatus() {
        setStatus(new PlayerStatus(this));
    }

    /**
     * @return the base PlayerTemplate link to the Player.
     */
    public PlayerTemplate getBaseTemplate() {
        return PlayerTemplateData.getInstance().getTemplate(baseClass);
    }

    /**
     * @return the PlayerTemplate link to the Player.
     */
    @Override
    public PlayerTemplate getTemplate() {
        return (PlayerTemplate) super.getTemplate();
    }

    /**
     * @param newclass
     */
    public void setTemplate(PlayerClass newclass) {
        super.setTemplate(PlayerTemplateData.getInstance().getTemplate(newclass));
    }

    @Override
    protected CreatureAI initAI() {
        return new PlayerAI(this);
    }

    /** Return the Level of the Player. */
    @Override
    public int getLevel() {
        return getStat().getLevel();
    }

    @Override
    public double getLevelMod() {
        if (isTransformed()) {
            final double levelMod = transformation.getLevelMod(this);
            if (levelMod > -1) {
                return levelMod;
            }
        }

        return super.getLevelMod();
    }

    public void setBaseClass(int baseClass) {
        this.baseClass = baseClass;
    }

    public void setBaseClass(PlayerClass playerClass) {
        baseClass = playerClass.getId();
    }

    public boolean isInStoreMode() {
        return privateStoreType != PrivateStoreType.NONE;
    }

    /**
     * @return a table containing all Common RecipeList of the Player.
     */
    public Collection<RecipeList> getCommonRecipeBook() {
        return commonRecipeBook.values();
    }

    /**
     * @return a table containing all Dwarf RecipeList of the Player.
     */
    public Collection<RecipeList> getDwarvenRecipeBook() {
        return dwarvenRecipeBook.values();
    }

    /**
     * Add a new RecipList to the table _commonrecipebook containing all RecipeList of the Player
     * @param recipe The RecipeList to add to the _recipebook
     * @param saveToDb
     */
    public void registerCommonRecipeList(RecipeList recipe, boolean saveToDb) {
        commonRecipeBook.put(recipe.getId(), recipe);

        if (saveToDb) {
            insertNewRecipeData(recipe.getId(), false);
        }
    }

    /**
     * Add a new RecipList to the table _recipebook containing all RecipeList of the Player
     * @param recipe The RecipeList to add to the _recipebook
     * @param saveToDb
     */
    public void registerDwarvenRecipeList(RecipeList recipe, boolean saveToDb) {
        dwarvenRecipeBook.put(recipe.getId(), recipe);

        if (saveToDb) {
            insertNewRecipeData(recipe.getId(), true);
        }
    }

    /**
     * @param recipeId The Identifier of the RecipeList to check in the player's recipe books
     * @return {@code true}if player has the recipe on Common or Dwarven Recipe book else returns {@code false}
     */
    public boolean hasRecipeList(int recipeId) {
        return dwarvenRecipeBook.containsKey(recipeId) || commonRecipeBook.containsKey(recipeId);
    }

    /**
     * Tries to remove a RecipList from the table _DwarvenRecipeBook or from table _CommonRecipeBook, those table contain all RecipeList of the Player
     * @param recipeId The Identifier of the RecipeList to remove from the _recipebook
     */
    public void unregisterRecipeList(int recipeId) {
        if (dwarvenRecipeBook.remove(recipeId) != null) {
            deleteRecipeData(recipeId, true);
        } else if (commonRecipeBook.remove(recipeId) != null) {
            deleteRecipeData(recipeId, false);
        } else {
            log.warn("Attempted to remove unknown RecipeList: {}", recipeId);
        }

        for (Shortcut sc : shortcuts.getAllShortcuts()) {
            if ((sc != null) && (sc.getId() == recipeId) && (sc.getType() == ShortcutType.RECIPE)) {
                deleteShortcut(sc.getSlot(), sc.getPage());
            }
        }
    }

    private void insertNewRecipeData(int recipeId, boolean isDwarf) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO character_recipebook (charId, id, classIndex, type) values(?,?,?,?)")) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, recipeId);
            ps.setInt(3, isDwarf ? classIndex : 0);
            ps.setInt(4, isDwarf ? 1 : 0);
            ps.execute();
        } catch (SQLException e) {
            log.warn("SQL exception while inserting recipe: {} from character {}", recipeId, getObjectId(), e);
        }
    }

    private void deleteRecipeData(int recipeId, boolean isDwarf) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM character_recipebook WHERE charId=? AND id=? AND classIndex=?")) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, recipeId);
            ps.setInt(3, isDwarf ? classIndex : 0);
            ps.execute();
        } catch (SQLException e) {
            log.warn("SQL exception while deleting recipe: {} from character {}", recipeId, getObjectId(), e);
        }
    }

    /**
     * @param quest The name of the quest
     * @return the QuestState object corresponding to the quest name.
     */
    public QuestState getQuestState(String quest) {
        return quests.get(quest);
    }

    /**
     * Add a QuestState to the table quest containing all quests began by the Player.
     * @param qs The QuestState to add to quest
     */
    public void setQuestState(QuestState qs) {
        quests.put(qs.getQuestName(), qs);
    }

    /**
     * Verify if the player has the quest state.
     * @param quest the quest state to check
     * @return {@code true} if the player has the quest state, {@code false} otherwise
     */
    public boolean hasQuestState(String quest) {
        return quests.containsKey(quest);
    }

    /**
     * Remove a QuestState from the table quest containing all quests began by the Player.
     * @param quest The name of the quest
     */
    public void delQuestState(String quest) {
        quests.remove(quest);
    }

    /**
     * Gets all the active quests.
     * @return a list of active quests
     */
    public List<Quest> getAllActiveQuests() {
        final List<Quest> quests = new ArrayList<>();
        for (QuestState qs : this.quests.values()) {
            if ((qs == null) || (qs.getQuest() == null) || !qs.isStarted()) {
                continue;
            }

            // Ignore other scripts.
            final int questId = qs.getQuest().getId();
            if ((questId > 19999) || (questId < 1)) {
                continue;
            }

            quests.add(qs.getQuest());
        }

        return quests;
    }

    public void processScriptEvent(String scriptName, String event) {
        final Quest quest = ScriptManager.getInstance().getScript(scriptName);
        if ((quest == null) || (event == null) || event.isEmpty()) {
            return;
        }

        final Npc target = lastFolkNPC;
        if ((target != null) && isInsideRadius2D(target, Npc.INTERACTION_DISTANCE)) {
            quest.notifyEvent(event, target, this);
        } else if (lastQuestNpcObject > 0) {
            final WorldObject object = World.findObject(getLastQuestNpcObject());
            if ((object != null) && object.isNpc() && isInsideRadius2D(object, Npc.INTERACTION_DISTANCE)) {
                final Npc npc = object.asNpc();
                quest.notifyEvent(event, npc, this);
            }
        }
    }

    /** List of all QuestState instance that needs to be notified of this Player's or its pet's death */
    @Getter
    private final Collection<QuestState> notifyQuestOfDeath = ConcurrentHashMap.newKeySet();

    /**
     * Add QuestState instance that is to be notified of Player's death.
     * @param qs The QuestState that subscribe to this event
     */
    public void addNotifyQuestOfDeath(QuestState qs) {
        if (qs == null) {
            return;
        }

        if (!notifyQuestOfDeath.contains(qs)) {
            notifyQuestOfDeath.add(qs);
        }
    }

    /**
     * Remove QuestState instance that is to be notified of Player's death.
     * @param qs The QuestState that subscribe to this event
     */
    public void removeNotifyQuestOfDeath(QuestState qs) {
        if (qs == null) {
            return;
        }

        notifyQuestOfDeath.remove(qs);
    }

    public boolean isNotifyQuestOfDeathEmpty() {
        return notifyQuestOfDeath.isEmpty();
    }

    /**
     * Restores all player shortcuts.
     */
    private void restoreShortcuts() {
        shortcuts.restoreMe();
    }

    /**
     * @return a collection containing all Shortcut of the Player.
     */
    public Collection<Shortcut> getAllShortcuts() {
        return shortcuts.getAllShortcuts();
    }

    /**
     * @param slot The slot in which the shortcuts is equipped
     * @param page The page of shortcuts containing the slot
     * @return the Shortcut of the Player corresponding to the position (page-slot).
     */
    public Shortcut getShortcut(int slot, int page) {
        return shortcuts.getShortcut(slot, page);
    }

    /**
     * Add a Shortcut to the Player shortcuts.
     * @param shortcut
     */
    public void registerShortcut(Shortcut shortcut) {
        shortcuts.registerShortcut(shortcut);
    }

    /**
     * Updates the shortcut bars with the new skill.
     * @param skillId the skill Id to search and update.
     * @param skillLevel the skill level to update.
     */
    public void updateShortcuts(int skillId, int skillLevel) {
        shortcuts.updateShortcuts(skillId, skillLevel);
    }

    /**
     * Delete the Shortcut corresponding to the position (page-slot) from the Player shortcuts.
     * @param slot
     * @param page
     */
    public void deleteShortcut(int slot, int page) {
        shortcuts.deleteShortcut(slot, page);
    }

    /**
     * @param macro the macro to add to this Player.
     */
    public void registerMacro(Macro macro) {
        macros.registerMacro(macro);
    }

    /**
     * @param id the macro Id to delete.
     */
    public void deleteMacro(int id) {
        macros.deleteMacro(id);
    }

    public boolean isRegisteredOnThisSiegeField(int value) {
        return (siegeSide == value) || ((siegeSide >= 81) && (siegeSide <= 89));
    }

    public boolean isSiegeFriend(WorldObject target) {
        // If i'm natural or not in siege zone, not friends.
        if ((siegeState == 0) || !isInsideZone(ZoneId.SIEGE)) {
            return false;
        }

        final Castle castle = CastleManager.getInstance().getCastleById(siegeSide);
        if (castle == null) {
            return false;
        }

        // If target isn't a player, is self.
        final Player targetPlayer = target.asPlayer();
        if ((targetPlayer == null) || (targetPlayer == this)) {
            return false;
        }

        // If target isn't on same siege or not on same state, not friends.
        if ((targetPlayer.getSiegeSide() != siegeSide) || (siegeState != targetPlayer.getSiegeState())) {
            return false;
        }

        if (siegeState == 1) {
            // Check first castle mid victory.
            if (!castle.isFirstMidVictory() && (siegeState == targetPlayer.getSiegeState())) {
                return true;
            }

            // Attackers are considered friends only if castle has no owner.
            return castle.getOwner() == null;
        }

        // Both are defenders, friends.
        return true;
    }

    /**
     * Set the PvP Flag of the Player.
     * @param pvpFlag
     */
    public void setPvpFlag(int pvpFlag) {
        this.pvpFlag = (byte) pvpFlag;
    }

    @Override
    public void updatePvPFlag(int value) {
        if (pvpFlag == value) {
            return;
        }

        setPvpFlag(value);

        updateUserInfo();

        // If this player has a pet update the pets pvp flag as well.
        if (hasSummon()) {
            sendPacket(new RelationChanged(summon, getRelation(this), false));
        }

        World.forEachVisibleObject(this, Player.class, target -> {
            target.sendPacket(new RelationChanged(this, getRelation(target), isAutoAttackable(target)));
            if (hasSummon()) {
                target.sendPacket(new RelationChanged(summon, getRelation(target), isAutoAttackable(target)));
            }
        });
    }

    @Override
    public void revalidateZone(boolean force) {
        // Cannot validate if not in a world region (happens during teleport).
        if (getWorldRegion() == null) {
            return;
        }

        // This function is called too often from movement code.
        if (!force && (calculateDistance3D(lastZoneValidateLocation) < 100)) {
            return;
        }

        lastZoneValidateLocation.setXYZ(this);

        ZoneManager.getInstance().getRegion(this).revalidateZones(this);

        if (GeneralConfig.ALLOW_WATER) {
            checkWaterState();
        }

        if (isInsideZone(ZoneId.ALTERED)) {
            if (lastCompassZone == ExSetCompassZoneCode.ALTEREDZONE) {
                return;
            }

            lastCompassZone = ExSetCompassZoneCode.ALTEREDZONE;
            sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.ALTEREDZONE));
        } else if (isInsideZone(ZoneId.SIEGE)) {
            if (lastCompassZone == ExSetCompassZoneCode.SIEGEWARZONE2) {
                return;
            }

            lastCompassZone = ExSetCompassZoneCode.SIEGEWARZONE2;
            sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.SIEGEWARZONE2));
        } else if (isInsideZone(ZoneId.PVP)) {
            if (lastCompassZone == ExSetCompassZoneCode.PVPZONE) {
                return;
            }

            lastCompassZone = ExSetCompassZoneCode.PVPZONE;
            sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.PVPZONE));
        } else if (in7sDungeon) {
            if (lastCompassZone == ExSetCompassZoneCode.SEVENSIGNSZONE) {
                return;
            }

            lastCompassZone = ExSetCompassZoneCode.SEVENSIGNSZONE;
            sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.SEVENSIGNSZONE));
        } else if (isInsideZone(ZoneId.PEACE)) {
            if (lastCompassZone == ExSetCompassZoneCode.PEACEZONE) {
                return;
            }

            lastCompassZone = ExSetCompassZoneCode.PEACEZONE;
            sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.PEACEZONE));
        } else if (isInsideZone(ZoneId.NO_PVP)) {
            if (lastCompassZone == ExSetCompassZoneCode.PEACEZONE) {
                return;
            }

            lastCompassZone = ExSetCompassZoneCode.PEACEZONE;
            sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.PEACEZONE));
        } else {
            if (lastCompassZone == ExSetCompassZoneCode.GENERALZONE) {
                return;
            }

            if (lastCompassZone == ExSetCompassZoneCode.SIEGEWARZONE2) {
                updatePvPStatus();
            }

            lastCompassZone = ExSetCompassZoneCode.GENERALZONE;
            sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.GENERALZONE));
        }
    }

    /**
     * @return True if the Player can Craft Dwarven Recipes.
     */
    public boolean hasDwarvenCraft() {
        return getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()) >= 1;
    }

    public int getDwarvenCraft() {
        return getSkillLevel(CommonSkill.CREATE_DWARVEN.getId());
    }

    /**
     * @return True if the Player can Craft Dwarven Recipes.
     */
    public boolean hasCommonCraft() {
        return getSkillLevel(CommonSkill.CREATE_COMMON.getId()) >= 1;
    }

    public int getCommonCraft() {
        return getSkillLevel(CommonSkill.CREATE_COMMON.getId());
    }

    /**
     * Set the PK counter of the Player.
     * @param pkKills
     */
    public void setPkKills(int pkKills) {
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_PK_CHANGED, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerPKChanged.builder()
                                    .player(this)
                                    .oldPoints(this.pkKills)
                                    .newPoints(pkKills)
                                    .build(),
                            this);
        }

        this.pkKills = pkKills;
    }

    /**
     * Increment the number of recommendation obtained by the Player (Max : 255).
     */
    protected void incRecomHave() {
        if (recomHave < 255) {
            recomHave++;
        }
    }

    /**
     * Set the number of recommendation obtained by the Player (Max : 255).
     * @param value
     */
    public void setRecomHave(int value) {
        recomHave = Math.clamp(value, 0, 255);
    }

    /**
     * Set the number of recommendation obtained by the Player (Max : 255).
     * @param value
     */
    public void setRecomLeft(int value) {
        recomLeft = Math.clamp(value, 0, 255);
    }

    /**
     * Increment the number of recommendation that the Player can give.
     */
    protected void decRecomLeft() {
        if (recomLeft > 0) {
            recomLeft--;
        }
    }

    public void giveRecom(Player target) {
        target.incRecomHave();
        decRecomLeft();
    }

    /**
     * Set the Karma of the Player and send a Server->Client packet StatusUpdate (broadcast).
     * @param value
     */
    @Override
    public void setKarma(int value) {
        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_KARMA_CHANGED, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerKarmaChanged.builder()
                                    .player(this)
                                    .oldKarma(getKarma())
                                    .newKarma(value)
                                    .build(),
                            this);
        }

        int karma = value;
        if (karma < 0) {
            karma = 0;
        }

        if ((getKarma() == 0) && (karma > 0)) {
            World.forEachVisibleObject(this, Guard.class, object -> {
                if (object.getAI().getIntention() == Intention.IDLE) {
                    object.getAI().setIntentionActive();
                }
            });
        } else if ((getKarma() > 0) && (karma == 0)) {
            // Send a Server->Client StatusUpdate packet with Karma and PvP Flag to the Player and all Player to inform
            // (broadcast).
            setKarmaFlag();
        }

        super.setKarma(karma);
        broadcastKarma();
    }

    public int getWeightPenalty() {
        return dietMode ? 0 : curWeightPenalty;
    }

    /**
     * Update the overloaded status of the Player.
     */
    public void refreshOverloaded() {
        final int maxLoad = getMaxLoad();
        if (maxLoad > 0) {
            final long weightproc = ((getCurrentLoad() - getBonusWeightPenalty()) * 1000) / getMaxLoad();
            int newWeightPenalty;
            if ((weightproc < 500) || dietMode) {
                newWeightPenalty = 0;
            } else if (weightproc < 666) {
                newWeightPenalty = 1;
            } else if (weightproc < 800) {
                newWeightPenalty = 2;
            } else if (weightproc < 1000) {
                newWeightPenalty = 3;
            } else {
                newWeightPenalty = 4;
            }

            if (curWeightPenalty != newWeightPenalty) {
                curWeightPenalty = newWeightPenalty;
                if ((newWeightPenalty > 0) && !dietMode) {
                    addSkill(SkillData.getInstance().getSkill(4270, newWeightPenalty));
                    setOverloaded(getCurrentLoad() > maxLoad);
                } else {
                    removeSkill(getKnownSkill(4270), false, true);
                    setOverloaded(false);
                }

                broadcastUserInfo();
                sendPacket(new EtcStatusUpdate(this));
            }
        }
    }

    public void refreshExpertisePenalty() {
        if (!PlayerConfig.EXPERTISE_PENALTY) {
            return;
        }

        final int expertiseLevel = getExpertiseLevel();
        int armorPenalty = 0;
        int weaponPenalty = 0;
        int crystaltype;
        for (Item item : inventory.getItems()) {
            if ((item != null)
                    && item.isEquipped()
                    && (item.getItemType() != EtcItemType.ARROW)
                    && (item.getItemType() != EtcItemType.BOLT)) {
                crystaltype = item.getTemplate().getCrystalType().getLevel();
                if (crystaltype > expertiseLevel) {
                    if (item.isWeapon() && (crystaltype > weaponPenalty)) {
                        weaponPenalty = crystaltype;
                    } else if (crystaltype > armorPenalty) {
                        armorPenalty = crystaltype;
                    }
                }
            }
        }

        boolean changed = false;

        // calc weapon penalty
        weaponPenalty = weaponPenalty - expertiseLevel - expertisePenaltyBonus;
        weaponPenalty = Math.clamp(weaponPenalty, 0, 4);
        if ((expertiseWeaponPenalty != weaponPenalty)
                || (getSkillLevel(CommonSkill.WEAPON_GRADE_PENALTY.getId()) != weaponPenalty)) {
            expertiseWeaponPenalty = weaponPenalty;
            if (expertiseWeaponPenalty > 0) {
                addSkill(SkillData.getInstance()
                        .getSkill(CommonSkill.WEAPON_GRADE_PENALTY.getId(), expertiseWeaponPenalty));
            } else {
                removeSkill(getKnownSkill(CommonSkill.WEAPON_GRADE_PENALTY.getId()), false, true);
            }

            changed = true;
        }

        // calc armor penalty
        armorPenalty = armorPenalty - expertiseLevel - expertisePenaltyBonus;
        armorPenalty = Math.clamp(armorPenalty, 0, 4);
        if ((expertiseArmorPenalty != armorPenalty)
                || (getSkillLevel(CommonSkill.ARMOR_GRADE_PENALTY.getId()) != armorPenalty)) {
            expertiseArmorPenalty = armorPenalty;
            if (expertiseArmorPenalty > 0) {
                addSkill(SkillData.getInstance()
                        .getSkill(CommonSkill.ARMOR_GRADE_PENALTY.getId(), expertiseArmorPenalty));
            } else {
                removeSkill(getKnownSkill(CommonSkill.ARMOR_GRADE_PENALTY.getId()), false, true);
            }

            changed = true;
        }

        if (changed) {
            sendPacket(new EtcStatusUpdate(this));
        }
    }

    public void useEquippableItem(Item item, boolean abortAttack) {
        // Check if the item is null.
        if (item == null) {
            return;
        }

        // Check if the item is owned by this player.
        if (item.getOwnerId() != getObjectId()) {
            return;
        }

        // Check if the item is in the inventory.
        final ItemLocation itemLocation = item.getItemLocation();
        if ((itemLocation != ItemLocation.INVENTORY) && (itemLocation != ItemLocation.PAPERDOLL)) {
            return;
        }

        // Equip or unEquip.
        Collection<Item> items = null;
        final boolean isEquiped = item.isEquipped();
        final int oldInvLimit = getInventoryLimit();
        SystemMessage sm = null;
        if (isEquiped) {
            final BodyPart bodyPart = BodyPart.fromItem(item);
            if (item.isEnchanted()) {
                sm = new SystemMessage(SystemMessageId.THE_EQUIPMENT_S1_S2_HAS_BEEN_REMOVED);
                sm.addInt(item.getEnchantLevel());
            } else {
                sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_DISARMED);
            }

            sm.addItemName(item);
            sendPacket(sm);

            // We can't unequip talisman by body slot.
            if (bodyPart == BodyPart.DECO) {
                items = inventory.unEquipItemInSlotAndRecord(item.getLocationSlot());
            } else {
                items = inventory.unEquipItemInBodySlotAndRecord(bodyPart);
            }
        } else {
            items = inventory.equipItemAndRecord(item);
            if (item.isEquipped()) {
                final BodyPart bodyPart = item.getTemplate().getBodyPart();
                if (item.isEnchanted()) {
                    sm = new SystemMessage(SystemMessageId.EQUIPPED_S1_S2);
                    sm.addInt(item.getEnchantLevel());
                } else {
                    sm = new SystemMessage(SystemMessageId.YOU_HAVE_EQUIPPED_YOUR_S1);
                }

                sm.addItemName(item);
                sendPacket(sm);

                // Consume mana - will start a task if required; returns if item is not a shadow item.
                item.decreaseMana(false);

                if ((bodyPart == BodyPart.R_HAND) || (bodyPart == BodyPart.LR_HAND)) {
                    rechargeShots(true, true);
                }

                // Notify to scripts.
                if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_EQUIP, item.getTemplate())) {
                    EventDispatcher.getInstance()
                            .notifyEventAsync(new OnPlayerItemEquip(this, item), item.getTemplate());
                }
            } else {
                sendPacket(SystemMessageId.YOU_DO_NOT_MEET_THE_REQUIRED_CONDITION_TO_EQUIP_THAT_ITEM);
            }
        }

        refreshExpertisePenalty();
        broadcastUserInfo();

        final InventoryUpdate iu = new InventoryUpdate();
        iu.addItems(items);
        sendInventoryUpdate(iu);

        if (abortAttack) {
            abortAttack();
        }

        if (getInventoryLimit() != oldInvLimit) {
            sendPacket(new ExStorageMaxCount(this));
        }
    }

    /**
     * Set the the PvP Kills of the Player (Number of player killed during a PvP).
     * @param pvpKills
     */
    public void setPvpKills(int pvpKills) {
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_PVP_CHANGED, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerPvPChanged.builder()
                                    .player(this)
                                    .oldPoints(this.pvpKills)
                                    .newPoints(pvpKills)
                                    .build(),
                            this);
        }

        this.pvpKills = pvpKills;
    }

    /**
     * Set the Fame of this PlayerInstane
     * @param fame
     */
    public void setFame(int fame) {
        int newFame = fame;
        if (fame > PlayerConfig.MAX_PERSONAL_FAME_POINTS) {
            newFame = PlayerConfig.MAX_PERSONAL_FAME_POINTS;
        } else if (fame < 0) {
            newFame = 0;
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_FAME_CHANGED, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerFameChanged.builder()
                                    .player(this)
                                    .oldFame(this.fame)
                                    .newFame(newFame)
                                    .build(),
                            this);
        }

        this.fame = newFame;
    }

    /**
     * @return the PlayerClass of the Player contained in PlayerTemplate.
     */
    public PlayerClass getPlayerClass() {
        return getTemplate().getPlayerClass();
    }

    /**
     * Set the template of the Player.
     * @param id The Identifier of the PlayerTemplate to set to the Player
     */
    public void setPlayerClass(int id) {
        if (changingClass) {
            return;
        }

        changingClass = true;

        try {
            if ((lvlJoinedAcademy != 0)
                    && (clan != null)
                    && CategoryData.getInstance().isInCategory(CategoryType.THIRD_CLASS_GROUP, id)) {
                if (lvlJoinedAcademy <= 16) {
                    clan.addReputationScore(FeatureConfig.JOIN_ACADEMY_MAX_REP_SCORE);
                } else if (lvlJoinedAcademy >= 39) {
                    clan.addReputationScore(FeatureConfig.JOIN_ACADEMY_MIN_REP_SCORE);
                } else {
                    clan.addReputationScore(FeatureConfig.JOIN_ACADEMY_MAX_REP_SCORE - ((lvlJoinedAcademy - 16) * 20));
                }

                setLvlJoinedAcademy(0);

                // Oust pledge member from the academy, cuz he has finished his 2nd class transfer.
                final SystemMessage msg = new SystemMessage(SystemMessageId.CLAN_MEMBER_S1_HAS_BEEN_EXPELLED);
                msg.addPcName(this);
                clan.broadcastToOnlineMembers(msg);
                clan.broadcastToOnlineMembers(new PledgeShowMemberListDelete(getName()));
                clan.removeClanMember(getObjectId(), 0);
                sendPacket(
                        SystemMessageId
                                .CONGRATULATIONS_YOU_WILL_NOW_GRADUATE_FROM_THE_CLAN_ACADEMY_AND_LEAVE_YOUR_CURRENT_CLAN_AS_A_GRADUATE_OF_THE_ACADEMY_YOU_CAN_IMMEDIATELY_JOIN_A_CLAN_AS_A_REGULAR_MEMBER_WITHOUT_BEING_SUBJECT_TO_ANY_PENALTIES);

                // receive graduation gift
                inventory.addItem(ItemProcessType.REWARD, 8181, 1, this, null); // give academy circlet
            }

            if (isSubClassActive()) {
                getSubClasses().get(classIndex).setPlayerClass(id);
            }

            setTarget(this);
            broadcastSkillPacket(new MagicSkillUse(this, 5103, 1, 0, 0), this);
            setClassTemplate(id);
            if (getPlayerClass().level() == 3) {
                sendPacket(SystemMessageId.CONGRATULATIONS_YOU_VE_COMPLETED_YOUR_THIRD_CLASS_TRANSFER_QUEST);
            } else {
                sendPacket(SystemMessageId.CONGRATULATIONS_YOU_VE_COMPLETED_A_CLASS_TRANSFER);
            }

            // Remove class permitted hennas.
            for (int slot = 1; slot < 4; slot++) {
                final Henna henna = getHenna(slot);
                if ((henna != null) && !henna.isAllowedClass(getPlayerClass())) {
                    removeHenna(slot);
                }
            }

            // Update class icon in party and clan.
            if (isInParty()) {
                party.broadcastPacket(new PartySmallWindowUpdate(this));
            }

            if (clan != null) {
                clan.broadcastToOnlineMembers(new PledgeShowMemberListUpdate(this));
            }

            // Add AutoGet skills and normal skills and/or learnByFS depending on configurations.
            rewardSkills();

            if (!isGM() && PlayerConfig.DECREASE_SKILL_LEVEL) {
                checkPlayerSkills();
            }
        } finally {
            changingClass = false;

            ThreadPool.schedule(
                    () -> {
                        getInventory().applyItemSkills();
                        sendSkillList();
                    },
                    100);
        }
    }

    /**
     * Used for AltGameSkillLearn to set a custom skill learning class Id.
     */
    @Getter
    @Setter
    private PlayerClass learningClass = getPlayerClass();

    /**
     * @return the Experience of the Player.
     */
    public long getExp() {
        return getStat().getExp();
    }

    public void setActiveEnchantItemId(int objectId) {
        // If we don't have a Enchant Item, we are not enchanting.
        if (objectId == ID_NONE) {
            setActiveEnchantSupportItemId(ID_NONE);
            setActiveEnchantTimestamp(0);
            setEnchanting(false);
        }

        activeEnchantItemId = objectId;
    }

    /**
     * Returns the appropriate fist weapon template for a given class ID.<br>
     * Fist weapons are used as default weapons when a player has no weapon equipped.
     * @param classId the class identifier for this Player
     * @return the Weapon template for the fist weapon corresponding to the class ID.
     */
    public Weapon findFistsWeaponItem(int classId) {
        final Weapon weaponItem;

        if ((classId >= 0x00) && (classId <= 0x09)) {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(246 /* Human Fighter Fist */);
        } else if ((classId >= 0x0a) && (classId <= 0x11)) {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(251 /* Human Mystic Fist */);
        } else if ((classId >= 0x12) && (classId <= 0x18)) {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(244 /* Elven Fighter Fist */);
        } else if ((classId >= 0x19) && (classId <= 0x1e)) {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(249 /* Elven Mystic Fist */);
        } else if ((classId >= 0x1f) && (classId <= 0x25)) {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(245 /* Dark Fighter Fist */);
        } else if ((classId >= 0x26) && (classId <= 0x2b)) {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(250 /* Dark Mystic Fist */);
        } else if ((classId >= 0x2c) && (classId <= 0x30)) {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(248 /* Orc Fighter Fist */);
        } else if ((classId >= 0x31) && (classId <= 0x34)) {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(252 /* Orc Shaman Fist */);
        } else if ((classId >= 0x35) && (classId <= 0x39)) {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(247 /* Dwarven Fighter Fist */);
        } else // Default fist item to avoid NPEs.
        {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(246 /* Human Fighter Fist */);
        }

        return weaponItem;
    }

    /**
     * This method reward all AutoGet skills and Normal skills if Auto-Learn configuration is true.
     */
    public void rewardSkills() {
        // Give all normal skills if activated Auto-Learn is activated, included AutoGet skills.
        if (PlayerConfig.AUTO_LEARN_SKILLS) {
            giveAvailableSkills(PlayerConfig.AUTO_LEARN_FS_SKILLS, true, PlayerConfig.AUTO_LEARN_SKILLS_WITHOUT_ITEMS);
        } else {
            giveAvailableAutoGetSkills();
        }

        if (PlayerConfig.DECREASE_SKILL_LEVEL && !isGM()) {
            checkPlayerSkills();
        }

        if (FreeMountsConfig.ENABLE_FREE_STRIDER) {
            final Skill skill = SkillData.getInstance().getSkill(49991, 1);
            if (skill != null) {
                addSkill(skill, false);
            }
        }

        if (FreeMountsConfig.ENABLE_FREE_WYVERN) {
            final Skill skill = SkillData.getInstance().getSkill(49992, 1);
            if (skill != null) {
                addSkill(skill, false);
            }
        }

        if (RebirthConfig.REBIRTH_ALLOW_REBIRTH) {
            RebirthManager.getInstance().grantRebirthSkills(this);
        }

        checkItemRestriction();
        sendSkillList();
    }

    /**
     * Re-give all skills which aren't saved to database, like Noble, Hero, Clan Skills.
     */
    public void regiveTemporarySkills() {
        // Do not call this on enterworld or char load.

        // Add noble skills if noble.
        if (noble) {
            setNoble(true);
        }

        // Add Hero skills if hero.
        if (hero) {
            setHero(true);
        }

        // Add clan skills.
        if (clan != null) {
            clan.addSkillEffects(this);

            if ((clan.getLevel() >= SiegeManager.getInstance().getSiegeClanMinLevel()) && isClanLeader()) {
                SiegeManager.getInstance().addSiegeSkills(this);
            }

            if (clan.getCastleId() > 0) {
                final Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
                if (castle != null) {
                    castle.giveResidentialSkills(this);
                }
            }

            if (clan.getFortId() > 0) {
                final Fort fort = FortManager.getInstance().getFortByOwner(clan);
                if (fort != null) {
                    fort.giveResidentialSkills(this);
                }
            }
        }

        if (RebirthConfig.REBIRTH_ALLOW_REBIRTH) {
            RebirthManager.getInstance().grantRebirthSkills(this);
        }

        // Reload passive skills from armors / jewels / weapons.
        getInventory().reloadEquippedItems();

        // Add Death Penalty Buff Level.
        restoreDeathPenaltyBuffLevel();
    }

    /**
     * Give all available skills to the player.
     * @param includeByFs if {@code true} forgotten scroll skills present in the skill tree will be added
     * @param includeAutoGet if {@code true} auto-get skills present in the skill tree will be added
     * @param includeRequiredItems if {@code true} skills that have required items will be added
     * @return the amount of new skills earned
     */
    public int giveAvailableSkills(boolean includeByFs, boolean includeAutoGet, boolean includeRequiredItems) {
        int skillCounter = 0;

        // Get available skills.
        final Collection<Skill> skills = SkillTreeData.getInstance()
                .getAllAvailableSkills(this, getPlayerClass(), includeByFs, includeAutoGet, includeRequiredItems);
        final List<Skill> skillsForStore = new ArrayList<>();
        for (Skill skill : skills) {
            if (getKnownSkill(skill.getId()) == skill) {
                continue;
            }

            if (getSkillLevel(skill.getId()) == 0) {
                skillCounter++;
            }

            // Fix when learning toggle skills.
            if (skill.isToggle() && isAffectedBySkill(skill.getId())) {
                stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
            }

            addSkill(skill, false);
            skillsForStore.add(skill);

            if (PlayerConfig.AUTO_LEARN_SKILLS) {
                updateShortcuts(skill.getId(), skill.getLevel());
            }
        }

        storeSkills(skillsForStore, -1);

        if (PlayerConfig.AUTO_LEARN_SKILLS && (skillCounter > 0)) {
            sendPacket(new ShortcutInit(this));
            sendMessage("You have learned " + skillCounter + " new skills.");
        }

        return skillCounter;
    }

    /**
     * Give all available auto-get skills to the player.
     */
    public void giveAvailableAutoGetSkills() {
        // Get available skills.
        final List<SkillLearn> autoGetSkills = SkillTreeData.getInstance().getAvailableAutoGetSkills(this);
        final SkillData st = SkillData.getInstance();
        Skill skill;
        for (SkillLearn s : autoGetSkills) {
            skill = st.getSkill(s.getSkillId(), s.getSkillLevel());
            if (skill != null) {
                addSkill(skill, true);
            } else {
                log.warn("Skipping null auto-get skill for {}", this);
            }
        }
    }

    /**
     * Set the Experience value of the Player.
     * @param exp
     */
    public void setExp(long exp) {
        getStat().setExp(Math.max(0, exp));
    }

    /**
     * @return the Race object of the Player.
     */
    @Override
    public Race getRace() {
        if (!isSubClassActive()) {
            return getTemplate().getRace();
        }

        return PlayerTemplateData.getInstance().getTemplate(baseClass).getRace();
    }

    /**
     * @return the SP amount of the Player.
     */
    public long getSp() {
        return getStat().getSp();
    }

    /**
     * Set the SP amount of the Player.
     * @param sp
     */
    public void setSp(long sp) {
        super.getStat().setSp(Math.max(0, sp));
    }

    /**
     * @param castleId
     * @return true if this Player is a clan leader in ownership of the passed castle
     */
    public boolean isCastleLord(int castleId) {
        // Player has clan and is the clan leader, check the castle info.
        if ((clan != null) && (clan.getLeader().getPlayer() == this)) {
            // If the clan has a castle and it is actually the queried castle, return true.
            final Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
            if ((castle != null) && (castle == CastleManager.getInstance().getCastleById(castleId))) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the Clan Crest Identifier of the Player or 0.
     */
    public int getClanCrestId() {
        return clan != null ? clan.getCrestId() : 0;
    }

    /**
     * @return The Clan CrestLarge Identifier or 0
     */
    public int getClanCrestLargeId() {
        if ((clan != null) && ((clan.getCastleId() != 0) || (clan.getHideoutId() != 0))) {
            return clan.getCrestLargeId();
        }

        return 0;
    }

    public void setOnlineTime(long time) {
        onlineTime = time;
        onlineBeginTime = ClockBridge.gameMillis();
    }

    public int getOnlineTimeMillis() {
        return (int) (ClockBridge.gameMillis() - onlineBeginTime);
    }

    /**
     * Delete a Shortcut of the Player shortcuts.
     * @param objectId
     */
    public void removeItemFromShortcut(int objectId) {
        shortcuts.deleteShortcutByObjectId(objectId);
    }

    /**
     * Set _waitTypeSitting to given value.
     * @param value
     */
    public void setSitting(boolean value) {
        if (value) {
            // Notify to scripts.
            if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_SIT, this)) {
                EventDispatcher.getInstance().notifyEventAsync(new OnPlayerSit(this), this);
            }
        }

        sitting = value;
    }

    /**
     * Set sittingInProgress to given value.
     * @param value
     */
    public void setSittingProgress(boolean value) {
        sittingInProgress = value;
    }

    /**
     * Sit down the Player, set the AI Intention to REST and send a Server->Client ChangeWaitType packet (broadcast)
     */
    public void sitDown() {
        sitDown(true);
    }

    public void sitDown(boolean checkCast) {
        if (sittingInProgress) {
            return;
        }

        if (checkCast && isCastingNow()) {
            sendMessage("Cannot sit while casting");
            return;
        }

        if (sitting || isAttackDisabled() || isOutOfControl() || isImmobilized()) {
            return;
        }

        breakAttack();
        setSitting(true);
        setSittingProgress(true);
        getAI().setIntentionRest();
        broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_SITTING));

        // Schedule a sit down task to wait for the animation to finish.
        ThreadPool.schedule(new SitDownTask(this), 2500);
    }

    /**
     * Stand up the Player, set the AI Intention to IDLE and send a Server->Client ChangeWaitType packet (broadcast)
     */
    public void standUp() {
        if (sittingInProgress) {
            return;
        }

        if (sitting && !isInStoreMode() && !isAlikeDead()) {
            setSittingProgress(true);
            if (getEffectList().isAffected(EffectFlag.RELAXING)) {
                stopEffects(EffectType.RELAXING);
            }

            broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_STANDING));

            // Schedule a stand up task to wait for the animation to finish.
            ThreadPool.schedule(new StandUpTask(this), 2500);
        }
    }

    /**
     * @return true if refund list is not empty
     */
    public boolean hasRefund() {
        return (refund != null) && (refund.getSize() > 0) && GeneralConfig.ALLOW_REFUND;
    }

    /**
     * @return refund object or create new if not exist
     */
    public PlayerRefund getRefund() {
        if (refund == null) {
            refund = new PlayerRefund(this);
        }

        return refund;
    }

    /**
     * Clear refund
     */
    public void clearRefund() {
        if (refund != null) {
            refund.deleteMe();
        }

        refund = null;
    }

    /**
     * @return the Adena amount of the Player.
     */
    public long getAdena() {
        return inventory.getAdena();
    }

    /**
     * @return the Ancient Adena amount of the Player.
     */
    public long getAncientAdena() {
        return inventory.getAncientAdena();
    }

    /**
     * Add adena to Inventory of the Player and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param count : int Quantity of adena to be added
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     */
    public void addAdena(ItemProcessType process, long count, WorldObject reference, boolean sendMessage) {
        final long currentAdena = inventory.getAdena();
        final long limitRemaining = PlayerConfig.MAX_ADENA - currentAdena;
        final long amountToAdd = count;

        if (count > limitRemaining) {
            count = limitRemaining;
        }

        if (sendMessage) {
            if ((count == 0) && (amountToAdd > 0)) {
                // Failed adding adena.
                final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_EARN_S1_ADENA);
                sm.addLong(amountToAdd);
                sendPacket(sm);
            } else {
                // Inform player how much adena they received.
                final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S1_ADENA);
                sm.addLong(count);
                sendPacket(sm);
            }
        }

        if (count > 0) {
            inventory.addAdena(process, count, this, reference);

            // Send update packet.
            if (count == getAdena()) {
                sendItemList(false);
            } else {
                final InventoryUpdate iu = new InventoryUpdate();
                iu.addModifiedItem(inventory.getAdenaInstance());
                sendInventoryUpdate(iu);
            }
        }

        // If the player has reached the adena limit, inform them.
        if ((inventory.getAdena() == PlayerConfig.MAX_ADENA) && sendMessage) {
            sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_YOUR_OUT_OF_POCKET_ADENA_LIMIT);
        }
    }

    /**
     * Reduce adena in Inventory of the Player and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param count : long Quantity of adena to be reduced
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return boolean informing if the action was successful
     */
    public boolean reduceAdena(ItemProcessType process, long count, WorldObject reference, boolean sendMessage) {
        if (count > inventory.getAdena()) {
            if (sendMessage) {
                sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
            }

            return false;
        }

        if (count > 0) {
            // The destroyed item is kept, spending the whole stack clears the inventory adena instance.
            final Item adenaItem = inventory.destroyItemByItemId(process, Inventory.ADENA_ID, count, this, reference);
            if (adenaItem == null) {
                return false;
            }

            // Send update packet.
            final InventoryUpdate iu = new InventoryUpdate();
            if (adenaItem.getCount() > 0) {
                iu.addModifiedItem(adenaItem);
            } else {
                iu.addRemovedItem(adenaItem);
            }

            sendInventoryUpdate(iu);

            if (sendMessage) {
                final SystemMessage sm = new SystemMessage(SystemMessageId.S1_ADENA_DISAPPEARED);
                sm.addLong(count);
                sendPacket(sm);
            }
        }

        return true;
    }

    /**
     * Add ancient adena to Inventory of the Player and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param count : int Quantity of ancient adena to be added
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     */
    public void addAncientAdena(ItemProcessType process, long count, WorldObject reference, boolean sendMessage) {
        if (sendMessage) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S2_S1_S);
            sm.addItemName(Inventory.ANCIENT_ADENA_ID);
            sm.addLong(count);
            sendPacket(sm);
        }

        if (count <= 0) {
            return;
        }

        inventory.addAncientAdena(process, count, this, reference);

        final InventoryUpdate iu = new InventoryUpdate();
        iu.addItem(inventory.getAncientAdenaInstance());
        sendInventoryUpdate(iu);
    }

    /**
     * Reduce ancient adena in Inventory of the Player and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param count : long Quantity of ancient adena to be reduced
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return boolean informing if the action was successful
     */
    public boolean reduceAncientAdena(ItemProcessType process, long count, WorldObject reference, boolean sendMessage) {
        if (count > inventory.getAncientAdena()) {
            if (sendMessage) {
                sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
            }

            return false;
        }

        if (count > 0) {
            if (!inventory.reduceAncientAdena(process, count, this, reference)) {
                return false;
            }

            final Item ancientAdenaItem = inventory.getAncientAdenaInstance();
            final InventoryUpdate iu = new InventoryUpdate();
            iu.addItem(ancientAdenaItem);
            sendInventoryUpdate(iu);

            if (sendMessage) {
                final SystemMessage sm;
                if (count > 1) {
                    sm = new SystemMessage(SystemMessageId.S2_S1_HAS_DISAPPEARED);
                    sm.addItemName(Inventory.ANCIENT_ADENA_ID);
                    sm.addLong(count);
                } else {
                    sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                    sm.addItemName(Inventory.ANCIENT_ADENA_ID);
                }

                sendPacket(sm);
            }
        }

        return true;
    }

    /**
     * Adds item to inventory and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param item : Item to be added
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     */
    public void addItem(ItemProcessType process, Item item, WorldObject reference, boolean sendMessage) {
        if (item.getCount() > 0) {
            // Sends message to client if requested.
            if (sendMessage) {
                final SystemMessage sm;
                if (item.getCount() > 1) {
                    sm = new SystemMessage(SystemMessageId.YOU_HAVE_OBTAINED_S2_S1);
                    sm.addItemName(item);
                    sm.addLong(item.getCount());
                } else if (item.isEnchanted()) {
                    sm = new SystemMessage(SystemMessageId.YOU_HAVE_OBTAINED_A_S1_S2);
                    sm.addInt(item.getEnchantLevel());
                    sm.addItemName(item);
                } else {
                    sm = new SystemMessage(SystemMessageId.YOU_HAVE_OBTAINED_S1);
                    sm.addItemName(item);
                }

                sendPacket(sm);
            }

            // Add the item to inventory.
            final Item newitem = inventory.addItem(process, item, this, reference);

            // Send inventory update packet.
            final InventoryUpdate playerIU = new InventoryUpdate();
            playerIU.addItem(newitem);
            sendInventoryUpdate(playerIU);

            // Update current load as well.
            final StatusUpdate su = new StatusUpdate(this);
            su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
            sendPacket(su);

            // If over capacity, drop the item.
            if (!isGM()
                    && !inventory.validateCapacity(0, item.isQuestItem())
                    && newitem.isDropable()
                    && (!newitem.isStackable() || (newitem.getLastChange() != Item.MODIFIED))) {
                dropItem(ItemProcessType.DROP, newitem, null, true, true);
            } else if (CursedWeaponsManager.getInstance().isCursed(newitem.getId())) {
                CursedWeaponsManager.getInstance().activate(this, newitem);
            }

            // Combat Flag
            else if (FortSiegeManager.getInstance().isCombat(item.getId())) {
                if (FortSiegeManager.getInstance().activateCombatFlag(this, item)) {
                    final Fort fort = FortManager.getInstance().getFort(this);
                    fort.getSiege()
                            .announceToPlayer(new SystemMessage(SystemMessageId.C1_HAS_ACQUIRED_THE_FLAG), getName());
                }
            }
            // Territory Ward
            else if ((item.getId() >= 13560) && (item.getId() <= 13568)) {
                final TerritoryWard ward = TerritoryWarManager.getInstance().getTerritoryWard(item.getId() - 13479);
                if (ward != null) {
                    ward.activate(this, item);
                }
            }
        }
    }

    /**
     * Adds item to Inventory and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param itemId : int Item Identifier of the item to be added
     * @param count : long Quantity of items to be added
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return
     */
    public Item addItem(ItemProcessType process, int itemId, long count, WorldObject reference, boolean sendMessage) {
        return addItem(process, itemId, count, -1, reference, sendMessage);
    }

    /**
     * Adds item to Inventory and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param itemId : int Item Identifier of the item to be added
     * @param count : long Quantity of items to be added
     * @param enchantLevel : int EnchantLevel of the item to be added
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return
     */
    public Item addItem(
            ItemProcessType process,
            int itemId,
            long count,
            int enchantLevel,
            WorldObject reference,
            boolean sendMessage) {
        if (count > 0) {
            final ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
            if (item == null) {
                log.error("Item doesn't exist so cannot be added. Item ID: {}", itemId);
                return null;
            }

            // Sends message to client if requested.
            if (sendMessage && ((!isCastingNow() && item.hasExImmediateEffect()) || !item.hasExImmediateEffect())) {
                if (count > 1) {
                    if ((process == ItemProcessType.SWEEP) || (process == ItemProcessType.QUEST)) {
                        final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S2_S1_S);
                        sm.addItemName(itemId);
                        sm.addLong(count);
                        sendPacket(sm);
                    } else {
                        final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_OBTAINED_S2_S1);
                        sm.addItemName(itemId);
                        sm.addLong(count);
                        sendPacket(sm);
                    }
                } else if ((process == ItemProcessType.SWEEP) || (process == ItemProcessType.QUEST)) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S1);
                    sm.addItemName(itemId);
                    sendPacket(sm);
                } else {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_OBTAINED_S1);
                    sm.addItemName(itemId);
                    sendPacket(sm);
                }
            }

            // Auto-use herbs.
            if (item.hasExImmediateEffect() && item.isEtcItem()) {
                for (SkillHolder skillHolder : item.getSkills()) {
                    doSimultaneousCast(skillHolder.getSkill());
                }

                broadcastInfo();
            } else {
                // Add the item to inventory.
                final Item createdItem = inventory.addItem(process, itemId, count, this, reference);
                if (enchantLevel > -1) {
                    createdItem.setEnchantLevel(enchantLevel);

                    // Update with an InventoryUpdate that has the new enchant value.
                    final InventoryUpdate playerIU = new InventoryUpdate();
                    if (createdItem.isStackable() && (createdItem.getCount() > count)) {
                        playerIU.addModifiedItem(createdItem);
                    } else {
                        playerIU.addNewItem(createdItem);
                    }
                    sendInventoryUpdate(playerIU);
                }

                // If over capacity, drop the item.
                if (!isGM()
                        && !inventory.validateCapacity(0, item.isQuestItem())
                        && createdItem.isDropable()
                        && (!createdItem.isStackable() || (createdItem.getLastChange() != Item.MODIFIED))) {
                    dropItem(ItemProcessType.DROP, createdItem, null, true);
                } else if (CursedWeaponsManager.getInstance().isCursed(createdItem.getId())) {
                    CursedWeaponsManager.getInstance().activate(this, createdItem);
                }
                // Territory Ward
                else if ((createdItem.getId() >= 13560) && (createdItem.getId() <= 13568)) {
                    final TerritoryWard ward =
                            TerritoryWarManager.getInstance().getTerritoryWard(createdItem.getId() - 13479);
                    if (ward != null) {
                        ward.activate(this, createdItem);
                    }
                }

                return createdItem;
            }
        }

        return null;
    }

    /**
     * @param process the ItemProcessType identifier of process triggering this action
     * @param item the item holder
     * @param reference the reference object
     * @param sendMessage if {@code true} a system message will be sent
     */
    public void addItem(ItemProcessType process, ItemHolder item, WorldObject reference, boolean sendMessage) {
        addItem(process, item.getId(), item.getCount(), reference, sendMessage);
    }

    /**
     * Destroy item from inventory and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param item : Item to be destroyed
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return boolean informing if the action was successful
     */
    public boolean destroyItem(ItemProcessType process, Item item, WorldObject reference, boolean sendMessage) {
        return destroyItem(process, item, item.getCount(), reference, sendMessage);
    }

    /**
     * Destroy item from inventory and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param item : Item to be destroyed
     * @param count
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return boolean informing if the action was successful
     */
    public boolean destroyItem(
            ItemProcessType process, Item item, long count, WorldObject reference, boolean sendMessage) {
        final boolean wasEquipped = item.isEquipped();
        final Item destoyedItem = inventory.destroyItem(process, item, count, this, reference);
        if (destoyedItem == null) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

        // Send inventory update packet.
        final InventoryUpdate playerIU = new InventoryUpdate();
        playerIU.addItem(destoyedItem);
        sendInventoryUpdate(playerIU);

        if (wasEquipped && (destoyedItem.getCount() == 0)) {
            // The client leaves the item in the paperdoll when the last one is used up, so the full list is sent as
            // well.
            sendItemList(false);
        }

        // Update current load as well.
        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(su);

        // Sends message to client if requested.
        if (sendMessage) {
            final SystemMessage sm;
            if (count > 1) {
                sm = new SystemMessage(SystemMessageId.S2_S1_HAS_DISAPPEARED);
                sm.addItemName(destoyedItem);
                sm.addLong(count);
            } else {
                sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                sm.addItemName(destoyedItem);
            }

            sendPacket(sm);
        }

        return true;
    }

    /**
     * Destroys item from inventory and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action (if null item will not be logged)
     * @param objectId : int Item Instance identifier of the item to be destroyed
     * @param count : long Quantity of items to be destroyed
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return boolean informing if the action was successful
     */
    @Override
    public boolean destroyItem(
            ItemProcessType process, int objectId, long count, WorldObject reference, boolean sendMessage) {
        final Item item = inventory.getItemByObjectId(objectId);
        if ((item == null) || (item.getCount() < count)) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

        return destroyItem(process, item, count, reference, sendMessage);
    }

    /**
     * Destroy item from inventory by using its <b>itemId</b> and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param itemId : int Item identifier of the item to be destroyed
     * @param count : long Quantity of items to be destroyed
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return boolean informing if the action was successful
     */
    @Override
    public boolean destroyItemByItemId(
            ItemProcessType process, int itemId, long count, WorldObject reference, boolean sendMessage) {
        if (itemId == Inventory.ADENA_ID) {
            return reduceAdena(process, count, reference, sendMessage);
        }

        final Item item = inventory.getItemByItemId(itemId);
        if (item == null) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

        final long itemCount = item.isStackable() ? item.getCount() : inventory.getInventoryItemCount(itemId, -1);
        final long removeCount = count < 0 ? itemCount : count;
        if ((removeCount <= 0)
                || (itemCount < removeCount)
                || (inventory.destroyItemByItemId(process, itemId, removeCount, this, reference) == null)) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

        // Send inventory update packet.
        final InventoryUpdate playerIU = new InventoryUpdate();
        playerIU.addItem(item);
        sendInventoryUpdate(playerIU);

        // Update current load as well.
        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(su);

        // Sends message to client if requested.
        if (sendMessage) {
            final SystemMessage sm;
            if (removeCount > 1) {
                sm = new SystemMessage(SystemMessageId.S2_S1_HAS_DISAPPEARED);
                sm.addItemName(itemId);
                sm.addLong(removeCount);
            } else {
                sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                sm.addItemName(itemId);
            }

            sendPacket(sm);
        }

        return true;
    }

    /**
     * Transfers item to another ItemContainer and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param objectId : int Item Identifier of the item to be transfered
     * @param count : long Quantity of items to be transfered
     * @param target
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the new item or the updated item in inventory
     */
    public Item transferItem(
            ItemProcessType process, int objectId, long count, Inventory target, WorldObject reference) {
        final Item oldItem = checkItemManipulation(objectId, count, "transfer");
        if (oldItem == null) {
            return null;
        }

        final Item newItem = inventory.transferItem(process, objectId, count, target, this, reference);
        if (newItem == null) {
            return null;
        }

        // Send inventory update packet.
        final InventoryUpdate playerIU = new InventoryUpdate();
        if ((oldItem.getCount() > 0) && (oldItem != newItem)) {
            playerIU.addModifiedItem(oldItem);
        } else {
            playerIU.addRemovedItem(oldItem);
        }

        sendInventoryUpdate(playerIU);

        // Update current load as well.
        StatusUpdate playerSU = new StatusUpdate(this);
        playerSU.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(playerSU);

        // Send target update packet.
        if (target instanceof PlayerInventory) {
            final Player targetPlayer = ((PlayerInventory) target).getOwner();
            final InventoryUpdate targetIU = new InventoryUpdate();
            if (newItem.getCount() > count) {
                targetIU.addModifiedItem(newItem);
            } else {
                targetIU.addNewItem(newItem);
            }

            targetPlayer.sendInventoryUpdate(targetIU);

            // Update current load as well.
            playerSU = new StatusUpdate(targetPlayer);
            playerSU.addAttribute(StatusUpdate.CUR_LOAD, targetPlayer.getCurrentLoad());
            targetPlayer.sendPacket(playerSU);
        } else if (target instanceof PetInventory) {
            final PetInventoryUpdate petIU = new PetInventoryUpdate();
            if (newItem.getCount() > count) {
                petIU.addModifiedItem(newItem);
            } else {
                petIU.addNewItem(newItem);
            }

            ((PetInventory) target).getOwner().sendPacket(petIU);
        }

        return newItem;
    }

    /**
     * Use instead of calling {@link #addItem(ItemProcessType, Item, WorldObject, boolean)} and {@link #destroyItemByItemId(ItemProcessType, int, long, WorldObject, boolean)}<br>
     * This method validates slots and weight limit, for stackable and non-stackable items.
     * @param process a ItemProcessType representing the process that is exchanging this items
     * @param reference the (probably NPC) reference, could be null
     * @param coinId the item Id of the item given on the exchange
     * @param cost the amount of items given on the exchange
     * @param rewardId the item received on the exchange
     * @param count the amount of items received on the exchange
     * @param sendMessage if {@code true} it will send messages to the acting player
     * @return {@code true} if the player successfully exchanged the items, {@code false} otherwise
     */
    public boolean exchangeItemsById(
            ItemProcessType process,
            WorldObject reference,
            int coinId,
            long cost,
            int rewardId,
            long count,
            boolean sendMessage) {
        if (!inventory.validateCapacityByItemId(rewardId, count)) {
            if (sendMessage) {
                sendPacket(SystemMessageId.YOUR_INVENTORY_IS_FULL);
            }

            return false;
        }

        if (!inventory.validateWeightByItemId(rewardId, count)) {
            if (sendMessage) {
                sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_WEIGHT_LIMIT);
            }

            return false;
        }

        if (destroyItemByItemId(process, coinId, cost, reference, sendMessage)) {
            addItem(process, rewardId, count, reference, sendMessage);
            return true;
        }

        return false;
    }

    /**
     * Drop item from inventory and send a Server->Client InventoryUpdate packet to the Player.
     * @param process ItemProcessType identifier of process triggering this action
     * @param item Item to be dropped
     * @param reference WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage boolean Specifies whether to send message to Client about this action
     * @param protectItem whether or not dropped item must be protected temporary against other players
     * @return boolean informing if the action was successful
     */
    public boolean dropItem(
            ItemProcessType process, Item item, WorldObject reference, boolean sendMessage, boolean protectItem) {
        final Item droppedItem = inventory.dropItem(process, item, this, reference);
        if (droppedItem == null) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

        droppedItem.dropMe(
                this,
                (getX() + ClockBridge.random().nextInt(50)) - 25,
                (getY() + ClockBridge.random().nextInt(50)) - 25,
                getZ() + 20);
        if ((GeneralConfig.AUTODESTROY_ITEM_AFTER > 0)
                && GeneralConfig.DESTROY_DROPPED_PLAYER_ITEM
                && !GeneralConfig.LIST_PROTECTED_ITEMS.contains(droppedItem.getId())
                && ((droppedItem.isEquipable() && GeneralConfig.DESTROY_EQUIPABLE_PLAYER_ITEM)
                        || !droppedItem.isEquipable())) {
            ItemsAutoDestroyTaskManager.getInstance().addItem(droppedItem);
        }

        // Protection against auto destroy dropped item.
        if (GeneralConfig.DESTROY_DROPPED_PLAYER_ITEM) {
            droppedItem.setProtected(droppedItem.isEquipable()
                    && (!droppedItem.isEquipable() || !GeneralConfig.DESTROY_EQUIPABLE_PLAYER_ITEM));
        } else {
            droppedItem.setProtected(true);
        }

        // Retail drop protection.
        if (protectItem) {
            droppedItem.getDropProtection().protect(this);
        }

        // Send inventory update packet.
        final InventoryUpdate playerIU = new InventoryUpdate();
        playerIU.addItem(droppedItem);
        sendInventoryUpdate(playerIU);

        // Update current load as well.
        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(su);

        // Sends message to client if requested.
        if (sendMessage) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_DROPPED_S1);
            sm.addItemName(droppedItem);
            sendPacket(sm);
        }

        return true;
    }

    public boolean dropItem(ItemProcessType process, Item item, WorldObject reference, boolean sendMessage) {
        return dropItem(process, item, reference, sendMessage, false);
    }

    /**
     * Drop item from inventory by using its <b>objectID</b> and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param objectId : int Item Instance identifier of the item to be dropped
     * @param count : long Quantity of items to be dropped
     * @param x : int coordinate for drop X
     * @param y : int coordinate for drop Y
     * @param z : int coordinate for drop Z
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @param protectItem
     * @return Item corresponding to the new item or the updated item in inventory
     */
    public Item dropItem(
            ItemProcessType process,
            int objectId,
            long count,
            int x,
            int y,
            int z,
            WorldObject reference,
            boolean sendMessage,
            boolean protectItem) {
        final Item invitem = inventory.getItemByObjectId(objectId);
        final Item item = inventory.dropItem(process, objectId, count, this, reference);
        if (item == null) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return null;
        }

        item.dropMe(this, x, y, z);
        if ((GeneralConfig.AUTODESTROY_ITEM_AFTER > 0)
                && GeneralConfig.DESTROY_DROPPED_PLAYER_ITEM
                && !GeneralConfig.LIST_PROTECTED_ITEMS.contains(item.getId())
                && ((item.isEquipable() && GeneralConfig.DESTROY_EQUIPABLE_PLAYER_ITEM) || !item.isEquipable())) {
            ItemsAutoDestroyTaskManager.getInstance().addItem(item);
        }

        if (GeneralConfig.DESTROY_DROPPED_PLAYER_ITEM) {
            item.setProtected(
                    !(!item.isEquipable() || (item.isEquipable() && GeneralConfig.DESTROY_EQUIPABLE_PLAYER_ITEM)));
        } else {
            item.setProtected(true);
        }

        // Retail drop protection.
        if (protectItem) {
            item.getDropProtection().protect(this);
        }

        // Send inventory update packet.
        final InventoryUpdate playerIU = new InventoryUpdate();
        playerIU.addItem(invitem);
        sendInventoryUpdate(playerIU);

        // Update current load as well.
        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(su);

        // Sends message to client if requested.
        if (sendMessage) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_DROPPED_S1);
            sm.addItemName(item);
            sendPacket(sm);
        }

        return item;
    }

    public Item checkItemManipulation(int objectId, long count, String action) {
        // TODO: if we remove objects that are not visible from the World, we'll have to remove this check
        if (World.findObject(objectId) == null) {
            log.trace("{}: player tried to {} item not available in World", getObjectId(), action);
            return null;
        }

        final Item item = inventory.getItemByObjectId(objectId);
        if ((item == null) || (item.getOwnerId() != getObjectId())) {
            log.trace("{}: player tried to {} item he is not owner of", getObjectId(), action);
            return null;
        }

        if ((count < 0) || ((count > 1) && !item.isStackable())) {
            log.trace("{}: player tried to {} item with invalid count: {}", getObjectId(), action, count);
            return null;
        }

        if (count > item.getCount()) {
            log.trace("{}: player tried to {} more items than he owns", getObjectId(), action);
            return null;
        }

        // Pet is summoned and not the item that summoned the pet AND not the buggle from strider you're mounting.
        if ((hasSummon() && (summon.getControlObjectId() == objectId)) || (mountObjectID == objectId)) {
            return null;
        }

        if (activeEnchantItemId == objectId) {
            return null;
        }

        // We cannot put a Weapon with Augmention in WH while casting (Possible Exploit).
        if (item.isAugmented() && (isCastingNow() || isCastingSimultaneouslyNow())) {
            return null;
        }

        return item;
    }

    public boolean isSpawnProtected() {
        return (spawnProtectEndTime != 0) && (spawnProtectEndTime > ClockBridge.gameMillis());
    }

    public boolean isTeleportProtected() {
        return (teleportProtectEndTime != 0) && (teleportProtectEndTime > ClockBridge.gameMillis());
    }

    public void setSpawnProtection(boolean protect) {
        spawnProtectEndTime = protect ? ClockBridge.gameMillis() + (PlayerConfig.PLAYER_SPAWN_PROTECTION * 1000) : 0;
    }

    public void setTeleportProtection(boolean protect) {
        teleportProtectEndTime =
                protect ? ClockBridge.gameMillis() + (PlayerConfig.PLAYER_TELEPORT_PROTECTION * 1000) : 0;
    }

    /**
     * Set protection from aggro mobs when getting up from fake death, according settings.
     * @param protect
     */
    public void setRecentFakeDeath(boolean protect) {
        recentFakeDeathEndTime = protect
                ? GameTimeTaskManager.getInstance().getGameTicks()
                        + (PlayerConfig.PLAYER_FAKEDEATH_UP_PROTECTION * GameTimeTaskManager.TICKS_PER_SECOND)
                : 0;
    }

    public boolean isRecentFakeDeath() {
        return recentFakeDeathEndTime > GameTimeTaskManager.getInstance().getGameTicks();
    }

    @Override
    public boolean isAlikeDead() {
        return super.isAlikeDead() || fakeDeath;
    }

    public void setClient(GameClient client) {
        this.client = client;
        if ((this.client != null) && (this.client.getIp() != null)) {
            ipAddress = this.client.getIp();
        }
    }

    public String getIPAddress() {
        return ipAddress;
    }

    @Override
    public void enableSkill(Skill skill) {
        super.enableSkill(skill);
        removeTimeStamp(skill);
    }

    @Override
    public boolean checkDoCastConditions(Skill skill) {
        if (!super.checkDoCastConditions(skill) || observerMode) {
            return false;
        }

        if (inOlympiadMode && skill.isBlockedInOlympiad()) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_THAT_SKILL_IN_A_GRAND_OLYMPIAD_MATCH);
            return false;
        }

        // Check if the spell using charges or not in AirShip.
        if ((charges.get() < skill.getChargeConsumeCount())
                || (isInAirShip() && !skill.hasEffectType(EffectType.REFUEL_AIRSHIP))) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
            sm.addSkillName(skill);
            sendPacket(sm);
            return false;
        }

        return true;
    }

    /**
     * Returns true if cp update should be done, false if not.
     * @return boolean
     */
    private boolean needCpUpdate() {
        final double currentCp = getCurrentCp();
        if ((currentCp <= 1.0) || (getMaxCp() < MAX_HP_BAR_PX)) {
            return true;
        }

        if ((currentCp <= cpUpdateDecCheck) || (currentCp >= cpUpdateIncCheck)) {
            if (currentCp == getMaxCp()) {
                cpUpdateIncCheck = currentCp + 1;
                cpUpdateDecCheck = currentCp - cpUpdateInterval;
            } else {
                final double doubleMulti = currentCp / cpUpdateInterval;
                int intMulti = (int) doubleMulti;
                cpUpdateDecCheck = cpUpdateInterval * (doubleMulti < intMulti ? intMulti - 1 : intMulti);
                cpUpdateIncCheck = cpUpdateDecCheck + cpUpdateInterval;
            }

            return true;
        }

        return false;
    }

    /**
     * Returns true if mp update should be done, false if not.
     * @return boolean
     */
    private boolean needMpUpdate() {
        final double currentMp = getCurrentMp();
        if ((currentMp <= 1.0) || (getMaxMp() < MAX_HP_BAR_PX)) {
            return true;
        }

        if ((currentMp <= mpUpdateDecCheck) || (currentMp >= mpUpdateIncCheck)) {
            if (currentMp == getMaxMp()) {
                mpUpdateIncCheck = currentMp + 1;
                mpUpdateDecCheck = currentMp - mpUpdateInterval;
            } else {
                final double doubleMulti = currentMp / mpUpdateInterval;
                int intMulti = (int) doubleMulti;
                mpUpdateDecCheck = mpUpdateInterval * (doubleMulti < intMulti ? intMulti - 1 : intMulti);
                mpUpdateIncCheck = mpUpdateDecCheck + mpUpdateInterval;
            }

            return true;
        }

        return false;
    }

    /**
     * Send packet StatusUpdate with current HP,MP and CP to the Player and only current HP, MP and Level to all other Player of the Party. <b><u>Actions</u>:</b>
     * <li>Send the Server->Client packet StatusUpdate with current HP, MP and CP to this Player</li>
     * <li>Send the Server->Client packet PartySmallWindowUpdate with current HP, MP and Level to all other Player of the Party</li> <font color=#FF0000><b><u>Caution</u>: This method DOESN'T SEND current HP and MP to all Player of the _statusListener</b></font>
     */
    @Override
    public void broadcastStatusUpdate() {
        if (broadcastStatusUpdateTask == null) {
            broadcastStatusUpdateTask = ThreadPool.schedule(
                    () -> {
                        final StatusUpdate su = new StatusUpdate(this);
                        su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
                        su.addAttribute(StatusUpdate.CUR_HP, (int) getCurrentHp());
                        su.addAttribute(StatusUpdate.MAX_MP, getMaxMp());
                        su.addAttribute(StatusUpdate.CUR_MP, (int) getCurrentMp());
                        su.addAttribute(StatusUpdate.MAX_CP, getMaxCp());
                        su.addAttribute(StatusUpdate.CUR_CP, (int) getCurrentCp());
                        sendPacket(su);

                        final boolean needCpUpdate = needCpUpdate();
                        final boolean needHpUpdate = needHpUpdate();
                        final Party party = getParty();

                        // Check if a party is in progress and party window update is useful.
                        if ((party != null) && (needCpUpdate || needHpUpdate || needMpUpdate())) {
                            party.broadcastToPartyMembers(this, new PartySmallWindowUpdate(this));
                        }

                        if (inOlympiadMode && olympiadStart && (needCpUpdate || needHpUpdate)) {
                            final OlympiadGameTask game =
                                    OlympiadGameManager.getInstance().getOlympiadTask(getOlympiadGameId());
                            if ((game != null) && game.isBattleStarted()) {
                                game.getZone().broadcastStatusUpdate(this);
                            }
                        }

                        // In duel MP updated only with CP or HP.
                        if (inDuel && (needCpUpdate || needHpUpdate)) {
                            DuelManager.getInstance().broadcastToOppositTeam(this, new ExDuelUpdateUserInfo(this));
                        }

                        broadcastStatusUpdateTask = null;
                    },
                    50);
        }
    }

    /**
     * Send a Server->Client packet UserInfo to this Player and CharInfo to all known players.<br>
     * <font color=#FF0000><b><u>Caution</u>: DON'T SEND UserInfo packet to other players instead of CharInfo packet.<br>
     * UserInfo packet contains PRIVATE DATA as MaxHP, STR, DEX...</b></font>
     */
    public void broadcastUserInfo() {
        // Send user info to the current player.
        updateUserInfo();

        // Broadcast char info to known players.
        broadcastCharInfo();
    }

    public void updateUserInfo() {
        sendPacket(new UserInfo(this));
        sendPacket(new ExBrExtraUserInfo(this));
    }

    public void broadcastCharInfo() {
        // Client is disconnected.
        if (isOnlineInt() == 0) {
            return;
        }

        if (broadcastCharInfoTask == null) {
            broadcastCharInfoTask = ThreadPool.schedule(
                    () -> {
                        final CharInfo charInfo = new CharInfo(this, false);
                        charInfo.sendInBroadcast();
                        World.forEachVisibleObject(this, Player.class, player -> {
                            if (isVisibleFor(player)) {
                                if (isInvisible() && isGM()) {
                                    player.sendPacket(new CharInfo(this, true));
                                } else {
                                    player.sendPacket(charInfo);
                                }

                                player.sendPacket(new ExBrExtraUserInfo(this));

                                // Update relation.
                                final int relation = getRelation(player);
                                final boolean isAutoAttackable = isAutoAttackable(player);
                                final RelationCache oldrelation =
                                        getKnownRelations().get(player.getObjectId());
                                if ((oldrelation == null)
                                        || (oldrelation.relation() != relation)
                                        || (oldrelation.autoAttackable() != isAutoAttackable)) {
                                    player.sendPacket(new RelationChanged(this, relation, isAutoAttackable));
                                    if (hasSummon()) {
                                        player.sendPacket(new RelationChanged(summon, relation, isAutoAttackable));
                                    }

                                    getKnownRelations()
                                            .put(player.getObjectId(), new RelationCache(relation, isAutoAttackable));
                                }
                            }
                        });
                        broadcastCharInfoTask = null;
                    },
                    100);
        }
    }

    public void broadcastTitleInfo() {
        // Send a Server->Client packet UserInfo to this Player.
        updateUserInfo();

        // Send a Server->Client packet TitleUpdate to all known players.
        broadcastPacket(new NicknameChanged(this));
    }

    @Override
    public void broadcastPacket(ServerPacket packet) {
        packet.sendInBroadcast();

        final boolean isCharInfo = packet instanceof CharInfo;
        if (!isCharInfo) {
            sendPacket(packet);
        }

        World.forEachVisibleObject(this, Player.class, player -> {
            if (!isVisibleFor(player)) {
                return;
            }

            player.sendPacket(packet);

            if (isCharInfo) {
                final int relation = getRelation(player);
                final boolean isAutoAttackable = isAutoAttackable(player);
                final RelationCache cache = getKnownRelations().get(player.getObjectId());
                if ((cache == null) || (cache.relation() != relation) || (cache.autoAttackable() != isAutoAttackable)) {
                    player.sendPacket(new RelationChanged(this, relation, isAutoAttackable));
                    if (hasSummon()) {
                        player.sendPacket(new RelationChanged(summon, relation, isAutoAttackable));
                    }

                    getKnownRelations().put(player.getObjectId(), new RelationCache(relation, isAutoAttackable));
                }
            }
        });
    }

    /**
     * @return the Alliance Identifier of the Player.
     */
    @Override
    public int getAllyId() {
        return clan == null ? 0 : clan.getAllyId();
    }

    public int getAllyCrestId() {
        return getAllyId() == 0 ? 0 : clan.getAllyCrestId();
    }

    @Override
    public void sendPacket(ServerPacket packet) {
        if (client != null) {
            client.sendPacket(packet);
        }
    }

    /**
     * Send SystemMessage packet.
     * @param id SystemMessageId
     */
    @Override
    public void sendPacket(SystemMessageId id) {
        sendPacket(new SystemMessage(id));
    }

    /**
     * Manage Interact Task with another Player. <b><u>Actions</u>:</b>
     * <li>If the private store is a STORE_PRIVATE_SELL, send a Server->Client PrivateBuyListSell packet to the Player</li>
     * <li>If the private store is a STORE_PRIVATE_BUY, send a Server->Client PrivateBuyListBuy packet to the Player</li>
     * <li>If the private store is a STORE_PRIVATE_MANUFACTURE, send a Server->Client RecipeShopSellList packet to the Player</li><br>
     * @param target The Creature targeted
     */
    public void doInteract(Creature target) {
        if (target == null) {
            return;
        }

        if (target.isPlayer()) {
            final Player temp = target.asPlayer();
            sendPacket(ActionFailed.STATIC_PACKET);

            if ((temp.getPrivateStoreType() == PrivateStoreType.SELL)
                    || (temp.getPrivateStoreType() == PrivateStoreType.PACKAGE_SELL)) {
                sendPacket(new PrivateStoreListSell(this, temp));
            } else if (temp.getPrivateStoreType() == PrivateStoreType.BUY) {
                sendPacket(new PrivateStoreListBuy(this, temp));
            } else if (temp.getPrivateStoreType() == PrivateStoreType.MANUFACTURE) {
                sendPacket(new RecipeShopSellList(this, temp));
            }
        } else // _interactTarget=null should never happen but one never knows ^^;
        {
            target.onAction(this);
        }
    }

    /**
     * Manages AutoLoot Task.<br>
     * <ul>
     * <li>Send a system message to the player.</li>
     * <li>Add the item to the player's inventory.</li>
     * <li>Send a Server->Client packet InventoryUpdate to this player with NewItem (use a new slot) or ModifiedItem (increase amount).</li>
     * <li>Send a Server->Client packet StatusUpdate to this player with current weight.</li>
     * </ul>
     * <font color=#FF0000><b><u>Caution</u>: If a party is in progress, distribute the items between the party members!</b></font>
     * @param target the NPC dropping the item
     * @param itemId the item ID
     * @param itemCount the item count
     */
    public void doAutoLoot(Attackable target, int itemId, long itemCount) {
        if (isInParty() && !ItemData.getInstance().getTemplate(itemId).hasExImmediateEffect()) {
            party.distributeItem(this, itemId, itemCount, false, target);
        } else if (itemId == Inventory.ADENA_ID) {
            addAdena(ItemProcessType.LOOT, itemCount, target, true);
        } else {
            addItem(ItemProcessType.LOOT, itemId, itemCount, target, true);
        }
    }

    /**
     * Method overload for {@link Player#doAutoLoot(Attackable, int, long)}
     * @param target the NPC dropping the item
     * @param item the item holder
     */
    public void doAutoLoot(Attackable target, ItemHolder item) {
        doAutoLoot(target, item.getId(), item.getCount());
    }

    /**
     * Manage Pickup Task. <b><u>Actions</u>:</b>
     * <li>Send a Server->Client packet StopMove to this Player</li>
     * <li>Remove the Item from the world and send server->client GetItem packets</li>
     * <li>Send a System Message to the Player : YOU_PICKED_UP_S1_ADENA or YOU_PICKED_UP_S1_S2</li>
     * <li>Add the Item to the Player inventory</li>
     * <li>Send a Server->Client packet InventoryUpdate to this Player with NewItem (use a new slot) or ModifiedItem (increase amount)</li>
     * <li>Send a Server->Client packet StatusUpdate to this Player with current weight</li> <font color=#FF0000><b><u>Caution</u>: If a Party is in progress, distribute Items between party members</b></font>
     * @param object The Item to pick up
     */
    @Override
    public void doPickupItem(WorldObject object) {
        if (isAlikeDead() || fakeDeath) {
            return;
        }

        if (getActiveTradeList() != null) {
            sendPacket(SystemMessageId.YOU_CANNOT_PICK_UP_OR_USE_ITEMS_WHILE_TRADING);
            sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Set the AI Intention to IDLE.
        getAI().setIntentionIdle();

        // Check if the WorldObject to pick up is a Item.
        if (!object.isItem()) {
            // Do not try to pickup anything that is not an item :)
            log.warn("{} trying to pickup wrong target.{}", this, getTarget());
            return;
        }

        final Item target = (Item) object;
        sendPacket(new StopMove(this));
        SystemMessage smsg = null;
        synchronized (target) {
            // Check if the target to pick up is visible.
            if (!target.isSpawned()) {
                // Send a Server->Client packet ActionFailed to this Player.
                sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }

            if (!target.getDropProtection().tryPickUp(this)) {
                sendPacket(ActionFailed.STATIC_PACKET);
                smsg = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S1);
                smsg.addItemName(target);
                sendPacket(smsg);
                return;
            }

            if (((isInParty() && (party.getDistributionType() == PartyDistributionType.FINDERS_KEEPERS))
                            || !isInParty())
                    && !inventory.validateCapacity(target)) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.YOUR_INVENTORY_IS_FULL);
                return;
            }

            if (isInvisible() && !isGM()) {
                return;
            }

            if ((target.getOwnerId() != 0)
                    && (target.getOwnerId() != getObjectId())
                    && !isInLooterParty(target.getOwnerId())) {
                if (target.getId() == Inventory.ADENA_ID) {
                    smsg = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S1_ADENA);
                    smsg.addLong(target.getCount());
                } else if (target.getCount() > 1) {
                    smsg = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S2_S1_S);
                    smsg.addItemName(target);
                    smsg.addLong(target.getCount());
                } else {
                    smsg = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S1);
                    smsg.addItemName(target);
                }

                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(smsg);
                return;
            }

            // You can pickup only 1 combat flag.
            if (FortSiegeManager.getInstance().isCombat(target.getId())
                    && !FortSiegeManager.getInstance().checkIfCanPickup(this)) {
                return;
            }

            if ((target.getItemLootShedule() != null)
                    && ((target.getOwnerId() == getObjectId()) || isInLooterParty(target.getOwnerId()))) {
                target.resetOwnerTimer();
            }

            // Remove the Item from the world and send server->client GetItem packets.
            target.pickupMe(this);
            if (GeneralConfig.SAVE_DROPPED_ITEM) {
                ItemsOnGroundManager.getInstance().removeObject(target);
            }
        }

        // Auto use herbs - pick up.
        if (target.getTemplate().hasExImmediateEffect()) {
            final IItemHandler handler = ItemHandler.getInstance().getHandler(target.getEtcItem());
            if (handler != null) {
                handler.onItemUse(this, target, false);
            } else {
                log.warn("No item handler registered for item ID: {}.", target.getId());
            }

            ItemManager.destroyItem(ItemProcessType.NONE, target, this, null);
        }
        // Cursed Weapons are not distributed.
        else if (CursedWeaponsManager.getInstance().isCursed(target.getId())) {
            addItem(ItemProcessType.PICKUP, target, null, true);
        } else if (FortSiegeManager.getInstance().isCombat(target.getId())) {
            addItem(ItemProcessType.PICKUP, target, null, true);
        } else {
            // If item is instance of ArmorType or WeaponType broadcast an "Attention" system message.
            if ((target.getItemType() instanceof ArmorType) || (target.getItemType() instanceof WeaponType)) {
                if (target.isEnchanted()) {
                    smsg = new SystemMessage(SystemMessageId.ATTENTION_C1_HAS_PICKED_UP_S2_S3);
                    smsg.addPcName(this);
                    smsg.addInt(target.getEnchantLevel());
                } else {
                    smsg = new SystemMessage(SystemMessageId.ATTENTION_C1_HAS_PICKED_UP_S2);
                    smsg.addPcName(this);
                }

                smsg.addItemName(target.getId());
                broadcastPacket(smsg);
            }

            // Check if a Party is in progress.
            if (isInParty()) {
                party.distributeItem(this, target);
            } else if ((target.getId() == Inventory.ADENA_ID) && (inventory.getAdenaInstance() != null)) {
                addAdena(ItemProcessType.PICKUP, target.getCount(), null, true);
                ItemManager.destroyItem(ItemProcessType.PICKUP, target, this, null);
            } else {
                addItem(ItemProcessType.PICKUP, target, null, true);

                // Auto-Equip arrows/bolts if player has a bow/crossbow and player picks up arrows/bolts.
                final Item weapon = inventory.getPaperdollItem(Inventory.PAPERDOLL_RHAND);
                if (weapon != null) {
                    final EtcItem etcItem = target.getEtcItem();
                    if (etcItem != null) {
                        final EtcItemType itemType = etcItem.getItemType();
                        if ((weapon.getItemType() == WeaponType.BOW) && (itemType == EtcItemType.ARROW)) {
                            checkAndEquipArrows();
                        } else if ((weapon.getItemType() == WeaponType.CROSSBOW) && (itemType == EtcItemType.BOLT)) {
                            checkAndEquipBolts();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void doAttack(Creature target) {
        super.doAttack(target);
        setRecentFakeDeath(false);
        if (target.isFakePlayer() && !FakePlayersConfig.FAKE_PLAYER_AUTO_ATTACKABLE) {
            updatePvPStatus();
        }
    }

    @Override
    public void doCast(Skill skill) {
        if ((currentSkill != null)
                && !checkUseMagicConditions(skill, currentSkill.isCtrlPressed(), currentSkill.isShiftPressed())) {
            setCastingNow(false);
            setCastingSimultaneouslyNow(false);
            return;
        }

        super.doCast(skill);
        setRecentFakeDeath(false);
    }

    public boolean canOpenPrivateStore() {
        if ((PrivateStoreRangeConfig.SHOP_MIN_RANGE_FROM_NPC > 0)
                || (PrivateStoreRangeConfig.SHOP_MIN_RANGE_FROM_PLAYER > 0)) {
            if (World.getFirstVisibleObject(
                            this,
                            Creature.class,
                            creature -> (creature.getMinShopDistance() > 0)
                                    && LocationUtil.checkIfInRange(creature.getMinShopDistance(), this, creature, true))
                    != null) {
                sendPacket(new SystemMessage(SystemMessageId.YOU_CANNOT_OPEN_A_PRIVATE_STORE_HERE));
                return false;
            }
        }

        return !isAlikeDead() && !inOlympiadMode && !isMounted() && !isInsideZone(ZoneId.NO_STORE) && !isCastingNow();
    }

    @Override
    public int getMinShopDistance() {
        return sitting ? PrivateStoreRangeConfig.SHOP_MIN_RANGE_FROM_PLAYER : 0;
    }

    public void tryOpenPrivateBuyStore() {
        // Player shouldn't be able to set stores if he/she is alike dead (dead or fake death).
        if (canOpenPrivateStore()) {
            if ((privateStoreType == PrivateStoreType.BUY) || (privateStoreType == PrivateStoreType.BUY_MANAGE)) {
                setPrivateStoreType(PrivateStoreType.NONE);
            }

            if (privateStoreType == PrivateStoreType.NONE) {
                if (sitting) {
                    standUp();
                }

                setPrivateStoreType(PrivateStoreType.BUY_MANAGE);
                sendPacket(new PrivateStoreManageListBuy(this));
            }
        } else {
            if (isInsideZone(ZoneId.NO_STORE)) {
                sendPacket(SystemMessageId.YOU_CANNOT_OPEN_A_PRIVATE_STORE_HERE);
            }

            sendPacket(ActionFailed.STATIC_PACKET);
        }
    }

    public void tryOpenPrivateSellStore(boolean isPackageSale) {
        // Player shouldn't be able to set stores if he/she is alike dead (dead or fake death).
        if (canOpenPrivateStore()) {
            if ((privateStoreType == PrivateStoreType.SELL)
                    || (privateStoreType == PrivateStoreType.SELL_MANAGE)
                    || (privateStoreType == PrivateStoreType.PACKAGE_SELL)) {
                setPrivateStoreType(PrivateStoreType.NONE);
            }

            if (privateStoreType == PrivateStoreType.NONE) {
                if (sitting) {
                    standUp();
                }

                setPrivateStoreType(PrivateStoreType.SELL_MANAGE);
                sendPacket(new PrivateStoreManageListSell(this, isPackageSale));
            }
        } else {
            if ((privateStoreType != PrivateStoreType.NONE) && !isAlikeDead()) {
                setPrivateStoreType(PrivateStoreType.NONE);
                if (sitting) {
                    standUp();
                }
                return;
            }

            if (isInsideZone(ZoneId.NO_STORE)) {
                sendPacket(SystemMessageId.YOU_CANNOT_OPEN_A_PRIVATE_STORE_HERE);
            }

            sendPacket(ActionFailed.STATIC_PACKET);
        }
    }

    @Override
    public boolean isTransformed() {
        return (transformation != null) && !transformation.isStance();
    }

    public boolean isInStance() {
        return (transformation != null) && transformation.isStance();
    }

    public void transform(Transform transformation) {
        if (transformation.isFlying() && (getX() > World.GRACIA_MAX_X)) {
            return;
        }

        if (!FeatureConfig.ALLOW_MOUNTS_DURING_SIEGE && transformation.isRiding() && isInsideZone(ZoneId.SIEGE)) {
            return;
        }

        if (this.transformation != null) {
            // You already polymorphed and cannot polymorph again.
            sendPacket(new SystemMessage(SystemMessageId.YOU_ALREADY_POLYMORPHED_AND_CANNOT_POLYMORPH_AGAIN));
            return;
        }

        setQueuedSkill(null, false, false);
        if (isMounted()) {
            // Get off the strider or something else if character is mounted.
            dismount();
        }

        this.transformation = transformation;
        getEffectList().stopAllToggles();
        transformation.onTransform(this);
        sendSkillList();
        sendPacket(new SkillCoolTime(this));
        broadcastUserInfo();

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_TRANSFORM, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerTransform(this, transformation.getId()), this);
        }
    }

    @Override
    public void untransform() {
        if (transformation == null) {
            return;
        }

        setQueuedSkill(null, false, false);
        transformation.onUntransform(this);
        transformation = null;
        getEffectList().stopSkillEffects(SkillFinishType.NORMAL, AbnormalType.TRANSFORM);
        sendSkillList();
        sendPacket(new SkillCoolTime(this));
        broadcastUserInfo();

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_TRANSFORM, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerTransform(this, 0), this);
        }
    }

    /**
     * This returns the transformation Id of the current transformation. For example, if a player is transformed as a Buffalo, and then picks up the Zariche, the transform Id returned will be that of the Zariche, and NOT the Buffalo.
     * @return Transformation Id
     */
    public int getTransformationId() {
        return isTransformed() ? transformation.getId() : 0;
    }

    public int getTransformationDisplayId() {
        return isTransformed() ? transformation.getDisplayId() : 0;
    }

    /**
     * Set a target. <b><u>Actions</u>:</b>
     * <ul>
     * <li>Remove the Player from the _statusListener of the old target if it was a Creature</li>
     * <li>Add the Player to the _statusListener of the new target if it's a Creature</li>
     * <li>Target the new WorldObject (add the target to the Player target, _knownObject and Player to _KnownObject of the WorldObject)</li>
     * </ul>
     * @param worldObject The WorldObject to target
     */
    @Override
    public void setTarget(WorldObject worldObject) {
        WorldObject newTarget = worldObject;
        if (newTarget != null) {
            final boolean isInParty = newTarget.isPlayer() && isInParty() && party.containsPlayer(newTarget.asPlayer());

            // Prevents /target exploiting.
            if (!isInParty && (Math.abs(newTarget.getZ() - getZ()) > 3000)) {
                newTarget = null;
            }

            // Check if the new target is visible.
            if ((newTarget != null) && !isInParty && !newTarget.isSpawned()) {
                newTarget = null;
            }

            // Vehicles cannot be targeted.
            if (!isGM() && (newTarget instanceof Vehicle)) {
                newTarget = null;
            }
        }

        // Get the current target.
        final WorldObject oldTarget = getTarget();
        if (oldTarget != null) {
            if (oldTarget.equals(newTarget)) // no target change?
            {
                // Validate location of the target.
                if ((newTarget != null) && (newTarget.getObjectId() != getObjectId())) {
                    sendPacket(new ValidateLocation(newTarget));
                }
                return;
            }

            // Remove the target from the status listener.
            oldTarget.removeStatusListener(this);
        }

        if ((newTarget != null) && newTarget.isCreature()) {
            final Creature target = newTarget.asCreature();

            // Validate location of the new target.
            if (newTarget.getObjectId() != getObjectId()) {
                sendPacket(new ValidateLocation(target));
            }

            // Show the client his new target.
            sendPacket(new MyTargetSelected(this, target));

            // Register target to listen for hp changes.
            target.addStatusListener(this);

            // Send max/current hp.
            final StatusUpdate su = new StatusUpdate(target);
            su.addAttribute(StatusUpdate.MAX_HP, target.getMaxHp());
            su.addAttribute(StatusUpdate.CUR_HP, (int) target.getCurrentHp());
            sendPacket(su);

            // To others the new target, and not yourself!
            World.broadcastToVisiblePlayers(
                    this, new TargetSelected(getObjectId(), newTarget.getObjectId(), getX(), getY(), getZ()));
        }

        // Target was removed?
        if ((newTarget == null) && (getTarget() != null)) {
            broadcastPacket(new TargetUnselected(this));
        }

        // Target the new WorldObject (add the target to the Player target, _knownObject and Player to _KnownObject of
        // the WorldObject).
        super.setTarget(newTarget);
    }

    /**
     * Return the active weapon instance (always equipped in the right hand).
     */
    @Override
    public Item getActiveWeaponInstance() {
        return inventory.getPaperdollItem(Inventory.PAPERDOLL_RHAND);
    }

    /**
     * Return the active weapon item (always equipped in the right hand).
     */
    @Override
    public Weapon getActiveWeaponItem() {
        final Item weapon = getActiveWeaponInstance();
        return weapon == null ? fistsWeaponItem : (Weapon) weapon.getTemplate();
    }

    public Item getChestArmorInstance() {
        return inventory.getPaperdollItem(Inventory.PAPERDOLL_CHEST);
    }

    public Item getLegsArmorInstance() {
        return inventory.getPaperdollItem(Inventory.PAPERDOLL_LEGS);
    }

    public Armor getActiveChestArmorItem() {
        final Item armor = getChestArmorInstance();
        return armor == null ? null : (Armor) armor.getTemplate();
    }

    public Armor getActiveLegsArmorItem() {
        final Item legs = getLegsArmorInstance();
        return legs == null ? null : (Armor) legs.getTemplate();
    }

    public boolean isWearingHeavyArmor() {
        final Item legs = getLegsArmorInstance();
        final Item armor = getChestArmorInstance();
        if ((armor != null)
                && (legs != null)
                && (legs.getItemType() == ArmorType.HEAVY)
                && (armor.getItemType() == ArmorType.HEAVY)) {
            return true;
        }

        if ((armor != null)
                && (getInventory()
                                .getPaperdollItem(Inventory.PAPERDOLL_CHEST)
                                .getTemplate()
                                .getBodyPart()
                        == BodyPart.FULL_ARMOR)
                && (armor.getItemType() == ArmorType.HEAVY)) {
            return true;
        }

        return false;
    }

    public boolean isWearingLightArmor() {
        final Item legs = getLegsArmorInstance();
        final Item armor = getChestArmorInstance();
        if ((armor != null)
                && (legs != null)
                && (legs.getItemType() == ArmorType.LIGHT)
                && (armor.getItemType() == ArmorType.LIGHT)) {
            return true;
        }

        if ((armor != null)
                && (inventory
                                .getPaperdollItem(Inventory.PAPERDOLL_CHEST)
                                .getTemplate()
                                .getBodyPart()
                        == BodyPart.FULL_ARMOR)
                && (armor.getItemType() == ArmorType.LIGHT)) {
            return true;
        }

        return false;
    }

    public boolean isWearingMagicArmor() {
        final Item legs = getLegsArmorInstance();
        final Item armor = getChestArmorInstance();
        if ((armor != null)
                && (legs != null)
                && (legs.getItemType() == ArmorType.MAGIC)
                && (armor.getItemType() == ArmorType.MAGIC)) {
            return true;
        }

        if ((armor != null)
                && (inventory
                                .getPaperdollItem(Inventory.PAPERDOLL_CHEST)
                                .getTemplate()
                                .getBodyPart()
                        == BodyPart.FULL_ARMOR)
                && (armor.getItemType() == ArmorType.MAGIC)) {
            return true;
        }

        return false;
    }

    public void setEngageRequest(boolean state, int playerid) {
        engageRequest = state;
        engageId = playerid;
    }

    public void engageAnswer(int answer) {
        if (!engageRequest || (engageId == 0)) {
            return;
        }

        final Player ptarget = World.getPlayer(engageId);
        setEngageRequest(false, 0);
        if (ptarget != null) {
            if (answer == 1) {
                CoupleManager.getInstance().createCouple(ptarget, Player.this);
                ptarget.sendMessage("Request to Engage has been >ACCEPTED<");
            } else {
                ptarget.sendMessage("Request to Engage has been >DENIED<!");
            }
        }
    }

    /**
     * Return the secondary weapon instance (always equipped in the left hand).
     */
    @Override
    public Item getSecondaryWeaponInstance() {
        return inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
    }

    /**
     * Return the secondary Item item (always equipped in the left hand).<br>
     * Arrows, Shield..
     */
    @Override
    public ItemTemplate getSecondaryWeaponItem() {
        final Item item = inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
        return item != null ? item.getTemplate() : null;
    }

    /**
     * Kill the Creature, Apply Death Penalty, Manage gain/loss Karma and Item Drop. <b><u>Actions</u>:</b>
     * <li>Reduce the Experience of the Player in function of the calculated Death Penalty</li>
     * <li>If necessary, unsummon the Pet of the killed Player</li>
     * <li>Manage Karma gain for attacker and Karam loss for the killed Player</li>
     * <li>If the killed Player has Karma, manage Drop Item</li>
     * <li>Kill the Player</li><br>
     * @param killer
     */
    @Override
    public boolean doDie(Creature killer) {
        // Kill the Player.
        if (!super.doDie(killer)) {
            return false;
        }

        if (isMounted()) {
            stopFeed();
        }

        // synchronized (this)
        // {
        if (fakeDeath) {
            stopFakeDeath(true);
        }
        // }

        final KrateiArena arena = getKrateiArena();
        if (arena != null) {
            arena.setRespawnTask(this);
            if ((killer != null) && killer.isPlayable()) {
                final Player player = killer.asPlayer();
                if (player != null) {
                    final KrateiArena killerArena = player.getKrateiArena();
                    if (killerArena != null) {
                        killerArena.addPoints(player, true);
                    }
                }
            }
        }

        if (killer != null) {
            final Player pk = killer.asPlayer();
            final boolean fpcKill = killer.isFakePlayer();
            if ((pk != null) || fpcKill) {
                if ((getParty() != null) && (getParty().getUCState() instanceof UCTeam)) {
                    ((UCTeam) getParty().getUCState()).onKill(this, pk);
                }

                if (pk != null) {
                    if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_PVP_KILL, this)) {
                        EventDispatcher.getInstance()
                                .notifyEventAsync(
                                        OnPlayerPvPKill.builder()
                                                .player(pk)
                                                .target(this)
                                                .build(),
                                        this);
                    }

                    // pvp/pk item rewards
                    if (!(PvpRewardItemConfig.DISABLE_REWARDS_IN_INSTANCES && (getInstanceId() != 0))
                            && //
                            !(PvpRewardItemConfig.DISABLE_REWARDS_IN_PVP_ZONES && isInsideZone(ZoneId.PVP))) {
                        // pvp
                        if (PvpRewardItemConfig.REWARD_PVP_ITEM && (pvpFlag != 0)) {
                            pk.addItem(
                                    ItemProcessType.REWARD,
                                    PvpRewardItemConfig.REWARD_PVP_ITEM_ID,
                                    PvpRewardItemConfig.REWARD_PVP_ITEM_AMOUNT,
                                    this,
                                    PvpRewardItemConfig.REWARD_PVP_ITEM_MESSAGE);
                        }

                        // pk
                        if (PvpRewardItemConfig.REWARD_PK_ITEM && (pvpFlag == 0)) {
                            pk.addItem(
                                    ItemProcessType.REWARD,
                                    PvpRewardItemConfig.REWARD_PK_ITEM_ID,
                                    PvpRewardItemConfig.REWARD_PK_ITEM_AMOUNT,
                                    this,
                                    PvpRewardItemConfig.REWARD_PK_ITEM_MESSAGE);
                        }
                    }
                }

                // announce pvp/pk
                if (PvpAnnounceConfig.ANNOUNCE_PK_PVP && (((pk != null) && !pk.isGM()) || fpcKill)) {
                    String msg = "";
                    if (pvpFlag == 0) {
                        msg = PvpAnnounceConfig.ANNOUNCE_PK_MSG
                                .replace("$killer", pk != null ? pk.getName() : killer.getName())
                                .replace("$target", getName());
                        if (PvpAnnounceConfig.ANNOUNCE_PK_PVP_NORMAL_MESSAGE) {
                            final SystemMessage sm = new SystemMessage(SystemMessageId.S1);
                            sm.addString(msg);
                            World.broadcastToAllOnlinePlayers(sm);
                        } else {
                            World.broadcastToAllOnlinePlayers(msg, false);
                        }
                    } else if (pvpFlag != 0) {
                        msg = PvpAnnounceConfig.ANNOUNCE_PVP_MSG
                                .replace("$killer", killer.getName())
                                .replace("$target", getName());
                        if (PvpAnnounceConfig.ANNOUNCE_PK_PVP_NORMAL_MESSAGE) {
                            final SystemMessage sm = new SystemMessage(SystemMessageId.S1);
                            sm.addString(msg);
                            World.broadcastToAllOnlinePlayers(sm);
                        } else {
                            World.broadcastToAllOnlinePlayers(msg, false);
                        }
                    }
                }

                if (fpcKill && FakePlayersConfig.FAKE_PLAYER_KILL_KARMA && (pvpFlag == 0) && (getKarma() <= 0)) {
                    killer.setKarma(killer.getKarma() + 150);
                    killer.broadcastInfo();
                }
            }

            broadcastStatusUpdate();

            // Clear resurrect xp calculation.
            setExpBeforeDeath(0);

            // Issues drop of Cursed Weapon.
            if (isCursedWeaponEquipped()) {
                CursedWeaponsManager.getInstance().drop(cursedWeaponEquippedId, killer);
            } else if (combatFlagEquipped) {
                // TODO: Fort siege during TW??
                if (TerritoryWarManager.getInstance().isTWInProgress()) {
                    TerritoryWarManager.getInstance().dropCombatFlag(this, true, false);
                } else {
                    final Fort fort = FortManager.getInstance().getFort(this);
                    if (fort != null) {
                        FortSiegeManager.getInstance().dropCombatFlag(this, fort.getResidenceId());
                    } else {
                        final BodyPart bodyPart = BodyPart.fromItem(inventory.getItemByItemId(9819));
                        inventory.unEquipItemInBodySlot(bodyPart);
                        destroyItem(ItemProcessType.DESTROY, inventory.getItemByItemId(9819), null, true);
                    }
                }
            } else {
                final boolean insidePvpZone = isInsideZone(ZoneId.PVP);
                final boolean insideSiegeZone = isInsideZone(ZoneId.SIEGE);
                if ((pk == null) || !pk.isCursedWeaponEquipped()) {
                    onDieDropItem(killer); // Check if any item should be dropped.
                    if (!insidePvpZone && !insideSiegeZone) {
                        if ((pk != null)
                                && (pk.getClan() != null)
                                && (getClan() != null)
                                && !isAcademyMember()
                                && !(pk.isAcademyMember())) {
                            if ((clan.isAtWarWith(pk.getClanId())
                                            && pk.getClan().isAtWarWith(clan.getId()))
                                    || (isInSiege() && pk.isInSiege())) {
                                if (AntiFeedManager.getInstance().check(killer, this)) {
                                    // When your reputation score is 0 or below, the other clan cannot acquire any
                                    // reputation points.
                                    if (clan.getReputationScore() > 0) {
                                        pk.getClan().addReputationScore(FeatureConfig.REPUTATION_SCORE_PER_KILL);
                                    }

                                    // when the opposing sides reputation score is 0 or below, your clans reputation
                                    // score does not decrease
                                    if (pk.getClan().getReputationScore() > 0) {
                                        clan.takeReputationScore(FeatureConfig.REPUTATION_SCORE_PER_KILL);
                                    }
                                }
                            }
                        }
                    }

                    // Should not penalize player when lucky, in a non siege PvP zone, has advent blessing or is in an
                    // event.
                    if (PlayerConfig.PLAYER_DELEVEL
                            && !isLucky()
                            && (insideSiegeZone || !insidePvpZone)
                            && !nevitSystem.isAdventBlessingActive()
                            && !isOnEvent()) {
                        calculateDeathExpPenalty(killer, isAtWarWith(pk));
                    }
                }
            }
        }

        // Unsummon Cubics
        if (!cubics.isEmpty()) {
            for (Cubic cubic : cubics.values()) {
                cubic.stopAction();
                cubic.cancelDisappear();
            }

            cubics.clear();
            cubicOrder.clear();
        }

        if (isChannelized()) {
            getSkillChannelized().abortChannelization();
        }

        if (isInParty() && party.isInDimensionalRift()) {
            party.getDimensionalRift().getDeadMemberList().add(this);
        }

        if (agathionId != 0) {
            setAgathionId(0);
        }

        // calculate death penalty buff
        calculateDeathPenaltyBuffLevel(killer);

        if (hasSummon()) {
            if (summon.isBetrayed()) {
                sendPacket(SystemMessageId.YOUR_PET_SERVITOR_IS_UNRESPONSIVE_AND_WILL_NOT_OBEY_ANY_ORDERS);
            }

            summon.cancelAction();
        }

        stopRentPet();
        stopWaterTask();

        AntiFeedManager.getInstance().setLastDeathTime(getObjectId());

        // FIXME: Karma reduction tempfix.
        if (getKarma() > 0) // && (killer instanceof GuardInstance))
        {
            setKarma(getKarma() < 200 ? 0 : (int) (getKarma() - (getKarma() / 4)));
        }

        if (PlayerConfig.DISCONNECT_AFTER_DEATH) {
            DecayTaskManager.getInstance().add(this);
        }

        return true;
    }

    private void onDieDropItem(Creature killer) {
        if (isOnEvent() || (killer == null)) {
            return;
        }

        final Player pk = killer.asPlayer();
        if ((getKarma() <= 0)
                && (pk != null)
                && (pk.getClan() != null)
                && (getClan() != null)
                && (pk.getClan().isAtWarWith(clanId)

                // || _clan.isAtWarWith(killer.asPlayer().getClanId())
                )) {
            return;
        }

        if ((!isInsideZone(ZoneId.PVP) || (pk == null)) && (!isGM() || PvpConfig.KARMA_DROP_GM)) {
            boolean isKarmaDrop = false;
            final boolean isKillerNpc = killer instanceof Npc;
            final int pkLimit = PvpConfig.KARMA_PK_LIMIT;
            int dropEquip = 0;
            int dropEquipWeapon = 0;
            int dropItem = 0;
            int dropLimit = 0;
            int dropPercent = 0;
            if ((getKarma() > 0) && (pkKills >= pkLimit)) {
                isKarmaDrop = true;
                dropPercent = RatesConfig.KARMA_RATE_DROP;
                dropEquip = RatesConfig.KARMA_RATE_DROP_EQUIP;
                dropEquipWeapon = RatesConfig.KARMA_RATE_DROP_EQUIP_WEAPON;
                dropItem = RatesConfig.KARMA_RATE_DROP_ITEM;
                dropLimit = RatesConfig.KARMA_DROP_LIMIT;
            } else if (isKillerNpc && (getLevel() > 4) && !isFestivalParticipant()) {
                dropPercent = RatesConfig.PLAYER_RATE_DROP;
                dropEquip = RatesConfig.PLAYER_RATE_DROP_EQUIP;
                dropEquipWeapon = RatesConfig.PLAYER_RATE_DROP_EQUIP_WEAPON;
                dropItem = RatesConfig.PLAYER_RATE_DROP_ITEM;
                dropLimit = RatesConfig.PLAYER_DROP_LIMIT;
            }

            if ((dropPercent > 0) && (ClockBridge.random().nextInt(100) < dropPercent)) {
                int dropCount = 0;
                int itemDropPercent = 0;
                for (Item itemDrop : inventory.getItems()) {
                    // Don't drop
                    if (itemDrop.isShadowItem()
                            || // do not drop Shadow Items
                            itemDrop.isTimeLimitedItem()
                            || // do not drop Time Limited Items
                            !itemDrop.isDropable()
                            || (itemDrop.getId() == Inventory.ADENA_ID)
                            || // Adena
                            (itemDrop.getTemplate().getType2() == ItemTemplate.TYPE2_QUEST)
                            || // Quest Items
                            (hasSummon() && (summon.getControlObjectId() == itemDrop.getId()))
                            || // Control Item of active pet
                            (Arrays.binarySearch(PvpConfig.KARMA_LIST_NONDROPPABLE_ITEMS, itemDrop.getId()) >= 0)
                            || // Item listed in the non droppable item list
                            (Arrays.binarySearch(PvpConfig.KARMA_LIST_NONDROPPABLE_PET_ITEMS, itemDrop.getId())
                                    >= 0 // Item listed in the non droppable pet item list
                            )) {
                        continue;
                    }

                    if (itemDrop.isEquipped()) {
                        // Set proper chance according to Item type of equipped Item.
                        itemDropPercent = itemDrop.getTemplate().getType2() == ItemTemplate.TYPE2_WEAPON
                                ? dropEquipWeapon
                                : dropEquip;
                    } else {
                        itemDropPercent = dropItem; // Item in inventory
                    }

                    // NOTE: Each time an item is dropped, the chance of another item being dropped gets lesser
                    // (dropCount * 2)
                    if (ClockBridge.random().nextInt(100) < itemDropPercent) {
                        if (itemDrop.isEquipped()) {
                            inventory.unEquipItemInSlot(itemDrop.getLocationSlot());
                        }

                        dropItem(ItemProcessType.DEATH, itemDrop, killer, true);
                        sendItemList(false);

                        if (isKarmaDrop) {
                            log.warn(
                                    "{} has karma and dropped id = {}, count = {}",
                                    getName(),
                                    itemDrop.getId(),
                                    itemDrop.getCount());
                        } else {
                            log.warn(
                                    "{} dropped id = {}, count = {}", getName(), itemDrop.getId(), itemDrop.getCount());
                        }

                        if (++dropCount >= dropLimit) {
                            break;
                        }
                    }
                }
            }
        }
    }

    public void onKillUpdatePvPKarma(Creature target) {
        if ((target == null) || !target.isPlayable()) {
            return;
        }

        // Avoid nulls && check if player != killedPlayer.
        final Player killedPlayer = target.asPlayer();
        if ((killedPlayer == null) || (killedPlayer == this)) {
            return;
        }

        // Cursed weapons progress.
        if (isCursedWeaponEquipped() && target.isPlayer()) {
            CursedWeaponsManager.getInstance().increaseKills(cursedWeaponEquippedId);
            return;
        }

        // Olympiad support
        if (isInOlympiadMode() || killedPlayer.isInOlympiadMode()) {
            return;
        }

        // Duel support
        if (isInDuel() && killedPlayer.isInDuel()) {
            return;
        }

        // If both players are in SIEGE zone just increase siege kills/deaths.
        if (target.isPlayer() && isInsideZone(ZoneId.SIEGE) && killedPlayer.isInsideZone(ZoneId.SIEGE)) {
            if (!isSiegeFriend(killedPlayer)) {
                final Clan targetClan = killedPlayer.getClan();
                if ((clan != null) && (targetClan != null)) {
                    clan.addSiegeKill();
                    targetClan.addSiegeDeath();
                }
            }
            return;
        }

        // Do nothing when in PVP zone.
        if (isInsideZone(ZoneId.PVP) || target.isInsideZone(ZoneId.PVP)) {
            return;
        }

        // Check if it's pvp.
        if (checkIfPvP(target) && (killedPlayer.getPvpFlag() != 0)) {
            increasePvpKills(target);
        } else {
            // Target player doesn't have pvp flag set.
            // check about wars
            if ((killedPlayer.getClan() != null)
                    && (getClan() != null)
                    && getClan().isAtWarWith(killedPlayer.getClanId())
                    && killedPlayer.getClan().isAtWarWith(getClanId())
                    && (killedPlayer.getPledgeType() != Clan.SUBUNIT_ACADEMY)
                    && (getPledgeType() != Clan.SUBUNIT_ACADEMY)) {
                // 'Both way war' -> 'PvP Kill'.
                increasePvpKills(target);
                return;
            }

            // 'No war' or 'One way war' -> 'Normal PK'.
            if (killedPlayer.getKarma() > 0) // Target player has karma.
            {
                if (PvpConfig.KARMA_AWARD_PK_KILL) {
                    increasePvpKills(target);
                }
            } else if (killedPlayer.getPvpFlag() == 0) // Target player doesn't have karma.
            {
                if (FactionSystemConfig.FACTION_SYSTEM_ENABLED) {
                    if ((good && killedPlayer.isGood()) || (evil && killedPlayer.isEvil())) {
                        increasePkKillsAndKarma(target);
                    }
                } else {
                    increasePkKillsAndKarma(target);
                }

                checkItemRestriction(); // Unequip adventurer items
            }
        }
    }

    /**
     * Increase the pvp kills count and send the info to the player
     * @param target
     */
    public void increasePvpKills(Creature target) {
        if (target.isPlayer() && AntiFeedManager.getInstance().check(this, target)) {
            setPvpKills(pvpKills + 1);
            updatePvpTitleAndColor(true);

            // Send a Server->Client UserInfo packet to attacker with its Karma and PK Counter.
            updateUserInfo();
        }
    }

    /**
     * Increase pk count, karma and send the info to the player
     * @param target
     */
    public void increasePkKillsAndKarma(Creature target) {
        // Only playables can increase karma/pk.
        if ((target == null) || !target.isPlayable()) {
            return;
        }

        // Calculate new karma. (calculate karma before incrase pk count!).
        setKarma(getKarma() + Formulas.calculateKarmaGain(pkKills, target.isSummon()));

        // PK Points are increased only if you kill a player.
        if (target.isPlayer()) {
            setPkKills(pkKills + 1);
        }

        // Update player's UI.
        updateUserInfo();
    }

    public void updatePvpTitleAndColor(boolean broadcastInfo) {
        if (PvpTitleColorConfig.PVP_COLOR_SYSTEM_ENABLED
                && !FactionSystemConfig.FACTION_SYSTEM_ENABLED) // Faction system uses title colors.
        {
            if ((pvpKills >= PvpTitleColorConfig.PVP_AMOUNT1) && (pvpKills < PvpTitleColorConfig.PVP_AMOUNT2)) {
                setTitle("\u00AE " + PvpTitleColorConfig.TITLE_FOR_PVP_AMOUNT1 + " \u00AE");
                appearance.setTitleColor(PvpTitleColorConfig.NAME_COLOR_FOR_PVP_AMOUNT1);
            } else if ((pvpKills >= PvpTitleColorConfig.PVP_AMOUNT2) && (pvpKills < PvpTitleColorConfig.PVP_AMOUNT3)) {
                setTitle("\u00AE " + PvpTitleColorConfig.TITLE_FOR_PVP_AMOUNT2 + " \u00AE");
                appearance.setTitleColor(PvpTitleColorConfig.NAME_COLOR_FOR_PVP_AMOUNT2);
            } else if ((pvpKills >= PvpTitleColorConfig.PVP_AMOUNT3) && (pvpKills < PvpTitleColorConfig.PVP_AMOUNT4)) {
                setTitle("\u00AE " + PvpTitleColorConfig.TITLE_FOR_PVP_AMOUNT3 + " \u00AE");
                appearance.setTitleColor(PvpTitleColorConfig.NAME_COLOR_FOR_PVP_AMOUNT3);
            } else if ((pvpKills >= PvpTitleColorConfig.PVP_AMOUNT4) && (pvpKills < PvpTitleColorConfig.PVP_AMOUNT5)) {
                setTitle("\u00AE " + PvpTitleColorConfig.TITLE_FOR_PVP_AMOUNT4 + " \u00AE");
                appearance.setTitleColor(PvpTitleColorConfig.NAME_COLOR_FOR_PVP_AMOUNT4);
            } else if (pvpKills >= PvpTitleColorConfig.PVP_AMOUNT5) {
                setTitle("\u00AE " + PvpTitleColorConfig.TITLE_FOR_PVP_AMOUNT5 + " \u00AE");
                appearance.setTitleColor(PvpTitleColorConfig.NAME_COLOR_FOR_PVP_AMOUNT5);
            }

            if (broadcastInfo) {
                broadcastTitleInfo();
            }
        }
    }

    public void updatePvPStatus() {
        if (isInsideZone(ZoneId.PVP)) {
            return;
        }

        setPvpFlagLasts(ClockBridge.gameMillis() + PvpConfig.PVP_NORMAL_TIME);
        if (pvpFlag == 0) {
            startPvPFlag();
        }
    }

    public void updatePvPStatus(Creature target) {
        final Player targetPlayer = target.asPlayer();
        if (targetPlayer == null) {
            return;
        }

        if (this == targetPlayer) {
            return;
        }

        if (FactionSystemConfig.FACTION_SYSTEM_ENABLED
                && target.isPlayer()
                && ((isGood() && targetPlayer.isEvil()) || (isEvil() && targetPlayer.isGood()))) {
            return;
        }

        if (inDuel && (targetPlayer.getDuelId() == getDuelId())) {
            return;
        }

        if ((!isInsideZone(ZoneId.PVP) || !target.isInsideZone(ZoneId.PVP)) && (targetPlayer.getKarma() == 0)) {
            if (checkIfPvP(targetPlayer)) {
                setPvpFlagLasts(ClockBridge.gameMillis() + PvpConfig.PVP_PVP_TIME);
            } else {
                setPvpFlagLasts(ClockBridge.gameMillis() + PvpConfig.PVP_NORMAL_TIME);
            }

            if (pvpFlag == 0) {
                startPvPFlag();
            }
        }
    }

    /**
     * @return {@code true} if player has Lucky effect and is level 9 or less
     */
    public boolean isLucky() {
        return (getLevel() <= 9) && isAffectedBySkill(CommonSkill.LUCKY.getId());
    }

    /**
     * Restore the specified % of experience this Player has lost and sends a Server->Client StatusUpdate packet.
     * @param restorePercent
     */
    public void restoreExp(double restorePercent) {
        if (expBeforeDeath > 0) {
            // Restore the specified % of lost experience.
            getStat().addExp(Math.round(((expBeforeDeath - getExp()) * restorePercent) / 100));
            setExpBeforeDeath(0);
        }
    }

    /**
     * Reduce the Experience (and level if necessary) of the Player in function of the calculated Death Penalty.<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <li>Calculate the Experience loss</li>
     * <li>Set the value of expBeforeDeath</li>
     * <li>Set the new Experience value of the Player and Decrease its level if necessary</li>
     * <li>Send a Server->Client StatusUpdate packet with its new Experience</li><br>
     * @param killer
     * @param atWar
     */
    public void calculateDeathExpPenalty(Creature killer, boolean atWar) {
        final int level = getLevel();
        double percentLost = ExperienceLossData.getInstance().getPercentLost(getLevel());

        // Calculate reduce exp lost stats.
        if (killer != null) {
            if (killer.isRaid()) {
                percentLost *= calcStat(Stat.REDUCE_EXP_LOST_BY_RAID, 1);
            } else if (killer.isMonster()) {
                percentLost *= calcStat(Stat.REDUCE_EXP_LOST_BY_MOB, 1);
            } else if (killer.isPlayable()) {
                percentLost *= calcStat(Stat.REDUCE_EXP_LOST_BY_PVP, 1);
            }
        }

        // Calculate karma loss configuration.
        if (getKarma() > 0) {
            percentLost *= RatesConfig.RATE_KARMA_EXP_LOST;
        }

        // Get the total exp for the current player level.
        long currentLevelExp = 0;
        if (!isOnEvent()) {
            final int maxLevel = ExperienceData.getInstance().getMaxLevel();
            if (level < maxLevel) {
                currentLevelExp =
                        getStat().getExpForLevel(level + 1) - getStat().getExpForLevel(level);
            } else {
                currentLevelExp = getStat().getExpForLevel(maxLevel) - getStat().getExpForLevel(maxLevel - 1);
            }
        }

        // Calculate the experience loss.
        long lostExp = 0;
        if (currentLevelExp > 0) {
            lostExp = Math.round((currentLevelExp * percentLost) / 100);

            // Capping loss to 10% of current level experience.
            lostExp = Math.min(lostExp, currentLevelExp / 10);

            // Reduce experience lost for festival and war kills.
            if (isFestivalParticipant() || atWar) {
                lostExp /= 4;
            }

            // No exp loss for Advent Blessing.
            if (nevitSystem.isAdventBlessingActive()) {
                lostExp = 0;
            }
        }

        setExpBeforeDeath(getExp());
        getStat().removeExp(lostExp);
    }

    public boolean isPartyWaiting() {
        return PartyMatchWaitingList.getInstance().getPlayers().contains(this);
    }

    public boolean isInPartyMatchRoom() {
        return partyRoom > 0;
    }

    /**
     * Stop the HP/MP/CP Regeneration task. <b><u>Actions</u>:</b>
     * <li>Set the RegenActive flag to False</li>
     * <li>Stop the HP/MP/CP Regeneration task</li>
     */
    public void stopAllTimers() {
        stopHpMpRegeneration();
        stopWarnUserTakeBreak();
        stopWaterTask();
        stopFeed();
        storePetFood(mountNpcId);
        stopRentPet();
        stopPvpRegTask();
        stopSoulTask();
        stopChargeTask();
        stopFameTask();
        stopVitalityTask();
        stopNevitHourglassTask();
        stopRecoGiveTask();
    }

    /**
     * @return {@code true} if the character has a summon, {@code false} otherwise
     */
    public boolean hasSummon() {
        return summon != null;
    }

    /**
     * @return {@code true} if the character has a pet, {@code false} otherwise
     */
    public boolean hasPet() {
        return (summon != null) && summon.isPet();
    }

    /**
     * @return {@code true} if the character has a servitor, {@code false} otherwise
     */
    public boolean hasServitor() {
        return (summon != null) && summon.isServitor();
    }

    /**
     * @return the Summon of the Player or null.
     */
    public Summon getSummon() {
        return summon;
    }

    /**
     * Set the Summon of the Player.
     * @param summon
     */
    public void setPet(Summon summon) {
        this.summon = summon;
    }

    /**
     * Set the Summon of the Player.
     * @param tamedBeast
     */
    public void addTrainedBeast(TamedBeast tamedBeast) {
        if (trainedBeasts == null) {
            trainedBeasts = ConcurrentHashMap.newKeySet();
        }

        trainedBeasts.add(tamedBeast);
    }

    /**
     * @return the Player requester of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).
     */
    public Player getActiveRequester() {
        final Player requester = activeRequester;
        if ((requester != null) && requester.isRequestExpired() && (activeTradeList == null)) {
            activeRequester = null;
        }

        return activeRequester;
    }

    /**
     * @return True if a transaction is in progress.
     */
    public boolean isProcessingRequest() {
        return (getActiveRequester() != null)
                || (requestExpireTime > GameTimeTaskManager.getInstance().getGameTicks());
    }

    /**
     * @return True if a transaction is in progress.
     */
    public boolean isProcessingTransaction() {
        return (getActiveRequester() != null)
                || (activeTradeList != null)
                || (requestExpireTime > GameTimeTaskManager.getInstance().getGameTicks());
    }

    /**
     * Select the Warehouse to be used in next activity.
     * @param partner
     */
    public void onTransactionRequest(Player partner) {
        requestExpireTime = GameTimeTaskManager.getInstance().getGameTicks()
                + (REQUEST_TIMEOUT * GameTimeTaskManager.TICKS_PER_SECOND);
        partner.setActiveRequester(this);
    }

    /**
     * Return true if last request is expired.
     * @return
     */
    public boolean isRequestExpired() {
        return requestExpireTime <= GameTimeTaskManager.getInstance().getGameTicks();
    }

    /**
     * Select the Warehouse to be used in next activity.
     */
    public void onTransactionResponse() {
        requestExpireTime = 0;
    }

    public void onTradeStart(Player partner) {
        activeTradeList = new TradeList(this);
        activeTradeList.setPartner(partner);

        final SystemMessage msg = new SystemMessage(SystemMessageId.YOU_BEGIN_TRADING_WITH_C1);
        msg.addPcName(partner);
        sendPacket(msg);
        sendPacket(new TradeStart(this));
    }

    public void onTradeConfirm(Player partner) {
        final SystemMessage msg = new SystemMessage(SystemMessageId.C1_HAS_CONFIRMED_THE_TRADE);
        msg.addPcName(partner);
        sendPacket(msg);
        sendPacket(TradeOtherDone.STATIC_PACKET);
    }

    public void onTradeCancel(Player partner) {
        if (activeTradeList == null) {
            return;
        }

        activeTradeList.lock();
        activeTradeList = null;
        sendPacket(new TradeDone(0));
        final SystemMessage msg = new SystemMessage(SystemMessageId.C1_HAS_CANCELLED_THE_TRADE);
        msg.addPcName(partner);
        sendPacket(msg);
    }

    public void onTradeFinish(boolean successfull) {
        activeTradeList = null;
        sendPacket(new TradeDone(1));
        if (successfull) {
            sendPacket(SystemMessageId.YOUR_TRADE_WAS_SUCCESSFUL);
        }
    }

    public void startTrade(Player partner) {
        onTradeStart(partner);
        partner.onTradeStart(this);
    }

    public void cancelActiveTrade() {
        if (activeTradeList == null) {
            return;
        }

        final Player partner = activeTradeList.getPartner();
        if (partner != null) {
            partner.onTradeCancel(this);
        }

        onTradeCancel(this);
    }

    public boolean hasManufactureShop() {
        return (manufactureItems != null) && !manufactureItems.isEmpty();
    }

    /**
     * Get the manufacture items map of this player.
     * @return the the manufacture items map
     */
    public Map<Integer, ManufactureItem> getManufactureItems() {
        if (manufactureItems == null) {
            synchronized (this) {
                manufactureItems = Collections.synchronizedMap(new LinkedHashMap<>());
            }
        }

        return manufactureItems;
    }

    /**
     * Set the store name.
     * @param name the store name to set
     */
    public void setStoreName(String name) {
        storeName = name == null ? "" : name;
    }

    /**
     * @return the buyList object of the Player.
     */
    public TradeList getSellList() {
        if (sellList == null) {
            sellList = new TradeList(this);
        }

        return sellList;
    }

    /**
     * @return the buyList object of the Player.
     */
    public TradeList getBuyList() {
        if (buyList == null) {
            buyList = new TradeList(this);
        }

        return buyList;
    }

    /**
     * Set the Private Store type of the Player. <b><u>Values</u>:</b>
     * <li>0 : STORE_PRIVATE_NONE</li>
     * <li>1 : STORE_PRIVATE_SELL</li>
     * <li>2 : sellmanage</li>
     * <li>3 : STORE_PRIVATE_BUY</li>
     * <li>4 : buymanage</li>
     * <li>5 : STORE_PRIVATE_MANUFACTURE</li><br>
     * @param privateStoreType
     */
    public void setPrivateStoreType(PrivateStoreType privateStoreType) {
        this.privateStoreType = privateStoreType;
        if (OfflineTradeConfig.OFFLINE_DISCONNECT_FINISHED
                && (privateStoreType == PrivateStoreType.NONE)
                && ((client == null) || client.isDetached())) {
            OfflineTraderTable.getInstance().removeTrader(getObjectId());
            Disconnection.of(this).storeAndDelete();
        }
    }

    /**
     * Set the clan object, clanId, _clanLeader Flag and title of the Player.
     * @param clan
     */
    public void setClan(Clan clan) {
        this.clan = clan;
        if (clan == null) {
            setTitle("");
            clanId = 0;
            clanPrivileges = new ClanPrivileges();
            pledgeType = 0;
            powerGrade = 0;
            lvlJoinedAcademy = 0;
            apprentice = 0;
            sponsor = 0;
            activeWarehouse = null;
            return;
        }

        if (!clan.isMember(getObjectId())) {
            // Char has been kicked from clan.
            setClan(null);
            return;
        }

        clanId = clan.getId();
    }

    /**
     * @return True if the Player is the leader of its clan.
     */
    public boolean isClanLeader() {
        return (clan != null) && (getObjectId() == clan.getLeaderId());
    }

    /**
     * Reduce the number of arrows/bolts owned by the Player and send it Server->Client Packet InventoryUpdate or ItemList (to unequip if the last arrow was consummed).
     */
    @Override
    protected void reduceArrowCount(boolean bolts) {
        final Item arrows = inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
        if (arrows == null) {
            inventory.unEquipItemInSlot(Inventory.PAPERDOLL_LHAND);
            if (bolts) {
                boltItem = null;
            } else {
                arrowItem = null;
            }

            sendItemList(false);
            return;
        }

        // Adjust item quantity
        if (arrows.getCount() > 1) {
            synchronized (arrows) {
                arrows.changeCount(null, -1, this, null);
                arrows.setLastChange(Item.MODIFIED);
                inventory.refreshWeight();
            }
        } else {
            // Destroy entire item and save to database.
            inventory.destroyItem(ItemProcessType.NONE, arrows, this, null);
            inventory.unEquipItemInSlot(Inventory.PAPERDOLL_LHAND);
            if (bolts) {
                boltItem = null;
            } else {
                arrowItem = null;
            }

            sendItemList(false);
            return;
        }

        final InventoryUpdate iu = new InventoryUpdate();
        iu.addModifiedItem(arrows);
        sendInventoryUpdate(iu);
    }

    /**
     * Equip arrows needed in left hand and send a Server->Client packet ItemList to the Player then return True.
     */
    @Override
    protected boolean checkAndEquipArrows() {
        // Check if nothing is equipped in left hand.
        if (inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND) == null) {
            // Get the Item of the arrows needed for this bow.
            arrowItem = inventory.findArrowForBow(getActiveWeaponItem());
            if (arrowItem != null) {
                // Equip arrows needed in left hand.
                inventory.setPaperdollItem(Inventory.PAPERDOLL_LHAND, arrowItem);

                // Send a Server->Client packet ItemList to this Player to update left hand equipement.
                sendItemList(false);
            }
        } else {
            // Get the Item of arrows equipped in left hand.
            arrowItem = inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
        }

        return arrowItem != null;
    }

    /**
     * Equip bolts needed in left hand and send a Server->Client packet ItemList to the Player then return True.
     */
    @Override
    protected boolean checkAndEquipBolts() {
        // Check if nothing is equipped in left hand.
        if (inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND) == null) {
            // Get the Item of the arrows needed for this bow.
            boltItem = inventory.findBoltForCrossBow(getActiveWeaponItem());
            if (boltItem != null) {
                // Equip arrows needed in left hand.
                inventory.setPaperdollItem(Inventory.PAPERDOLL_LHAND, boltItem);

                // Send a Server->Client packet ItemList to this Player to update left hand equipement.
                sendItemList(false);
            }
        } else {
            // Get the Item of arrows equipped in left hand.
            boltItem = inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
        }

        return boltItem != null;
    }

    /**
     * Disarm the player's weapon.
     * @return {@code true} if the player was disarmed or doesn't have a weapon to disarm, {@code false} otherwise.
     */
    public boolean disarmWeapons() {
        // If there is no weapon to disarm then return true.
        final Item wpn = inventory.getPaperdollItem(Inventory.PAPERDOLL_RHAND);
        if (wpn == null) {
            return true;
        }

        // Don't allow disarming a cursed weapon.
        if (isCursedWeaponEquipped()) {
            return false;
        }

        // Don't allow disarming a Combat Flag or Territory Ward.
        if (combatFlagEquipped) {
            return false;
        }

        // Don't allow disarming if the weapon is force equip.
        if (wpn.getWeaponItem().isForceEquip()) {
            return false;
        }

        final Collection<Item> unequipped =
                inventory.unEquipItemInBodySlotAndRecord(wpn.getTemplate().getBodyPart());
        final InventoryUpdate iu = new InventoryUpdate();
        for (Item itm : unequipped) {
            iu.addModifiedItem(itm);
        }

        sendInventoryUpdate(iu);

        abortAttack();
        broadcastUserInfo();

        // This can be 0 if the user pressed the right mousebutton twice very fast.
        if (!unequipped.isEmpty()) {
            final SystemMessage sm;
            final Item unequippedItem = unequipped.iterator().next();
            if (unequippedItem.isEnchanted()) {
                sm = new SystemMessage(SystemMessageId.THE_EQUIPMENT_S1_S2_HAS_BEEN_REMOVED);
                sm.addInt(unequippedItem.getEnchantLevel());
            } else {
                sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_DISARMED);
            }

            sm.addItemName(unequippedItem);
            sendPacket(sm);
        }

        return true;
    }

    /**
     * Disarm the player's shield.
     * @return {@code true}.
     */
    public boolean disarmShield() {
        final Item sld = inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
        if (sld != null) {
            final Collection<Item> unequipped =
                    inventory.unEquipItemInBodySlotAndRecord(sld.getTemplate().getBodyPart());
            final InventoryUpdate iu = new InventoryUpdate();
            for (Item itm : unequipped) {
                iu.addModifiedItem(itm);
            }

            sendInventoryUpdate(iu);

            abortAttack();
            broadcastUserInfo();

            // This can be 0 if the user pressed the right mousebutton twice very fast.
            if (!unequipped.isEmpty()) {
                SystemMessage sm = null;
                final Item unequippedItem = unequipped.iterator().next();
                if (unequippedItem.isEnchanted()) {
                    sm = new SystemMessage(SystemMessageId.THE_EQUIPMENT_S1_S2_HAS_BEEN_REMOVED);
                    sm.addInt(unequippedItem.getEnchantLevel());
                } else {
                    sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_DISARMED);
                }

                sm.addItemName(unequippedItem);
                sendPacket(sm);
            }
        }

        return true;
    }

    public boolean mount(Summon pet) {
        // If mounts (excluding wyvern) are disallowed during siege, check if player is in a siege zone.
        if (!FeatureConfig.ALLOW_MOUNTS_DURING_SIEGE && isInsideZone(ZoneId.SIEGE)) {
            final MountType type = MountType.findByNpcId(pet.getId());

            // Only allow mounting if the pet is a Strider and the player is the castle lord of the current siege
            // castle.
            final boolean isAllowed = (type == MountType.STRIDER)
                    && (CastleManager.getInstance().getCastle(this) != null)
                    && isClanLeader()
                    && (getClanId()
                            == CastleManager.getInstance().getCastle(this).getOwnerId());

            if (!isAllowed) {
                sendMessage("You cannot mount during a siege.");
                return false;
            }
        }

        if (!disarmWeapons() || !disarmShield() || isTransformed()) {
            return false;
        }

        getEffectList().stopAllToggles();
        setMount(pet.getId(), pet.getLevel());
        setMountObjectID(pet.getControlObjectId());
        startFeed(pet.getId());
        broadcastPacket(new Ride(this));

        // Notify self and others about speed change.
        broadcastUserInfo();

        pet.unSummon(this);
        return true;
    }

    public boolean mount(int npcId, int controlItemObjId, boolean useFood) {
        if (!disarmWeapons() || !disarmShield() || isTransformed()) {
            return false;
        }

        getEffectList().stopAllToggles();
        setMount(npcId, getLevel());
        setMountObjectID(controlItemObjId);
        broadcastPacket(new Ride(this));

        // Notify self and others about speed change.
        broadcastUserInfo();
        if (useFood) {
            startFeed(npcId);
        }

        return true;
    }

    public boolean mountPlayer(Summon pet) {
        if ((pet != null) && pet.isMountable() && !isMounted() && !isBetrayed()) {
            if (isDead()) {
                // A strider cannot be ridden when dead.
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_STRIDER_CANNOT_BE_RIDDEN_WHEN_DEAD);
                return false;
            } else if (pet.isDead()) {
                // A dead strider cannot be ridden.
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_DEAD_STRIDER_CANNOT_BE_RIDDEN);
                return false;
            } else if (pet.isInCombat() || pet.isRooted()) {
                // A strider in battle cannot be ridden.
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_STRIDER_IN_BATTLE_CANNOT_BE_RIDDEN);
                return false;
            } else if (isInCombat()) {
                // A strider cannot be ridden while in battle.
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_STRIDER_CANNOT_BE_RIDDEN_WHILE_IN_BATTLE);
                return false;
            } else if (sitting) {
                // A strider can be ridden only when standing.
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_STRIDER_CAN_BE_RIDDEN_ONLY_WHEN_STANDING);
                return false;
            } else if (fishing) {
                // You can't mount, dismount, break and drop items while fishing.
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.YOU_CANNOT_DO_THAT_WHILE_FISHING_2);
                return false;
            } else if (isTransformed() || isCursedWeaponEquipped()) {
                // No message needed, player while transformed doesn't have mount action.
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            } else if (inventory.getItemByItemId(9819) != null) {
                sendPacket(ActionFailed.STATIC_PACKET);

                // FIXME: Wrong Message
                sendMessage("You cannot mount a steed while holding a flag.");
                return false;
            } else if (pet.isHungry()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_HUNGRY_STRIDER_CANNOT_BE_MOUNTED_OR_DISMOUNTED);
                return false;
            } else if (!LocationUtil.checkIfInRange(200, this, pet, true)) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.YOU_ARE_TOO_FAR_AWAY_FROM_YOUR_MOUNT_TO_RIDE);
                return false;
            } else if (!pet.isDead() && !isMounted()) {
                mount(pet);
            }
        } else if (isRentedPet()) {
            stopRentPet();
        } else if (isMounted()) {
            if ((mountType == MountType.WYVERN) && isInsideZone(ZoneId.NO_LANDING) && !isGM()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.YOU_ARE_NOT_ALLOWED_TO_DISMOUNT_IN_THIS_LOCATION);
                return false;
            } else if (isHungry()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_HUNGRY_STRIDER_CANNOT_BE_MOUNTED_OR_DISMOUNTED);
                return false;
            } else {
                dismount();
            }
        }

        return true;
    }

    public boolean dismount() {
        if (ZoneManager.getInstance().getZone(getX(), getY(), getZ() - 300, WaterZone.class) == null) {
            if (!isGM() && !isInWater() && (getZ() > 10000)) {
                sendPacket(SystemMessageId.YOU_ARE_NOT_ALLOWED_TO_DISMOUNT_IN_THIS_LOCATION);
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            // Skip elevation check only in NoLandingZone.
            if (!isInsideZone(ZoneId.NO_LANDING)
                    && ((GeoEngine.getInstance().getHeight(getX(), getY(), getZ()) + 300) < getZ())) {
                sendPacket(SystemMessageId.YOU_CANNOT_DISMOUNT_FROM_THIS_ELEVATION);
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }
        } else {
            ThreadPool.schedule(
                    () -> {
                        if (isInWater()) {
                            broadcastUserInfo();
                        }
                    },
                    1500);
        }

        final boolean wasFlying = isFlying();
        sendPacket(new SetupGauge(getObjectId(), 3, 0, 0));
        final int petId = mountNpcId;
        setMount(0, 0);
        stopFeed();
        if (wasFlying) {
            removeSkill(CommonSkill.WYVERN_BREATH.getSkill());
        }

        broadcastPacket(new Ride(this));
        setMountObjectID(0);
        storePetFood(petId);

        // Notify self and others about speed change.
        broadcastUserInfo();

        return true;
    }

    public long getUptime() {
        return ClockBridge.gameMillis() - uptime;
    }

    /**
     * Return True if the Player is invulnerable.
     */
    @Override
    public boolean isInvul() {
        return super.isInvul() || isTeleportProtected();
    }

    /**
     * Return True if the Player has a Party in progress.
     */
    @Override
    public boolean isInParty() {
        return party != null;
    }

    /**
     * Set the party object of the Player AND join it.
     * @param party
     */
    public void joinParty(Party party) {
        if (party != null) {
            // First set the party otherwise this wouldn't be considered.
            // as in a party into the Creature.updateEffectIcons() call.
            this.party = party;
            party.addPartyMember(this);
        }
    }

    /**
     * Manage the Leave Party task of the Player.
     */
    public void leaveParty() {
        if (isInParty()) {
            party.removePartyMember(this, PartyMessageType.DISCONNECTED);
            party = null;
        }
    }

    /**
     * Return True if the Player is a GM.
     */
    @Override
    public boolean isGM() {
        return getAccessLevel().isGm();
    }

    /**
     * Set the accessLevel of the Player.
     * @param level
     */
    public void setAccessLevel(int level) {
        accessLevel = AdminData.getInstance().getAccessLevel(level);
        appearance.setNameColor(accessLevel.getNameColor());
        appearance.setTitleColor(accessLevel.getTitleColor());
        broadcastUserInfo();

        CharInfoTable.getInstance().addName(this);

        if (!AdminData.getInstance().hasAccessLevel(level)) {
            log.warn(
                    "Tried to set unregistered access level {} for {}. Setting access level without privileges!",
                    level,
                    this);
        } else if (level > 0) {
            log.warn(
                    "{} access level set for character {}! Just a warning to be careful ;)",
                    accessLevel.getName(),
                    getName());
        }
    }

    public void setAccountAccesslevel(int level) {
        LoginServerThread.getInstance().sendAccessLevel(getAccountName(), level);
    }

    /**
     * @return the accessLevel of the Player.
     */
    @Override
    public AccessLevel getAccessLevel() {
        if (GeneralConfig.EVERYBODY_HAS_ADMIN_RIGHTS) {
            return AdminData.getInstance().getMasterAccessLevel();
        }

        if (accessLevel == null) {
            setAccessLevel(0);
        }

        return accessLevel;
    }

    /**
     * Update Stats of the Player client side by sending Server->Client packet UserInfo/StatusUpdate to this Player and CharInfo/StatusUpdate to all known players (broadcast).
     */
    public void updateAndBroadcastStatus() {
        if (updateAndBroadcastStatusTask == null) {
            updateAndBroadcastStatusTask = ThreadPool.schedule(
                    () -> {
                        refreshOverloaded();
                        refreshExpertisePenalty();

                        // Send a Server->Client packet UserInfo to this Player and CharInfo to all known players
                        // (broadcast).
                        broadcastUserInfo();

                        updateAndBroadcastStatusTask = null;
                    },
                    50);
        }
    }

    /**
     * Send a Server->Client StatusUpdate packet with Karma and PvP Flag to the Player and all Player to inform (broadcast).
     */
    public void setKarmaFlag() {
        updateUserInfo();
        World.forEachVisibleObject(this, Player.class, player -> {
            player.sendPacket(new RelationChanged(this, getRelation(player), isAutoAttackable(player)));
            if (hasSummon()) {
                player.sendPacket(new RelationChanged(summon, getRelation(player), isAutoAttackable(player)));
            }
        });
    }

    /**
     * Send a Server->Client StatusUpdate packet with Karma to the Player and all Player to inform (broadcast).
     */
    public void broadcastKarma() {
        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.KARMA, getKarma());
        sendPacket(su);

        World.forEachVisibleObject(this, Player.class, player -> {
            player.sendPacket(new RelationChanged(this, getRelation(player), isAutoAttackable(player)));
            if (hasSummon()) {
                player.sendPacket(new RelationChanged(summon, getRelation(player), isAutoAttackable(player)));
            }
        });
    }

    /**
     * Set the online Flag to True or False and update the characters table of the database with online status and lastAccess (called when login and logout).
     * @param isOnline
     * @param updateInDb
     */
    public void setOnlineStatus(boolean isOnline, boolean updateInDb) {
        if (online != isOnline) {
            online = isOnline;
        }

        // Update the characters table of the database with online status and lastAccess (called when login and logout).
        if (updateInDb) {
            updateOnlineStatus();
        }
    }

    /**
     * Update the characters table of the database with online status and lastAccess of this Player (called when login and logout).
     */
    public void updateOnlineStatus() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE characters SET online=?, lastAccess=? WHERE charId=?")) {
            ps.setInt(1, isOnlineInt());
            ps.setLong(2, ClockBridge.wallMillis());
            ps.setInt(3, getObjectId());
            ps.execute();
        } catch (Exception e) {
            log.error("Failed updating character online status.", e);
        }
    }

    /**
     * Create a new player in the characters table of the database.
     * @return
     */
    private boolean createDb() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(INSERT_CHARACTER)) {
            ps.setString(1, accountNamePlayer);
            ps.setInt(2, getObjectId());
            ps.setString(3, getName());
            ps.setInt(4, getLevel());
            ps.setInt(5, getMaxHp());
            ps.setDouble(6, getCurrentHp());
            ps.setInt(7, getMaxCp());
            ps.setDouble(8, getCurrentCp());
            ps.setInt(9, getMaxMp());
            ps.setDouble(10, getCurrentMp());
            ps.setInt(11, appearance.getFace());
            ps.setInt(12, appearance.getHairStyle());
            ps.setInt(13, appearance.getHairColor());
            ps.setInt(14, appearance.isFemale() ? 1 : 0);
            ps.setLong(15, getExp());
            ps.setLong(16, getSp());
            ps.setInt(17, getKarma());
            ps.setInt(18, fame);
            ps.setInt(19, pvpKills);
            ps.setInt(20, pkKills);
            ps.setInt(21, clanId);
            ps.setInt(22, getRace().ordinal());
            ps.setInt(23, getPlayerClass().getId());
            ps.setLong(24, deleteTimer);
            ps.setInt(25, hasDwarvenCraft() ? 1 : 0);
            ps.setString(26, getTitle());
            ps.setInt(27, appearance.getTitleColor());
            ps.setInt(28, getAccessLevel().getLevel());
            ps.setInt(29, isOnlineInt());
            ps.setInt(30, in7sDungeon ? 1 : 0);
            ps.setInt(31, clanPrivileges.getMask());
            ps.setInt(32, wantsPeace);
            ps.setInt(33, baseClass);
            ps.setInt(34, newbie);
            ps.setInt(35, noble ? 1 : 0);
            ps.setLong(36, 0);
            ps.setTimestamp(37, new Timestamp(createDate.getTimeInMillis()));
            ps.setLong(38, ClockBridge.wallMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Could not insert char data: {}", e.getMessage(), e);
            return false;
        }

        return true;
    }

    /**
     * Retrieve a Player from the characters table of the database and add it in _allObjects of the L2world. <b><u>Actions</u>:</b>
     * <li>Retrieve the Player from the characters table of the database</li>
     * <li>Add the Player object in _allObjects</li>
     * <li>Set the x,y,z position of the Player and make it invisible</li>
     * <li>Update the overloaded status of the Player</li><br>
     * @param objectId Identifier of the object to initialized
     * @return The Player loaded from the database
     */
    private static Player restore(int objectId) {
        Player player = null;
        double currentCp = 0;
        double currentHp = 0;
        double currentMp = 0;
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(RESTORE_CHARACTER)) {
            // Retrieve the Player from the characters table of the database.
            ps.setInt(1, objectId);
            try (ResultSet rset = ps.executeQuery()) {
                if (rset.next()) {
                    final int activeClassId = rset.getInt("classid");
                    final boolean female = rset.getInt("sex") != Sex.MALE.ordinal();
                    final PlayerTemplate template =
                            PlayerTemplateData.getInstance().getTemplate(activeClassId);
                    final PlayerAppearance app = new PlayerAppearance(
                            rset.getByte("face"), rset.getByte("hairColor"), rset.getByte("hairStyle"), female);
                    player = new Player(objectId, template, rset.getString("account_name"), app);
                    player.setName(rset.getString("char_name"));
                    player.setLastAccess(rset.getLong("lastAccess"));

                    final PlayerStat stat = player.getStat();
                    stat.setExp(rset.getLong("exp"));
                    player.setExpBeforeDeath(rset.getLong("expBeforeDeath"));
                    stat.setLevel(rset.getByte("level"));
                    stat.setSp(rset.getLong("sp"));

                    player.setWantsPeace(rset.getInt("wantspeace"));

                    player.setHeading(rset.getInt("heading"));

                    player.setKarma(rset.getInt("karma"));
                    player.setFame(rset.getInt("fame"));
                    player.setPvpKills(rset.getInt("pvpkills"));
                    player.setPkKills(rset.getInt("pkkills"));
                    player.setOnlineTime(rset.getLong("onlinetime"));
                    player.setNewbie(rset.getInt("newbie"));
                    player.setNoble(rset.getInt("nobless") == 1);

                    final int factionId = rset.getInt("faction");
                    if (factionId == 1) {
                        player.setGood();
                    }

                    if (factionId == 2) {
                        player.setEvil();
                    }

                    player.setClanJoinExpiryTime(rset.getLong("clan_join_expiry_time"));
                    if (player.getClanJoinExpiryTime() < ClockBridge.wallMillis()) {
                        player.setClanJoinExpiryTime(0);
                    }

                    player.setClanCreateExpiryTime(rset.getLong("clan_create_expiry_time"));
                    if (player.getClanCreateExpiryTime() < ClockBridge.wallMillis()) {
                        player.setClanCreateExpiryTime(0);
                    }

                    player.setPcCafePoints(rset.getInt("pccafe_points"));
                    player.setPowerGrade(rset.getInt("power_grade"));
                    player.setPledgeType(rset.getInt("subpledge"));
                    // player.setApprentice(rs.getInt("apprentice"));
                    player.setDeleteTimer(rset.getLong("deletetime"));
                    player.setTitle(rset.getString("title"));
                    player.setAccessLevel(rset.getInt("accesslevel"));
                    final int titleColor = rset.getInt("title_color");
                    if (titleColor != PlayerAppearance.DEFAULT_TITLE_COLOR) {
                        player.getAppearance().setTitleColor(titleColor);
                    }

                    player.setFistsWeaponItem(player.findFistsWeaponItem(activeClassId));
                    player.setUptime(ClockBridge.gameMillis());

                    currentHp = rset.getDouble("curHp");
                    currentCp = rset.getDouble("curCp");
                    currentMp = rset.getDouble("curMp");
                    player.classIndex = 0;
                    try {
                        player.setBaseClass(rset.getInt("base_class"));
                    } catch (Exception e) {
                        // TODO: Should this be logged?
                        player.setBaseClass(activeClassId);
                    }

                    // Restore Subclass Data (cannot be done earlier in function).
                    if (restoreSubClassData(player) && (activeClassId != player.getBaseClass())) {
                        for (SubClassHolder subClass : player.getSubClasses().values()) {
                            if (subClass.getId() == activeClassId) {
                                player.classIndex = subClass.getClassIndex();
                            }
                        }
                    }

                    if ((player.getClassIndex() == 0) && (activeClassId != player.getBaseClass())) {
                        // Subclass in use but doesn't exist in DB -
                        // A possible restart-while-modifysubclass cheat has been attempted.
                        // Switching to use base class.
                        player.setPlayerClass(player.getBaseClass());
                        log.warn(
                                "{} reverted to base class. Possibly has tried a relogin exploit while subclassing.",
                                player);
                    } else {
                        player.activeClass = activeClassId;
                    }

                    player.setApprentice(rset.getInt("apprentice"));
                    player.setSponsor(rset.getInt("sponsor"));
                    player.setLvlJoinedAcademy(rset.getInt("lvl_joined_academy"));
                    player.setIn7sDungeon(rset.getInt("isin7sdungeon") == 1);
                    CursedWeaponsManager.getInstance().checkPlayer(player);

                    player.setDeathPenaltyBuffLevel(rset.getInt("death_penalty_level"));

                    player.setVitalityPoints(rset.getInt("vitality_points"), true);

                    // Set the x,y,z position of the Player and make it invisible.
                    final int x = rset.getInt("x");
                    final int y = rset.getInt("y");
                    final int z = GeoEngine.getInstance().getHeight(x, y, rset.getInt("z"));
                    player.setXYZInvisible(x, y, z);
                    player.setLastServerPosition(x, y, z);

                    // Set Teleport Bookmark Slot.
                    player.setBookmarkSlot(rset.getInt("BookmarkSlot"));

                    // Character creation Time.
                    player.getCreateDate()
                            .setTimeInMillis(rset.getTimestamp("createDate").getTime());

                    // Language
                    player.setLang(rset.getString("language"));

                    // Set Hero status if it applies
                    player.setHero(Hero.getInstance().isHero(objectId));

                    final int clanId = rset.getInt("clanid");
                    Clan clan = null;
                    if (clanId > 0) {
                        clan = ClanTable.getInstance().getClan(clanId);
                        player.setClan(clan);
                        if ((clan != null) && clan.isMember(objectId)) {
                            if (clan.getLeaderId() != player.getObjectId()) {
                                if (player.getPowerGrade() == 0) {
                                    player.setPowerGrade(5);
                                }

                                player.setClanPrivileges(clan.getRankPrivs(player.getPowerGrade()));
                            } else {
                                player.getClanPrivileges().enableAll();
                                player.setPowerGrade(1);
                            }

                            player.setPledgeClass(ClanMember.calculatePledgeClass(player));
                        }
                    }

                    if (clan == null) {
                        if (player.isNoble()) {
                            player.setPledgeClass(5);
                        }

                        if (player.isHero()) {
                            player.setPledgeClass(8);
                        }

                        player.getClanPrivileges().disableAll();
                    }

                    // Retrieve the name and ID of the other characters assigned to this account.
                    try (PreparedStatement stmt = con.prepareStatement(
                            "SELECT charId, char_name FROM characters WHERE account_name=? AND charId<>?")) {
                        stmt.setString(1, player.accountNamePlayer);
                        stmt.setInt(2, objectId);
                        try (ResultSet chars = stmt.executeQuery()) {
                            while (chars.next()) {
                                player.accountChars.put(chars.getInt("charId"), chars.getString("char_name"));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed loading character.", e);
        }

        if (player == null) {
            return null;
        }

        // Retrieve from the database all items of this Player and add them to inventory.
        player.getInventory().restore();
        player.getWarehouse().restore();
        player.getFreight().restore();

        // Retrieve from the database all skills of this Player and add them to skills.
        player.restoreSkills();

        // Retrieve from the database all macros of this Player and add them to macros.
        player.getMacros().restoreMe();

        // Retrieve from the database all shortcuts of this Player and add them to shortcuts.
        player.restoreShortcuts();

        // Retrieve from the database all henna of this Player and add them to henna.
        player.restoreHenna();

        // Retrieve from the database all teleport bookmark of this Player and add them to _tpbookmark.
        player.restoreTeleportBookmark();

        // Retrieve from the database the recipe book of this Player.
        player.restoreRecipeBook(true);

        // Restore Recipe Shop list.
        if (PlayerConfig.STORE_RECIPE_SHOPLIST) {
            player.restoreRecipeShopList();
        }

        // Load Premium Item List.
        player.loadPremiumItemList();

        // Restore items in pet inventory.
        player.restorePetInventoryItems();

        // Reward auto-get skills and all available skills if auto-learn skills is true.
        player.rewardSkills();

        player.restoreItemReuse();

        // Restore current Cp, HP and MP values.
        player.setCurrentCp(currentCp);
        player.setCurrentHp(currentHp);
        player.setCurrentMp(currentMp);

        player.setOriginalCpHpMp(currentCp, currentHp, currentMp);
        if (currentHp < 0.5) {
            player.setDead(true);
            player.stopHpMpRegeneration();
        }

        // Restore pet if exists in the world.
        player.setPet(World.getPet(player.getObjectId()));
        if (player.hasSummon()) {
            player.getSummon().setOwner(player);
        }

        // Update the overloaded status of the Player.
        player.refreshOverloaded();

        // Update the expertise status of the Player.
        player.refreshExpertisePenalty();

        player.restoreFriendList();

        player.setOnlineStatus(true, false);

        PlayerAutoSaveTaskManager.getInstance().add(player);

        return player;
    }

    public Forum getMail() {
        if (forumMail == null) {
            setMail(ForumsBBSManager.getInstance().getForumByName("MailRoot").getChildByName(getName()));

            if (forumMail == null) {
                ForumsBBSManager.getInstance()
                        .createNewForum(
                                getName(),
                                ForumsBBSManager.getInstance().getForumByName("MailRoot"),
                                Forum.MAIL,
                                Forum.OWNERONLY,
                                getObjectId());
                setMail(ForumsBBSManager.getInstance()
                        .getForumByName("MailRoot")
                        .getChildByName(getName()));
            }
        }

        return forumMail;
    }

    public void setMail(Forum forum) {
        forumMail = forum;
    }

    public Forum getMemo() {
        if (forumMemo == null) {
            setMemo(ForumsBBSManager.getInstance().getForumByName("MemoRoot").getChildByName(accountNamePlayer));

            if (forumMemo == null) {
                ForumsBBSManager.getInstance()
                        .createNewForum(
                                accountNamePlayer,
                                ForumsBBSManager.getInstance().getForumByName("MemoRoot"),
                                Forum.MEMO,
                                Forum.OWNERONLY,
                                getObjectId());
                setMemo(ForumsBBSManager.getInstance()
                        .getForumByName("MemoRoot")
                        .getChildByName(accountNamePlayer));
            }
        }

        return forumMemo;
    }

    public void setMemo(Forum forum) {
        forumMemo = forum;
    }

    /**
     * Restores sub-class data for the Player, used to check the current class index for the character.
     * @param player
     * @return
     */
    private static boolean restoreSubClassData(Player player) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(RESTORE_CHAR_SUBCLASSES)) {
            ps.setInt(1, player.getObjectId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final SubClassHolder subClass = new SubClassHolder();
                    subClass.setPlayerClass(rs.getInt("class_id"));
                    subClass.setLevel(rs.getByte("level"));
                    subClass.setExp(rs.getLong("exp"));
                    subClass.setSp(rs.getLong("sp"));
                    subClass.setClassIndex(rs.getInt("class_index"));

                    // Enforce the correct indexing of subClasses against their class indexes.
                    player.getSubClasses().put(subClass.getClassIndex(), subClass);
                }
            }
        } catch (Exception e) {
            log.warn("Could not restore classes for {}: {}", player.getName(), e.getMessage(), e);
        }

        return true;
    }

    /**
     * Restore recipe book data for this Player.
     * @param loadCommon
     */
    private void restoreRecipeBook(boolean loadCommon) {
        final String sql = loadCommon
                ? "SELECT id, type, classIndex FROM character_recipebook WHERE charId=?"
                : "SELECT id FROM character_recipebook WHERE charId=? AND classIndex=? AND type = 1";
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, getObjectId());
            if (!loadCommon) {
                ps.setInt(2, classIndex);
            }

            try (ResultSet rs = ps.executeQuery()) {
                dwarvenRecipeBook.clear();

                RecipeList recipe;
                final RecipeData rd = RecipeData.getInstance();
                while (rs.next()) {
                    recipe = rd.getRecipeList(rs.getInt("id"));
                    if (loadCommon) {
                        if (rs.getInt(2) == 1) {
                            if (rs.getInt(3) == classIndex) {
                                registerDwarvenRecipeList(recipe, false);
                            }
                        } else {
                            registerCommonRecipeList(recipe, false);
                        }
                    } else {
                        registerDwarvenRecipeList(recipe, false);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Could not restore recipe book data:{}", e.getMessage(), e);
        }
    }

    private void loadPremiumItemList() {
        final String sql = "SELECT itemNum, itemId, itemCount, itemSender FROM character_premium_items WHERE charId=?";
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, getObjectId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    premiumItemList.put(
                            rs.getInt("itemNum"),
                            new PremiumItem(rs.getInt("itemId"), rs.getLong("itemCount"), rs.getString("itemSender")));
                }
            }
        } catch (Exception e) {
            log.error("Could not restore premium items: {}", e.getMessage(), e);
        }
    }

    public void updatePremiumItem(int itemNum, long newcount) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE character_premium_items SET itemCount=? WHERE charId=? AND itemNum=? ")) {
            ps.setLong(1, newcount);
            ps.setInt(2, getObjectId());
            ps.setInt(3, itemNum);
            ps.execute();
        } catch (Exception e) {
            log.error("Could not update premium items: {}", e.getMessage(), e);
        }
    }

    public void deletePremiumItem(int itemNum) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("DELETE FROM character_premium_items WHERE charId=? AND itemNum=? ")) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, itemNum);
            ps.execute();
        } catch (Exception e) {
            log.error("Could not delete premium item", e);
        }
    }

    /**
     * Update Player stats in the characters table of the database.
     * @param storeActiveEffects
     */
    public synchronized void store(boolean storeActiveEffects) {
        storeCharBase();
        storeCharSub();
        storeEffect(storeActiveEffects);
        storeItemReuseDelay();

        if (PlayerConfig.STORE_RECIPE_SHOPLIST) {
            storeRecipeShopList();
        }

        SevenSigns.getInstance().saveSevenSignsData(getObjectId());

        final PlayerVariables vars = getScript(PlayerVariables.class);
        if (vars != null) {
            vars.storeMe();
        }

        final AccountVariables aVars = getScript(AccountVariables.class);
        if (aVars != null) {
            aVars.storeMe();
        }

        getInventory().updateDatabase();
        getWarehouse().updateDatabase();
        getFreight().updateDatabase();
    }

    @Override
    public void storeMe() {
        store(true);
    }

    private void storeCharBase() {
        // Get the exp, level, and sp of base class to store in base table.
        final long exp = getStat().getBaseExp();
        final int level = getStat().getBaseLevel();
        final long sp = getStat().getBaseSp();
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(UPDATE_CHARACTER)) {
            ps.setInt(1, level);
            ps.setInt(2, getMaxHp());
            ps.setDouble(3, getCurrentHp());
            ps.setInt(4, getMaxCp());
            ps.setDouble(5, getCurrentCp());
            ps.setInt(6, getMaxMp());
            ps.setDouble(7, getCurrentMp());
            ps.setInt(8, appearance.getFace());
            ps.setInt(9, appearance.getHairStyle());
            ps.setInt(10, appearance.getHairColor());
            ps.setInt(11, appearance.isFemale() ? 1 : 0);
            ps.setInt(12, getHeading());
            ps.setInt(13, observerMode ? lastLocation.getX() : getX());
            ps.setInt(14, observerMode ? lastLocation.getY() : getY());
            ps.setInt(15, observerMode ? lastLocation.getZ() : getZ());
            ps.setLong(16, exp);
            ps.setLong(17, expBeforeDeath);
            ps.setLong(18, sp);
            ps.setInt(19, getKarma());
            ps.setInt(20, fame);
            ps.setInt(21, pvpKills);
            ps.setInt(22, pkKills);
            ps.setInt(23, clanId);
            ps.setInt(24, getRace().ordinal());
            ps.setInt(25, getPlayerClass().getId());
            ps.setLong(26, deleteTimer);
            ps.setString(27, getTitle());
            ps.setInt(28, appearance.getTitleColor());
            ps.setInt(29, getAccessLevel().getLevel());
            ps.setInt(30, isOnlineInt());
            ps.setInt(31, in7sDungeon ? 1 : 0);
            ps.setInt(32, clanPrivileges.getMask());
            ps.setInt(33, wantsPeace);
            ps.setInt(34, baseClass);
            long totalOnlineTime = onlineTime;
            if (onlineBeginTime > 0) {
                totalOnlineTime += (ClockBridge.gameMillis() - onlineBeginTime) / 1000;
            }

            ps.setLong(35, offlineStartTime > 0 ? onlineTime : totalOnlineTime);
            ps.setInt(36, newbie);
            ps.setInt(37, noble ? 1 : 0);
            ps.setInt(38, powerGrade);
            ps.setInt(39, pledgeType);
            ps.setInt(40, lvlJoinedAcademy);
            ps.setLong(41, apprentice);
            ps.setLong(42, sponsor);
            ps.setLong(43, clanJoinExpiryTime);
            ps.setLong(44, clanCreateExpiryTime);
            ps.setString(45, getName());
            ps.setLong(46, deathPenaltyBuffLevel);
            ps.setInt(47, bookmarkSlot);
            ps.setInt(48, getVitalityPoints());
            ps.setString(49, lang);
            int factionId = 0;
            if (good) {
                factionId = 1;
            }

            if (evil) {
                factionId = 2;
            }

            ps.setInt(50, factionId);
            ps.setInt(51, pcCafePoints);
            ps.setInt(52, getObjectId());
            ps.execute();
        } catch (Exception e) {
            log.warn("Could not store char base data: {} - {}", this, e.getMessage(), e);
        }
    }

    private void storeCharSub() {
        if (getTotalSubClasses() <= 0) {
            return;
        }

        // TODO(Zoey76): Refactor this to use batch.
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(UPDATE_CHAR_SUBCLASS)) {
            for (SubClassHolder subClass : getSubClasses().values()) {
                ps.setLong(1, subClass.getExp());
                ps.setLong(2, subClass.getSp());
                ps.setInt(3, subClass.getLevel());
                ps.setInt(4, subClass.getId());
                ps.setInt(5, getObjectId());
                ps.setInt(6, subClass.getClassIndex());
                ps.addBatch();
            }

            ps.executeBatch();
        } catch (Exception e) {
            log.warn("Could not store sub class data for {}: {}", getName(), e.getMessage(), e);
        }
    }

    @Override
    public void storeEffect(boolean storeEffects) {
        if (!PlayerConfig.STORE_SKILL_COOLTIME) {
            return;
        }

        try (Connection con = DatabaseFactory.getConnection()) {
            // Delete all current stored effects for char to avoid dupe.
            try (PreparedStatement delete = con.prepareStatement(DELETE_SKILL_SAVE)) {
                delete.setInt(1, getObjectId());
                delete.setInt(2, classIndex);
                delete.execute();
            }

            int buffIndex = 0;
            final List<Integer> storedSkills = new ArrayList<>();
            final GameClock clock = ClockBridge.clock();
            final long now = clock.gameMillis();

            // Store all effect data along with calulated remaining.
            // reuse delays for matching skills. 'restore_type'= 0.
            try (PreparedStatement statement = con.prepareStatement(ADD_SKILL_SAVE)) {
                if (storeEffects) {
                    for (BuffInfo info : getEffectList().getEffects()) {
                        if (info == null) {
                            continue;
                        }

                        final Skill skill = info.getSkill();

                        // Do not save heals.
                        if (skill.getAbnormalType() == AbnormalType.LIFE_FORCE_OTHERS) {
                            continue;
                        }

                        // Toggles are skipped, unless they are necessary to be always on.
                        if (skill.isToggle() && !PlayerConfig.ALT_STORE_TOGGLES) {
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

                        final TimeStamp t = getSkillReuseTimeStamp(skill.getReuseHashCode());
                        final boolean underReuse = (t != null) && (now < t.getStamp());
                        statement.setInt(1, getObjectId());
                        statement.setInt(2, skill.getId());
                        statement.setInt(3, skill.getLevel());
                        statement.setObject(4, stored.remainingMillis(), Types.BIGINT);
                        statement.setLong(5, stored.tickPhaseMillis());
                        statement.setLong(6, underReuse ? t.getReuse() : 0);
                        statement.setObject(7, underReuse ? clock.wallMillisAt(t.getStamp()) : null, Types.BIGINT);
                        statement.setInt(8, 0); // Store type 0, active buffs/debuffs.
                        statement.setInt(9, classIndex);
                        statement.setInt(10, ++buffIndex);
                        statement.addBatch();
                    }
                }

                // Skills under reuse.
                for (Entry<Integer, TimeStamp> ts : getSkillReuseTimeStamps().entrySet()) {
                    final int hash = ts.getKey();
                    if (storedSkills.contains(hash)) {
                        continue;
                    }

                    final TimeStamp t = ts.getValue();
                    if ((t != null) && (now < t.getStamp())) {
                        storedSkills.add(hash);

                        statement.setInt(1, getObjectId());
                        statement.setInt(2, t.getSkillId());
                        statement.setInt(3, t.getSkillLevel());
                        statement.setNull(4, Types.BIGINT);
                        statement.setNull(5, Types.BIGINT);
                        statement.setLong(6, t.getReuse());
                        statement.setLong(7, clock.wallMillisAt(t.getStamp()));
                        statement.setInt(8, 1); // Restore type 1, skill reuse.
                        statement.setInt(9, classIndex);
                        statement.setInt(10, ++buffIndex);
                        statement.addBatch();
                    }
                }

                statement.executeBatch();
            }
        } catch (Exception e) {
            log.warn("Could not store char effect data: ", e);
        }
    }

    private void storeItemReuseDelay() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps1 = con.prepareStatement(DELETE_ITEM_REUSE_SAVE);
                PreparedStatement ps2 = con.prepareStatement(ADD_ITEM_REUSE_SAVE)) {
            ps1.setInt(1, getObjectId());
            ps1.execute();

            final GameClock clock = ClockBridge.clock();
            final long now = clock.gameMillis();
            for (TimeStamp ts : getItemReuseTimeStamps().values()) {
                if ((ts != null) && (now < ts.getStamp())) {
                    ps2.setInt(1, getObjectId());
                    ps2.setInt(2, ts.getItemId());
                    ps2.setInt(3, ts.getItemObjectId());
                    ps2.setLong(4, ts.getReuse());
                    ps2.setLong(5, clock.wallMillisAt(ts.getStamp()));
                    ps2.addBatch();
                }
            }

            ps2.executeBatch();
        } catch (Exception e) {
            log.warn("Could not store char item reuse data: ", e);
        }
    }

    public int isOnlineInt() {
        if (online && (client != null)) {
            return client.isDetached() ? 2 : 1;
        }

        return 0;
    }

    public void startOfflinePlay() {
        if (hasPremiumStatus()
                && (DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP > 0)
                && !AntiFeedManager.getInstance()
                        .tryAddPlayer(
                                AntiFeedManager.OFFLINE_PLAY,
                                this,
                                DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP)) {
            String limit = String.valueOf(AntiFeedManager.getInstance()
                    .getLimit(this, DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PER_IP));
            sendMessage("Only " + limit + " offline players allowed per IP.");
            return;
        } else if ((DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PER_IP > 0)
                && !AntiFeedManager.getInstance()
                        .tryAddPlayer(
                                AntiFeedManager.OFFLINE_PLAY,
                                this,
                                DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PER_IP)) {
            String limit = String.valueOf(AntiFeedManager.getInstance()
                    .getLimit(this, DualboxCheckConfig.DUALBOX_CHECK_MAX_OFFLINEPLAY_PER_IP));
            sendMessage("Only " + limit + " offline players allowed per IP.");
            return;
        }

        AntiFeedManager.getInstance().removePlayer(AntiFeedManager.GAME_ID, this);

        sendPacket(LeaveWorld.STATIC_PACKET);

        if (OfflinePlayConfig.OFFLINE_PLAY_SET_NAME_COLOR) {
            getAppearance().setNameColor(OfflinePlayConfig.OFFLINE_PLAY_NAME_COLOR);
        }

        if (!OfflinePlayConfig.OFFLINE_PLAY_ABNORMAL_EFFECTS.isEmpty()) {
            startAbnormalVisualEffect(
                    true,
                    OfflinePlayConfig.OFFLINE_PLAY_ABNORMAL_EFFECTS.get(
                            ClockBridge.random().nextInt(OfflinePlayConfig.OFFLINE_PLAY_ABNORMAL_EFFECTS.size())));
        }

        broadcastUserInfo();

        offlinePlay = true;
        client.setDetached(true);

        if (OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS) {
            OfflinePlayTable.getInstance().storeOfflinePlay(this);
        }
    }

    public void setEnteredWorld() {
        enteredWorld = true;
    }

    public boolean hasEnteredWorld() {
        return enteredWorld;
    }

    /**
     * Verifies if the player is in offline mode.<br>
     * The offline mode may happen for different reasons:<br>
     * Abnormally: Player gets abruptly disconnected from server.<br>
     * Normally: The player gets into offline shop mode, only available by enabling the offline shop mod.
     * @return {@code true} if the player is in offline mode, {@code false} otherwise
     */
    public boolean isInOfflineMode() {
        return (client == null) || client.isDetached();
    }

    @Override
    public Skill addSkill(Skill newSkill) {
        addCustomSkill(newSkill);
        return super.addSkill(newSkill);
    }

    /**
     * Add a skill to the Player skills and its Func objects to the calculator set of the Player and save update in the character_skills table of the database. <b><u>Concept</u>:</b> All skills own by a Player are identified in <b>skills</b> <b><u> Actions</u>:</b>
     * <li>Replace oldSkill by newSkill or Add the newSkill</li>
     * <li>If an old skill has been replaced, remove all its Func objects of Creature calculator set</li>
     * <li>Add Func objects of newSkill to the calculator set of the Creature</li><br>
     * @param newSkill The Skill to add to the Creature
     * @param store
     * @return The Skill replaced or null if just added a new Skill
     */
    public Skill addSkill(Skill newSkill, boolean store) {
        // Add a skill to the Player skills and its Func objects to the calculator set of the Player.
        final Skill oldSkill = addSkill(newSkill);

        // Add or update a Player skill in the character_skills table of the database.
        if (store) {
            storeSkill(newSkill, oldSkill, -1);
        }

        return oldSkill;
    }

    @Override
    public Skill removeSkill(Skill skill, boolean store) {
        removeCustomSkill(skill);
        return store ? removeSkill(skill) : super.removeSkill(skill, true);
    }

    public Skill removeSkill(Skill skill, boolean store, boolean cancelEffect) {
        removeCustomSkill(skill);
        return store ? removeSkill(skill) : super.removeSkill(skill, cancelEffect);
    }

    /**
     * Remove a skill from the Creature and its Func objects from calculator set of the Creature and save update in the character_skills table of the database. <b><u>Concept</u>:</b> All skills own by a Creature are identified in <b>skills</b> <b><u> Actions</u>:</b>
     * <li>Remove the skill from the Creature skills</li>
     * <li>Remove all its Func objects from the Creature calculator set</li> <b><u> Overridden in</u>:</b>
     * <li>Player : Save update in the character_skills table of the database</li><br>
     * @param skill The Skill to remove from the Creature
     * @return The Skill removed
     */
    public Skill removeSkill(Skill skill) {
        removeCustomSkill(skill);

        // Remove a skill from the Creature and its Func objects from calculator set of the Creature.
        final Skill oldSkill = super.removeSkill(skill, true);
        if (oldSkill != null) {
            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement ps = con.prepareStatement(DELETE_SKILL_FROM_CHAR)) {
                // Remove or update a Player skill from the character_skills table of the database.
                ps.setInt(1, oldSkill.getId());
                ps.setInt(2, getObjectId());
                ps.setInt(3, classIndex);
                ps.execute();
            } catch (Exception e) {
                log.warn("Error could not delete skill: {}", e.getMessage(), e);
            }
        }

        if ((getTransformationId() > 0) || isCursedWeaponEquipped()) {
            return oldSkill;
        }

        if (skill != null) {
            for (Shortcut sc : shortcuts.getAllShortcuts()) {
                if ((sc != null)
                        && (sc.getId() == skill.getId())
                        && (sc.getType() == ShortcutType.SKILL)
                        && ((skill.getId() < 3080) || (skill.getId() > 3259))) {
                    deleteShortcut(sc.getSlot(), sc.getPage());
                }
            }
        }

        return oldSkill;
    }

    /**
     * Add or update a Player skill in the character_skills table of the database.<br>
     * If newClassIndex > -1, the skill will be stored with that class index, not the current one.
     * @param newSkill
     * @param oldSkill
     * @param newClassIndex
     */
    private void storeSkill(Skill newSkill, Skill oldSkill, int newClassIndex) {
        final int classIndex = (newClassIndex > -1) ? newClassIndex : this.classIndex;
        try (Connection con = DatabaseFactory.getConnection()) {
            if ((oldSkill != null) && (newSkill != null)) {
                try (PreparedStatement ps = con.prepareStatement(UPDATE_CHARACTER_SKILL_LEVEL)) {
                    ps.setInt(1, newSkill.getLevel());
                    ps.setInt(2, oldSkill.getId());
                    ps.setInt(3, getObjectId());
                    ps.setInt(4, classIndex);
                    ps.execute();
                }
            } else if (newSkill != null) {
                try (PreparedStatement ps = con.prepareStatement(ADD_NEW_SKILLS)) {
                    ps.setInt(1, getObjectId());
                    ps.setInt(2, newSkill.getId());
                    ps.setInt(3, newSkill.getLevel());
                    ps.setInt(4, classIndex);
                    ps.execute();
                }
            }
            // else
            // {
            // LOGGER.warning("Could not store new skill, it's null!");
            // }
        } catch (Exception e) {
            log.warn("Error could not store char skills: {}", e.getMessage(), e);
        }
    }

    /**
     * Adds or updates player's skills in the database.
     * @param newSkills the list of skills to store
     * @param newClassIndex if newClassIndex > -1, the skills will be stored for that class index, not the current one
     */
    private void storeSkills(List<Skill> newSkills, int newClassIndex) {
        if (newSkills.isEmpty()) {
            return;
        }

        final int classIndex = (newClassIndex > -1) ? newClassIndex : this.classIndex;
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(ADD_NEW_SKILLS)) {
            for (Skill addSkill : newSkills) {
                ps.setInt(1, getObjectId());
                ps.setInt(2, addSkill.getId());
                ps.setInt(3, addSkill.getLevel());
                ps.setInt(4, classIndex);
                ps.addBatch();
            }

            ps.executeBatch();
        } catch (SQLException e) {
            log.warn("Error could not store char skills: {}", e.getMessage(), e);
        }
    }

    /**
     * Retrieve from the database all skills of this Player and add them to skills.
     */
    private void restoreSkills() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(RESTORE_SKILLS_FOR_CHAR)) {
            // Retrieve all skills of this Player from the database.
            ps.setInt(1, getObjectId());
            ps.setInt(2, classIndex);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final int id = rs.getInt("skill_id");
                    final int level = rs.getInt("skill_level");

                    // Create a Skill object for each record.
                    final Skill skill = SkillData.getInstance().getSkill(id, level);
                    if (skill == null) {
                        log.warn(
                                "Skipped null skill Id: {} Level: {} while restoring player skills for playerObjId: {}",
                                id,
                                level,
                                getObjectId());
                        continue;
                    }

                    // Add the Skill object to the Creature skills and its Func objects to the calculator set of the
                    // Creature.
                    addSkill(skill);

                    if (GeneralConfig.SKILL_CHECK_ENABLE
                            && (!isGM() || GeneralConfig.SKILL_CHECK_GM)
                            && !SkillTreeData.getInstance().isSkillAllowed(this, skill)) {
                        PunishmentManager.handleIllegalPlayerAction(
                                this,
                                "Player " + getName() + " has invalid skill " + skill.getName() + " (" + skill.getId()
                                        + "/" + skill.getLevel() + "), class:"
                                        + ClassListData.getInstance()
                                                .getClass(getPlayerClass())
                                                .className(),
                                IllegalActionPunishmentType.BROADCAST);
                        if (GeneralConfig.SKILL_CHECK_REMOVE) {
                            removeSkill(skill);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not restore character {} skills: {}", this, e.getMessage(), e);
        }
    }

    /**
     * Retrieve from the database all skill effects of this Player and add them to the player.
     */
    @Override
    public void restoreEffects() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(RESTORE_SKILL_SAVE)) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, classIndex);
            try (ResultSet rs = ps.executeQuery()) {
                final GameClock clock = ClockBridge.clock();
                while (rs.next()) {
                    final Skill skill =
                            SkillData.getInstance().getSkill(rs.getInt("skill_id"), rs.getInt("skill_level"));
                    if (skill == null) {
                        continue;
                    }

                    final Long reuseEndsAt = rs.getObject("reuse_ends_at", Long.class);
                    if (reuseEndsAt != null) {
                        final long endsAt = clock.gameMillisAtWall(reuseEndsAt);
                        final long remaining = endsAt - clock.gameMillis();
                        if (remaining > 10) {
                            disableSkill(skill, remaining);
                            addTimeStamp(skill, rs.getLong("reuse_delay"), endsAt);
                        }
                    }

                    // Restore Type 1 The remaning skills lost effect upon logout but were still under a high reuse
                    // delay.
                    if (rs.getInt("restore_type") > 0) {
                        continue;
                    }

                    // Restore Type 0 These skill were still in effect on the character upon logout.
                    // Some of which were self casted and might still have had a long reuse delay which also is
                    // restored.
                    StoredEffectRows.read(rs, getObjectId())
                            .ifPresent(stored -> skill.applyStoredEffects(this, stored));
                }
            }

            // Remove previously restored skills.
            try (PreparedStatement delete = con.prepareStatement(DELETE_SKILL_SAVE)) {
                delete.setInt(1, getObjectId());
                delete.setInt(2, classIndex);
                delete.executeUpdate();
            }
        } catch (Exception e) {
            log.warn("Could not restore {} active effect data: {}", this, e.getMessage(), e);
        }
    }

    /**
     * Retrieve from the database all Item Reuse Time of this Player and add them to the player.
     */
    private void restoreItemReuse() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(RESTORE_ITEM_REUSE_SAVE);
                PreparedStatement delete = con.prepareStatement(DELETE_ITEM_REUSE_SAVE)) {
            ps.setInt(1, getObjectId());
            try (ResultSet rs = ps.executeQuery()) {
                final GameClock clock = ClockBridge.clock();
                while (rs.next()) {
                    final int itemId = rs.getInt("itemId");
                    final long reuseDelay = rs.getLong("reuseDelay");
                    final long endsAt = clock.gameMillisAtWall(rs.getLong("reuse_ends_at"));
                    boolean isInInventory = true;

                    // Using item Id
                    Item item = inventory.getItemByItemId(itemId);
                    if (item == null) {
                        item = getWarehouse().getItemByItemId(itemId);
                        isInInventory = false;
                    }

                    if ((item != null) && (item.getId() == itemId) && (item.getReuseDelay() > 0)) {
                        final long remainingTime = endsAt - clock.gameMillis();
                        if (remainingTime > 10) {
                            addTimeStampItem(item, reuseDelay, endsAt);
                            if (isInInventory && item.isEtcItem()) {
                                final int group = item.getSharedReuseGroup();
                                if (group > 0) {
                                    sendPacket(new ExUseSharedGroupItem(
                                            itemId, group, (int) remainingTime, (int) reuseDelay));
                                }
                            }
                        }
                    }
                }
            }

            // Delete item reuse.
            delete.setInt(1, getObjectId());
            delete.executeUpdate();
        } catch (Exception e) {
            log.warn("Could not restore {} Item Reuse data: {}", this, e.getMessage(), e);
        }
    }

    /**
     * Retrieve from the database all Henna of this Player, add them to henna and calculate stats of the Player.
     */
    private void restoreHenna() {
        for (int i = 0; i < 3; i++) {
            hennaList[i] = null;
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(RESTORE_CHAR_HENNAS)) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, classIndex);
            try (ResultSet rs = ps.executeQuery()) {
                int slot;
                int symbolId;
                while (rs.next()) {
                    slot = rs.getInt("slot");
                    if ((slot < 1) || (slot > 3)) {
                        continue;
                    }

                    symbolId = rs.getInt("symbol_id");
                    if (symbolId == 0) {
                        continue;
                    }

                    hennaList[slot - 1] = HennaData.getInstance().getHenna(symbolId);
                }
            }
        } catch (Exception e) {
            log.error("Failed restoing character {} hennas.", this, e);
        }

        // Calculate henna modifiers of this player.
        recalcHennaStats();
    }

    /**
     * @return the number of Henna empty slot of the Player.
     */
    public int getHennaEmptySlots() {
        int totalSlots = 0;
        if (getPlayerClass().level() == 1) {
            totalSlots = 2;
        } else if (getPlayerClass().level() > 1) {
            totalSlots = 3;
        }

        for (int i = 0; i < 3; i++) {
            if (hennaList[i] != null) {
                totalSlots--;
            }
        }

        if (totalSlots <= 0) {
            return 0;
        }

        return totalSlots;
    }

    /**
     * Remove a Henna of the Player, save update in the character_hennas table of the database and send Server->Client HennaInfo/UserInfo packet to this Player.
     * @param slot
     * @return
     */
    public boolean removeHenna(int slot) {
        if ((slot < 1) || (slot > 3)) {
            return false;
        }

        final Henna henna = hennaList[slot - 1];
        if (henna == null) {
            return false;
        }

        hennaList[slot - 1] = null;

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement = con.prepareStatement(DELETE_CHAR_HENNA)) {
            statement.setInt(1, getObjectId());
            statement.setInt(2, slot);
            statement.setInt(3, classIndex);
            statement.execute();
        } catch (Exception e) {
            log.error("Failed removing character henna.", e);
        }

        // Calculate Henna modifiers of this Player.
        recalcHennaStats();

        // Send Server->Client HennaInfo packet to this Player.
        sendPacket(new HennaInfo(this));

        // Send Server->Client UserInfo packet to this Player.
        updateUserInfo();

        // Add the recovered dyes to the player's inventory and notify them.
        inventory.addItem(ItemProcessType.RESTORE, henna.getDyeItemId(), henna.getCancelCount(), this, null);
        reduceAdena(ItemProcessType.FEE, henna.getCancelFee(), this, false);

        final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S2_S1_S);
        sm.addItemName(henna.getDyeItemId());
        sm.addLong(henna.getCancelCount());
        sendPacket(sm);
        sendPacket(SystemMessageId.THE_SYMBOL_HAS_BEEN_DELETED);

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_HENNA_REMOVE, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerHennaRemove(this, henna), this);
        }

        return true;
    }

    /**
     * Add a Henna to the Player, save update in the character_hennas table of the database and send Server->Client HennaInfo/UserInfo packet to this Player.
     * @param henna the henna to add to the player.
     * @return {@code true} if the henna is added to the player, {@code false} otherwise.
     */
    public boolean addHenna(Henna henna) {
        for (int i = 0; i < 3; i++) {
            if (hennaList[i] == null) {
                hennaList[i] = henna;

                // Calculate Henna modifiers of this Player.
                recalcHennaStats();

                try (Connection con = DatabaseFactory.getConnection();
                        PreparedStatement ps = con.prepareStatement(ADD_CHAR_HENNA)) {
                    ps.setInt(1, getObjectId());
                    ps.setInt(2, henna.getDyeId());
                    ps.setInt(3, i + 1);
                    ps.setInt(4, classIndex);
                    ps.execute();
                } catch (Exception e) {
                    log.error("Failed saving character henna.", e);
                }

                // Send Server->Client HennaInfo packet to this Player.
                sendPacket(new HennaInfo(this));

                // Send Server->Client UserInfo packet to this Player.
                updateUserInfo();

                // Notify to scripts.
                if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_HENNA_REMOVE, this)) {
                    EventDispatcher.getInstance().notifyEventAsync(new OnPlayerHennaRemove(this, henna), this);
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Calculate Henna modifiers of this Player.
     */
    private void recalcHennaStats() {
        hennaStatINT = 0;
        hennaStatSTR = 0;
        hennaStatCON = 0;
        hennaStatMEN = 0;
        hennaStatWIT = 0;
        hennaStatDEX = 0;
        for (Henna h : hennaList) {
            if (h == null) {
                continue;
            }

            hennaStatINT += ((hennaStatINT + h.getStatINT()) > 5) ? 5 - hennaStatINT : h.getStatINT();
            hennaStatSTR += ((hennaStatSTR + h.getStatSTR()) > 5) ? 5 - hennaStatSTR : h.getStatSTR();
            hennaStatMEN += ((hennaStatMEN + h.getStatMEN()) > 5) ? 5 - hennaStatMEN : h.getStatMEN();
            hennaStatCON += ((hennaStatCON + h.getStatCON()) > 5) ? 5 - hennaStatCON : h.getStatCON();
            hennaStatWIT += ((hennaStatWIT + h.getStatWIT()) > 5) ? 5 - hennaStatWIT : h.getStatWIT();
            hennaStatDEX += ((hennaStatDEX + h.getStatDEX()) > 5) ? 5 - hennaStatDEX : h.getStatDEX();
        }
    }

    /**
     * @param slot the character inventory henna slot.
     * @return the Henna of this Player corresponding to the selected slot.
     */
    public Henna getHenna(int slot) {
        return (slot < 1) || (slot > 3) ? null : hennaList[slot - 1];
    }

    /**
     * @return {@code true} if player has at least 1 henna symbol, {@code false} otherwise.
     */
    public boolean hasHennas() {
        for (Henna henna : hennaList) {
            if (henna != null) {
                return true;
            }
        }

        return false;
    }

    public void autoSave() {
        storeMe();
        storeRecommendations(false);

        if (GeneralConfig.UPDATE_ITEMS_ON_CHAR_STORE) {
            getInventory().updateDatabase();
            getWarehouse().updateDatabase();
            getFreight().updateDatabase();
        }
    }

    public boolean canLogout() {
        if (changingClass) {
            log.warn("Player {} tried to restart/logout during class change.", getName());
            return false;
        }

        if ((activeEnchantItemId != ID_NONE) || (activeEnchantAttrItemId != ID_NONE)) {
            return false;
        }

        if (AttackStanceTaskManager.getInstance().hasAttackStanceTask(this)
                && !(isGM() && GeneralConfig.GM_RESTART_FIGHTING)) {
            sendPacket(SystemMessageId.YOU_CANNOT_EXIT_THE_GAME_WHILE_IN_COMBAT);
            return false;
        }

        if (isRegisteredOnEvent()) {
            sendMessage("A superior power doesn't allow you to leave.");
            return false;
        }

        // Prevent player from logging out if they are a festival participant
        // and it is in progress, otherwise notify party members that the player
        // is not longer a participant.
        if (isFestivalParticipant()) {
            if (SevenSignsFestival.getInstance().isFestivalInitialized()) {
                sendMessage("You cannot log out while you are a participant in a Festival.");
                return false;
            }

            if (isInParty()) {
                party.broadcastPacket(new SystemMessage(getName() + " has been removed from the upcoming Festival."));
            }
        }

        return true;
    }

    /**
     * Return True if the Player is autoAttackable.<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Check if the attacker isn't the Player Pet</li>
     * <li>Check if the attacker is Monster</li>
     * <li>If the attacker is a Player, check if it is not in the same party</li>
     * <li>Check if the Player has Karma</li>
     * <li>If the attacker is a Player, check if it is not in the same siege clan (Attacker, Defender)</li>
     * </ul>
     */
    @Override
    public boolean isAutoAttackable(Creature attacker) {
        if (attacker == null) {
            return false;
        }

        // Invisible GM players should not be attackable.
        if (isInvisible() && isGM()) {
            return false;
        }

        // Check if the attacker isn't the Player Pet.
        if ((attacker == this) || (attacker == summon)) {
            return false;
        }

        // Friendly mobs do not attack players.
        if (attacker instanceof FriendlyMob) {
            return false;
        }

        // Check if the attacker is a Monster.
        if (attacker.isMonster()) {
            return true;
        }

        // is AutoAttackable if both players are in the same duel and the duel is still going on
        final Player attackerPlayer = attacker.asPlayer();
        if (attacker.isPlayable()
                && (duelState == Duel.DUELSTATE_DUELLING)
                && (getDuelId() == attackerPlayer.getDuelId())) {
            return true;
        }

        // Check if the attacker is not in the same party. NOTE: Party checks goes before oly checks in order to prevent
        // patry member autoattack at oly.
        if (isInParty() && party.getMembers().contains(attacker)) {
            return false;
        }

        // Check if the attacker is in olympia and olympia start.
        if (attacker.isPlayer() && attackerPlayer.isInOlympiadMode()) {
            return inOlympiadMode && olympiadStart && (attacker.asPlayer().getOlympiadGameId() == getOlympiadGameId());
        }

        // Check if the attacker is in an event.
        if (isOnEvent()) {
            return isOnSoloEvent() || (getTeam() != attacker.getTeam());
        }

        // Check if the attacker is a Playable.
        if (attacker.isPlayable()) {
            if (isInsideZone(ZoneId.PEACE) || isInsideZone(ZoneId.NO_PVP)) {
                return false;
            }

            // Get Player
            final Clan clan = getClan();
            final Clan attackerClan = attackerPlayer.getClan();
            if ((clan != null) && (attackerClan != null)) {
                if (clan != attackerClan) {
                    final Siege siege = SiegeManager.getInstance().getSiege(getX(), getY(), getZ());
                    if (siege != null) {
                        // Check if a siege is in progress and if attacker and the Player aren't in the Defender clan.
                        if (siege.checkIsDefender(attackerClan) && siege.checkIsDefender(clan)) {
                            return false;
                        }

                        // Check if a siege is in progress and if attacker and the Player aren't in the Attacker clan.
                        if (siege.checkIsAttacker(attackerClan) && siege.checkIsAttacker(clan)) {
                            // If first mid victory is achieved, attackers can attack attackers.
                            final Castle castle = CastleManager.getInstance().getCastleById(siegeSide);
                            return (castle != null) && castle.isFirstMidVictory();
                        }
                    }
                }

                // Check if clan is at war.
                if ((getWantsPeace() == 0)
                        && (attackerPlayer.getWantsPeace() == 0)
                        && !isAcademyMember()
                        && clan.isAtWarWith(attackerPlayer.getClanId())
                        && attackerClan.isAtWarWith(getClanId())) {
                    return true;
                }
            }

            // Check if the Player is in an arena, but NOT siege zone. NOTE: This check comes before clan/ally checks,
            // but after party checks.
            // This is done because in arenas, clan/ally members can autoattack if they are not in party.
            if ((isInsideZone(ZoneId.PVP) && attackerPlayer.isInsideZone(ZoneId.PVP))
                    && !(isInsideZone(ZoneId.SIEGE) && attackerPlayer.isInsideZone(ZoneId.SIEGE))) {
                return true;
            }

            // Check if the attacker is not in the same clan.
            if ((clan != null) && clan.isMember(attacker.getObjectId())) {
                return false;
            }

            // Check if the attacker is not in the same ally.
            if (attacker.isPlayer() && (getAllyId() != 0) && (getAllyId() == attackerPlayer.getAllyId())) {
                return false;
            }

            // Now check again if the Player is in pvp zone, but this time at siege PvP zone, applying clan/ally checks.
            if (isInsideZone(ZoneId.PVP)
                    && attackerPlayer.isInsideZone(ZoneId.PVP)
                    && isInsideZone(ZoneId.SIEGE)
                    && attackerPlayer.isInsideZone(ZoneId.SIEGE)) {
                return true;
            }

            if (FactionSystemConfig.FACTION_SYSTEM_ENABLED
                    && ((isGood() && attackerPlayer.isEvil()) || (isEvil() && attackerPlayer.isGood()))) {
                return true;
            }
        }

        if ((attacker instanceof Defender) && (this.clan != null)) {
            final Siege siege = SiegeManager.getInstance().getSiege(this);
            return (siege != null) && siege.checkIsAttacker(this.clan);
        }

        if (attacker instanceof Guard) {
            if (FactionSystemConfig.FACTION_SYSTEM_ENABLED
                    && FactionSystemConfig.FACTION_GUARDS_ENABLED
                    && ((good && attacker.asNpc().getTemplate().isClan(FactionSystemConfig.FACTION_EVIL_TEAM_NAME))
                            || (evil
                                    && attacker.asNpc()
                                            .getTemplate()
                                            .isClan(FactionSystemConfig.FACTION_GOOD_TEAM_NAME)))) {
                return true;
            }

            return (getKarma() > 0); // Guards attack only PK players.
        }

        // Check if the Player has Karma.
        if ((getKarma() > 0) || (pvpFlag > 0)) {
            return true;
        }

        return false;
    }

    /**
     * Check if the active Skill can be casted.<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Check if the skill isn't toggle and is offensive</li>
     * <li>Check if the target is in the skill cast range</li>
     * <li>Check if the skill is Spoil type and if the target isn't already spoiled</li>
     * <li>Check if the caster owns enought consummed Item, enough HP and MP to cast the skill</li>
     * <li>Check if the caster isn't sitting</li>
     * <li>Check if all skills are enabled and this skill is enabled</li>
     * <li>Check if the caster own the weapon needed</li>
     * <li>Check if the skill is active</li>
     * <li>Check if all casting conditions are completed</li>
     * <li>Notify the AI with CAST and target</li>
     * </ul>
     * @param skill The Skill to use
     * @param forceUse used to force ATTACK on players
     * @param dontMove used to prevent movement, if not in range
     */
    @Override
    public boolean useMagic(Skill skill, boolean forceUse, boolean dontMove) {
        // Check if the skill is active.
        if (skill.isPassive()) {
            // just ignore the passive skill request. why does the client send it anyway ??
            // Send a Server->Client packet ActionFailed to the Player.
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // ************************************* Check Casting in Progress *******************************************

        // If a skill is currently being used, queue this one if this is not the same.
        if (isCastingNow()) {
            // Check if new skill different from current skill in progress.
            if ((currentSkill != null) && (skill.getId() == currentSkill.getSkillId())) {
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            } else if (isSkillDisabled(skill)) {
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            // Create a new SkillUseHolder object and queue it in the player queuedSkill.
            setQueuedSkill(skill, forceUse, dontMove);
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        setCastingNow(true);

        // Create a new SkillUseHolder object and set the player currentSkill.
        // This is used mainly to save & queue the button presses, since Creature has.
        // _lastSkillCast which could otherwise replace it
        setCurrentSkill(skill, forceUse, dontMove);
        if (queuedSkill != null) {
            setQueuedSkill(null, false, false);
        }

        if (!checkUseMagicConditions(skill, forceUse, dontMove)) {
            setCastingNow(false);
            return false;
        }

        // Check if the target is correct and Notify the AI with CAST and target.
        WorldObject target = null;
        switch (skill.getTargetType()) {
            case AURA: // AURA, SELF should be cast even if no target has been found
            case FRONT_AURA:
            case BEHIND_AURA:
            case GROUND:
            case SELF:
            case AURA_CORPSE_MOB:
            case COMMAND_CHANNEL:
            case AURA_FRIENDLY: {
                target = this;
                break;
            }
            default: {
                // Get the first target of the list.
                target = skill.getFirstOfTargetList(this);
                break;
            }
        }

        // Notify the AI with CAST and target.
        getAI().setIntentionCast(skill, target);
        return true;
    }

    private boolean checkUseMagicConditions(Skill skill, boolean forceUse, boolean dontMove) {
        // ************************************* Check Player State *******************************************

        // Abnormal effects(ex : Stun, Sleep...) are checked in Creature useMagic().
        if (isOutOfControl() || isParalyzed() || isStunned() || isSleeping()) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // Check if the player is dead.
        if (isDead()) {
            // Send a Server->Client packet ActionFailed to the Player.
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (fishing && !skill.hasEffectType(EffectType.FISHING, EffectType.FISHING_START)) {
            // Only fishing skills are available
            sendPacket(SystemMessageId.ONLY_FISHING_SKILLS_MAY_BE_USED_AT_THIS_TIME);
            return false;
        }

        // Pumping and reeling are refused here, so that no reuse delay is applied when there is no fish to fight.
        if ((fishCombat == null) && skill.hasEffectType(EffectType.FISHING)) {
            if (skill.getId() == CommonSkill.PUMPING.getId()) {
                sendPacket(SystemMessageId.YOU_MAY_ONLY_USE_THE_PUMPING_SKILL_WHILE_YOU_ARE_FISHING);
            } else {
                sendPacket(SystemMessageId.YOU_MAY_ONLY_USE_THE_REELING_SKILL_WHILE_YOU_ARE_FISHING);
            }

            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (observerMode) {
            sendPacket(SystemMessageId.OBSERVERS_CANNOT_PARTICIPATE);
            abortCast();
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // Check if the caster is sitting.
        if (sitting) {
            // Send a System Message to the caster.
            sendPacket(SystemMessageId.YOU_CANNOT_MOVE_WHILE_SITTING);

            // Send a Server->Client packet ActionFailed to the Player.
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // Check if the skill type is toggle.
        if (skill.isToggle() && isAffectedBySkill(skill.getId())) {
            stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // Check if the player uses "Fake Death" skill.
        // Note: do not check this before TOGGLE reset
        if (fakeDeath) {
            // Send a Server->Client packet ActionFailed to the Player.
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // ************************************* Check Target *******************************************
        // Create and set a WorldObject containing the target of the skill.
        WorldObject target = null;
        final TargetType sklTargetType = skill.getTargetType();
        if ((sklTargetType == TargetType.GROUND) && (currentSkillWorldPosition == null)) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        switch (sklTargetType) {
            // Target the player if skill type is AURA, PARTY, CLAN or SELF.
            case AURA:
            case FRONT_AURA:
            case BEHIND_AURA:
            case PARTY:
            case CLAN:
            case PARTY_CLAN:
            case GROUND:
            case SELF:
            case AREA_SUMMON:
            case AURA_CORPSE_MOB:
            case COMMAND_CHANNEL:
            case AURA_FRIENDLY: {
                target = this;
                break;
            }
            case PET:
            case SERVITOR:
            case SUMMON: {
                target = summon;
                break;
            }
            default: {
                target = getTarget();
                break;
            }
        }

        // Check the validity of the target.
        if (target == null) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // Skills can be used on walls and doors only during siege.
        if (target.isDoor()) {
            final Door door = target.asDoor();
            if (door.isInsideZone(ZoneId.SIEGE)) {
                // Check if the door belongs to a castle and if the siege is in progress.
                final Castle castle = door.getCastle();
                if ((castle != null)
                        && (castle.getResidenceId() > 0)
                        && !castle.getSiege().isInProgress()) {
                    sendPacket(SystemMessageId.INVALID_TARGET);
                    return false;
                }

                // Check if the door belongs to a fort and if the siege is in progress.
                final Fort fort = door.getFort();
                if ((fort != null)
                        && (fort.getResidenceId() > 0)
                        && (!fort.getSiege().isInProgress() || !door.isShowHp())) {
                    sendPacket(SystemMessageId.INVALID_TARGET);
                    return false;
                }
            } else if (skill.getTargetType() != TargetType.UNLOCKABLE) {
                sendPacket(SystemMessageId.INVALID_TARGET);
                return false;
            }
        }

        // Are the target and the player in the same duel?
        if (inDuel && target.isPlayable()) {
            // Get Player
            final Player cha = target.asPlayer();
            if (cha.getDuelId() != getDuelId()) {
                sendMessage("You cannot do this while duelling.");
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }
        }

        // ************************************* Check skill availability *******************************************

        // Check if this skill is enabled (ex : reuse time).
        if (isSkillDisabled(skill)) {
            final SystemMessage sm;
            if (hasSkillReuse(skill.getReuseHashCode())) {
                final int remainingTime = (int) (getSkillRemainingReuseTime(skill.getReuseHashCode()) / 1000);
                final int hours = remainingTime / 3600;
                final int minutes = (remainingTime % 3600) / 60;
                final int seconds = remainingTime % 60;
                if (hours > 0) {
                    sm = new SystemMessage(
                            SystemMessageId
                                    .THERE_ARE_S2_HOUR_S_S3_MINUTE_S_AND_S4_SECOND_S_REMAINING_IN_S1_S_RE_USE_TIME);
                    sm.addSkillName(skill);
                    sm.addInt(hours);
                    sm.addInt(minutes);
                } else if (minutes > 0) {
                    sm = new SystemMessage(
                            SystemMessageId.THERE_ARE_S2_MINUTE_S_S3_SECOND_S_REMAINING_IN_S1_S_RE_USE_TIME);
                    sm.addSkillName(skill);
                    sm.addInt(minutes);
                } else {
                    sm = new SystemMessage(SystemMessageId.THERE_ARE_S2_SECOND_S_REMAINING_IN_S1_S_RE_USE_TIME);
                    sm.addSkillName(skill);
                }

                sm.addInt(seconds);
            } else {
                sm = new SystemMessage(SystemMessageId.S1_IS_NOT_AVAILABLE_AT_THIS_TIME_BEING_PREPARED_FOR_REUSE);
                sm.addSkillName(skill);
            }

            sendPacket(sm);
            return false;
        }

        // ************************************* Check casting conditions *******************************************

        // Check if all casting conditions are completed.
        if (!skill.checkCondition(this, target, false)) {
            // Send a Server->Client packet ActionFailed to the Player.
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // ************************************* Check Skill Type *******************************************

        // Check if this is bad magic skill.
        if (skill.hasNegativeEffect()) {
            if (isInsidePeaceZone(this, target) && !getAccessLevel().allowPeaceAttack()) {
                // If Creature or target is in a peace zone, send a system message TARGET_IN_PEACEZONE a Server->Client
                // packet ActionFailed.
                sendPacket(SystemMessageId.YOU_MAY_NOT_ATTACK_THIS_TARGET_IN_A_PEACEFUL_ZONE);
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            if (inOlympiadMode && !olympiadStart) {
                // If Player is in Olympia and the match isn't already start, send a Server->Client packet ActionFailed.
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            if (isSiegeFriend(target)) {
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

            if (!target.canBeAttacked() && !getAccessLevel().allowPeaceAttack() && !target.isDoor()) {
                // If target is not attackable, send a Server->Client packet ActionFailed.
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            // Check for Event Mob's
            if ((target instanceof EventMonster) && ((EventMonster) target).eventSkillAttackBlocked()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            // Check if a Forced ATTACK is in progress on non-attackable target.
            if (!target.isAutoAttackable(this) && !forceUse) {
                switch (sklTargetType) {
                    case AURA:
                    case FRONT_AURA:
                    case BEHIND_AURA:
                    case AURA_CORPSE_MOB:
                    case CLAN:
                    case PARTY:
                    case SELF:
                    case GROUND:
                    case AREA_SUMMON:
                    case UNLOCKABLE:
                    case AURA_FRIENDLY: {
                        break;
                    }
                    default: // Send a Server->Client packet ActionFailed to the Player.
                    {
                        sendPacket(ActionFailed.STATIC_PACKET);
                        return false;
                    }
                }
            }

            // Check if the target is in the skill cast range.
            if (dontMove) {
                // Calculate the distance between the Player and the target.
                if (sklTargetType == TargetType.GROUND) {
                    if (!isInsideRadius2D(
                            currentSkillWorldPosition.getX(),
                            currentSkillWorldPosition.getY(),
                            currentSkillWorldPosition.getZ(),
                            skill.getCastRange() + getTemplate().getCollisionRadius())) {
                        // Send a System Message to the caster.
                        sendPacket(SystemMessageId.YOUR_TARGET_IS_OUT_OF_RANGE);

                        // Send a Server->Client packet ActionFailed to the Player.
                        sendPacket(ActionFailed.STATIC_PACKET);
                        return false;
                    }
                } else if ((skill.getCastRange() > 0)
                        && !isInsideRadius2D(
                                target, skill.getCastRange() + getTemplate().getCollisionRadius())) {
                    // Send a System Message to the caster.
                    sendPacket(SystemMessageId.YOUR_TARGET_IS_OUT_OF_RANGE);

                    // Send a Server->Client packet ActionFailed to the Player.
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return false;
                }
            }
        }

        // Check if the skill is a good magic, target is a monster and if force attack is set, if not then we don't want
        // to cast.
        if ((skill.getEffectPoint() > 0) && target.isMonster() && !forceUse) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // Check if this is a Pvp skill and target isn't a non-flagged/non-karma player.
        switch (sklTargetType) {
            case PARTY:
            case CLAN: // For such skills, checkPvpSkill() is called from Skill.getTargetList().
            case PARTY_CLAN: // For such skills, checkPvpSkill() is called from Skill.getTargetList().
            case AURA:
            case FRONT_AURA:
            case BEHIND_AURA:
            case AREA_SUMMON:
            case GROUND:
            case SELF: {
                break;
            }
            default: {
                // Verify that player can attack a player or summon.
                if (target.isPlayable() && !getAccessLevel().allowPeaceAttack() && !checkPvpSkill(target, skill)) {
                    // Send a System Message to the player.
                    sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);

                    // Send a Server->Client packet ActionFailed to the player.
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return false;
                }
            }
        }

        // GeoData Los Check here
        if (skill.getCastRange() > 0) {
            if (sklTargetType == TargetType.GROUND) {
                if (!GeoEngine.getInstance().canSeeTarget(this, currentSkillWorldPosition)) {
                    sendPacket(SystemMessageId.CANNOT_SEE_TARGET);
                    sendPacket(ActionFailed.STATIC_PACKET);
                    return false;
                }
            } else if (!GeoEngine.getInstance().canSeeTarget(this, target)) {
                sendPacket(SystemMessageId.CANNOT_SEE_TARGET);
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }
        }

        if ((skill.getFlyType() == FlyType.CHARGE)
                && !GeoEngine.getInstance()
                        .canMoveToTarget(
                                getX(), getY(), getZ(), target.getX(), target.getY(), target.getZ(), getInstanceId())) {
            sendPacket(SystemMessageId.THE_TARGET_IS_LOCATED_WHERE_YOU_CANNOT_CHARGE);
            return false;
        }

        // finally, after passing all conditions
        return true;
    }

    public boolean isInLooterParty(int looterId) {
        final Player looter = World.getPlayer(looterId);

        // If Player is in a CommandChannel.
        if (isInParty() && party.isInCommandChannel() && (looter != null)) {
            return party.getCommandChannel().getMembers().contains(looter);
        }

        return isInParty() && (looter != null) && party.getMembers().contains(looter);
    }

    /**
     * Check if the requested casting is a Pc->Pc skill cast and if it's a valid pvp condition
     * @param target WorldObject instance containing the target
     * @param skill Skill instance with the skill being casted
     * @return {@code false} if the skill is a pvpSkill and target is not a valid pvp target, {@code true} otherwise.
     */
    public boolean checkPvpSkill(WorldObject target, Skill skill) {
        if ((skill == null) || (target == null)) {
            return false;
        }

        if (!target.isPlayable()) {
            return true;
        }

        if (skill.isDebuff() || skill.hasEffectType(EffectType.STEAL_ABNORMAL) || skill.hasNegativeEffect()) {
            final Player targetPlayer = target.asPlayer();
            if ((targetPlayer == null) || (this == target)) {
                return false;
            }

            // Duel
            if (isInDuel() && targetPlayer.isInDuel() && (getDuelId() == targetPlayer.getDuelId())) {
                return true;
            }

            // Olympiad
            if (isInOlympiadMode()
                    && targetPlayer.isInOlympiadMode()
                    && (getOlympiadGameId() == targetPlayer.getOlympiadGameId())) {
                return true;
            }

            // Peace Zone
            if (target.isInsideZone(ZoneId.PEACE)) {
                return false;
            }

            // PvP Skills
            if (skill.isPvPOnly()
                    && ((targetPlayer.getPvpFlag() == 0) && (targetPlayer.getKarma() == 0))
                    && !isInsideZone(ZoneId.PVP)
                    && !isInsideZone(ZoneId.SIEGE)) {
                return false;
            }

            // Siege
            if (isSiegeFriend(targetPlayer)) {
                sendPacket(SystemMessageId.FORCE_ATTACK_IS_IMPOSSIBLE_AGAINST_A_TEMPORARY_ALLIED_MEMBER_DURING_A_SIEGE);
                return false;
            }

            final boolean isCtrlPressed = (currentSkill != null) && currentSkill.isCtrlPressed();
            // Party
            if ((isInParty() && targetPlayer.isInParty()) //
                    && ((getParty().getLeader() == targetPlayer.getParty().getLeader())
                            || ((party.getCommandChannel() != null)
                                    && party.getCommandChannel().containsPlayer(targetPlayer)))) {
                return (skill.getEffectRange() > 0) && isCtrlPressed && (getTarget() == target) && skill.isDamage();
            }

            // You can debuff anyone except party members while in an arena...
            if (isInsideZone(ZoneId.PVP) && targetPlayer.isInsideZone(ZoneId.PVP)) {
                return true;
            }

            final Clan tClan = targetPlayer.getClan();
            if ((clan != null) && (tClan != null)) {
                if (clan.isAtWarWith(tClan.getId()) && tClan.isAtWarWith(clan.getId())) {
                    // Always return true at war.
                    return true;
                } else if ((getClanId() == targetPlayer.getClanId())
                        || ((getAllyId() > 0) && (getAllyId() == targetPlayer.getAllyId()))) {
                    // Check if skill can do dmg.
                    return (skill.getEffectRange() > 0) && isCtrlPressed && (getTarget() == target) && skill.isDamage();
                }
            }

            // On retail, it is impossible to debuff a "peaceful" player.
            if ((targetPlayer.getPvpFlag() == 0) && (targetPlayer.getKarma() == 0)) {
                // Check if skill can do dmg.
                return (skill.getEffectRange() > 0) && isCtrlPressed && (getTarget() == target) && skill.isDamage();
            }

            return (targetPlayer.getPvpFlag() > 0) || (targetPlayer.getKarma() > 0);
        }

        return true;
    }

    /**
     * @return True if the Player is a Mage.
     */
    public boolean isMageClass() {
        return getPlayerClass().isMage();
    }

    public boolean isMounted() {
        return mountType != MountType.NONE;
    }

    public boolean checkLandingState() {
        // Check if char is in a no landing zone.
        if (isInsideZone(ZoneId.NO_LANDING)) {
            return true;
        } else
        // If this is a castle that is currently being sieged, and the rider is NOT a castle owner.
        // he cannot land.
        // Castle owner is the leader of the clan that owns the castle where the pc is.
        if (isInsideZone(ZoneId.SIEGE)
                && ((getClan() == null)
                        || (CastleManager.getInstance().getCastle(this)
                                != CastleManager.getInstance().getCastleByOwner(getClan()))
                        || (this != getClan().getLeader().getPlayer()))) {
            return true;
        }

        return false;
    }

    // Returns false if the change of mount type fails.
    public void setMount(int npcId, int npcLevel) {
        final MountType type = MountType.findByNpcId(npcId);
        switch (type) {
            case NONE: // None
            {
                if (isFlying()) {
                    removeSkill(CommonSkill.WYVERN_BREATH.getSkill().getId(), false);
                    setFlying(false);
                    sendSkillList();
                }
                break;
            }
            case WYVERN: // Wyvern
            {
                setFlying(true);
                addSkill(CommonSkill.WYVERN_BREATH.getSkill(), false);
                sendSkillList();
                break;
            }
        }

        mountType = type;
        mountNpcId = npcId;
        mountLevel = npcLevel;
    }

    @Override
    public void stopAllEffects() {
        super.stopAllEffects();
        updateAndBroadcastStatus();
    }

    @Override
    public void stopAllEffectsExceptThoseThatLastThroughDeath() {
        super.stopAllEffectsExceptThoseThatLastThroughDeath();
        updateAndBroadcastStatus();
    }

    public void stopAllEffectsNotStayOnSubclassChange() {
        getEffectList().stopAllEffectsNotStayOnSubclassChange();
        updateAndBroadcastStatus();
    }

    public void stopCubics() {
        if (cubics.isEmpty()) {
            return;
        }

        for (Cubic cubic : cubics.values()) {
            cubic.stopAction();
            cubic.cancelDisappear();
        }

        cubics.clear();
        cubicOrder.clear();
        broadcastUserInfo();
    }

    public void stopCubicsByOthers() {
        if (cubics.isEmpty()) {
            return;
        }

        boolean broadcast = false;
        for (Cubic cubic : cubics.values()) {
            if (cubic.givenByOther()) {
                removeCubic(cubic.getId());
                broadcast = true;
            }
        }

        if (broadcast) {
            broadcastUserInfo();
        }
    }

    /**
     * Send a Server->Client packet UserInfo to this Player and CharInfo to all known players.
     */
    @Override
    public void updateAbnormalEffect() {
        broadcastUserInfo();
    }

    /**
     * Disable the Inventory and create a new task to enable it after 1.5s.
     * @param value
     */
    public void setInventoryBlockingStatus(boolean value) {
        inventoryDisabled = value;
        if (value) {
            ThreadPool.schedule(new InventoryEnableTask(this), 1500);
        }
    }

    /**
     * Add a cubic to this player.
     * @param cubicId the cubic ID
     * @param level
     * @param cubicPower
     * @param cubicDelay
     * @param cubicSkillChance
     * @param cubicMaxCount
     * @param cubicDuration
     * @param givenByOther
     * @return the old cubic for this cubic ID if any, otherwise {@code null}
     */
    public Cubic addCubic(
            int cubicId,
            int level,
            double cubicPower,
            int cubicDelay,
            int cubicSkillChance,
            int cubicMaxCount,
            int cubicDuration,
            boolean givenByOther) {
        final Cubic cubic = new Cubic(
                this,
                cubicId,
                level,
                (int) cubicPower,
                cubicDelay,
                cubicSkillChance,
                cubicMaxCount,
                cubicDuration,
                givenByOther);
        final Cubic oldCubic = cubics.put(cubicId, cubic);
        cubicOrder.remove(cubicId);
        cubicOrder.add(cubicId);
        return oldCubic;
    }

    /**
     * Remove a cubic and cancel its scheduled tasks.
     * @param cubicId the cubic ID
     * @return the removed cubic, {@code null} otherwise
     */
    public Cubic removeCubic(int cubicId) {
        final Cubic cubic = cubics.remove(cubicId);
        cubicOrder.remove(cubicId);
        if (cubic != null) {
            cubic.stopAction();
            cubic.cancelDisappear();
        }
        return cubic;
    }

    /**
     * Remove the oldest cubic by summon order.
     * @return the removed cubic, {@code null} otherwise
     */
    public Cubic removeFirstCubic() {
        while (!cubicOrder.isEmpty()) {
            final Integer cubicId = cubicOrder.poll();
            if (cubicId == null) {
                return null;
            }

            final Cubic cubic = cubics.remove(cubicId);
            if (cubic != null) {
                cubic.stopAction();
                cubic.cancelDisappear();
                return cubic;
            }
        }
        return null;
    }

    /**
     * Get the player's cubics.
     * @return the cubics
     */
    public Map<Integer, Cubic> getCubics() {
        return Collections.unmodifiableMap(cubics);
    }

    /**
     * Get the player cubic by cubic ID, if any.
     * @param cubicId the cubic ID
     * @return the cubic with the given cubic ID, {@code null} otherwise
     */
    public Cubic getCubicById(int cubicId) {
        return cubics.get(cubicId);
    }

    /**
     * @return the modifier corresponding to the Enchant Effect of the Active Weapon (Min : 127).
     */
    public int getEnchantEffect() {
        final Item wpn = getActiveWeaponInstance();
        return wpn == null ? 0 : Math.min(127, wpn.getEnchantLevel());
    }

    /**
     * @return True if Player is a participant in the Festival of Darkness.
     */
    public boolean isFestivalParticipant() {
        return SevenSignsFestival.getInstance().isParticipant(this);
    }

    public void addAutoSoulShot(int itemId) {
        autoSoulShot.add(itemId);
    }

    public boolean removeAutoSoulShot(int itemId) {
        return autoSoulShot.remove(itemId);
    }

    @Override
    public void rechargeShots(boolean physical, boolean magic) {
        Item item;
        IItemHandler handler;
        if ((autoSoulShot == null) || autoSoulShot.isEmpty()) {
            return;
        }

        for (int itemId : autoSoulShot) {
            item = inventory.getItemByItemId(itemId);
            if (item != null) {
                if (magic && (item.getTemplate().getDefaultAction() == ActionType.SPIRITSHOT)) {
                    handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
                    if (handler != null) {
                        handler.onItemUse(this, item, false);
                    }
                }

                if (physical && (item.getTemplate().getDefaultAction() == ActionType.SOULSHOT)) {
                    handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
                    if (handler != null) {
                        handler.onItemUse(this, item, false);
                    }
                }
            } else {
                removeAutoSoulShot(itemId);
            }
        }
    }

    /**
     * Cancel autoshot for all shots matching crystaltype {@link ItemTemplate#getCrystalType()}.
     * @param crystalType int type to disable
     */
    public void disableAutoShotByCrystalType(int crystalType) {
        for (int itemId : autoSoulShot) {
            if (ItemData.getInstance().getTemplate(itemId).getCrystalType().getLevel() == crystalType) {
                disableAutoShot(itemId);
            }
        }
    }

    /**
     * Cancel autoshot use for shot itemId
     * @param itemId int id to disable
     * @return true if canceled.
     */
    public boolean disableAutoShot(int itemId) {
        if (autoSoulShot.contains(itemId)) {
            removeAutoSoulShot(itemId);
            sendPacket(new ExAutoSoulShot(itemId, 0));

            final SystemMessage sm = new SystemMessage(SystemMessageId.THE_AUTOMATIC_USE_OF_S1_HAS_BEEN_DEACTIVATED);
            sm.addItemName(itemId);
            sendPacket(sm);
            return true;
        }

        return false;
    }

    /**
     * Cancel all autoshots for player
     */
    public void disableAutoShotsAll() {
        for (int itemId : autoSoulShot) {
            sendPacket(new ExAutoSoulShot(itemId, 0));
            final SystemMessage sm = new SystemMessage(SystemMessageId.THE_AUTOMATIC_USE_OF_S1_HAS_BEEN_DEACTIVATED);
            sm.addItemName(itemId);
            sendPacket(sm);
        }

        autoSoulShot.clear();
    }

    public void setClanPrivileges(ClanPrivileges clanPrivileges) {
        this.clanPrivileges = clanPrivileges.clone();
    }

    public boolean hasAccess(ClanAccess access) {
        return clanPrivileges.hasMinimumPrivileges(access);
    }

    // baron etc
    public void setPledgeClass(int id) {
        pledgeClass = id;
        checkItemRestriction();
    }

    @Override
    public void sendMessage(String message) {
        sendPacket(new SystemMessage(SendMessageLocalisationData.getLocalisation(this, message)));
    }

    /**
     * Sends a system message to the player.
     * <p>
     * If the GM startup builder hide configuration is enabled, the message will be sent using a localized say packet. Otherwise, the message will be sent using the standard sendMessage method.
     * </p>
     * @param message the message to send to the player.
     */
    public void sendSysMessage(String message) {
        if (GeneralConfig.GM_STARTUP_BUILDER_HIDE) {
            sendPacket(new CreatureSay(
                    null, ChatType.GENERAL, "SYS", SendMessageLocalisationData.getLocalisation(this, message)));
        } else {
            sendMessage(message);
        }
    }

    /**
     * Toggles the hiding state for GM characters.
     * <p>
     * Only applicable for GM characters. This method sets the player's invisibility, invulnerability and silence mode based on the provided hiding state. It also updates the player's abnormal visual effects and broadcasts user info.
     * </p>
     * @param hide {@code true} to enable hiding (invisibility, silence, invulnerability), {@code false} to disable hiding.
     * @return {@code true} if the hiding state was changed, {@code false} if the state was already set or the player is not a GM.
     */
    public boolean setHiding(boolean hide) {
        if (!isGM()) {
            return false;
        }

        if (hasEnteredWorld()) {
            if (isInvisible() && hide) {
                // Already hiding.
                return false;
            }

            if (!isInvisible() && !hide) {
                // Already visible.
                return false;
            }
        }

        setSilenceMode(hide);
        setInvul(hide);
        setInvisible(hide);

        if (hide && hasEnteredWorld()) {
            World.forEachVisibleObject(this, Player.class, player -> {
                if (player.getTarget() == this) {
                    player.setTarget(null);
                }
            });
        }

        broadcastUserInfo();
        return true;
    }

    public void enterObserverMode(Location loc) {
        setLastLocation();

        // Remove Hide.
        getEffectList().stopSkillEffects(SkillFinishType.REMOVED, AbnormalType.HIDE);
        observerMode = true;
        setTarget(null);
        setParalyzed(true);
        startParalyze();
        setInvul(true);
        setInvisible(true);
        sendPacket(new ObservationMode(loc));
        teleToLocation(loc, false);
        broadcastUserInfo();
    }

    public void setLastLocation() {
        lastLocation.setXYZ(getX(), getY(), getZ());
    }

    public void unsetLastLocation() {
        lastLocation.setXYZ(0, 0, 0);
    }

    public void enterOlympiadObserverMode(Location loc, int id, int instanceId) {
        if (hasSummon()) {
            summon.unSummon(this);
        }

        // Remove Hide.
        getEffectList().stopSkillEffects(SkillFinishType.REMOVED, AbnormalType.HIDE);
        if (!cubics.isEmpty()) {
            for (Cubic cubic : cubics.values()) {
                cubic.stopAction();
                cubic.cancelDisappear();
            }

            cubics.clear();
            cubicOrder.clear();
        }

        if (party != null) {
            party.removePartyMember(this, PartyMessageType.EXPELLED);
        }

        olympiadGameId = id;
        if (sitting) {
            standUp();
        }

        if (!observerMode) {
            setLastLocation();
        }

        observerMode = true;
        setTarget(null);
        setInvul(true);
        setInvisible(true);
        teleToLocation(loc, instanceId, 0);
        sendPacket(new ExOlympiadMode(3));
        broadcastUserInfo();
    }

    public void leaveObserverMode() {
        setTarget(null);

        teleToLocation(lastLocation, false);
        unsetLastLocation();
        sendPacket(new ObservationReturn(getLocation()));
        setParalyzed(false);
        if (!isGM()) {
            setInvisible(false);
            setInvul(false);
        }

        if (hasAI()) {
            getAI().setIntentionIdle();
        }

        setFalling(); // Prevent receive falling damage.
        observerMode = false;
        broadcastUserInfo();
    }

    public void leaveOlympiadObserverMode() {
        if (olympiadGameId == -1) {
            return;
        }

        olympiadGameId = -1;
        observerMode = false;
        setTarget(null);
        sendPacket(new ExOlympiadMode(0));
        setInstanceId(0);
        teleToLocation(lastLocation, true);
        if (!isGM()) {
            setInvisible(false);
            setInvul(false);
        }

        if (hasAI()) {
            getAI().setIntentionIdle();
        }

        unsetLastLocation();
        broadcastUserInfo();
    }

    public boolean inObserverMode() {
        return observerMode;
    }

    public void setLoto(int i, int value) {
        loto[i] = value;
    }

    public int getLoto(int i) {
        return loto[i];
    }

    public void setRaceTicket(int i, int value) {
        raceTickets[i] = value;
    }

    public int getRaceTicket(int i) {
        return raceTickets[i];
    }

    public boolean getMessageRefusal() {
        return messageRefusal;
    }

    public void setMessageRefusal(boolean mode) {
        messageRefusal = mode;
        sendPacket(new EtcStatusUpdate(this));
    }

    public boolean getDietMode() {
        return dietMode;
    }

    public boolean getTradeRefusal() {
        return tradeRefusal;
    }

    public boolean getExchangeRefusal() {
        return exchangeRefusal;
    }

    public void setHero(boolean hero) {
        if (hero && (baseClass == activeClass)) {
            for (Skill skill : SkillTreeData.getInstance().getHeroSkillTree().values()) {
                addSkill(skill, false); // Don't persist hero skills into database.
            }
        } else {
            for (Skill skill : SkillTreeData.getInstance().getHeroSkillTree().values()) {
                removeSkill(skill, false, true); // Just remove skills from non-hero players.
            }
        }

        this.hero = hero;
        sendSkillList();
    }

    public void setStartingDuel() {
        startingDuel = true;
    }

    /**
     * Sets up the duel state using a non 0 duelId.
     * @param duelId 0=not in a duel
     */
    public void setInDuel(int duelId) {
        if (duelId > 0) {
            inDuel = true;
            duelState = Duel.DUELSTATE_DUELLING;
            this.duelId = duelId;
        } else {
            if (duelState == Duel.DUELSTATE_DEAD) {
                enableAllSkills();
                getStatus().startHpMpRegeneration();
            }

            inDuel = false;
            duelState = Duel.DUELSTATE_NODUEL;
            this.duelId = 0;
        }

        startingDuel = false;
    }

    /**
     * This returns a SystemMessage stating why the player is not available for duelling.
     * @return S1_CANNOT_DUEL... message
     */
    public SystemMessage getNoDuelReason() {
        final SystemMessage sm = new SystemMessage(noDuelReason);
        sm.addPcName(this);
        noDuelReason = SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL;
        return sm;
    }

    /**
     * Checks if this player might join / start a duel.<br>
     * To get the reason use getNoDuelReason() after calling this function.
     * @return true if the player might join/start a duel.
     */
    public boolean canDuel() {
        if (isInCombat() || isJailed()) {
            noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_ENGAGED_IN_BATTLE;
            return false;
        }

        if (isDead() || isAlikeDead() || ((getCurrentHp() < (getMaxHp() / 2)) || (getCurrentMp() < (getMaxMp() / 2)))) {
            noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_S_HP_OR_MP_IS_BELOW_50;
            return false;
        }

        if (inDuel || startingDuel) {
            noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_ALREADY_ENGAGED_IN_A_DUEL;
            return false;
        }

        if (inOlympiadMode) {
            noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_PARTICIPATING_IN_THE_OLYMPIAD;
            return false;
        }

        if (isCursedWeaponEquipped()) {
            noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_IN_A_CHAOTIC_STATE;
            return false;
        }

        if (privateStoreType != PrivateStoreType.NONE) {
            noDuelReason =
                    SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_ENGAGED_IN_A_PRIVATE_STORE_OR_MANUFACTURE;
            return false;
        }

        if (isMounted() || isInBoat()) {
            noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_RIDING_A_BOAT_STEED_OR_STRIDER;
            return false;
        }

        if (fishing) {
            noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_FISHING;
            return false;
        }

        if (isInsideZone(ZoneId.PVP)
                || isInsideZone(ZoneId.PEACE)
                || isInsideZone(ZoneId.SIEGE)
                || isInsideZone(ZoneId.NO_PVP)) {
            noDuelReason = SystemMessageId
                    .C1_CANNOT_MAKE_A_CHALLENGE_TO_A_DUEL_BECAUSE_C1_IS_CURRENTLY_IN_A_DUEL_PROHIBITED_AREA_PEACEFUL_ZONE_SEVEN_SIGNS_ZONE_NEAR_WATER_RESTART_PROHIBITED_AREA;
            return false;
        }

        return true;
    }

    public void setNoble(boolean value) {
        final Collection<Skill> nobleSkillTree =
                SkillTreeData.getInstance().getNobleSkillTree().values();
        if (value) {
            for (Skill skill : nobleSkillTree) {
                addSkill(skill, false);
            }
        } else {
            for (Skill skill : nobleSkillTree) {
                removeSkill(skill, false, true);
            }
        }

        noble = value;
        sendSkillList();
    }

    @Override
    public boolean isAcademyMember() {
        return lvlJoinedAcademy > 0;
    }

    @Override
    public void setTeam(Team team) {
        super.setTeam(team);
        broadcastUserInfo();
        if (hasSummon()) {
            summon.broadcastStatusUpdate();
        }
    }

    public synchronized void sendSkillList() {
        if (skillListTask == null) {
            skillListTask = ThreadPool.schedule(
                    () -> {
                        boolean isDisabled = false;
                        final SkillList skillList = new SkillList();
                        for (Skill skill : getAllSkills()) {
                            if ((skill == null)
                                    || ((transformation != null) && !skill.isPassive())
                                    || (hasTransformSkill(skill.getId()) && skill.isPassive())) {
                                continue;
                            }

                            if (clan != null) {
                                isDisabled = skill.isClanSkill() && (clan.getReputationScore() < 0);
                            }

                            boolean isEnchantable = SkillData.getInstance().isEnchantable(skill.getId());
                            if (isEnchantable) {
                                final EnchantSkillLearn esl = EnchantSkillGroupsData.getInstance()
                                        .getSkillEnchantmentBySkillId(skill.getId());
                                if ((esl == null) || (skill.getLevel() < esl.getBaseLevel())) {
                                    isEnchantable = false;
                                }
                            }

                            skillList.addSkill(
                                    skill.getDisplayId(),
                                    skill.getDisplayLevel(),
                                    skill.isPassive(),
                                    isDisabled,
                                    isEnchantable);
                        }

                        if (transformation != null) {
                            final Map<Integer, Integer> ts = new TreeMap<>();
                            for (SkillHolder holder :
                                    transformation.getTemplate(this).getSkills()) {
                                ts.putIfAbsent(holder.getSkillId(), holder.getSkillLevel());
                                if (ts.get(holder.getSkillId()) < holder.getSkillLevel()) {
                                    ts.put(holder.getSkillId(), holder.getSkillLevel());
                                }
                            }

                            for (AdditionalSkillHolder holder :
                                    transformation.getTemplate(this).getAdditionalSkills()) {
                                if (getLevel() >= holder.getMinLevel()) {
                                    ts.putIfAbsent(holder.getSkillId(), holder.getSkillLevel());
                                    if (ts.get(holder.getSkillId()) < holder.getSkillLevel()) {
                                        ts.put(holder.getSkillId(), holder.getSkillLevel());
                                    }
                                }
                            }

                            // Add collection skills.
                            if (transformation.isFlying()) {
                                for (SkillLearn skill : SkillTreeData.getInstance()
                                        .getCollectSkillTree()
                                        .values()) {
                                    if ((getKnownSkill(skill.getSkillId()) != null)
                                            && (!ts.containsKey(skill.getSkillId())
                                                    || (ts.get(skill.getSkillId()) < skill.getSkillLevel()))) {
                                        ts.put(skill.getSkillId(), skill.getSkillLevel());
                                    }
                                }
                            }

                            for (Entry<Integer, Integer> transformSkill : ts.entrySet()) {
                                final Skill sk = SkillData.getInstance()
                                        .getSkill(transformSkill.getKey(), transformSkill.getValue());
                                addTransformSkill(sk);
                                skillList.addSkill(
                                        transformSkill.getKey(), transformSkill.getValue(), false, false, false);
                            }
                        }

                        sendPacket(skillList);
                        skillListTask = null;
                    },
                    300);
        }
    }

    /**
     * 1. Add the specified class ID as a subclass (up to the maximum number of <b>three</b>) for this character.<br>
     * 2. This method no longer changes the active classIndex of the player. This is only done by the calling of setActiveClass() method as that should be the only way to do so.
     * @param classId
     * @param classIndex
     * @return boolean subclassAdded
     */
    public boolean addSubClass(int classId, int classIndex) {
        if (changingClass) {
            return false;
        }

        changingClass = true;

        try {
            if ((getTotalSubClasses() == PlayerConfig.MAX_SUBCLASS) || (classIndex == 0)) {
                return false;
            }

            if (getSubClasses().containsKey(classIndex)) {
                return false;
            }

            // Note: Never change classIndex in any method other than setActiveClass().

            final SubClassHolder newClass = new SubClassHolder();
            newClass.setPlayerClass(classId);
            newClass.setClassIndex(classIndex);

            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement ps = con.prepareStatement(ADD_CHAR_SUBCLASS)) {
                // Store the basic info about this new sub-class.
                ps.setInt(1, getObjectId());
                ps.setInt(2, newClass.getId());
                ps.setLong(3, newClass.getExp());
                ps.setLong(4, newClass.getSp());
                ps.setInt(5, newClass.getLevel());
                ps.setInt(6, newClass.getClassIndex()); // <-- Added
                ps.execute();
            } catch (Exception e) {
                log.warn("WARNING: Could not add character sub class for {}: {}", getName(), e.getMessage(), e);
                return false;
            }

            // Commit after database INSERT incase exception is thrown.
            getSubClasses().put(newClass.getClassIndex(), newClass);

            final PlayerClass subTemplate = PlayerClass.getPlayerClass(classId);
            final Map<Integer, SkillLearn> skillTree =
                    SkillTreeData.getInstance().getCompleteClassSkillTree(subTemplate);
            final Map<Integer, Skill> prevSkillList = new HashMap<>();
            for (SkillLearn skillInfo : skillTree.values()) {
                if (skillInfo.getGetLevel() <= 40) {
                    final Skill prevSkill = prevSkillList.get(skillInfo.getSkillId());
                    final Skill newSkill =
                            SkillData.getInstance().getSkill(skillInfo.getSkillId(), skillInfo.getSkillLevel());
                    if ((prevSkill != null) && (prevSkill.getLevel() > newSkill.getLevel())) {
                        continue;
                    }

                    prevSkillList.put(newSkill.getId(), newSkill);
                    storeSkill(newSkill, prevSkill, classIndex);
                }
            }

            return true;
        } finally {
            changingClass = false;
        }
    }

    /**
     * 1. Completely erase all existance of the subClass linked to the classIndex.<br>
     * 2. Send over the newClassId to addSubClass() to create a new instance on this classIndex.<br>
     * 3. Upon Exception, revert the player to their BaseClass to avoid further problems.
     * @param classIndex the class index to delete
     * @param newClassId the new class Id
     * @return {@code true} if the sub-class was modified, {@code false} otherwise
     */
    public boolean modifySubClass(int classIndex, int newClassId) {
        final SubClassHolder subClass = getSubClasses().get(classIndex);
        if (subClass == null) {
            return false;
        }

        // Notify to scripts before class is removed.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_PROFESSION_CANCEL, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerProfessionCancel(this, subClass.getId()), this);
        }

        getSubClasses().remove(classIndex);

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement deleteHennas = con.prepareStatement(DELETE_CHAR_HENNA);
                PreparedStatement deleteShortcuts = con.prepareStatement(DELETE_CHAR_SHORTCUTS);
                PreparedStatement deleteSkillReuse = con.prepareStatement(DELETE_SKILL_SAVE);
                PreparedStatement deleteSkills = con.prepareStatement(DELETE_CHAR_SKILLS);
                PreparedStatement deleteSubclass = con.prepareStatement(DELETE_CHAR_SUBCLASS)) {
            // Remove class permitted hennas.
            for (int slot = 1; slot < 4; slot++) {
                final Henna henna = getHenna(slot);
                if (henna != null) {
                    deleteHennas.setInt(1, getObjectId());
                    deleteHennas.setInt(2, slot);
                    deleteHennas.setInt(3, classIndex);
                    deleteHennas.execute();
                }
            }

            // Remove all shortcuts info stored for this sub-class.
            deleteShortcuts.setInt(1, getObjectId());
            deleteShortcuts.setInt(2, classIndex);
            deleteShortcuts.execute();

            // Remove all effects info stored for this sub-class.
            deleteSkillReuse.setInt(1, getObjectId());
            deleteSkillReuse.setInt(2, classIndex);
            deleteSkillReuse.execute();

            // Remove all skill info stored for this sub-class.
            deleteSkills.setInt(1, getObjectId());
            deleteSkills.setInt(2, classIndex);
            deleteSkills.execute();

            // Remove all basic info stored about this sub-class.
            deleteSubclass.setInt(1, getObjectId());
            deleteSubclass.setInt(2, classIndex);
            deleteSubclass.execute();
        } catch (Exception e) {
            log.warn(
                    "Could not modify sub class for {} to class index {}: {}",
                    getName(),
                    classIndex,
                    e.getMessage(),
                    e);
            return false;
        }

        return addSubClass(newClassId, classIndex);
    }

    public boolean isSubClassActive() {
        return classIndex > 0;
    }

    public int getTotalSubClasses() {
        return getSubClasses().size();
    }

    private void setClassTemplate(int classId) {
        activeClass = classId;

        final PlayerTemplate pcTemplate = PlayerTemplateData.getInstance().getTemplate(classId);
        if (pcTemplate == null) {
            log.error("Missing template for classId: {}", classId);
            throw new Error();
        }

        // Set the template of the Player.
        setTemplate(pcTemplate);

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_PROFESSION_CHANGE, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerProfessionChange.builder()
                                    .player(this)
                                    .template(pcTemplate)
                                    .subClass(isSubClassActive())
                                    .build(),
                            this);
        }
    }

    /**
     * Changes the character's class based on the given class index.<br>
     * An index of zero specifies the character's original (base) class, while indexes 1-3 specifies the character's sub-classes respectively.<br>
     * <font color="00FF00"/>WARNING: Use only on subclase change</font>
     * @param classIndex
     */
    public void setActiveClass(int classIndex) {
        if (changingClass) {
            return;
        }

        changingClass = true;

        try {
            // Cannot switch or change subclasses while transformed.
            if (transformation != null) {
                return;
            }

            // Remove active item skills before saving char to database.
            // because next time when choosing this class, weared items can
            // be different
            for (Item item : inventory.getAugmentedItems()) {
                if ((item != null) && item.isEquipped()) {
                    item.getAugmentation().removeBonus(this);
                }
            }

            // Abort any kind of cast.
            abortCast();

            if (isChannelized()) {
                getSkillChannelized().abortChannelization();
            }

            // 1. Call store() before modifying classIndex to avoid skill effects rollover.
            // 2. Register the correct classId against applied 'classIndex'.
            store(PlayerConfig.SUBCLASS_STORE_SKILL_COOLTIME);

            resetTimeStamps();

            // Clear charges.
            charges.set(0);
            stopChargeTask();

            if (hasServitor()) {
                summon.unSummon(this);
            }

            if (classIndex == 0) {
                setClassTemplate(baseClass);
            } else {
                try {
                    setClassTemplate(getSubClasses().get(classIndex).getId());
                } catch (Exception e) {
                    log.warn(
                            "Could not switch {}'s sub class to class index {}: {}",
                            getName(),
                            classIndex,
                            e.getMessage(),
                            e);
                    return;
                }
            }

            this.classIndex = classIndex;
            setLearningClass(getPlayerClass());

            if (isInParty()) {
                party.recalculatePartyLevel();
            }

            // Update the character's change in class status.
            // 1. Remove any active cubics from the player.
            // 2. Renovate the characters table in the database with the new class info, storing also buff/effect data.
            // 3. Remove all existing skills.
            // 4. Restore all the learned skills for the current class from the database.
            // 5. Restore effect/buff data for the new class.
            // 6. Restore henna data for the class, applying the new stat modifiers while removing existing ones.
            // 7. Reset HP/MP/CP stats and send Server->Client character status packet to reflect changes.
            // 8. Restore shortcut data related to this class.
            // 9. Resend a class change animation effect to broadcast to all nearby players.
            autoUseSettings.getAutoSkills().clear();
            autoUseSettings.getAutoBuffs().clear();
            for (Skill oldSkill : getAllSkills()) {
                removeSkill(oldSkill, false, true);
            }

            stopAllEffectsExceptThoseThatLastThroughDeath();
            stopAllEffectsNotStayOnSubclassChange();
            getEffectList().stopAllToggles();
            stopCubics();
            restoreRecipeBook(false);

            // Restore any Death Penalty Buff.
            restoreDeathPenaltyBuffLevel();

            restoreSkills();
            rewardSkills();
            regiveTemporarySkills();
            getInventory().applyItemSkills();

            // Prevents some issues when changing between subclases that shares skills.
            resetDisabledSkills();

            restoreEffects();

            sendPacket(new EtcStatusUpdate(this));

            // if player has quest 422: Repent Your Sins, remove it
            final QuestState st = getQuestState("Q00422_RepentYourSins");
            if (st != null) {
                st.exitQuest(true);
            }

            for (int i = 0; i < 3; i++) {
                hennaList[i] = null;
            }

            restoreHenna();
            sendPacket(new HennaInfo(this));
            if (getCurrentHp() > getMaxHp()) {
                setCurrentHp(getMaxHp());
            }

            if (getCurrentMp() > getMaxMp()) {
                setCurrentMp(getMaxMp());
            }

            if (getCurrentCp() > getMaxCp()) {
                setCurrentCp(getMaxCp());
            }

            refreshOverloaded();
            refreshExpertisePenalty();
            broadcastUserInfo();

            // Clear resurrect xp calculation.
            setExpBeforeDeath(0);

            shortcuts.restoreMe();
            sendPacket(new ShortcutInit(this));
            broadcastPacket(new SocialAction(getObjectId(), SocialAction.LEVEL_UP));
            sendPacket(new SkillCoolTime(this));
            sendPacket(new ExStorageMaxCount(this));
        } finally {
            changingClass = false;
        }
    }

    public void stopWarnUserTakeBreak() {
        if (taskWarnUserTakeBreak == null) {
            return;
        }

        taskWarnUserTakeBreak.cancel(true);
        taskWarnUserTakeBreak = null;
    }

    public void startWarnUserTakeBreak() {
        if (taskWarnUserTakeBreak == null) {
            taskWarnUserTakeBreak = ThreadPool.scheduleAtFixedRate(new WarnUserTakeBreakTask(this), 7200000, 7200000);
        }
    }

    public void stopRentPet() {
        if (taskRentPet != null) {
            // If the rent of a wyvern expires while over a flying zone, tp to down before unmounting.
            if (checkLandingState() && (mountType == MountType.WYVERN)) {
                teleToLocation(TeleportWhereType.TOWN);
            }

            if (dismount()) // This should always be true now, since we teleported already.
            {
                taskRentPet.cancel(true);
                taskRentPet = null;
            }
        }
    }

    public void startRentPet(int seconds) {
        if (taskRentPet == null) {
            taskRentPet = ThreadPool.scheduleAtFixedRate(new RentPetTask(this), seconds * 1000, seconds * 1000);
        }
    }

    public boolean isRentedPet() {
        return taskRentPet != null;
    }

    public void stopWaterTask() {
        if (taskWater == null) {
            return;
        }

        taskWater.cancel(false);
        taskWater = null;
        sendPacket(new SetupGauge(getObjectId(), 2, 0));
    }

    public void startWaterTask() {
        if (isDead() || (taskWater != null)) {
            return;
        }

        // Base time in water is 60 seconds (60000 milliseconds).
        final int baseTimeInWater = 60000;

        // Calculate the total stat as a percentage of the base time.
        final double breathPercentage = calcStat(Stat.BREATH, getBaseTemplate().getBaseBreath()) / 100d;

        // Apply the percentage to the base time.
        final int timeInWater = (int) (baseTimeInWater * breathPercentage);

        // Send the setup gauge packet with the calculated time.
        sendPacket(new SetupGauge(getObjectId(), 2, timeInWater));

        // Schedule the water task.
        taskWater = ThreadPool.scheduleAtFixedRate(new WaterTask(this), timeInWater, 1000);
    }

    public boolean isInWater() {
        return taskWater != null;
    }

    public void checkWaterState() {
        if (isInsideZone(ZoneId.WATER)) {
            startWaterTask();
        } else {
            stopWaterTask();
        }
    }

    public void onPlayerEnter() {
        startWarnUserTakeBreak();

        if (SevenSigns.getInstance().isSealValidationPeriod()
                || SevenSigns.getInstance().isCompResultsPeriod()) {
            if (!isGM()
                    && isIn7sDungeon()
                    && (SevenSigns.getInstance().getPlayerCabal(getObjectId())
                            != SevenSigns.getInstance().getCabalHighestScore())) {
                teleToLocation(TeleportWhereType.TOWN);
                setIn7sDungeon(false);
                sendMessage(
                        "You have been teleported to the nearest town due to the beginning of the Seal Validation period.");
            }
        } else {
            if (!isGM()
                    && isIn7sDungeon()
                    && (SevenSigns.getInstance().getPlayerCabal(getObjectId()) == SevenSigns.CABAL_NULL)) {
                teleToLocation(TeleportWhereType.TOWN);
                setIn7sDungeon(false);
                sendMessage("You have been teleported to the nearest town because you have not signed for any cabal.");
            }
        }

        if (isGM() && !GeneralConfig.GM_STARTUP_BUILDER_HIDE) {
            // Bleah, see L2J custom below.
            if (isInvul()) {
                sendMessage("Entering world in Invulnerable mode.");
            }

            if (isInvisible()) {
                sendMessage("Entering world in Invisible mode.");
            }

            if (silenceMode) {
                sendMessage("Entering world in Silence mode.");
            }
        }

        // Buff and status icons
        if (PlayerConfig.STORE_SKILL_COOLTIME) {
            restoreEffects();
        }

        revalidateZone(true);

        notifyFriends();
        if (!isGM() && PlayerConfig.DECREASE_SKILL_LEVEL) {
            checkPlayerSkills();
        }

        try {
            for (ZoneType zone : ZoneManager.getInstance().getZones(this)) {
                zone.onPlayerLoginInside(this);
            }
        } catch (Exception e) {
            log.error("", e);
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_LOGIN, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerLogin(this), this);
        }

        // TODO : Need to fix that hack!
        if (!isDead()) {
            // Run on a separate thread to give time to above events to be notified.
            ThreadPool.schedule(
                    () -> {
                        setCurrentHp(originalHp);
                        setCurrentMp(originalMp);
                        setCurrentCp(originalCp);
                    },
                    300);
        }
    }

    /**
     * Fully restores the player's HP, MP, and CP to their maximum values.
     */
    @Override
    public void fullRestore() {
        setCurrentHp(getMaxHp());
        setCurrentMp(getMaxMp());
        setCurrentCp(getMaxCp());
    }

    @Override
    public void doRevive() {
        super.doRevive();

        // Stop decay task.
        DecayTaskManager.getInstance().cancel(this);

        updateEffectIcons();
        sendPacket(new EtcStatusUpdate(this));
        revivingPet = false;
        reviveRequested = 0;
        revivePower = 0;

        // Teleport summon to player.
        if (isInsideZone(ZoneId.PEACE) && (summon != null) && !summon.isInsideZone(ZoneId.SIEGE)) {
            summon.teleToLocation(getLocation(), true);
        }

        if (isMounted()) {
            startFeed(mountNpcId);
        }

        if (isInParty()
                && party.isInDimensionalRift()
                && !DimensionalRiftManager.getInstance().checkIfInPeaceZone(getX(), getY(), getZ())) {
            party.getDimensionalRift().memberRessurected(this);
        }

        if (isInInstance()) {
            final Instance instance = InstanceManager.getInstance().getInstance(getInstanceId());
            if (instance != null) {
                instance.cancelEjectDeadPlayer(this);
            }
        }
    }

    @Override
    public void setName(String value) {
        super.setName(value);
        CharInfoTable.getInstance().addName(this);
    }

    @Override
    public void doRevive(double revivePower) {
        doRevive();
        restoreExp(revivePower);
    }

    public void reviveRequest(Player reviver, boolean pet, int power) {
        if (isResurrectionBlocked()) {
            return;
        }

        if (reviveRequested == 1) {
            if (revivingPet == pet) {
                reviver.sendPacket(
                        SystemMessageId
                                .RESURRECTION_HAS_ALREADY_BEEN_PROPOSED); // Resurrection is already been proposed.
            } else if (pet) {
                reviver.sendPacket(
                        SystemMessageId
                                .A_PET_CANNOT_BE_RESURRECTED_WHILE_IT_S_OWNER_IS_IN_THE_PROCESS_OF_RESURRECTING); // A
                // pet
                // cannot be resurrected while it's owner is in the process of resurrecting.
            } else {
                reviver.sendPacket(
                        SystemMessageId
                                .WHILE_A_PET_IS_BEING_RESURRECTED_IT_CANNOT_HELP_IN_RESURRECTING_ITS_MASTER); // While a
                // pet is
                // attempting to resurrect, it cannot help in resurrecting its master.
            }
            return;
        }

        if ((pet && hasPet() && summon.isDead()) || (!pet && isDead())) {
            reviveRequested = 1;
            int restoreExp = 0;
            revivePower = Formulas.calculateSkillResurrectRestorePercent(power, reviver);
            restoreExp = (int) Math.round(((expBeforeDeath - getExp()) * revivePower) / 100);
            revivingPet = pet;
            if (hasCharmOfCourage) {
                final ConfirmDlg dlg = new ConfirmDlg(
                        SystemMessageId.YOUR_CHARM_OF_COURAGE_IS_TRYING_TO_RESURRECT_YOU_WOULD_YOU_LIKE_TO_RESURRECT_NOW
                                .getId());
                dlg.addTime(60000);
                sendPacket(dlg);
                return;
            }

            final ConfirmDlg dlg = new ConfirmDlg(SystemMessageId
                    .C1_IS_MAKING_AN_ATTEMPT_TO_RESURRECT_YOU_IF_YOU_CHOOSE_THIS_PATH_S2_EXPERIENCE_POINTS_WILL_BE_RETURNED_TO_YOU_DO_YOU_WANT_TO_BE_RESURRECTED
                    .getId());
            dlg.getSystemMessage().addPcName(reviver);
            dlg.getSystemMessage().addString(Integer.toString(restoreExp));
            sendPacket(dlg);
        }
    }

    public void reviveAnswer(int answer) {
        if ((reviveRequested != 1) || (!isDead() && !revivingPet) || (revivingPet && hasPet() && !summon.isDead())) {
            return;
        }

        if (answer == 1) {
            if (!revivingPet) {
                if (revivePower != 0) {
                    doRevive(revivePower);
                } else {
                    doRevive();
                }
            } else if (hasPet()) {
                if (revivePower != 0) {
                    summon.doRevive(revivePower);
                } else {
                    summon.doRevive();
                }
            }
        }

        revivingPet = false;
        reviveRequested = 0;
        revivePower = 0;
    }

    public boolean isReviveRequested() {
        return reviveRequested == 1;
    }

    public void removeReviving() {
        reviveRequested = 0;
        revivePower = 0;
    }

    public void onActionRequest() {
        if (isSpawnProtected()) {
            setSpawnProtection(false);
            if (!isInsideZone(ZoneId.PEACE)) {
                sendPacket(SystemMessageId.YOU_ARE_NO_LONGER_PROTECTED_FROM_AGGRESSIVE_MONSTERS);
            }

            if (PlayerConfig.RESTORE_SERVITOR_ON_RECONNECT
                    && !hasSummon()
                    && CharSummonTable.getInstance().getServitors().containsKey(getObjectId())) {
                CharSummonTable.getInstance().restoreServitor(this);
            }

            if (PlayerConfig.RESTORE_PET_ON_RECONNECT
                    && !hasSummon()
                    && CharSummonTable.getInstance().getPets().containsKey(getObjectId())) {
                CharSummonTable.getInstance().restorePet(this);
            }
        }

        if (isTeleportProtected()) {
            setTeleportProtection(false);
            if (!isInsideZone(ZoneId.PEACE)) {
                sendMessage("Teleport spawn protection ended.");
            }
        }
    }

    /**
     * Expertise of the Player (None=0, D=1, C=2, B=3, A=4, S=5, S80=6, S84=7)
     * @return int Expertise skill level.
     */
    public int getExpertiseLevel() {
        return getSkillLevel(239);
    }

    @Override
    public void teleToLocation(ILocational loc, boolean allowRandomOffset) {
        if ((vehicle != null) && !vehicle.isTeleporting()) {
            setVehicle(null);
        }

        if (isFlyingMounted() && (loc.getZ() < -1005)) {
            super.teleToLocation(loc.getX(), loc.getY(), -1005, loc.getHeading(), loc.getInstanceId());
        }

        super.teleToLocation(loc, allowRandomOffset);
    }

    @Override
    public synchronized void onTeleported() {
        super.onTeleported();

        if (isInAirShip()) {
            getAirShip().sendInfo(this);
        } else // Update last player position upon teleport.
        {
            setLastServerPosition(getX(), getY(), getZ());
        }

        // Force a revalidation
        revalidateZone(true);

        checkItemRestriction();

        if ((PlayerConfig.PLAYER_TELEPORT_PROTECTION > 0) && !inOlympiadMode) {
            setTeleportProtection(true);
        }

        // Trained beast is lost after teleport.
        if (trainedBeasts != null) {
            for (TamedBeast tamedBeast : trainedBeasts) {
                tamedBeast.deleteMe();
            }

            trainedBeasts.clear();
        }

        // Modify the position of the pet if necessary.
        if (summon != null) {
            summon.setFollowStatus(false);
            summon.teleToLocation(getLocation(), false);
            ((SummonAI) summon.getAI()).setStartFollowController(true);
            summon.setFollowStatus(true);
            summon.updateAndBroadcastStatus(0);
        }

        // show movie if available
        if (movieHolder != null) {
            sendPacket(new ExStartScenePlayer(movieHolder.getMovie()));
        }

        // Stop auto play.
        if (AutoPlayConfig.ENABLE_AUTO_PLAY) {
            AutoPlayTaskManager.getInstance().stopAutoPlay(this);
            AutoUseTaskManager.getInstance().stopAutoUseTask(this);
        }

        // Send info to nearby players.
        broadcastInfo();
    }

    @Override
    public void setTeleporting(boolean teleport) {
        setTeleporting(teleport, true);
    }

    public void setTeleporting(boolean teleport, boolean useWatchDog) {
        super.setTeleporting(teleport);
        if (!useWatchDog) {
            return;
        }

        if (teleport) {
            if ((teleportWatchdog == null) && (PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT > 0)) {
                synchronized (this) {
                    teleportWatchdog = ThreadPool.schedule(
                            new TeleportWatchdogTask(this), PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT * 1000);
                }
            }
        } else if (teleportWatchdog != null) {
            teleportWatchdog.cancel(false);
            teleportWatchdog = null;
        }
    }

    public void setLastServerPosition(int x, int y, int z) {
        lastServerPosition.setXYZ(x, y, z);
    }

    public void setBlinkActive(boolean value) {
        blinkActive.set(value);
    }

    public boolean isBlinkActive() {
        return blinkActive.get();
    }

    @Override
    public synchronized void addExpAndSp(double addToExp, double addToSp) {
        getStat().addExpAndSp(addToExp, addToSp, false);
    }

    public synchronized void addExpAndSp(double addToExp, double addToSp, boolean useVitality) {
        getStat().addExpAndSp(addToExp, addToSp, useVitality);
    }

    public void removeExpAndSp(long removeExp, long removeSp) {
        getStat().removeExpAndSp(removeExp, removeSp, true);
    }

    public void removeExpAndSp(long removeExp, long removeSp, boolean sendMessage) {
        getStat().removeExpAndSp(removeExp, removeSp, sendMessage);
    }

    @Override
    public void reduceCurrentHp(double value, Creature attacker, boolean awake, boolean isDOT, Skill skill) {
        if (skill != null) {
            getStatus().reduceHp(value, attacker, awake, isDOT, skill.isToggle(), skill.getDmgDirectlyToHP());
        } else {
            getStatus().reduceHp(value, attacker, awake, isDOT, false, false);
        }

        // Notify the tamed beast of attacks.
        if (trainedBeasts != null) {
            for (TamedBeast tamedBeast : trainedBeasts) {
                tamedBeast.onOwnerGotAttacked(attacker);
            }
        }
    }

    public void broadcastSnoop(ChatType type, String name, String text, CreatureSay cs) {
        if (snoopListener.isEmpty()) {
            return;
        }

        final Snoop sn = new Snoop(getObjectId(), getName(), type, name, text);
        for (Player pci : snoopListener) {
            if (pci != null) {
                pci.sendPacket(cs);
                pci.sendPacket(sn);
            }
        }
    }

    public void addSnooper(Player pci) {
        snoopListener.add(pci);
    }

    public void removeSnooper(Player pci) {
        snoopListener.remove(pci);
    }

    public void addSnooped(Player pci) {
        snoopedPlayer.add(pci);
    }

    public void removeSnooped(Player pci) {
        snoopedPlayer.remove(pci);
    }

    public void addHtmlAction(HtmlActionScope scope, String action) {
        htmlActionCaches[scope.ordinal()].add(action);
    }

    public void clearHtmlActions(HtmlActionScope scope) {
        htmlActionCaches[scope.ordinal()].clear();
    }

    public void setHtmlActionOriginObjectId(HtmlActionScope scope, int npcObjId) {
        if (npcObjId < 0) {
            throw new IllegalArgumentException();
        }

        htmlActionOriginObjectIds[scope.ordinal()] = npcObjId;
    }

    private boolean validateHtmlAction(Iterable<String> actionIter, String action) {
        for (String cachedAction : actionIter) {
            if (cachedAction.charAt(cachedAction.length() - 1) == AbstractHtmlPacket.VAR_PARAM_START_CHAR) {
                if (action.startsWith(
                        cachedAction.substring(0, cachedAction.length() - 1).trim())) {
                    return true;
                }
            } else if (cachedAction.equals(action)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the HTML action was sent in a HTML packet.<br>
     * If the HTML action was not sent for whatever reason, -1 is returned.<br>
     * Otherwise, the NPC object ID or 0 is returned.<br>
     * 0 means the HTML action was not bound to an NPC<br>
     * and no range checks need to be made.
     * @param action the HTML action to check
     * @return NPC object ID, 0 or -1
     */
    public int validateHtmlAction(String action) {
        for (int i = 0; i < htmlActionCaches.length; ++i) {
            if (validateHtmlAction(htmlActionCaches[i], action)) {
                lastHtmlActionOriginId = htmlActionOriginObjectIds[i];
                return lastHtmlActionOriginId;
            }
        }

        return -1;
    }

    /**
     * Performs following tests:
     * <ul>
     * <li>Inventory contains item</li>
     * <li>Item owner id == owner id</li>
     * <li>It isn't pet control item while mounting pet or pet summoned</li>
     * <li>It isn't active enchant item</li>
     * <li>It isn't cursed weapon/item</li>
     * <li>It isn't wear item</li>
     * </ul>
     * @param objectId item object id
     * @param itemProcessType the item process type
     * @return
     */
    public boolean validateItemManipulation(int objectId, ItemProcessType itemProcessType) {
        final Item item = inventory.getItemByObjectId(objectId);
        if ((item == null) || (item.getOwnerId() != getObjectId())) {
            log.trace("{}: player tried to {} item he is not owner of.", getObjectId(), itemProcessType);
            return false;
        }

        // Pet is summoned and not the item that summoned the pet AND not the buggle from strider you're mounting.
        if ((hasSummon() && (summon.getControlObjectId() == objectId)) || (mountObjectID == objectId)) {
            return false;
        }

        if (activeEnchantItemId == objectId) {
            return false;
        }

        if (CursedWeaponsManager.getInstance().isCursed(item.getId())) {
            // Can not trade a cursed weapon.
            return false;
        }

        return true;
    }

    /**
     * @return Returns the inBoat.
     */
    public boolean isInBoat() {
        return (vehicle != null) && vehicle.isBoat();
    }

    /**
     * @return
     */
    public Boat getBoat() {
        return (Boat) vehicle;
    }

    /**
     * @return Returns the inAirShip.
     */
    public boolean isInAirShip() {
        return (vehicle != null) && vehicle.isAirShip();
    }

    /**
     * @return
     */
    public AirShip getAirShip() {
        return (AirShip) vehicle;
    }

    public void setVehicle(Vehicle v) {
        if ((v == null) && (vehicle != null)) {
            vehicle.removePassenger(this);
        }

        vehicle = v;
    }

    public boolean isInVehicle() {
        return vehicle != null;
    }

    /**
     * Manage the delete task of a Player (Leave Party, Unsummon pet, Save its inventory in the database, Remove it from the world...).<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>If the Player is in observer mode, set its position to its position before entering in observer mode</li>
     * <li>Set the online Flag to True or False and update the characters table of the database with online status and lastAccess</li>
     * <li>Stop the HP/MP/CP Regeneration task</li>
     * <li>Cancel Crafting, Attack or Cast</li>
     * <li>Remove the Player from the world</li>
     * <li>Stop Party and Unsummon Pet</li>
     * <li>Update database with items in its inventory and remove them from the world</li>
     * <li>Remove all WorldObject from _knownObjects and _knownPlayer of the Creature then cancel Attak or Cast and notify AI</li>
     * <li>Close the connection with the client</li>
     * </ul>
     * <br>
     * Remember this method is not to be used to half-ass disconnect players! This method is dedicated only to erase the player from the world.<br>
     * If you intend to disconnect a player please use {@link Disconnection}
     */
    @Override
    public boolean deleteMe() {
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_LOGOUT, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerLogout(this), this);
        }

        try {
            for (ZoneType zone : ZoneManager.getInstance().getZones(this)) {
                zone.onPlayerLogoutInside(this);
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        // Set the online Flag to True or False and update the characters table of the database with online status and
        // lastAccess (called when login and logout).
        try {
            if (!online) {
                log.error("deleteMe() called on offline character {}", this, new RuntimeException());
            }

            setOnlineStatus(false, true);
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            if (GeneralConfig.ENABLE_BLOCK_CHECKER_EVENT && (blockCheckerArena != -1)) {
                HandysBlockCheckerManager.getInstance().onDisconnect(this);
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            if (krateiArena != null) {
                krateiArena.removePlayer(this);
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            online = false;
            offlinePlay = false;
            abortAttack();
            abortCast();
            stopMove(null);
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        // Remove combat flag.
        try {
            if (inventory.getItemByItemId(9819) != null) {
                final Fort fort = FortManager.getInstance().getFort(this);
                if (fort != null) {
                    FortSiegeManager.getInstance().dropCombatFlag(this, fort.getResidenceId());
                } else {
                    final BodyPart bodyPart = BodyPart.fromItem(inventory.getItemByItemId(9819));
                    inventory.unEquipItemInBodySlot(bodyPart);
                    destroyItem(ItemProcessType.DESTROY, inventory.getItemByItemId(9819), null, true);
                }
            } else if (combatFlagEquipped) {
                TerritoryWarManager.getInstance().dropCombatFlag(this, false, false);
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            PartyMatchWaitingList.getInstance().removePlayer(this);
            if (partyRoom != 0) {
                final PartyMatchRoom room = PartyMatchRoomList.getInstance().getRoom(partyRoom);
                if (room != null) {
                    room.deleteMember(this);
                }
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            if (isFlying()) {
                removeSkill(CommonSkill.WYVERN_BREATH.getSkill());
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        // Make sure player variables are stored.
        getVariables().saveNow();

        // Make sure account variables are stored.
        getAccountVariables().saveNow();

        // Recommendations must be saved before task (timer) is canceled.
        try {
            storeRecommendations(false);
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        // Stop the HP/MP/CP Regeneration task (scheduled tasks).
        try {
            stopAllTimers();
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            setTeleporting(false);
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        // Stop crafting, if in progress.
        try {
            RecipeManager.getInstance().requestMakeItemAbort(this);
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        // Cancel Attak or Cast.
        try {
            setTarget(null);
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        if (isChannelized()) {
            getSkillChannelized().abortChannelization();
        }

        // Stop all toggles.
        getEffectList().stopAllToggles();

        // Remove from world regions zones.
        final ZoneRegion region = ZoneManager.getInstance().getRegion(this);
        if (region != null) {
            region.removeFromZones(this);
        }

        // If a Party is in progress, leave it (and festival party).
        if (isInParty()) {
            try {
                leaveParty();
            } catch (Exception e) {
                log.error("deleteMe()", e);
            }
        }

        stopCubics();

        // Remove the Player from the world.
        try {
            decayMe();
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        if (OlympiadManager.getInstance().isRegistered(this) || (getOlympiadGameId() != -1)) {
            OlympiadManager.getInstance().removeDisconnectedCompetitor(this);
        }

        // If the Player has Pet, unsummon it.
        if (hasSummon()) {
            try {
                summon.setRestoreSummon(true);

                summon.unSummon(this);

                // Dead pet wasn't unsummoned, broadcast npcinfo changes (pet will be without owner name - means owner
                // offline).
                if (hasSummon()) {
                    summon.broadcastNpcInfo(0);
                }
            } catch (Exception e) {
                log.error("deleteMe()", e);
            } // Returns pet to control item.
        }

        if (clan != null) {
            // Set the status for pledge member list to OFFLINE.
            try {
                final ClanMember clanMember = clan.getClanMember(getObjectId());
                if (clanMember != null) {
                    clanMember.setPlayer(null);
                }
            } catch (Exception e) {
                log.error("deleteMe()", e);
            }
        }

        if (getActiveRequester() != null) {
            // Deals with sudden exit in the middle of transaction.
            setActiveRequester(null);
            cancelActiveTrade();
        }

        // If the Player is a GM, remove it from the GM List.
        if (isGM()) {
            try {
                AdminData.getInstance().deleteGm(this);
            } catch (Exception e) {
                log.error("deleteMe()", e);
            }
        }

        try {
            // Check if the Player is in observer mode to set its position to its position.
            // before entering in observer mode.
            if (observerMode) {
                setLocationInvisible(lastLocation);
            }

            if (vehicle != null) {
                vehicle.oustPlayer(this);
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        // Remove player from instance and set spawn location if any.
        try {
            final int instanceId = getInstanceId();
            if ((instanceId != 0) && !GeneralConfig.RESTORE_PLAYER_INSTANCE) {
                final Instance inst = InstanceManager.getInstance().getInstance(instanceId);
                if (inst != null) {
                    inst.removePlayer(getObjectId());
                    final Location loc = inst.getExitLoc();
                    if (loc != null) {
                        final int x = loc.getX() + ClockBridge.random().nextInt(-30, 31);
                        final int y = loc.getY() + ClockBridge.random().nextInt(-30, 31);
                        setXYZInvisible(x, y, loc.getZ());
                        if (hasSummon()) // dead pet
                        {
                            summon.teleToLocation(loc, true);
                            summon.setInstanceId(0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        // Update database with items in its inventory and remove them from the world.
        try {
            inventory.deleteMe();
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        // Update database with items in its warehouse and remove them from the world.
        try {
            getWarehouse().deleteMe();
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            freight.deleteMe();
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            clearRefund();
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        if (isCursedWeaponEquipped()) {
            try {
                CursedWeaponsManager.getInstance()
                        .getCursedWeapon(cursedWeaponEquippedId)
                        .setPlayer(null);
            } catch (Exception e) {
                log.error("deleteMe()", e);
            }
        }

        if (clanId > 0) {
            clan.broadcastToOtherOnlineMembers(new PledgeShowMemberListUpdate(this), this);
            // ClanTable.getInstance().getClan(getClanId()).broadcastToOnlineMembers(new PledgeShowMemberListAdd(this));
        }

        for (Player player : snoopedPlayer) {
            player.removeSnooper(this);
        }

        for (Player player : snoopListener) {
            player.removeSnooped(this);
        }

        try {
            notifyFriends();
            blockList.playerLogout();
        } catch (Exception e) {
            log.warn("Exception on deleteMe() notifyFriends: {}", e.getMessage(), e);
        }

        PlayerAutoSaveTaskManager.getInstance().remove(this);

        return super.deleteMe();
    }

    // startFishing() was stripped of any pre-fishing related checks, namely the fishing zone check.
    // Also worthy of note is the fact the code to find the hook landing position was also striped.
    // The stripped code was moved into fishing.java.
    // In my opinion it makes more sense for it to be there since all other skill related checks were also there.
    // Last but not least, moving the zone check there, fixed a bug where baits would always be consumed no matter if
    // fishing actualy took place.
    // startFishing() now takes up 3 arguments, wich are acurately described as being the hook landing coordinates.
    public void startFishing(int x, int y, int z) {
        stopMove(null);
        setImmobilized(true);
        fishing = true;
        fishX = x;
        fishY = y;
        fishZ = z;

        // broadcastUserInfo();
        // Starts fishing
        final int lvl = getRandomFishLvl();
        final int grade = getRandomFishGrade();
        final int group = getRandomFishGroup(grade);
        final List<Fish> fish = FishData.getInstance().getFish(lvl, group, grade);
        if ((fish == null) || fish.isEmpty()) {
            sendMessage("Error - Fish are not defined");
            endFishing(false);
            return;
        }

        // Use a copy constructor else the fish data may be over-written below
        this.fish = fish.get(ClockBridge.random().nextInt(fish.size())).clone();
        fish.clear();
        sendPacket(SystemMessageId.YOU_CAST_YOUR_LINE_AND_START_TO_FISH);
        // Night lures only catch at night, ordinary lures only during the day.
        if (GameTimeTaskManager.getInstance().isNight() != lure.isNightLure()) {
            this.fish.setFishGroup(-1);
        }

        // sendMessage("Hook x,y: " + _x + "," + _y + " - Water Z, Player Z:" + _z + ", " + getZ()); //debug line,
        // uncoment to show coordinates used in fishing.
        broadcastPacket(new ExFishingStart(this, this.fish.getFishGroup(), x, y, z, lure.isNightLure()));
        sendPacket(new PlaySound(1, "SF_P_01", 0, 0, 0, 0, 0));
        startLookingForFishTask();
    }

    public void stopLookingForFishTask() {
        if (taskForFish == null) {
            return;
        }

        taskForFish.cancel(false);
        taskForFish = null;
    }

    public void startLookingForFishTask() {
        if (!isDead() && (taskForFish == null)) {
            int checkDelay = 0;
            boolean isNoob = false;
            boolean isUpperGrade = false;
            if (lure != null) {
                final int lureid = lure.getId();
                isNoob = fish.getFishGrade() == 0;
                isUpperGrade = fish.getFishGrade() == 2;
                if ((lureid == 6519)
                        || (lureid == 6522)
                        || (lureid == 6525)
                        || (lureid == 8505)
                        || (lureid == 8508)
                        || (lureid == 8511)) {
                    checkDelay = fish.getGutsCheckTime() * 133;
                } else if ((lureid == 6520)
                        || (lureid == 6523)
                        || (lureid == 6526)
                        || ((lureid >= 8505) && (lureid <= 8513))
                        || ((lureid >= 7610) && (lureid <= 7613))
                        || ((lureid >= 7807) && (lureid <= 7809))
                        || ((lureid >= 8484) && (lureid <= 8486))) {
                    checkDelay = fish.getGutsCheckTime() * 100;
                } else if ((lureid == 6521)
                        || (lureid == 6524)
                        || (lureid == 6527)
                        || (lureid == 8507)
                        || (lureid == 8510)
                        || (lureid == 8513)) {
                    checkDelay = fish.getGutsCheckTime() * 66;
                }
            }

            taskForFish = ThreadPool.scheduleAtFixedRate(
                    new LookingForFishTask(
                            this,
                            fish.getStartCombatTime(),
                            fish.getFishGuts(),
                            fish.getFishGroup(),
                            isNoob,
                            isUpperGrade),
                    10000,
                    checkDelay);
        }
    }

    private int getRandomFishGrade() {
        switch (lure.getId()) {
            case 7807: // green for beginners
            case 7808: // purple for beginners
            case 7809: // yellow for beginners
            case 8486: // prize-winning for beginners
            {
                return 0;
            }
            case 8485: // prize-winning luminous
            case 8506: // green luminous
            case 8509: // purple luminous
            case 8512: // yellow luminous
            {
                return 2;
            }
            default: {
                return 1;
            }
        }
    }

    private int getRandomFishGroup(int group) {
        final int check = ClockBridge.random().nextInt(100);
        int type = 1;
        switch (group) {
            case 0: // fish for novices
            {
                switch (lure.getId()) {
                    case 7807: // green lure, preferred by fast-moving (nimble) fish (type 5)
                    {
                        if (check <= 54) {
                            type = 5;
                        } else if (check <= 77) {
                            type = 4;
                        } else {
                            type = 6;
                        }
                        break;
                    }
                    case 7808: // purple lure, preferred by fat fish (type 4)
                    {
                        if (check <= 54) {
                            type = 4;
                        } else if (check <= 77) {
                            type = 6;
                        } else {
                            type = 5;
                        }
                        break;
                    }
                    case 7809: // yellow lure, preferred by ugly fish (type 6)
                    {
                        if (check <= 54) {
                            type = 6;
                        } else if (check <= 77) {
                            type = 5;
                        } else {
                            type = 4;
                        }
                        break;
                    }
                    case 8486: // prize-winning fishing lure for beginners
                    {
                        if (check <= 33) {
                            type = 4;
                        } else if (check <= 66) {
                            type = 5;
                        } else {
                            type = 6;
                        }
                        break;
                    }
                }
                break;
            }
            case 1: // normal fish
            {
                switch (lure.getId()) {
                    case 7610:
                    case 7611:
                    case 7612:
                    case 7613: {
                        type = 3;
                        break;
                    }
                    case 6519: // all theese lures (green) are prefered by fast-moving (nimble) fish (type 1)
                    case 8505:
                    case 6520:
                    case 6521:
                    case 8507: {
                        if (check <= 54) {
                            type = 1;
                        } else if (check <= 74) {
                            type = 0;
                        } else if (check <= 94) {
                            type = 2;
                        } else {
                            type = 3;
                        }
                        break;
                    }
                    case 6522: // all theese lures (purple) are prefered by fat fish (type 0)
                    case 8508:
                    case 6523:
                    case 6524:
                    case 8510: {
                        if (check <= 54) {
                            type = 0;
                        } else if (check <= 74) {
                            type = 1;
                        } else if (check <= 94) {
                            type = 2;
                        } else {
                            type = 3;
                        }
                        break;
                    }
                    case 6525: // all theese lures (yellow) are prefered by ugly fish (type 2)
                    case 8511:
                    case 6526:
                    case 6527:
                    case 8513: {
                        if (check <= 55) {
                            type = 2;
                        } else if (check <= 74) {
                            type = 1;
                        } else if (check <= 94) {
                            type = 0;
                        } else {
                            type = 3;
                        }
                        break;
                    }
                    case 8484: // prize-winning fishing lure
                    {
                        if (check <= 33) {
                            type = 0;
                        } else if (check <= 66) {
                            type = 1;
                        } else {
                            type = 2;
                        }
                        break;
                    }
                }
                break;
            }
            case 2: // upper grade fish, luminous lure
            {
                switch (lure.getId()) {
                    case 8506: // green lure, preferred by fast-moving (nimble) fish (type 8)
                    {
                        if (check <= 54) {
                            type = 8;
                        } else if (check <= 77) {
                            type = 7;
                        } else {
                            type = 9;
                        }
                        break;
                    }
                    case 8509: // purple lure, preferred by fat fish (type 7)
                    {
                        if (check <= 54) {
                            type = 7;
                        } else if (check <= 77) {
                            type = 9;
                        } else {
                            type = 8;
                        }
                        break;
                    }
                    case 8512: // yellow lure, preferred by ugly fish (type 9)
                    {
                        if (check <= 54) {
                            type = 9;
                        } else if (check <= 77) {
                            type = 8;
                        } else {
                            type = 7;
                        }
                        break;
                    }
                    case 8485: // prize-winning fishing lure
                    {
                        if (check <= 33) {
                            type = 7;
                        } else if (check <= 66) {
                            type = 8;
                        } else {
                            type = 9;
                        }
                        break;
                    }
                }
            }
        }

        return type;
    }

    private int getRandomFishLvl() {
        int skillLevel = getSkillLevel(1315 /* Fishing Expertise */);
        final BuffInfo info = getEffectList().getBuffInfoBySkillId(2274 /* Fisherman's Potion */);
        if (info != null) {
            skillLevel = (int) info.getSkill().getPower();
        }

        if (skillLevel <= 0) {
            return 1;
        }

        int randomlvl;
        final int check = ClockBridge.random().nextInt(100);
        if (check <= 50) {
            randomlvl = skillLevel;
        } else if (check <= 85) {
            randomlvl = skillLevel - 1;
            if (randomlvl <= 0) {
                randomlvl = 1;
            }
        } else {
            randomlvl = skillLevel + 1;
            if (randomlvl > 27) {
                randomlvl = 27;
            }
        }

        return randomlvl;
    }

    public void startFishCombat(boolean isNoob, boolean isUpperGrade) {
        // Every night lure fights like a night catch, the fish turns deceptive and reverses reeling and pumping.
        fishCombat = new Fishing(this, fish, isNoob, isUpperGrade || lure.isNightLure(), lure.getId());
    }

    public void endFishing(boolean win) {
        fishing = false;
        fishX = 0;
        fishY = 0;
        fishZ = 0;

        // broadcastUserInfo();
        if (fishCombat == null) {
            sendPacket(SystemMessageId.THE_BAIT_HAS_BEEN_LOST_BECAUSE_THE_FISH_GOT_AWAY);
        }

        fishCombat = null;
        lure = null;

        // Ends fishing
        broadcastPacket(new ExFishingEnd(win, this));
        sendPacket(SystemMessageId.YOU_REEL_YOUR_LINE_IN_AND_STOP_FISHING);
        setImmobilized(false);
        stopLookingForFishTask();
    }

    public int getInventoryLimit() {
        int ivlim;
        if (isGM()) {
            ivlim = PlayerConfig.INVENTORY_MAXIMUM_GM;
        } else {
            ivlim = getRace() == Race.DWARF
                    ? PlayerConfig.INVENTORY_MAXIMUM_DWARF
                    : PlayerConfig.INVENTORY_MAXIMUM_NO_DWARF;
        }

        return ivlim += (int) getStat().calcStat(Stat.INV_LIM, 0, null, null);
    }

    public int getWareHouseLimit() {
        return (getRace() == Race.DWARF ? PlayerConfig.WAREHOUSE_SLOTS_DWARF : PlayerConfig.WAREHOUSE_SLOTS_NO_DWARF)
                + (int) getStat().calcStat(Stat.WH_LIM, 0, null, null);
    }

    public int getPrivateSellStoreLimit() {
        return (getRace() == Race.DWARF
                        ? PlayerConfig.MAX_PVTSTORESELL_SLOTS_DWARF
                        : PlayerConfig.MAX_PVTSTORESELL_SLOTS_OTHER)
                + (int) getStat().calcStat(Stat.P_SELL_LIM, 0, null, null);
    }

    public int getPrivateBuyStoreLimit() {
        return (getRace() == Race.DWARF
                        ? PlayerConfig.MAX_PVTSTOREBUY_SLOTS_DWARF
                        : PlayerConfig.MAX_PVTSTOREBUY_SLOTS_OTHER)
                + (int) getStat().calcStat(Stat.P_BUY_LIM, 0, null, null);
    }

    public int getDwarfRecipeLimit() {
        return PlayerConfig.DWARF_RECIPE_LIMIT + (int) getStat().calcStat(Stat.REC_D_LIM, 0, null, null);
    }

    public int getCommonRecipeLimit() {
        return PlayerConfig.COMMON_RECIPE_LIMIT + (int) getStat().calcStat(Stat.REC_C_LIM, 0, null, null);
    }

    /**
     * Create a new SkillUseHolder object and set the player currentSkill.
     * @param currentSkill
     * @param ctrlPressed
     * @param shiftPressed
     */
    public void setCurrentSkill(Skill currentSkill, boolean ctrlPressed, boolean shiftPressed) {
        if (currentSkill == null) {
            this.currentSkill = null;
            return;
        }

        this.currentSkill = new SkillUseHolder(currentSkill, ctrlPressed, shiftPressed);
    }

    /**
     * Create a new SkillUseHolder object and set the player currentPetSkill.
     * @param currentSkill
     * @param ctrlPressed
     * @param shiftPressed
     */
    public void setCurrentPetSkill(Skill currentSkill, boolean ctrlPressed, boolean shiftPressed) {
        if (currentSkill == null) {
            currentPetSkill = null;
            return;
        }

        currentPetSkill = new SkillUseHolder(currentSkill, ctrlPressed, shiftPressed);
    }

    /**
     * Create a new SkillUseHolder object and queue it in the player queuedSkill.
     * @param queuedSkill
     * @param ctrlPressed
     * @param shiftPressed
     */
    public void setQueuedSkill(Skill queuedSkill, boolean ctrlPressed, boolean shiftPressed) {
        if (queuedSkill == null) {
            this.queuedSkill = null;
            return;
        }

        this.queuedSkill = new SkillUseHolder(queuedSkill, ctrlPressed, shiftPressed);
    }

    /**
     * @return {@code true} if player is jailed, {@code false} otherwise.
     */
    public boolean isJailed() {
        return PunishmentManager.getInstance()
                        .hasPunishment(getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.JAIL) //
                || PunishmentManager.getInstance()
                        .hasPunishment(getAccountName(), PunishmentAffect.ACCOUNT, PunishmentType.JAIL) //
                || PunishmentManager.getInstance()
                        .hasPunishment(getIPAddress(), PunishmentAffect.IP, PunishmentType.JAIL) //
                || ((client != null)
                        && (client.getHardwareInfo() != null)
                        && PunishmentManager.getInstance()
                                .hasPunishment(
                                        client.getHardwareInfo().getMacAddress(),
                                        PunishmentAffect.HWID,
                                        PunishmentType.JAIL));
    }

    /**
     * @return {@code true} if player is chat banned, {@code false} otherwise.
     */
    public boolean isChatBanned() {
        return PunishmentManager.getInstance()
                        .hasPunishment(getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.CHAT_BAN) //
                || PunishmentManager.getInstance()
                        .hasPunishment(getAccountName(), PunishmentAffect.ACCOUNT, PunishmentType.CHAT_BAN) //
                || PunishmentManager.getInstance()
                        .hasPunishment(getIPAddress(), PunishmentAffect.IP, PunishmentType.CHAT_BAN) //
                || ((client != null)
                        && (client.getHardwareInfo() != null)
                        && PunishmentManager.getInstance()
                                .hasPunishment(
                                        client.getHardwareInfo().getMacAddress(),
                                        PunishmentAffect.HWID,
                                        PunishmentType.CHAT_BAN));
    }

    public void startFameTask(long delay, int fameFixRate) {
        if (!PlayerConfig.FAME_SYSTEM_ENABLED) {
            return;
        }

        if ((getLevel() < 40) || (getPlayerClass().level() < 2)) {
            return;
        }

        if (fameTask == null) {
            fameTask = ThreadPool.scheduleAtFixedRate(new FameTask(this, fameFixRate), delay, delay);
        }
    }

    public void stopFameTask() {
        if (fameTask == null) {
            return;
        }

        fameTask.cancel(false);
        fameTask = null;
    }

    public void startVitalityTask() {
        if (PlayerConfig.ENABLE_VITALITY && (vitalityTask == null)) {
            vitalityTask = ThreadPool.scheduleAtFixedRate(new VitalityTask(this), 1000, 60000);
        }
    }

    public void stopVitalityTask() {
        if (vitalityTask != null) {
            vitalityTask.cancel(false);
            vitalityTask = null;
        }
    }

    public boolean isCursedWeaponEquipped() {
        return cursedWeaponEquippedId != 0;
    }

    /**
     * Increase Souls
     * @param count
     */
    public void increaseSouls(int count) {
        chargedSouls += count;
        final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_SOUL_COUNT_HAS_INCREASED_BY_S1_IT_IS_NOW_AT_S2);
        sm.addInt(count);
        sm.addInt(chargedSouls);
        sendPacket(sm);
        restartSoulTask();
        sendPacket(new EtcStatusUpdate(this));
    }

    /**
     * Decreases existing Souls.
     * @param count
     * @return
     */
    public boolean decreaseSouls(int count) {
        chargedSouls -= count;
        if (chargedSouls < 0) {
            chargedSouls = 0;
        }

        if (chargedSouls == 0) {
            stopSoulTask();
        } else {
            restartSoulTask();
        }

        sendPacket(new EtcStatusUpdate(this));
        return true;
    }

    /**
     * Clear out all Souls from this Player
     */
    public void clearSouls() {
        chargedSouls = 0;
        stopSoulTask();
        sendPacket(new EtcStatusUpdate(this));
    }

    /**
     * Starts/Restarts the SoulTask to Clear Souls after 10 Mins.
     */
    private void restartSoulTask() {
        if (soulTask != null) {
            soulTask.cancel(false);
            soulTask = null;
        }

        soulTask = ThreadPool.schedule(new ResetSoulsTask(this), 600000);
    }

    /**
     * Stops the Clearing Task.
     */
    public void stopSoulTask() {
        if (soulTask == null) {
            return;
        }

        soulTask.cancel(false);
        soulTask = null;
    }

    public void calculateDeathPenaltyBuffLevel(Creature killer) {
        if (killer == null) {
            log.warn("{} called calculateDeathPenaltyBuffLevel with killer null!", this);
            return;
        }

        if (isResurrectSpecialAffected()
                || isLucky()
                || isOnEvent()
                || isInsideZone(ZoneId.PVP)
                || isInsideZone(ZoneId.SIEGE)
                || isGM()) {
            return;
        }

        double percent = 1.0;
        if (killer.isRaid()) {
            percent *= calcStat(Stat.REDUCE_DEATH_PENALTY_BY_RAID, 1);
        } else if (killer.isMonster()) {
            percent *= calcStat(Stat.REDUCE_DEATH_PENALTY_BY_MOB, 1);
        } else if (killer.isPlayable()) {
            percent *= calcStat(Stat.REDUCE_DEATH_PENALTY_BY_PVP, 1);
        }

        if ((ClockBridge.random().nextInt(1, 101) <= ((PlayerConfig.DEATH_PENALTY_CHANCE) * percent))
                && (!killer.isPlayable() || (getKarma() > 0))) {
            increaseDeathPenaltyBuffLevel();
        }
    }

    public void increaseDeathPenaltyBuffLevel() {
        if (deathPenaltyBuffLevel >= 15) {
            return;
        }

        if (deathPenaltyBuffLevel != 0) {
            final Skill skill = SkillData.getInstance().getSkill(5076, getDeathPenaltyBuffLevel());
            if (skill != null) {
                removeSkill(skill, true);
            }
        }

        deathPenaltyBuffLevel++;
        addSkill(SkillData.getInstance().getSkill(5076, getDeathPenaltyBuffLevel()), false);
        sendPacket(new EtcStatusUpdate(this));
        final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_DEATH_PENALTY_IS_NOW_LEVEL_S1);
        sm.addInt(deathPenaltyBuffLevel);
        sendPacket(sm);
    }

    public void reduceDeathPenaltyBuffLevel() {
        if (deathPenaltyBuffLevel <= 0) {
            return;
        }

        final Skill skill = SkillData.getInstance().getSkill(5076, getDeathPenaltyBuffLevel());
        if (skill != null) {
            removeSkill(skill, true);
        }

        deathPenaltyBuffLevel--;

        if (deathPenaltyBuffLevel > 0) {
            addSkill(SkillData.getInstance().getSkill(5076, getDeathPenaltyBuffLevel()), false);
            sendPacket(new EtcStatusUpdate(this));
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_DEATH_PENALTY_IS_NOW_LEVEL_S1);
            sm.addInt(deathPenaltyBuffLevel);
            sendPacket(sm);
        } else {
            sendPacket(new EtcStatusUpdate(this));
            sendPacket(SystemMessageId.YOUR_DEATH_PENALTY_HAS_BEEN_LIFTED);
        }
    }

    public void restoreDeathPenaltyBuffLevel() {
        if (deathPenaltyBuffLevel > 0) {
            addSkill(SkillData.getInstance().getSkill(5076, getDeathPenaltyBuffLevel()), false);
        }
    }

    @Override
    public void sendDamageMessage(Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss) {
        // Check if hit is missed.
        if (miss) {
            if (target.isPlayer()) {
                final SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_EVADED_C2_S_ATTACK);
                sm.addPcName(target.asPlayer());
                sm.addString(getName());
                target.sendPacket(sm);
            }

            final SystemMessage sm = new SystemMessage(SystemMessageId.C1_S_ATTACK_WENT_ASTRAY);
            sm.addPcName(this);
            sendPacket(sm);
            return;
        }

        // Check if hit is critical.
        if (pcrit) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.C1_LANDED_A_CRITICAL_HIT);
            sm.addPcName(this);
            sendPacket(sm);
        }

        if (mcrit) {
            sendPacket(SystemMessageId.MAGIC_CRITICAL_HIT);
        }

        if (isInOlympiadMode()
                && target.isPlayer()
                && target.asPlayer().isInOlympiadMode()
                && (target.asPlayer().getOlympiadGameId() == getOlympiadGameId())) {
            OlympiadGameManager.getInstance().notifyCompetitorDamage(this, damage);
        }

        if (target.isInvul() && !target.isNpc()) {
            sendPacket(SystemMessageId.THE_ATTACK_HAS_BEEN_BLOCKED);
        } else if (target.isDoor() || (target instanceof ControlTower)) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HIT_FOR_S1_DAMAGE);
            sm.addInt(damage);
            sendPacket(sm);
        } else if (this != target) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_DONE_S3_POINTS_OF_DAMAGE_TO_C2);
            sm.addPcName(this);

            // Localisation related.
            String targetName = target.getName();
            if (MultilingualSupportConfig.MULTILANG_ENABLE && target.isNpc()) {
                final String[] localisation =
                        NpcNameLocalisationData.getInstance().getLocalisation(lang, target.getId());
                if (localisation != null) {
                    targetName = localisation[0];
                }
            }

            sm.addString(targetName);
            sm.addInt(damage);
            sendPacket(sm);
        }
    }

    public int getVitalityPoints() {
        return getStat().getVitalityPoints();
    }

    /**
     * @return Vitality Level
     */
    public int getVitalityLevel() {
        return getStat().getVitalityLevel();
    }

    public void setVitalityPoints(int points, boolean quiet) {
        getStat().setVitalityPoints(points, quiet);
    }

    public void updateVitalityPoints(float points, boolean useRates, boolean quiet) {
        getStat().updateVitalityPoints(points, useRates, quiet);
    }

    public void checkItemRestriction() {
        for (int i = 0; i < Inventory.PAPERDOLL_TOTALSLOTS; i++) {
            final Item equippedItem = inventory.getPaperdollItem(i);
            if ((equippedItem != null) && !equippedItem.getTemplate().checkCondition(this, this, false)) {
                inventory.unEquipItemInSlot(i);

                final InventoryUpdate iu = new InventoryUpdate();
                iu.addModifiedItem(equippedItem);
                sendInventoryUpdate(iu);

                SystemMessage sm = null;
                if (equippedItem.getTemplate().getBodyPart() == BodyPart.BACK) {
                    sendPacket(
                            SystemMessageId
                                    .YOUR_CLOAK_HAS_BEEN_UNEQUIPPED_BECAUSE_YOUR_ARMOR_SET_IS_NO_LONGER_COMPLETE);
                    return;
                }

                if (equippedItem.isEnchanted()) {
                    sm = new SystemMessage(SystemMessageId.THE_EQUIPMENT_S1_S2_HAS_BEEN_REMOVED);
                    sm.addInt(equippedItem.getEnchantLevel());
                } else {
                    sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_DISARMED);
                }

                sm.addItemName(equippedItem);
                sendPacket(sm);
            }
        }
    }

    public void addTransformSkill(Skill sk) {
        transformSkills.put(sk.getId(), sk);
        if (sk.isPassive()) {
            addSkill(sk, false);
        }
    }

    public Skill getTransformSkill(int id) {
        return transformSkills.get(id);
    }

    public boolean hasTransformSkill(int id) {
        return transformSkills.containsKey(id);
    }

    public void removeAllTransformSkills() {
        transformSkills.clear();
    }

    protected void startFeed(int npcId) {
        canFeed = npcId > 0;
        if (!isMounted()) {
            return;
        }

        if (hasSummon()) {
            setCurrentFeed(summon.asPet().getCurrentFed());
            controlItemId = summon.getControlObjectId();
            sendPacket(new SetupGauge(
                    getObjectId(),
                    3,
                    (currentFeed * 10000) / getFeedConsume(),
                    (getMaxFeed() * 10000) / getFeedConsume()));
            if (!isDead()) {
                mountFeedTask = ThreadPool.scheduleAtFixedRate(new PetFeedTask(this), 10000, 10000);
            }
        } else if (canFeed) {
            setCurrentFeed(getMaxFeed());
            sendPacket(new SetupGauge(
                    getObjectId(),
                    3,
                    (currentFeed * 10000) / getFeedConsume(),
                    (getMaxFeed() * 10000) / getFeedConsume()));
            if (!isDead()) {
                mountFeedTask = ThreadPool.scheduleAtFixedRate(new PetFeedTask(this), 10000, 10000);
            }
        }
    }

    public void stopFeed() {
        if (mountFeedTask == null) {
            return;
        }

        mountFeedTask.cancel(false);
        mountFeedTask = null;
    }

    private PetLevelData getPetLevelData(int npcId) {
        if (leveldata == null) {
            leveldata = PetDataTable.getInstance().getPetData(npcId).getPetLevelData(getMountLevel());
        }

        return leveldata;
    }

    public int getFeedConsume() {
        // If pet is attacking.
        if (isAttackingNow()) {
            return getPetLevelData(mountNpcId).getPetFeedBattle();
        }

        return getPetLevelData(mountNpcId).getPetFeedNormal();
    }

    public void setCurrentFeed(int num) {
        final boolean lastHungryState = isHungry();
        currentFeed = num > getMaxFeed() ? getMaxFeed() : num;
        sendPacket(new SetupGauge(
                getObjectId(), 3, (currentFeed * 10000) / getFeedConsume(), (getMaxFeed() * 10000) / getFeedConsume()));

        // Broadcast move speed change when strider becomes hungry / full.
        if (lastHungryState != isHungry()) {
            broadcastUserInfo();
        }
    }

    private int getMaxFeed() {
        return getPetLevelData(mountNpcId).getPetMaxFeed();
    }

    public boolean isHungry() {
        return canFeed
                && (getCurrentFeed()
                        < ((PetDataTable.getInstance()
                                                .getPetData(getMountNpcId())
                                                .getHungryLimit()
                                        / 100f)
                                * getPetLevelData(getMountNpcId()).getPetMaxFeed()));
    }

    public void enteredNoLanding(int delay) {
        dismountTask = ThreadPool.schedule(new DismountTask(this), delay * 1000);
    }

    public void exitedNoLanding() {
        if (dismountTask == null) {
            return;
        }

        dismountTask.cancel(true);
        dismountTask = null;
    }

    public void storePetFood(int petId) {
        if ((controlItemId == 0) || (petId == 0)) {
            return;
        }

        final String req = "UPDATE pets SET fed=? WHERE item_obj_id = ?";
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(req)) {
            ps.setInt(1, currentFeed);
            ps.setInt(2, controlItemId);
            ps.executeUpdate();
            controlItemId = 0;
        } catch (Exception e) {
            log.error("Failed to store Pet [NpcId: {}] data", petId, e);
        }
    }

    public boolean isFlyingMounted() {
        return isTransformed() && transformation.isFlying();
    }

    /**
     * Returns the Number of Charges this Player got.
     * @return
     */
    public int getCharges() {
        return charges.get();
    }

    public void increaseCharges(int count, int max) {
        if (charges.get() >= max) {
            sendPacket(SystemMessageId.YOUR_FORCE_HAS_REACHED_MAXIMUM_CAPACITY);
            return;
        }

        // Charge clear task should be reset every time a charge is increased.
        restartChargeTask();

        if (charges.addAndGet(count) >= max) {
            charges.set(max);
            sendPacket(SystemMessageId.YOUR_FORCE_HAS_REACHED_MAXIMUM_CAPACITY);
        } else {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_FORCE_HAS_INCREASED_TO_LEVEL_S1);
            sm.addInt(charges.get());
            sendPacket(sm);
        }

        sendPacket(new EtcStatusUpdate(this));
    }

    public boolean decreaseCharges(int count) {
        if (charges.get() < count) {
            return false;
        }

        // Charge clear task should be reset every time a charge is decreased and stopped when charges become 0.
        if (charges.addAndGet(-count) == 0) {
            stopChargeTask();
        } else {
            restartChargeTask();
        }

        sendPacket(new EtcStatusUpdate(this));
        return true;
    }

    public void clearCharges() {
        charges.set(0);
        sendPacket(new EtcStatusUpdate(this));
    }

    /**
     * Starts/Restarts the ChargeTask to Clear Charges after 10 Mins.
     */
    private void restartChargeTask() {
        if (chargeTask != null) {
            synchronized (this) {
                chargeTask.cancel(false);
            }
        }

        chargeTask = ThreadPool.schedule(new ResetChargesTask(this), 600000);
    }

    /**
     * Stops the Charges Clearing Task.
     */
    public void stopChargeTask() {
        if (chargeTask == null) {
            return;
        }

        chargeTask.cancel(false);
        chargeTask = null;
    }

    public void teleportBookmarkModify(int id, int icon, String tag, String name) {
        final TeleportBookmark bookmark = tpbookmarks.get(id);
        if (bookmark != null) {
            bookmark.setIcon(icon);
            bookmark.setTag(tag);
            bookmark.setName(name);

            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement ps = con.prepareStatement(UPDATE_TP_BOOKMARK)) {
                ps.setInt(1, icon);
                ps.setString(2, tag);
                ps.setString(3, name);
                ps.setInt(4, getObjectId());
                ps.setInt(5, id);
                ps.execute();
            } catch (Exception e) {
                log.warn("Could not update character teleport bookmark data: {}", e.getMessage(), e);
            }
        }

        sendPacket(new ExGetBookMarkInfoPacket(this));
    }

    public void teleportBookmarkDelete(int id) {
        if (tpbookmarks.remove(id) == null) {
            return;
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(DELETE_TP_BOOKMARK)) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, id);
            ps.execute();
        } catch (Exception e) {
            log.warn("Could not delete character teleport bookmark data: {}", e.getMessage(), e);
        }

        sendPacket(new ExGetBookMarkInfoPacket(this));
    }

    public void teleportBookmarkGo(int id) {
        if (!teleportBookmarkCondition(0)) {
            return;
        }

        if (inventory.getInventoryItemCount(13016, 0) == 0) {
            sendPacket(SystemMessageId.YOU_CANNOT_TELEPORT_BECAUSE_YOU_DO_NOT_HAVE_A_TELEPORT_ITEM);
            return;
        }

        final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
        sm.addItemName(13016);
        sendPacket(sm);

        final TeleportBookmark bookmark = tpbookmarks.get(id);
        if (bookmark != null) {
            destroyItem(ItemProcessType.NONE, inventory.getItemByItemId(13016).getObjectId(), 1, null, false);
            teleToLocation(bookmark, false);
        }

        sendPacket(new ExGetBookMarkInfoPacket(this));
    }

    public boolean teleportBookmarkCondition(int type) {
        if (isInCombat()) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_DURING_A_BATTLE);
            return false;
        } else if (inSiege || (siegeState != 0)) {
            sendPacket(
                    SystemMessageId
                            .YOU_CANNOT_USE_MY_TELEPORTS_WHILE_PARTICIPATING_A_LARGE_SCALE_BATTLE_SUCH_AS_A_CASTLE_SIEGE_FORTRESS_SIEGE_OR_HIDEOUT_SIEGE);
            return false;
        }

        if (inDuel || startingDuel) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_DURING_A_DUEL);
            return false;
        } else if (isFlying()) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_FLYING);
            return false;
        } else if (inOlympiadMode) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_PARTICIPATING_IN_AN_OLYMPIAD_MATCH);
            return false;
        } else if (isParalyzed()) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_YOU_ARE_IN_A_PETRIFIED_OR_PARALYZED_STATE);
            return false;
        } else if (isDead()) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_YOU_ARE_DEAD);
            return false;
        } else if ((type == 1) && (in7sDungeon || (isInParty() && party.isInDimensionalRift()))) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_TO_REACH_THIS_AREA);
            return false;
        } else if (isInWater()) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_UNDERWATER);
            return false;
        } else if ((type == 1)
                && (isInsideZone(ZoneId.SIEGE)
                        || isInsideZone(ZoneId.CLAN_HALL)
                        || isInsideZone(ZoneId.JAIL)
                        || isInsideZone(ZoneId.CASTLE)
                        || isInsideZone(ZoneId.NO_SUMMON_FRIEND)
                        || isInsideZone(ZoneId.FORT))) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_TO_REACH_THIS_AREA);
            return false;
        } else if (isInsideZone(ZoneId.NO_BOOKMARK) || isInBoat() || isInAirShip()) {
            if (type == 0) {
                sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_IN_THIS_AREA);
            } else if (type == 1) {
                sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_TO_REACH_THIS_AREA);
            }

            return false;
        } else {
            return true;
        }
    }

    public void teleportBookmarkAdd(int x, int y, int z, int icon, String tag, String name) {
        if (!teleportBookmarkCondition(1)) {
            return;
        }

        if (tpbookmarks.size() >= bookmarkSlot) {
            sendPacket(SystemMessageId.YOU_HAVE_NO_SPACE_TO_SAVE_THE_TELEPORT_LOCATION);
            return;
        }

        if (GeneralConfig.BOOKMARK_CONSUME_ITEM_ID > 0) {
            if (inventory.getInventoryItemCount(GeneralConfig.BOOKMARK_CONSUME_ITEM_ID, -1) == 0) {
                if (GeneralConfig.BOOKMARK_CONSUME_ITEM_ID == 20033) {
                    sendPacket(
                            SystemMessageId
                                    .YOU_CANNOT_BOOKMARK_THIS_LOCATION_BECAUSE_YOU_DO_NOT_HAVE_A_MY_TELEPORT_FLAG);
                } else {
                    sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
                }
                return;
            }

            destroyItem(
                    ItemProcessType.NONE,
                    inventory
                            .getItemByItemId(GeneralConfig.BOOKMARK_CONSUME_ITEM_ID)
                            .getObjectId(),
                    1,
                    null,
                    true);
        }

        int id;
        for (id = 1; id <= bookmarkSlot; ++id) {
            if (!tpbookmarks.containsKey(id)) {
                break;
            }
        }

        tpbookmarks.put(id, new TeleportBookmark(id, x, y, z, icon, tag, name));

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(INSERT_TP_BOOKMARK)) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, id);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            ps.setInt(6, icon);
            ps.setString(7, tag);
            ps.setString(8, name);
            ps.execute();
        } catch (Exception e) {
            log.warn("Could not insert character teleport bookmark data: {}", e.getMessage(), e);
        }

        sendPacket(new ExGetBookMarkInfoPacket(this));
    }

    public void restoreTeleportBookmark() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(RESTORE_TP_BOOKMARK)) {
            ps.setInt(1, getObjectId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tpbookmarks.put(
                            rs.getInt("Id"),
                            new TeleportBookmark(
                                    rs.getInt("Id"),
                                    rs.getInt("x"),
                                    rs.getInt("y"),
                                    rs.getInt("z"),
                                    rs.getInt("icon"),
                                    rs.getString("tag"),
                                    rs.getString("name")));
                }
            }
        } catch (Exception e) {
            log.error("Failed restoing character teleport bookmark.", e);
        }
    }

    @Override
    public void sendInfo(Player player) {
        if (isInBoat()) {
            setXYZ(getBoat().getLocation());
            player.sendPacket(new CharInfo(this, isInvisible() && player.isGM()));
            player.sendPacket(new ExBrExtraUserInfo(this));
            player.sendPacket(new GetOnVehicle(getObjectId(), getBoat().getObjectId(), inVehiclePosition));
        } else if (isInAirShip()) {
            setXYZ(getAirShip().getLocation());
            player.sendPacket(new CharInfo(this, isInvisible() && player.isGM()));
            player.sendPacket(new ExBrExtraUserInfo(this));
            player.sendPacket(new ExGetOnAirShip(this, getAirShip()));
        } else {
            player.sendPacket(new CharInfo(this, isInvisible() && player.isGM()));
            player.sendPacket(new ExBrExtraUserInfo(this));
        }

        final int relation = getRelation(player);
        final boolean isAutoAttackable = isAutoAttackable(player);
        final RelationCache cache = getKnownRelations().get(player.getObjectId());
        if ((cache == null) || (cache.relation() != relation) || (cache.autoAttackable() != isAutoAttackable)) {
            player.sendPacket(new RelationChanged(this, relation, isAutoAttackable));
            if (hasSummon()) {
                player.sendPacket(new RelationChanged(summon, relation, isAutoAttackable));
            }

            getKnownRelations().put(player.getObjectId(), new RelationCache(relation, isAutoAttackable));
        }

        switch (privateStoreType) {
            case SELL: {
                player.sendPacket(new PrivateStoreMsgSell(this));
                break;
            }
            case PACKAGE_SELL: {
                player.sendPacket(new ExPrivateStoreSetWholeMsg(this));
                break;
            }
            case BUY: {
                player.sendPacket(new PrivateStoreMsgBuy(this));
                break;
            }
            case MANUFACTURE: {
                player.sendPacket(new RecipeShopMsg(this));
                break;
            }
        }

        // Required for showing mount transformations to players that just entered the game.
        if (isTransformed()) {
            player.sendPacket(new CharInfo(this, isInvisible() && player.isGM()));
        }
    }

    public void playMovie(MovieHolder holder) {
        if (movieHolder != null) {
            return;
        }

        abortAttack();

        // abortCast(); Confirmed in retail, playing a movie does not abort cast.
        stopMove(null);
        setMovieHolder(holder);
        if (!isTeleporting()) {
            sendPacket(new ExStartScenePlayer(holder.getMovie()));
        }
    }

    public void stopMovie() {
        setMovieHolder(null);
    }

    public boolean isAllowedToEnchantSkills() {
        if (changingClass) {
            return false;
        }

        if (isTransformed() || isInStance()) {
            return false;
        }

        if (AttackStanceTaskManager.getInstance().hasAttackStanceTask(this)) {
            return false;
        }

        if (isCastingNow() || isCastingSimultaneouslyNow()) {
            return false;
        }

        if (isInBoat() || isInAirShip()) {
            return false;
        }

        return true;
    }

    /**
     * @return number of days to char birthday.
     */
    public int checkBirthDay() {
        final Calendar now = ClockBridge.calendar();

        // "Characters with a February 29 creation date will receive a gift on February 28."
        if ((createDate.get(Calendar.DAY_OF_MONTH) == 29) && (createDate.get(Calendar.MONTH) == 1)) {
            createDate.add(Calendar.HOUR_OF_DAY, -24);
        }

        if ((now.get(Calendar.MONTH) == createDate.get(Calendar.MONTH))
                && (now.get(Calendar.DAY_OF_MONTH) == createDate.get(Calendar.DAY_OF_MONTH))
                && (now.get(Calendar.YEAR) != createDate.get(Calendar.YEAR))) {
            return 0;
        }

        int i;
        for (i = 1; i < 6; i++) {
            now.add(Calendar.HOUR_OF_DAY, 24);
            if ((now.get(Calendar.MONTH) == createDate.get(Calendar.MONTH))
                    && (now.get(Calendar.DAY_OF_MONTH) == createDate.get(Calendar.DAY_OF_MONTH))
                    && (now.get(Calendar.YEAR) != createDate.get(Calendar.YEAR))) {
                return i;
            }
        }

        return -1;
    }

    /** Friend list. */
    @Getter
    private final Collection<Integer> friendList = ConcurrentHashMap.newKeySet();

    public void restoreFriendList() {
        friendList.clear();

        final String sqlQuery = "SELECT friendId FROM character_friends WHERE charId=? AND relation=0";
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(sqlQuery)) {
            ps.setInt(1, getObjectId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final int friendId = rs.getInt("friendId");
                    if (friendId == getObjectId()) {
                        continue;
                    }

                    friendList.add(friendId);
                }
            }
        } catch (Exception e) {
            log.warn("Error found in {}'s FriendList: {}", getName(), e.getMessage(), e);
        }
    }

    private void notifyFriends() {
        final FriendStatusPacket pkt = new FriendStatusPacket(getObjectId());
        for (int id : friendList) {
            final Player friend = World.getPlayer(id);
            if (friend != null) {
                friend.sendPacket(pkt);
            }
        }
    }

    /**
     * While at silenceMode, checks if this player blocks PMs for this user
     * @param playerObjId the player object Id
     * @return {@code true} if the given Id is not excluded and this player is in silence mode, {@code false} otherwise
     */
    public boolean isSilenceMode(int playerObjId) {
        if (PlayerConfig.SILENCE_MODE_EXCLUDE && silenceMode && (silenceModeExcluded != null)) {
            return !silenceModeExcluded.contains(playerObjId);
        }

        return silenceMode;
    }

    /**
     * Set the silence mode.
     * @param mode the value
     */
    public void setSilenceMode(boolean mode) {
        silenceMode = mode;
        if (silenceModeExcluded != null) {
            silenceModeExcluded.clear(); // Clear the excluded list on each setSilenceMode.
        }

        sendPacket(new EtcStatusUpdate(this));
    }

    /**
     * Add a player to the "excluded silence mode" list.
     * @param playerObjId the player's object Id
     */
    public void addSilenceModeExcluded(int playerObjId) {
        if (silenceModeExcluded == null) {
            silenceModeExcluded = new ArrayList<>(1);
        }

        silenceModeExcluded.add(playerObjId);
    }

    private void storeRecipeShopList() {
        if (hasManufactureShop()) {
            try (Connection con = DatabaseFactory.getConnection()) {
                try (PreparedStatement st = con.prepareStatement(DELETE_CHAR_RECIPE_SHOP)) {
                    st.setInt(1, getObjectId());
                    st.execute();
                }

                try (PreparedStatement st = con.prepareStatement(INSERT_CHAR_RECIPE_SHOP)) {
                    final AtomicInteger slot = new AtomicInteger(1);
                    for (ManufactureItem item : manufactureItems.values()) {
                        st.setInt(1, getObjectId());
                        st.setInt(2, item.getRecipeId());
                        st.setLong(3, item.getCost());
                        st.setInt(4, slot.getAndIncrement());
                        st.addBatch();
                    }

                    st.executeBatch();
                    // No need to call con.commit() as HikariCP autocommit is true.
                }
            } catch (Exception e) {
                log.error("Could not store recipe shop for playerId {}: ", getObjectId(), e);
            }
        }
    }

    private void restoreRecipeShopList() {
        if (manufactureItems != null) {
            manufactureItems.clear();
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(RESTORE_CHAR_RECIPE_SHOP)) {
            ps.setInt(1, getObjectId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    getManufactureItems()
                            .put(
                                    rs.getInt("recipeId"),
                                    new ManufactureItem(rs.getInt("recipeId"), rs.getLong("price")));
                }
            }
        } catch (Exception e) {
            log.error("Could not restore recipe shop list data for playerId: {}", getObjectId(), e);
        }
    }

    public float getCollisionRadius() {
        if (isMounted() && (mountNpcId > 0)) {
            return NpcData.getInstance().getTemplate(getMountNpcId()).getFCollisionRadius();
        }

        if (isTransformed()) {
            return transformation.getCollisionRadius(this);
        }

        return appearance.isFemale()
                ? getBaseTemplate().getFCollisionRadiusFemale()
                : getBaseTemplate().getFCollisionRadius();
    }

    public float getCollisionHeight() {
        if (isMounted() && (mountNpcId > 0)) {
            return NpcData.getInstance().getTemplate(getMountNpcId()).getFCollisionHeight();
        }

        if (isTransformed()) {
            return transformation.getCollisionHeight(this);
        }

        return appearance.isFemale()
                ? getBaseTemplate().getFCollisionHeightFemale()
                : getBaseTemplate().getFCollisionHeight();
    }

    /**
     * @param z
     * @return true if character falling now on the start of fall return false for correct coord sync!
     */
    public boolean isFalling(int z) {
        if (isDead() || isFlying() || isFlyingMounted() || isInsideZone(ZoneId.WATER)) {
            return false;
        }

        if ((fallingTimestamp != 0) && (ClockBridge.gameMillis() < fallingTimestamp)) {
            return true;
        }

        final int deltaZ = getZ() - z;
        if (deltaZ <= getBaseTemplate().getSafeFallHeight()) {
            fallingTimestamp = 0;
            return false;
        }

        // If there is no geodata loaded for the place we are, client Z correction might cause falling damage.
        if (!GeoEngine.getInstance().hasGeo(getX(), getY())) {
            fallingTimestamp = 0;
            return false;
        }

        if (fallingDamage == 0) {
            fallingDamage = (int) Formulas.calcFallDam(this, deltaZ);
        }

        if (fallingDamageTask != null) {
            fallingDamageTask.cancel(true);
        }

        fallingDamageTask = ThreadPool.schedule(
                () -> {
                    if ((fallingDamage > 0) && !isInvul()) {
                        reduceCurrentHp(Math.min(fallingDamage, getCurrentHp() - 1), null, false, true, null);
                        final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_RECEIVED_S1_FALLING_DAMAGE);
                        sm.addInt(fallingDamage);
                        sendPacket(sm);
                    }

                    fallingDamage = 0;
                    fallingDamageTask = null;
                },
                1500);

        // Prevent falling under ground.
        sendPacket(new ValidateLocation(this));
        setFalling();

        return false;
    }

    /**
     * Set falling timestamp
     */
    public void setFalling() {
        fallingTimestamp = ClockBridge.gameMillis() + FALLING_VALIDATION_DELAY;
    }

    /**
     * Update last item auction request timestamp to current
     */
    public void updateLastItemAuctionRequest() {
        lastItemAuctionInfoRequest = ClockBridge.gameMillis();
    }

    /**
     * @return true if receiving item auction requests<br>
     *         (last request was in 2 seconds before)
     */
    public boolean isItemAuctionPolling() {
        return (ClockBridge.gameMillis() - lastItemAuctionInfoRequest) < 2000;
    }

    @Override
    public boolean isMovementDisabled() {
        return super.isMovementDisabled() || (movieHolder != null);
    }

    public String getHtmlPrefix() {
        return MultilingualSupportConfig.MULTILANG_ENABLE ? htmlPrefix : "";
    }

    public boolean setLang(String lang) {
        boolean result = false;
        if (MultilingualSupportConfig.MULTILANG_ENABLE) {
            if (MultilingualSupportConfig.MULTILANG_ALLOWED.contains(lang)) {
                this.lang = lang;
                result = true;
            } else {
                this.lang = MultilingualSupportConfig.MULTILANG_DEFAULT;
            }

            htmlPrefix = this.lang.equals("en") ? "" : "data/lang/" + this.lang + "/";
        } else {
            this.lang = null;
            htmlPrefix = "";
        }

        return result;
    }

    public void setPcCafePoints(int count) {
        pcCafePoints = count < PremiumSystemConfig.PC_CAFE_MAX_POINTS ? count : PremiumSystemConfig.PC_CAFE_MAX_POINTS;
    }

    /**
     * Remove player from BossZones (used on char logout/exit)
     */
    public void removeFromBossZone() {
        try {
            for (BossZone zone : GrandBossManager.getInstance().getZones().values()) {
                zone.removePlayer(this);
            }
        } catch (Exception e) {
            log.warn("Exception on removeFromBossZone(): {}", e.getMessage(), e);
        }
    }

    /**
     * Check all player skills for skill level. If player level is lower than skill learn level - 9, skill level is decreased to next possible level.
     */
    public void checkPlayerSkills() {
        for (Entry<Integer, Skill> e : getSkills().entrySet()) {
            final SkillLearn learn = SkillTreeData.getInstance()
                    .getClassSkill(e.getKey(), e.getValue().getLevel() % 100, getPlayerClass());
            if (learn != null) {
                final int levelDiff = e.getKey() == CommonSkill.EXPERTISE.getId() ? 0 : 9;
                if (getLevel() < (learn.getGetLevel() - levelDiff)) {
                    deacreaseSkillLevel(e.getValue(), levelDiff);
                }
            }
        }
    }

    private void deacreaseSkillLevel(Skill skill, int levelDiff) {
        int nextLevel = -1;
        final Map<Integer, SkillLearn> skillTree =
                SkillTreeData.getInstance().getCompleteClassSkillTree(getPlayerClass());
        for (SkillLearn sl : skillTree.values()) {
            if ((sl.getSkillId() == skill.getId())
                    && (nextLevel < sl.getSkillLevel())
                    && (getLevel() >= (sl.getGetLevel() - levelDiff))) {
                nextLevel = sl.getSkillLevel(); // next possible skill level
            }
        }

        if (nextLevel == -1) {
            log.info("Removing skill {} from {}", skill, this);
            removeSkill(skill, true); // there is no lower skill
        } else {
            log.info("Decreasing skill {} to {} for {}", skill, nextLevel, this);
            addSkill(SkillData.getInstance().getSkill(skill.getId(), nextLevel), true); // replace with lower one
        }
    }

    public boolean canMakeSocialAction() {
        return (privateStoreType == PrivateStoreType.NONE)
                && (getActiveRequester() == null)
                && !isAlikeDead()
                && !isAllSkillsDisabled()
                && !isCastingNow()
                && !isCastingSimultaneouslyNow()
                && (getAI().getIntention() == Intention.IDLE);
    }

    public void setMultiSocialAction(int id, int targetId) {
        multiSociaAction = id;
        multiSocialTarget = targetId;
    }

    public Collection<TeleportBookmark> getTeleportBookmarks() {
        return tpbookmarks.values();
    }

    public void setBookmarkSlot(int slot) {
        bookmarkSlot = slot;
        sendPacket(new ExGetBookMarkInfoPacket(this));
    }

    public int getQuestInventoryLimit() {
        return PlayerConfig.INVENTORY_MAXIMUM_QUEST_ITEMS;
    }

    public boolean canAttackCreature(Creature creature) {
        if (creature == null) {
            return false;
        }

        if (creature.isAttackable()) {
            return true;
        }

        if (creature.isPlayable()) {
            if (creature.isInsideZone(ZoneId.PVP) && !creature.isInsideZone(ZoneId.SIEGE)) {
                return true;
            }

            Player target;
            if (creature.isSummon()) {
                target = creature.asSummon().getOwner();
            } else {
                target = creature.asPlayer();
            }

            if (isInDuel() && target.isInDuel() && (target.getDuelId() == getDuelId())) {
                return true;
            } else if (isInParty() && target.isInParty()) {
                if (getParty() == target.getParty()) {
                    return false;
                }

                if (((getParty().getCommandChannel() != null)
                                || (target.getParty().getCommandChannel() != null))
                        && (getParty().getCommandChannel() == target.getParty().getCommandChannel())) {
                    return false;
                }
            } else if ((getClan() != null) && (target.getClan() != null)) {
                if (getClanId() == target.getClanId()) {
                    return false;
                }

                if (((getAllyId() > 0) || (target.getAllyId() > 0)) && (getAllyId() == target.getAllyId())) {
                    return false;
                }

                if (getClan().isAtWarWith(target.getClan().getId())
                        && target.getClan().isAtWarWith(getClan().getId())) {
                    return true;
                }
            } else if ((getClan() == null) || (target.getClan() == null)) {
                if ((target.getPvpFlag() == 0) && (target.getKarma() == 0)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Test if player inventory is under 90% capacity
     * @param includeQuestInv check also quest inventory
     * @return
     */
    public boolean isInventoryUnder90(boolean includeQuestInv) {
        return (includeQuestInv ? inventory.getSize() : inventory.getNonQuestSize()) <= (getInventoryLimit() * 0.9);
    }

    /**
     * Test if player inventory is under 80% capacity
     * @param includeQuestInv check also quest inventory
     * @return
     */
    public boolean isInventoryUnder80(boolean includeQuestInv) {
        return (includeQuestInv ? inventory.getSize() : inventory.getNonQuestSize()) <= (getInventoryLimit() * 0.8);
    }

    public boolean havePetInvItems() {
        return petItems;
    }

    public void setPetInvItems(boolean haveit) {
        petItems = haveit;
    }

    /**
     * Restore Pet's inventory items from database.
     */
    private void restorePetInventoryItems() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT object_id FROM `items` WHERE `owner_id`=? AND (`loc`='PET' OR `loc`='PET_EQUIP') LIMIT 1;")) {
            ps.setInt(1, getObjectId());
            try (ResultSet rs = ps.executeQuery()) {
                setPetInvItems(rs.next() && (rs.getInt("object_id") > 0));
            }
        } catch (Exception e) {
            log.error("Could not check Items in Pet Inventory for playerId: {}", getObjectId(), e);
        }
    }

    /**
     * Load Player Recommendations data.
     * @return
     */
    private long loadRecommendations() {
        long timeLeft = 0;
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT rec_have,rec_left,time_left FROM character_reco_bonus WHERE charId=? LIMIT 1")) {
            ps.setInt(1, getObjectId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    setRecomHave(rs.getInt("rec_have"));
                    setRecomLeft(rs.getInt("rec_left"));
                    timeLeft = rs.getLong("time_left");
                } else {
                    timeLeft = 3600000;
                }
            }
        } catch (Exception e) {
            log.error("Could not restore Recommendations for player: {}", getObjectId(), e);
        }

        return timeLeft;
    }

    /**
     * Update Player Recommendations data.
     * @param cancelRecoTask
     */
    public void storeRecommendations(boolean cancelRecoTask) {
        long recoTaskEnd = 0;
        if ((nevitHourglassTask != null) && !nevitHourglassTask.isDone()) {
            recoTaskEnd = nevitHourglassTask.getDelay(TimeUnit.MILLISECONDS);
            if (cancelRecoTask) {
                nevitHourglassTask.cancel(false);
                nevitHourglassTask = null;
            }
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "REPLACE INTO character_reco_bonus (charId,rec_have,rec_left,time_left) VALUES (?,?,?,?)")) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, recomHave);
            ps.setInt(3, recomLeft);
            ps.setLong(4, getStat().hasPausedNevitHourglass() ? loadRecommendations() : recoTaskEnd);
            ps.execute();
        } catch (Exception e) {
            log.error("Could not update Recommendations for player: {}", getObjectId(), e);
        }
    }

    /**
     * Store recommendation values without tapping into Nevit Hourglass task.
     */
    public void storeRecommendationValues() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "REPLACE INTO character_reco_bonus (charId,rec_have,rec_left) VALUES (?,?,?)")) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, recomHave);
            ps.setInt(3, recomLeft);
            ps.execute();
        } catch (Exception e) {
            log.error("Could not update Recommendations for player: {}", getObjectId(), e);
        }
    }

    public void checkRecoBonusTask() {
        // Create bonus task
        startNevitHourglassTask();

        // Create task to give new recommendations.
        recoGiveTask = ThreadPool.scheduleAtFixedRate(new RecoGiveTask(this), 7200000, 3600000);

        // Store new data.
        storeRecommendations(false);
    }

    public void startNevitHourglassTask() {
        if (nevitHourglassTask == null) {
            // Load data
            final long taskTime = loadRecommendations();
            if (taskTime > 0) {
                // If player have some timeleft, start bonus task
                nevitHourglassTask = ThreadPool.schedule(new RecoBonusTaskEnd(this), taskTime);
            }

            sendPacket(new ExVoteSystemInfo(this));
        }
    }

    public void stopNevitHourglassTask() {
        if (nevitHourglassTask != null) {
            nevitHourglassTask.cancel(false);
            nevitHourglassTask = null;
        }
    }

    public void stopRecoGiveTask() {
        if (recoGiveTask != null) {
            recoGiveTask.cancel(false);
            recoGiveTask = null;
        }
    }

    public int getNevitHourglassTime() {
        if (nevitHourglassTask != null) {
            return (int) Math.max(0, nevitHourglassTask.getDelay(TimeUnit.SECONDS));
        }

        return 0;
    }

    public boolean isNevitHourglassActive() {
        return (getNevitHourglassTime() > 0) || getStat().hasPausedNevitHourglass();
    }

    public int getNevitHourglassStatus() {
        if (getStat().hasPausedNevitHourglass()) {
            return NEVIT_HOURGLASS_PAUSED;
        } else if (getNevitHourglassTime() > 0) {
            return NEVIT_HOURGLASS_ACTIVE;
        } else {
            return NEVIT_HOURGLASS_MAINTAIN;
        }
    }

    public int getNevitHourglassBonus() {
        if (online && (recomHave != 0) && isNevitHourglassActive()) {
            final int lvl = getLevel() / 10;
            final int exp = (Math.min(100, recomHave) - 1) / 10;
            return NEVIT_HOURGLASS_BONUS[lvl][exp];
        }

        return 0;
    }

    public double getNevitHourglassMultiplier() {
        double multiplier = 1.0;
        if (online && (recomHave != 0) && isNevitHourglassActive()) {
            final double bonus = getNevitHourglassBonus();
            if (bonus > 0) {
                multiplier += (bonus / 100);
            }
        }

        return multiplier;
    }

    public boolean hasPremiumStatus() {
        return PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && premiumStatus;
    }

    public void updateNotMoveUntil() {
        notMoveUntil = ClockBridge.gameMillis() + PlayerConfig.PLAYER_MOVEMENT_BLOCK_TIME;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public Player asPlayer() {
        return this;
    }

    @Override
    public boolean isChargedShot(ShotType type) {
        final Item weapon = getActiveWeaponInstance();
        return (weapon != null) && weapon.isChargedShot(type);
    }

    @Override
    public void setChargedShot(ShotType type, boolean charged) {
        final Item weapon = getActiveWeaponInstance();
        if (weapon != null) {
            weapon.setChargedShot(type, charged);
        }
    }

    /**
     * @param skillId the display skill Id
     * @return the custom skill
     */
    public Skill getCustomSkill(int skillId) {
        return (customSkills != null) ? customSkills.get(skillId) : null;
    }

    /**
     * Add a skill level to the custom skills map.
     * @param skill the skill to add
     */
    private void addCustomSkill(Skill skill) {
        if ((skill == null) || (skill.getDisplayId() == skill.getId())) {
            return;
        }

        if (customSkills == null) {
            customSkills = new ConcurrentHashMap<>();
        }

        customSkills.put(skill.getDisplayId(), skill);
    }

    /**
     * Remove a skill level from the custom skill map.
     * @param skill the skill to remove
     */
    private void removeCustomSkill(Skill skill) {
        if ((skill != null) && (customSkills != null) && (skill.getDisplayId() != skill.getId())) {
            customSkills.remove(skill.getDisplayId());
        }
    }

    /**
     * @return {@code true} if current player can revive and shows 'To Village' button upon death, {@code false} otherwise.
     */
    @Override
    public boolean canRevive() {
        return canRevive;
    }

    public boolean isRegisteredOnEvent() {
        return registeredOnEvent || onEvent;
    }

    public int getBlockCheckerArena() {
        return blockCheckerArena;
    }

    public int getUCKills() {
        return ucKills;
    }

    public void addKillCountUC() {
        ucKills++;
    }

    public int getUCDeaths() {
        return ucDeaths;
    }

    public void addDeathCountUC() {
        ucDeaths++;
    }

    public void cleanUCStats() {
        ucDeaths = 0;
        ucKills = 0;
    }

    public void setUCState(int state) {
        setRegisteredOnEvent(state != UC_STATE_NONE);
        ucState = state;
    }

    public int getUCState() {
        return ucState;
    }

    public void setOriginalCpHpMp(double cp, double hp, double mp) {
        originalCp = cp;
        originalHp = hp;
        originalMp = mp;
    }

    /**
     * @return {@code true} if {@link PlayerVariables} instance is attached to current player's scripts, {@code false} otherwise.
     */
    public boolean hasVariables() {
        return getScript(PlayerVariables.class) != null;
    }

    /**
     * @return {@link PlayerVariables} instance containing parameters regarding player.
     */
    public PlayerVariables getVariables() {
        final PlayerVariables vars = getScript(PlayerVariables.class);
        return vars != null ? vars : addScript(new PlayerVariables(getObjectId()));
    }

    /**
     * @return {@code true} if {@link AccountVariables} instance is attached to current player's scripts, {@code false} otherwise.
     */
    public boolean hasAccountVariables() {
        return getScript(AccountVariables.class) != null;
    }

    /**
     * @return {@link AccountVariables} instance containing parameters regarding player.
     */
    public AccountVariables getAccountVariables() {
        final AccountVariables vars = getScript(AccountVariables.class);
        return vars != null ? vars : addScript(new AccountVariables(getAccountName()));
    }

    @Override
    public int getId() {
        return getPlayerClass().getId();
    }

    public boolean isPartyBanned() {
        return PunishmentManager.getInstance()
                .hasPunishment(getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.PARTY_BAN);
    }

    /**
     * @param act
     * @return {@code true} if action was added successfully, {@code false} otherwise.
     */
    public boolean addAction(PlayerAction act) {
        if (!hasAction(act)) {
            actionMask |= act.getMask();
            return true;
        }

        return false;
    }

    /**
     * @param act
     * @return {@code true} if action was removed successfully, {@code false} otherwise.
     */
    public boolean removeAction(PlayerAction act) {
        if (hasAction(act)) {
            actionMask &= ~act.getMask();
            return true;
        }

        return false;
    }

    /**
     * @param act
     * @return {@code true} if action is present, {@code false} otherwise.
     */
    public boolean hasAction(PlayerAction act) {
        return (actionMask & act.getMask()) == act.getMask();
    }

    // High Five: Nevit's Bonus System
    @Getter
    private final Nevit nevitSystem = new Nevit(this);

    /**
     * Set true/false if character got Charm of Courage
     * @param value true/false
     */
    public void setCharmOfCourage(boolean value) {
        hasCharmOfCourage = value;
    }

    /**
     * @return {@code true} if effect is present, {@code false} otherwise.
     */
    public boolean hasCharmOfCourage() {
        return hasCharmOfCourage;
    }

    public void setGood() {
        good = true;
        evil = false;
    }

    public void setEvil() {
        good = false;
        evil = true;
    }

    /**
     * @param target the target
     * @return {@code true} if this player got war with the target, {@code false} otherwise.
     */
    public boolean isAtWarWith(Creature target) {
        if (target == null) {
            return false;
        }

        if ((clan != null) && !isAcademyMember() && (target.getClan() != null) && !target.isAcademyMember()) {
            return clan.isAtWarWith(target.getClan());
        }

        return false;
    }

    /**
     * @param target the target
     * @return {@code true} if this player in same party with the target, {@code false} otherwise.
     */
    public boolean isInPartyWith(Creature target) {
        return isInParty()
                && target.isInParty()
                && (getParty().getLeaderObjectId() == target.getParty().getLeaderObjectId());
    }

    /**
     * @param target the target
     * @return {@code true} if this player in same command channel with the target, {@code false} otherwise.
     */
    public boolean isInCommandChannelWith(Creature target) {
        return isInParty()
                && target.isInParty()
                && getParty().isInCommandChannel()
                && target.getParty().isInCommandChannel()
                && (getParty().getCommandChannel().getLeaderObjectId()
                        == target.getParty().getCommandChannel().getLeaderObjectId());
    }

    /**
     * @param target the target
     * @return {@code true} if this player in same clan with the target, {@code false} otherwise.
     */
    public boolean isInClanWith(Creature target) {
        return (getClanId() != 0) && (target.getClanId() != 0) && (getClanId() == target.getClanId());
    }

    /**
     * @param target the target
     * @return {@code true} if this player in same ally with the target, {@code false} otherwise.
     */
    public boolean isInAllyWith(Creature target) {
        return (getAllyId() != 0) && (target.getAllyId() != 0) && (getAllyId() == target.getAllyId());
    }

    /**
     * @param target the target
     * @return {@code true} if this player at duel with the target, {@code false} otherwise.
     */
    public boolean isInDuelWith(Creature target) {
        return isInDuel() && target.isInDuel() && (getDuelId() == target.getDuelId());
    }

    /**
     * @param target the target
     * @return {@code true} if this player is on same siege side with the target, {@code false} otherwise.
     */
    public boolean isOnSameSiegeSideWith(Creature target) {
        return (getSiegeState() > 0)
                && isInsideZone(ZoneId.SIEGE)
                && (getSiegeState() == target.getSiegeState())
                && (getSiegeSide() == target.getSiegeSide());
    }

    /**
     * @return the game shop points of the player.
     */
    public long getGamePoints() {
        return getAccountVariables().getInt(GAME_POINTS_VAR, 0);
    }

    /**
     * Sets game shop points for current player.
     * @param points
     */
    public void setGamePoints(long points) {
        // Immediate store upon change.
        final AccountVariables vars = getAccountVariables();
        vars.set(GAME_POINTS_VAR, Math.max(points, 0));
        vars.storeMe();
    }

    private TerminateReturn onExperienceReceived() {
        if (isDead()) {
            return new TerminateReturn(false, false, false);
        }

        return new TerminateReturn(true, true, true);
    }

    public void disableExpGain() {
        addListener(new FunctionEventListener(
                this, EventType.ON_PLAYABLE_EXP_CHANGED, (OnPlayableExpChanged _) -> onExperienceReceived(), this));
    }

    public void enableExpGain() {
        removeListenerIf(EventType.ON_PLAYABLE_EXP_CHANGED, listener -> listener.getOwner() == this);
    }

    public void sendInventoryUpdate(InventoryUpdate iu) {
        if (inventoryUpdateTask != null) {
            inventoryUpdateTask.cancel(false);
        }

        inventoryUpdate.putAll(iu.getItemEntries());

        inventoryUpdateTask = ThreadPool.schedule(
                () -> {
                    sendPacket(inventoryUpdate);
                },
                100);
    }

    /**
     * Sends the pending inventory update now instead of waiting for the scheduled task.
     */
    public void flushInventoryUpdate() {
        if (inventoryUpdateTask != null) {
            inventoryUpdateTask.cancel(false);
            inventoryUpdateTask = null;
        }

        sendPacket(inventoryUpdate);
    }

    public void sendItemList(boolean open) {
        if (itemListTask != null) {
            itemListTask.cancel(false);
        }

        itemListTask = ThreadPool.schedule(
                () -> {
                    sendPacket(new ItemList(this, open));
                },
                250);
    }

    /**
     * Precautionary method to end all tasks upon disconnection.
     * @TODO: Rework stopAllTimers() method.
     */
    public void stopAllTasks() {
        if ((mountFeedTask != null) && !mountFeedTask.isDone() && !mountFeedTask.isCancelled()) {
            mountFeedTask.cancel(false);
            mountFeedTask = null;
        }

        if ((dismountTask != null) && !dismountTask.isDone() && !dismountTask.isCancelled()) {
            dismountTask.cancel(false);
            dismountTask = null;
        }

        if ((fameTask != null) && !fameTask.isDone() && !fameTask.isCancelled()) {
            fameTask.cancel(false);
            fameTask = null;
        }

        if ((vitalityTask != null) && !vitalityTask.isDone() && !vitalityTask.isCancelled()) {
            vitalityTask.cancel(false);
            vitalityTask = null;
        }

        if ((teleportWatchdog != null) && !teleportWatchdog.isDone() && !teleportWatchdog.isCancelled()) {
            teleportWatchdog.cancel(false);
            teleportWatchdog = null;
        }

        if ((taskForFish != null) && !taskForFish.isDone() && !taskForFish.isCancelled()) {
            taskForFish.cancel(false);
            taskForFish = null;
        }

        if ((chargeTask != null) && !chargeTask.isDone() && !chargeTask.isCancelled()) {
            chargeTask.cancel(false);
            chargeTask = null;
        }

        if ((soulTask != null) && !soulTask.isDone() && !soulTask.isCancelled()) {
            soulTask.cancel(false);
            soulTask = null;
        }

        if ((taskRentPet != null) && !taskRentPet.isDone() && !taskRentPet.isCancelled()) {
            taskRentPet.cancel(false);
            taskRentPet = null;
        }

        if ((taskWater != null) && !taskWater.isDone() && !taskWater.isCancelled()) {
            taskWater.cancel(false);
            taskWater = null;
        }

        if ((fallingDamageTask != null) && !fallingDamageTask.isDone() && !fallingDamageTask.isCancelled()) {
            fallingDamageTask.cancel(false);
            fallingDamageTask = null;
        }

        if ((taskWarnUserTakeBreak != null)
                && !taskWarnUserTakeBreak.isDone()
                && !taskWarnUserTakeBreak.isCancelled()) {
            taskWarnUserTakeBreak.cancel(false);
            taskWarnUserTakeBreak = null;
        }

        synchronized (questTimers) {
            for (QuestTimer timer : questTimers) {
                timer.cancelTask();
            }

            questTimers.clear();
        }

        synchronized (timerHolders) {
            for (TimerHolder<?> timer : timerHolders) {
                timer.cancelTask();
            }

            timerHolders.clear();
        }
    }

    public void addQuestTimer(QuestTimer questTimer) {
        synchronized (questTimers) {
            questTimers.add(questTimer);
        }
    }

    public void removeQuestTimer(QuestTimer questTimer) {
        synchronized (questTimers) {
            questTimers.remove(questTimer);
        }
    }

    public void addTimerHolder(TimerHolder<?> timer) {
        synchronized (timerHolders) {
            timerHolders.add(timer);
        }
    }

    public void removeTimerHolder(TimerHolder<?> timer) {
        synchronized (timerHolders) {
            timerHolders.remove(timer);
        }
    }

    public double getServitorShareBonus(Stat stat) {
        final Map<Stat, Double> stats = servitorShare;
        if (stats == null) {
            return 1.0d;
        }

        final Double val = stats.get(stat);
        if (val == null) {
            return 1.0d;
        }

        return val;
    }

    public void setAutoPlaying(boolean value) {
        autoPlaying.set(value);

        if (!value && offlinePlay && OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS) {
            OfflinePlayTable.getInstance().removeOfflinePlay(this);
        }
    }

    public boolean isAutoPlaying() {
        return autoPlaying.get();
    }
}
