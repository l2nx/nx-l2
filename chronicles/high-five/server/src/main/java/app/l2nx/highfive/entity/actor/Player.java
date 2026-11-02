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
package app.l2nx.highfive.entity.actor;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.ai.Intention;
import app.l2nx.highfive.ai.PlayerAI;
import app.l2nx.highfive.ai.SummonAI;
import app.l2nx.highfive.cache.RelationCache;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.chat.view.ChatMessage;
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
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.IntSnapshot;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
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
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.managers.AntiFeedManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.CharacterRelationsManager;
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
import app.l2nx.highfive.managers.PlayerPersistenceManager;
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
import app.l2nx.highfive.network.enums.HtmlActionScope;
import app.l2nx.highfive.network.holders.RequestPartner;
import app.l2nx.highfive.network.holders.TradeList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AbstractHtmlPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ChangeWaitType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CharInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ConfirmDlg;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.EtcStatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExAutoSoulShot;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExBrExtraUserInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExDuelUpdateUserInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExFishingEnd;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExFishingStart;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExGetBookMarkInfoPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExGetOnAirShip;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOlympiadMode;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPrivateStoreSetWholeMsg;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExSetCompassZoneCode;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExStartScenePlayer;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExStorageMaxCount;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExUseSharedGroupItem;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExVoteSystemInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.FriendStatusPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.GetOnVehicle;
import app.l2nx.highfive.network.protocol.p267.serverpackets.HennaInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ItemList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LeaveWorld;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillUse;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MyTargetSelected;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NicknameChanged;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ObservationMode;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ObservationReturn;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PartySmallWindowUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PetInventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PlaySound;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowMemberListDelete;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowMemberListUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreListBuy;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreListSell;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreManageListBuy;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreManageListSell;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreMsgBuy;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreMsgSell;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RecipeShopMsg;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RecipeShopSellList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RelationChanged;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Ride;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SetupGauge;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ShortcutInit;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SkillCoolTime;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SkillList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Snoop;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SocialAction;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StopMove;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.TargetSelected;
import app.l2nx.highfive.network.protocol.p267.serverpackets.TargetUnselected;
import app.l2nx.highfive.network.protocol.p267.serverpackets.TradeDone;
import app.l2nx.highfive.network.protocol.p267.serverpackets.TradeOtherDone;
import app.l2nx.highfive.network.protocol.p267.serverpackets.TradeStart;
import app.l2nx.highfive.network.protocol.p267.serverpackets.UserInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ValidateLocation;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.model.CharacterHenna;
import app.l2nx.highfive.repository.character.model.CharacterItemReuse;
import app.l2nx.highfive.repository.character.model.CharacterPremiumItem;
import app.l2nx.highfive.repository.character.model.CharacterRecipe;
import app.l2nx.highfive.repository.character.model.CharacterRecipeShopItem;
import app.l2nx.highfive.repository.character.model.CharacterRecommendation;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.repository.character.model.CharacterSkill;
import app.l2nx.highfive.repository.character.model.CharacterSkillSave;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import app.l2nx.highfive.repository.character.model.CharacterTeleportBookmark;
import app.l2nx.highfive.repository.character.model.Faction;
import app.l2nx.highfive.repository.character.model.OnlineStatus;
import app.l2nx.highfive.repository.character.model.SkillRestoreType;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import app.l2nx.highfive.taskmanagers.AutoPlayTaskManager;
import app.l2nx.highfive.taskmanagers.AutoUseTaskManager;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.taskmanagers.ItemsAutoDestroyTaskManager;
import app.l2nx.highfive.taskmanagers.PlayerAutoSaveTaskManager;
import app.l2nx.highfive.taskmanagers.PvpFlagTaskManager;
import app.l2nx.highfive.util.LocationUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMaps;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;

/**
 * This class represents all player characters in the world.<br>
 * There is always a client-thread connected to this (except if a player-store is activated upon logout).
 */
@Slf4j
@SingleWriter
public class Player extends Playable {
    public static final String NEWBIE_KEY = "NEWBIE";

    public static final int ID_NONE = -1;

    public static final int REQUEST_TIMEOUT = 15;

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

    private GameClient client;

    private String ipAddress = "N/A";

    private final UUID accountId;

    private long deleteTimer;

    private Calendar createDate = ClockBridge.calendar();

    private String lang = null;

    private String htmlPrefix = "";

    private boolean online = false;

    private boolean offlinePlay = false;

    private boolean enteredWorld = false;
    private long onlineTime;
    private long onlineBeginTime;

    private long lastAccess;

    private long uptime;

    private final InventoryUpdate inventoryUpdate = new InventoryUpdate();
    private ScheduledFuture<?> inventoryUpdateTask;
    private ScheduledFuture<?> itemListTask;
    private ScheduledFuture<?> skillListTask;
    private ScheduledFuture<?> updateAndBroadcastStatusTask;
    private ScheduledFuture<?> broadcastCharInfoTask;
    private ScheduledFuture<?> broadcastStatusUpdateTask;

    private boolean changingClass = false;

    protected int baseClass;

    protected int activeClass;

    protected int classIndex = 0;

    private int controlItemId;

    private PetLevelData leveldata;

    private int currentFeed;

    protected ScheduledFuture<?> mountFeedTask;
    private ScheduledFuture<?> dismountTask;
    private boolean petItems = false;

    /** The list of sub-classes this character has, by class index. */
    private final Int2ObjectSortedMap<SubClassHolder> subClasses = new Int2ObjectRBTreeMap<>();

    private final PlayerAppearance appearance;

    private long expBeforeDeath;

    private int pvpKills;

    /** Kills of players without a PvP flag. */
    private int pkKills;

    /** 0 = white, 1 = purple. */
    private byte pvpFlag;

    private int fame;

    private ScheduledFuture<?> fameTask;

    private ScheduledFuture<?> vitalityTask;

    private ScheduledFuture<?> teleportWatchdog;

    private byte siegeState = 0;

    /** Id of the castle or fort the player is registered to siege. */
    private int siegeSide = 0;

    private int curWeightPenalty = 0;

    private int lastCompassZone;

    private boolean in7sDungeon = false;

    private final ContactList contactList = new ContactList(this);

    private int bookmarkSlot = 0;

    /** By bookmark id, which orders the client's list. */
    private final Int2ObjectSortedMap<TeleportBookmark> tpbookmarks = new Int2ObjectRBTreeMap<>();

    private boolean canFeed;

    private boolean inSiege;

    private boolean inHideoutSiege = false;

    private boolean inOlympiadMode = false;

    private boolean olympiadStart = false;

    private int olympiadGameId = -1;

    private int olympiadSide = -1;
    private int olympiadBuffCount = 0;

    private boolean inDuel = false;

    private boolean startingDuel = false;

    private int duelState = Duel.DUELSTATE_NODUEL;

    private int duelId = 0;

    private SystemMessageId noDuelReason = SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL;

    private Vehicle vehicle = null;

    private Location inVehiclePosition;

    private ScheduledFuture<?> taskForFish;

    private MountType mountType = MountType.NONE;

    private int mountNpcId;

    private int mountLevel;
    /** Object id of the control item that summoned the mounted strider. */
    private int mountObjectID = 0;

    private int teleMode = 0;

    private boolean inCrystallize;

    private boolean crafting;

    private long offlineStartTime = 0;

    private Transform transformation;

    private final Int2ObjectLinkedOpenHashMap<Skill> transformSkills = new Int2ObjectLinkedOpenHashMap<>();

    private final Int2ObjectLinkedOpenHashMap<RecipeList> dwarvenRecipeBook = new Int2ObjectLinkedOpenHashMap<>();

    private final Int2ObjectLinkedOpenHashMap<RecipeList> commonRecipeBook = new Int2ObjectLinkedOpenHashMap<>();

    private final Int2ObjectLinkedOpenHashMap<PremiumItem> premiumItemList = new Int2ObjectLinkedOpenHashMap<>();

    private boolean sitting;

    private boolean sittingInProgress;

    /** Location before entering Observer Mode */
    private final Location lastLocation = new Location(0, 0, 0);

    private boolean observerMode = false;

    private final Location lastServerPosition = new Location(0, 0, 0);

    private boolean blinkActive;

    private int recomHave;
    private int recomLeft;
    private ScheduledFuture<?> nevitHourglassTask;
    private ScheduledFuture<?> recoGiveTask;
    protected boolean recoTwoHoursGiven = false;

    private final PlayerInventory inventory = new PlayerInventory(this);

    private final PlayerFreight freight = new PlayerFreight(this);

    private final PlayerWarehouse warehouse = new PlayerWarehouse(this);

    private PlayerRefund refund;

    private PrivateStoreType privateStoreType = PrivateStoreType.NONE;

    private TradeList activeTradeList;

    private ItemContainer activeWarehouse;

    private final Int2ObjectLinkedOpenHashMap<ManufactureItem> manufactureItems = new Int2ObjectLinkedOpenHashMap<>();

    private String storeName = "";

    private TradeList sellList;
    private TradeList buyList;

    private PreparedListContainer multiSell = null;

    /** Bitmask used to keep track of one-time/newbie quest rewards */
    private int newbie;

    private boolean noble = false;

    private boolean hero = false;

    private boolean premiumStatus = false;

    private boolean good = false;

    private boolean evil = false;

    private Npc lastFolkNPC = null;

    private int lastQuestNpcObject = 0;

    private final TreeMap<String, QuestState> quests = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private final Shortcuts shortcuts = new Shortcuts(this);

    private final MacroList macros = new MacroList(this);

    private final ObjectLinkedOpenHashSet<Player> snoopListener = new ObjectLinkedOpenHashSet<>(1);
    private final ObjectLinkedOpenHashSet<Player> snoopedPlayer = new ObjectLinkedOpenHashSet<>(1);

    private final Henna[] hennaList = new Henna[3];

    private int hennaStatSTR;

    private int hennaStatINT;

    private int hennaStatDEX;

    private int hennaStatMEN;

    private int hennaStatWIT;

    private int hennaStatCON;

    private Summon summon = null;
    private Decoy decoy = null;
    private Trap trap = null;
    private int agathionId = 0;

    // A player can hold a summon and tamed beasts at once, and several beasts since Freya.
    private ObjectLinkedOpenHashSet<TamedBeast> trainedBeasts = null;

    private boolean minimapAllowed = false;

    private final Radar radar;

    private ScheduledFuture<?> taskWarnUserTakeBreak;

    private int partyRoom = 0;

    private int clanId;

    private Clan clan;

    private int apprentice = 0;

    private int sponsor = 0;

    private long clanJoinExpiryTime;

    private long clanCreateExpiryTime;

    private int powerGrade = 0;

    private ClanPrivileges clanPrivileges = new ClanPrivileges();

    private int pledgeClass = 0;

    private int pledgeType = 0;

    private int lvlJoinedAcademy = 0;

    private int wantsPeace = 0;

    private int deathPenaltyBuffLevel = 0;

    private int charges;
    private ScheduledFuture<?> chargeTask = null;

    private int chargedSouls = 0;

    private ScheduledFuture<?> soulTask = null;

    // Target of TARGET_SIGNET_GROUND skills.
    private Location currentSkillWorldPosition;

    private AccessLevel accessLevel;

    private boolean messageRefusal = false;

    private boolean silenceMode = false;

    private List<Integer> silenceModeExcluded;

    private boolean dietMode = false; // ignore weight penalty

    private boolean tradeRefusal = false;

    private boolean exchangeRefusal = false;

    private Party party;

    private PartyDistributionType partyDistributionType;

    // The inviter, needed for the party response; only one party request can be active at once.
    private Player activeRequester;

    private long requestExpireTime = 0;

    private final RequestPartner request = new RequestPartner(this);

    private Item arrowItem;
    private Item boltItem;

    private long spawnProtectEndTime = 0;
    private long teleportProtectEndTime = 0;

    // Protects from aggro mobs while getting up from fake death.
    private long recentFakeDeathEndTime = 0;

    private boolean fakeDeath;

    private Weapon fistsWeaponItem;

    private final Int2ObjectLinkedOpenHashMap<String> accountChars = new Int2ObjectLinkedOpenHashMap<>();

    private int expertiseArmorPenalty = 0;

    private int expertiseWeaponPenalty = 0;

    private int expertisePenaltyBonus = 0;

    private boolean enchanting = false;

    private int activeEnchantItemId = ID_NONE;

    private int activeEnchantSupportItemId = ID_NONE;

    private int activeEnchantAttrItemId = ID_NONE;

    private long activeEnchantTimestamp = 0;

    protected boolean inventoryDisabled = false;
    private final Int2ObjectSortedMap<Cubic> cubics = new Int2ObjectRBTreeMap<>();

    /** Cubic ids, the oldest summoned first. */
    private final IntLinkedOpenHashSet cubicOrder = new IntLinkedOpenHashSet();

    private final IntLinkedOpenHashSet autoSoulShot = new IntLinkedOpenHashSet(1);

    private boolean registeredOnEvent = false;

    private boolean onSoloEvent = false;

    private boolean onEvent = false;

    private byte blockCheckerArena = -1;

    private KrateiArena krateiArena = null;

    private int ucKills = 0;
    private int ucDeaths = 0;
    private int ucState = 0;
    public static final int UC_STATE_NONE = 0;
    public static final int UC_STATE_POINT = 1;
    public static final int UC_STATE_ARENA = 2;

    private final int[] loto = new int[5];
    private final int[] raceTickets = new int[2];

    private final BlockList blockList = new BlockList(this);

    private Fish fish;

    private Item lure;

    private Fishing fishCombat;

    private boolean fishing = false;

    private int fishX = 0;

    private int fishY = 0;

    private int fishZ = 0;

    private ScheduledFuture<?> taskRentPet;
    private ScheduledFuture<?> taskWater;

    /** Last Html Npcs, 0 = last html was not bound to an npc */
    private final int[] htmlActionOriginObjectIds = new int[HtmlActionScope.values().length];

    private int lastHtmlActionOriginId;

    @SuppressWarnings("unchecked")
    private final List<String>[] htmlActionCaches = new List[HtmlActionScope.values().length];

    private Forum forumMail;
    private Forum forumMemo;

    /** The skill button last pressed; distinct from the cast state Creature tracks. */
    private SkillUseHolder currentSkill;

    private SkillUseHolder currentPetSkill;

    private SkillUseHolder queuedSkill;

    private int cursedWeaponEquippedId = 0;

    private boolean combatFlagEquipped = false;

    private boolean canRevive = true;

    private int reviveRequested = 0;
    private double revivePower = 0;

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

    private int clientX;

    private int clientY;

    private int clientZ;

    private int clientHeading;

    // Position validation is disabled for this many ms after a fall.
    private static final int FALLING_VALIDATION_DELAY = 1000;
    private long fallingTimestamp = 0;
    private int fallingDamage = 0;
    private ScheduledFuture<?> fallingDamageTask = null;

    private int multiSocialTarget = 0;

    private int multiSociaAction = 0;

    private MovieHolder movieHolder = null;

    private String adminConfirmCmd = null;

    private long lastItemAuctionInfoRequest = 0;

    private long pvpFlagLasts;

    private long notMoveUntil = 0;

    private Int2ObjectLinkedOpenHashMap<Skill> customSkills = null;

    private int actionMask;

    private Map<Stat, Double> servitorShare;

    private Player(int objectId, PlayerTemplate template, UUID accountId, PlayerAppearance app) {
        super(objectId, template);
        setInstanceType(InstanceType.Player);
        initCharStatusUpdateValues();
        initPcStatusUpdateValues();

        for (int i = 0; i < htmlActionCaches.length; ++i) {
            htmlActionCaches[i] = new ArrayList<>();
        }

        this.accountId = accountId;
        app.setOwner(this);
        appearance = app;

        getAI();

        radar = new Radar(this);
        startVitalityTask();
    }

    private Player(PlayerTemplate template, UUID accountId, PlayerAppearance app) {
        this(IdManager.getInstance().getNextId(), template, accountId, app);
    }

    public int getPcCafePoints() {
        WorldPartition.checkOwner();
        return pcCafePoints;
    }

    public GameClient getClient() {
        WorldPartition.checkOwner();
        return client;
    }

    public UUID getAccountId() {
        WorldPartition.checkOwner();
        return accountId;
    }

    public long getDeleteTimer() {
        WorldPartition.checkOwner();
        return deleteTimer;
    }

    public void setDeleteTimer(long deleteTimer) {
        WorldPartition.checkOwner();
        this.deleteTimer = deleteTimer;
    }

    public Calendar getCreateDate() {
        WorldPartition.checkOwner();
        return createDate;
    }

    public void setCreateDate(Calendar createDate) {
        WorldPartition.checkOwner();
        this.createDate = createDate;
    }

    public String getLang() {
        WorldPartition.checkOwner();
        return lang;
    }

    public boolean isOnline() {
        WorldPartition.checkOwner();
        return online;
    }

    public boolean isOfflinePlay() {
        WorldPartition.checkOwner();
        return offlinePlay;
    }

    public void setOfflinePlay(boolean offlinePlay) {
        WorldPartition.checkOwner();
        this.offlinePlay = offlinePlay;
    }

    public long getLastAccess() {
        WorldPartition.checkOwner();
        return lastAccess;
    }

    protected void setLastAccess(long lastAccess) {
        WorldPartition.checkOwner();
        this.lastAccess = lastAccess;
    }

    public void setUptime(long uptime) {
        WorldPartition.checkOwner();
        this.uptime = uptime;
    }

    public boolean isChangingClass() {
        WorldPartition.checkOwner();
        return changingClass;
    }

    public int getBaseClass() {
        WorldPartition.checkOwner();
        return baseClass;
    }

    public int getActiveClass() {
        WorldPartition.checkOwner();
        return activeClass;
    }

    public int getClassIndex() {
        WorldPartition.checkOwner();
        return classIndex;
    }

    public int getCurrentFeed() {
        WorldPartition.checkOwner();
        return currentFeed;
    }

    public Map<Integer, SubClassHolder> getSubClasses() {
        WorldPartition.checkOwner();
        return Int2ObjectSortedMaps.unmodifiable(new Int2ObjectRBTreeMap<>(subClasses));
    }

    /** @return the sub-class of the given class index, {@code null} for none */
    public SubClassHolder getSubClass(int classIndex) {
        WorldPartition.checkOwner();
        return subClasses.get(classIndex);
    }

    public boolean hasSubClass(int classIndex) {
        WorldPartition.checkOwner();
        return subClasses.containsKey(classIndex);
    }

    public boolean hasSubClasses() {
        WorldPartition.checkOwner();
        return !subClasses.isEmpty();
    }

    public PlayerAppearance getAppearance() {
        WorldPartition.checkOwner();
        return appearance;
    }

    public long getExpBeforeDeath() {
        WorldPartition.checkOwner();
        return expBeforeDeath;
    }

    public void setExpBeforeDeath(long expBeforeDeath) {
        WorldPartition.checkOwner();
        this.expBeforeDeath = expBeforeDeath;
    }

    public int getPvpKills() {
        WorldPartition.checkOwner();
        return pvpKills;
    }

    public int getPkKills() {
        WorldPartition.checkOwner();
        return pkKills;
    }

    public byte getPvpFlag() {
        WorldPartition.checkOwner();
        return pvpFlag;
    }

    public int getFame() {
        WorldPartition.checkOwner();
        return fame;
    }

    public byte getSiegeState() {
        WorldPartition.checkOwner();
        return siegeState;
    }

    public void setSiegeState(byte siegeState) {
        WorldPartition.checkOwner();
        this.siegeState = siegeState;
    }

    public int getSiegeSide() {
        WorldPartition.checkOwner();
        return siegeSide;
    }

    public void setSiegeSide(int siegeSide) {
        WorldPartition.checkOwner();
        this.siegeSide = siegeSide;
    }

    public boolean isIn7sDungeon() {
        WorldPartition.checkOwner();
        return in7sDungeon;
    }

    public void setIn7sDungeon(boolean in7sDungeon) {
        WorldPartition.checkOwner();
        this.in7sDungeon = in7sDungeon;
    }

    public ContactList getContactList() {
        WorldPartition.checkOwner();
        return contactList;
    }

    public int getBookmarkSlot() {
        WorldPartition.checkOwner();
        return bookmarkSlot;
    }

    public boolean isInSiege() {
        WorldPartition.checkOwner();
        return inSiege;
    }

    public void setInSiege(boolean inSiege) {
        WorldPartition.checkOwner();
        this.inSiege = inSiege;
    }

    public boolean isInHideoutSiege() {
        WorldPartition.checkOwner();
        return inHideoutSiege;
    }

    public void setInHideoutSiege(boolean inHideoutSiege) {
        WorldPartition.checkOwner();
        this.inHideoutSiege = inHideoutSiege;
    }

    public boolean isInOlympiadMode() {
        WorldPartition.checkOwner();
        return inOlympiadMode;
    }

    public void setInOlympiadMode(boolean inOlympiadMode) {
        WorldPartition.checkOwner();
        this.inOlympiadMode = inOlympiadMode;
    }

    public boolean isOlympiadStart() {
        WorldPartition.checkOwner();
        return olympiadStart;
    }

    public void setOlympiadStart(boolean olympiadStart) {
        WorldPartition.checkOwner();
        this.olympiadStart = olympiadStart;
    }

    public int getOlympiadGameId() {
        WorldPartition.checkOwner();
        return olympiadGameId;
    }

    public void setOlympiadGameId(int olympiadGameId) {
        WorldPartition.checkOwner();
        this.olympiadGameId = olympiadGameId;
    }

    public int getOlympiadSide() {
        WorldPartition.checkOwner();
        return olympiadSide;
    }

    public void setOlympiadSide(int olympiadSide) {
        WorldPartition.checkOwner();
        this.olympiadSide = olympiadSide;
    }

    public int getOlympiadBuffCount() {
        WorldPartition.checkOwner();
        return olympiadBuffCount;
    }

    public void setOlympiadBuffCount(int olympiadBuffCount) {
        WorldPartition.checkOwner();
        this.olympiadBuffCount = olympiadBuffCount;
    }

    public boolean isInDuel() {
        WorldPartition.checkOwner();
        return inDuel;
    }

    public int getDuelState() {
        WorldPartition.checkOwner();
        return duelState;
    }

    public void setDuelState(int duelState) {
        WorldPartition.checkOwner();
        this.duelState = duelState;
    }

    public int getDuelId() {
        WorldPartition.checkOwner();
        return duelId;
    }

    public Vehicle getVehicle() {
        WorldPartition.checkOwner();
        return vehicle;
    }

    public Location getInVehiclePosition() {
        WorldPartition.checkOwner();
        return inVehiclePosition;
    }

    public void setInVehiclePosition(Location inVehiclePosition) {
        WorldPartition.checkOwner();
        this.inVehiclePosition = inVehiclePosition;
    }

    public MountType getMountType() {
        WorldPartition.checkOwner();
        return mountType;
    }

    public int getMountNpcId() {
        WorldPartition.checkOwner();
        return mountNpcId;
    }

    public int getMountLevel() {
        WorldPartition.checkOwner();
        return mountLevel;
    }

    public int getMountObjectID() {
        WorldPartition.checkOwner();
        return mountObjectID;
    }

    public void setMountObjectID(int mountObjectID) {
        WorldPartition.checkOwner();
        this.mountObjectID = mountObjectID;
    }

    public int getTeleMode() {
        WorldPartition.checkOwner();
        return teleMode;
    }

    public void setTeleMode(int teleMode) {
        WorldPartition.checkOwner();
        this.teleMode = teleMode;
    }

    public boolean isInCrystallize() {
        WorldPartition.checkOwner();
        return inCrystallize;
    }

    public void setInCrystallize(boolean inCrystallize) {
        WorldPartition.checkOwner();
        this.inCrystallize = inCrystallize;
    }

    public boolean isCrafting() {
        WorldPartition.checkOwner();
        return crafting;
    }

    public void setCrafting(boolean crafting) {
        WorldPartition.checkOwner();
        this.crafting = crafting;
    }

    public long getOfflineStartTime() {
        WorldPartition.checkOwner();
        return offlineStartTime;
    }

    public void setOfflineStartTime(long offlineStartTime) {
        WorldPartition.checkOwner();
        this.offlineStartTime = offlineStartTime;
    }

    @Override
    protected Transform transformation() {
        return transformation;
    }

    public Map<Integer, PremiumItem> getPremiumItemList() {
        WorldPartition.checkOwner();
        return Int2ObjectMaps.unmodifiable(new Int2ObjectLinkedOpenHashMap<>(premiumItemList));
    }

    /** @return the premium item of the given number, {@code null} for none */
    public PremiumItem getPremiumItem(int itemNum) {
        WorldPartition.checkOwner();
        return premiumItemList.get(itemNum);
    }

    public boolean hasPremiumItems() {
        WorldPartition.checkOwner();
        return !premiumItemList.isEmpty();
    }

    public void removePremiumItem(int itemNum) {
        WorldPartition.checkOwner();
        premiumItemList.remove(itemNum);
    }

    public boolean isSitting() {
        WorldPartition.checkOwner();
        return sitting;
    }

    public Location getLastLocation() {
        WorldPartition.checkOwner();
        return lastLocation;
    }

    public Location getLastServerPosition() {
        WorldPartition.checkOwner();
        return lastServerPosition;
    }

    public int getRecomHave() {
        WorldPartition.checkOwner();
        return recomHave;
    }

    public int getRecomLeft() {
        WorldPartition.checkOwner();
        return recomLeft;
    }

    public boolean isRecoTwoHoursGiven() {
        WorldPartition.checkOwner();
        return recoTwoHoursGiven;
    }

    public void setRecoTwoHoursGiven(boolean recoTwoHoursGiven) {
        WorldPartition.checkOwner();
        this.recoTwoHoursGiven = recoTwoHoursGiven;
    }

    public PlayerInventory getInventory() {
        WorldPartition.checkOwner();
        return inventory;
    }

    public PlayerFreight getFreight() {
        WorldPartition.checkOwner();
        return freight;
    }

    public PlayerWarehouse getWarehouse() {
        WorldPartition.checkOwner();
        return warehouse;
    }

    public PrivateStoreType getPrivateStoreType() {
        WorldPartition.checkOwner();
        return privateStoreType;
    }

    public TradeList getActiveTradeList() {
        WorldPartition.checkOwner();
        return activeTradeList;
    }

    public void setActiveTradeList(TradeList activeTradeList) {
        WorldPartition.checkOwner();
        this.activeTradeList = activeTradeList;
    }

    public ItemContainer getActiveWarehouse() {
        WorldPartition.checkOwner();
        return activeWarehouse;
    }

    public void setActiveWarehouse(ItemContainer activeWarehouse) {
        WorldPartition.checkOwner();
        this.activeWarehouse = activeWarehouse;
    }

    public String getStoreName() {
        WorldPartition.checkOwner();
        return storeName;
    }

    public PreparedListContainer getMultiSell() {
        WorldPartition.checkOwner();
        return multiSell;
    }

    public void setMultiSell(PreparedListContainer multiSell) {
        WorldPartition.checkOwner();
        this.multiSell = multiSell;
    }

    public int getNewbie() {
        WorldPartition.checkOwner();
        return newbie;
    }

    public void setNewbie(int newbie) {
        WorldPartition.checkOwner();
        this.newbie = newbie;
    }

    public boolean isNoble() {
        WorldPartition.checkOwner();
        return noble;
    }

    public boolean isHero() {
        WorldPartition.checkOwner();
        return hero;
    }

    public void setPremiumStatus(boolean premiumStatus) {
        WorldPartition.checkOwner();
        this.premiumStatus = premiumStatus;
    }

    public boolean isGood() {
        WorldPartition.checkOwner();
        return good;
    }

    public boolean isEvil() {
        WorldPartition.checkOwner();
        return evil;
    }

    public Npc getLastFolkNPC() {
        WorldPartition.checkOwner();
        return lastFolkNPC;
    }

    public void setLastFolkNPC(Npc lastFolkNPC) {
        WorldPartition.checkOwner();
        this.lastFolkNPC = lastFolkNPC;
    }

    public int getLastQuestNpcObject() {
        WorldPartition.checkOwner();
        return lastQuestNpcObject;
    }

    public void setLastQuestNpcObject(int lastQuestNpcObject) {
        WorldPartition.checkOwner();
        this.lastQuestNpcObject = lastQuestNpcObject;
    }

    public MacroList getMacros() {
        WorldPartition.checkOwner();
        return macros;
    }

    /** @return a copy of the three henna slots, {@code null} for an empty slot */
    public Henna[] getHennaList() {
        WorldPartition.checkOwner();
        return hennaList.clone();
    }

    public int getHennaStatSTR() {
        WorldPartition.checkOwner();
        return hennaStatSTR;
    }

    public int getHennaStatINT() {
        WorldPartition.checkOwner();
        return hennaStatINT;
    }

    public int getHennaStatDEX() {
        WorldPartition.checkOwner();
        return hennaStatDEX;
    }

    public int getHennaStatMEN() {
        WorldPartition.checkOwner();
        return hennaStatMEN;
    }

    public int getHennaStatWIT() {
        WorldPartition.checkOwner();
        return hennaStatWIT;
    }

    public int getHennaStatCON() {
        WorldPartition.checkOwner();
        return hennaStatCON;
    }

    public Decoy getDecoy() {
        WorldPartition.checkOwner();
        return decoy;
    }

    public void setDecoy(Decoy decoy) {
        WorldPartition.checkOwner();
        this.decoy = decoy;
    }

    public Trap getTrap() {
        WorldPartition.checkOwner();
        return trap;
    }

    public void setTrap(Trap trap) {
        WorldPartition.checkOwner();
        this.trap = trap;
    }

    public int getAgathionId() {
        WorldPartition.checkOwner();
        return agathionId;
    }

    public void setAgathionId(int agathionId) {
        WorldPartition.checkOwner();
        this.agathionId = agathionId;
    }

    /** @return a snapshot of the tamed beasts, {@code null} when the player never had one */
    public Collection<TamedBeast> getTrainedBeasts() {
        WorldPartition.checkOwner();
        return trainedBeasts == null ? null : ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(trainedBeasts));
    }

    public void removeTrainedBeast(TamedBeast tamedBeast) {
        WorldPartition.checkOwner();
        if (trainedBeasts != null) {
            trainedBeasts.remove(tamedBeast);
        }
    }

    public boolean isMinimapAllowed() {
        WorldPartition.checkOwner();
        return minimapAllowed;
    }

    public void setMinimapAllowed(boolean minimapAllowed) {
        WorldPartition.checkOwner();
        this.minimapAllowed = minimapAllowed;
    }

    public Radar getRadar() {
        WorldPartition.checkOwner();
        return radar;
    }

    public int getPartyRoom() {
        WorldPartition.checkOwner();
        return partyRoom;
    }

    public void setPartyRoom(int partyRoom) {
        WorldPartition.checkOwner();
        this.partyRoom = partyRoom;
    }

    public int getClanId() {
        WorldPartition.checkOwner();
        return clanId;
    }

    public Clan getClan() {
        WorldPartition.checkOwner();
        return clan;
    }

    public int getApprentice() {
        WorldPartition.checkOwner();
        return apprentice;
    }

    public void setApprentice(int apprentice) {
        WorldPartition.checkOwner();
        this.apprentice = apprentice;
    }

    public int getSponsor() {
        WorldPartition.checkOwner();
        return sponsor;
    }

    public void setSponsor(int sponsor) {
        WorldPartition.checkOwner();
        this.sponsor = sponsor;
    }

    public long getClanJoinExpiryTime() {
        WorldPartition.checkOwner();
        return clanJoinExpiryTime;
    }

    public void setClanJoinExpiryTime(long clanJoinExpiryTime) {
        WorldPartition.checkOwner();
        this.clanJoinExpiryTime = clanJoinExpiryTime;
    }

    public long getClanCreateExpiryTime() {
        WorldPartition.checkOwner();
        return clanCreateExpiryTime;
    }

    public void setClanCreateExpiryTime(long clanCreateExpiryTime) {
        WorldPartition.checkOwner();
        this.clanCreateExpiryTime = clanCreateExpiryTime;
    }

    public int getPowerGrade() {
        WorldPartition.checkOwner();
        return powerGrade;
    }

    public void setPowerGrade(int powerGrade) {
        WorldPartition.checkOwner();
        this.powerGrade = powerGrade;
    }

    public ClanPrivileges getClanPrivileges() {
        WorldPartition.checkOwner();
        return clanPrivileges;
    }

    public int getPledgeClass() {
        WorldPartition.checkOwner();
        return pledgeClass;
    }

    public int getPledgeType() {
        WorldPartition.checkOwner();
        return pledgeType;
    }

    public void setPledgeType(int pledgeType) {
        WorldPartition.checkOwner();
        this.pledgeType = pledgeType;
    }

    public int getLvlJoinedAcademy() {
        WorldPartition.checkOwner();
        return lvlJoinedAcademy;
    }

    public void setLvlJoinedAcademy(int lvlJoinedAcademy) {
        WorldPartition.checkOwner();
        this.lvlJoinedAcademy = lvlJoinedAcademy;
    }

    public int getWantsPeace() {
        WorldPartition.checkOwner();
        return wantsPeace;
    }

    public void setWantsPeace(int wantsPeace) {
        WorldPartition.checkOwner();
        this.wantsPeace = wantsPeace;
    }

    public int getDeathPenaltyBuffLevel() {
        WorldPartition.checkOwner();
        return deathPenaltyBuffLevel;
    }

    public void setDeathPenaltyBuffLevel(int deathPenaltyBuffLevel) {
        WorldPartition.checkOwner();
        this.deathPenaltyBuffLevel = deathPenaltyBuffLevel;
    }

    public int getChargedSouls() {
        WorldPartition.checkOwner();
        return chargedSouls;
    }

    public Location getCurrentSkillWorldPosition() {
        WorldPartition.checkOwner();
        return currentSkillWorldPosition;
    }

    public void setCurrentSkillWorldPosition(Location currentSkillWorldPosition) {
        WorldPartition.checkOwner();
        this.currentSkillWorldPosition = currentSkillWorldPosition;
    }

    public boolean isSilenceMode() {
        WorldPartition.checkOwner();
        return silenceMode;
    }

    public void setDietMode(boolean dietMode) {
        WorldPartition.checkOwner();
        this.dietMode = dietMode;
    }

    public void setTradeRefusal(boolean tradeRefusal) {
        WorldPartition.checkOwner();
        this.tradeRefusal = tradeRefusal;
    }

    public void setExchangeRefusal(boolean exchangeRefusal) {
        WorldPartition.checkOwner();
        this.exchangeRefusal = exchangeRefusal;
    }

    public Party getParty() {
        WorldPartition.checkOwner();
        return party;
    }

    public void setParty(Party party) {
        WorldPartition.checkOwner();
        this.party = party;
    }

    public PartyDistributionType getPartyDistributionType() {
        WorldPartition.checkOwner();
        return partyDistributionType;
    }

    public void setPartyDistributionType(PartyDistributionType partyDistributionType) {
        WorldPartition.checkOwner();
        this.partyDistributionType = partyDistributionType;
    }

    public void setActiveRequester(Player activeRequester) {
        WorldPartition.checkOwner();
        this.activeRequester = activeRequester;
    }

    public RequestPartner getRequest() {
        WorldPartition.checkOwner();
        return request;
    }

    public boolean isFakeDeath() {
        WorldPartition.checkOwner();
        return fakeDeath;
    }

    public void setFakeDeath(boolean fakeDeath) {
        WorldPartition.checkOwner();
        this.fakeDeath = fakeDeath;
    }

    public Weapon getFistsWeaponItem() {
        WorldPartition.checkOwner();
        return fistsWeaponItem;
    }

    public void setFistsWeaponItem(Weapon fistsWeaponItem) {
        WorldPartition.checkOwner();
        this.fistsWeaponItem = fistsWeaponItem;
    }

    /** @return the other characters of the account by object id, in the order of the character list */
    public Map<Integer, String> getAccountChars() {
        WorldPartition.checkOwner();
        return Int2ObjectMaps.unmodifiable(new Int2ObjectLinkedOpenHashMap<>(accountChars));
    }

    public int getExpertiseArmorPenalty() {
        WorldPartition.checkOwner();
        return expertiseArmorPenalty;
    }

    public int getExpertiseWeaponPenalty() {
        WorldPartition.checkOwner();
        return expertiseWeaponPenalty;
    }

    public int getExpertisePenaltyBonus() {
        WorldPartition.checkOwner();
        return expertisePenaltyBonus;
    }

    public void setExpertisePenaltyBonus(int expertisePenaltyBonus) {
        WorldPartition.checkOwner();
        this.expertisePenaltyBonus = expertisePenaltyBonus;
    }

    public boolean isEnchanting() {
        WorldPartition.checkOwner();
        return enchanting;
    }

    public void setEnchanting(boolean enchanting) {
        WorldPartition.checkOwner();
        this.enchanting = enchanting;
    }

    public int getActiveEnchantItemId() {
        WorldPartition.checkOwner();
        return activeEnchantItemId;
    }

    public int getActiveEnchantSupportItemId() {
        WorldPartition.checkOwner();
        return activeEnchantSupportItemId;
    }

    public void setActiveEnchantSupportItemId(int activeEnchantSupportItemId) {
        WorldPartition.checkOwner();
        this.activeEnchantSupportItemId = activeEnchantSupportItemId;
    }

    public int getActiveEnchantAttrItemId() {
        WorldPartition.checkOwner();
        return activeEnchantAttrItemId;
    }

    public void setActiveEnchantAttrItemId(int activeEnchantAttrItemId) {
        WorldPartition.checkOwner();
        this.activeEnchantAttrItemId = activeEnchantAttrItemId;
    }

    public long getActiveEnchantTimestamp() {
        WorldPartition.checkOwner();
        return activeEnchantTimestamp;
    }

    public void setActiveEnchantTimestamp(long activeEnchantTimestamp) {
        WorldPartition.checkOwner();
        this.activeEnchantTimestamp = activeEnchantTimestamp;
    }

    public boolean isInventoryDisabled() {
        WorldPartition.checkOwner();
        return inventoryDisabled;
    }

    public Set<Integer> getAutoSoulShot() {
        WorldPartition.checkOwner();
        return IntSets.unmodifiable(new IntLinkedOpenHashSet(autoSoulShot));
    }

    public boolean hasAutoSoulShot(int itemId) {
        WorldPartition.checkOwner();
        return autoSoulShot.contains(itemId);
    }

    public boolean hasAutoSoulShots() {
        WorldPartition.checkOwner();
        return !autoSoulShot.isEmpty();
    }

    public void setRegisteredOnEvent(boolean registeredOnEvent) {
        WorldPartition.checkOwner();
        this.registeredOnEvent = registeredOnEvent;
    }

    public boolean isOnSoloEvent() {
        WorldPartition.checkOwner();
        return onSoloEvent;
    }

    public void setOnSoloEvent(boolean onSoloEvent) {
        WorldPartition.checkOwner();
        this.onSoloEvent = onSoloEvent;
    }

    public boolean isOnEvent() {
        WorldPartition.checkOwner();
        return onEvent;
    }

    public void setOnEvent(boolean onEvent) {
        WorldPartition.checkOwner();
        this.onEvent = onEvent;
    }

    public void setBlockCheckerArena(byte blockCheckerArena) {
        WorldPartition.checkOwner();
        this.blockCheckerArena = blockCheckerArena;
    }

    public KrateiArena getKrateiArena() {
        WorldPartition.checkOwner();
        return krateiArena;
    }

    public void setKrateiArena(KrateiArena krateiArena) {
        WorldPartition.checkOwner();
        this.krateiArena = krateiArena;
    }

    public BlockList getBlockList() {
        WorldPartition.checkOwner();
        return blockList;
    }

    public Item getLure() {
        WorldPartition.checkOwner();
        return lure;
    }

    public void setLure(Item lure) {
        WorldPartition.checkOwner();
        this.lure = lure;
    }

    public Fishing getFishCombat() {
        WorldPartition.checkOwner();
        return fishCombat;
    }

    public boolean isFishing() {
        WorldPartition.checkOwner();
        return fishing;
    }

    public void setFishing(boolean fishing) {
        WorldPartition.checkOwner();
        this.fishing = fishing;
    }

    public int getFishX() {
        WorldPartition.checkOwner();
        return fishX;
    }

    public int getFishY() {
        WorldPartition.checkOwner();
        return fishY;
    }

    public int getFishZ() {
        WorldPartition.checkOwner();
        return fishZ;
    }

    public int getLastHtmlActionOriginId() {
        WorldPartition.checkOwner();
        return lastHtmlActionOriginId;
    }

    public SkillUseHolder getCurrentSkill() {
        WorldPartition.checkOwner();
        return currentSkill;
    }

    public SkillUseHolder getCurrentPetSkill() {
        WorldPartition.checkOwner();
        return currentPetSkill;
    }

    public SkillUseHolder getQueuedSkill() {
        WorldPartition.checkOwner();
        return queuedSkill;
    }

    public int getCursedWeaponEquippedId() {
        WorldPartition.checkOwner();
        return cursedWeaponEquippedId;
    }

    public void setCursedWeaponEquippedId(int cursedWeaponEquippedId) {
        WorldPartition.checkOwner();
        this.cursedWeaponEquippedId = cursedWeaponEquippedId;
    }

    public boolean isCombatFlagEquipped() {
        WorldPartition.checkOwner();
        return combatFlagEquipped;
    }

    public void setCombatFlagEquipped(boolean combatFlagEquipped) {
        WorldPartition.checkOwner();
        this.combatFlagEquipped = combatFlagEquipped;
    }

    public void setCanRevive(boolean canRevive) {
        WorldPartition.checkOwner();
        this.canRevive = canRevive;
    }

    public boolean isRevivingPet() {
        WorldPartition.checkOwner();
        return revivingPet;
    }

    public int getClientX() {
        WorldPartition.checkOwner();
        return clientX;
    }

    public void setClientX(int clientX) {
        WorldPartition.checkOwner();
        this.clientX = clientX;
    }

    public int getClientY() {
        WorldPartition.checkOwner();
        return clientY;
    }

    public void setClientY(int clientY) {
        WorldPartition.checkOwner();
        this.clientY = clientY;
    }

    public int getClientZ() {
        WorldPartition.checkOwner();
        return clientZ;
    }

    public void setClientZ(int clientZ) {
        WorldPartition.checkOwner();
        this.clientZ = clientZ;
    }

    public int getClientHeading() {
        WorldPartition.checkOwner();
        return clientHeading;
    }

    public void setClientHeading(int clientHeading) {
        WorldPartition.checkOwner();
        this.clientHeading = clientHeading;
    }

    public int getMultiSocialTarget() {
        WorldPartition.checkOwner();
        return multiSocialTarget;
    }

    public int getMultiSociaAction() {
        WorldPartition.checkOwner();
        return multiSociaAction;
    }

    public MovieHolder getMovieHolder() {
        WorldPartition.checkOwner();
        return movieHolder;
    }

    public void setMovieHolder(MovieHolder movieHolder) {
        WorldPartition.checkOwner();
        this.movieHolder = movieHolder;
    }

    public String getAdminConfirmCmd() {
        WorldPartition.checkOwner();
        return adminConfirmCmd;
    }

    public void setAdminConfirmCmd(String adminConfirmCmd) {
        WorldPartition.checkOwner();
        this.adminConfirmCmd = adminConfirmCmd;
    }

    public long getPvpFlagLasts() {
        WorldPartition.checkOwner();
        return pvpFlagLasts;
    }

    public void setPvpFlagLasts(long pvpFlagLasts) {
        WorldPartition.checkOwner();
        this.pvpFlagLasts = pvpFlagLasts;
    }

    public long getNotMoveUntil() {
        WorldPartition.checkOwner();
        return notMoveUntil;
    }

    public void setServitorShare(Map<Stat, Double> servitorShare) {
        WorldPartition.checkOwner();
        this.servitorShare = servitorShare;
    }

    public boolean isMarried() {
        WorldPartition.checkOwner();
        return married;
    }

    public void setMarried(boolean married) {
        WorldPartition.checkOwner();
        this.married = married;
    }

    public int getPartnerId() {
        WorldPartition.checkOwner();
        return partnerId;
    }

    public void setPartnerId(int partnerId) {
        WorldPartition.checkOwner();
        this.partnerId = partnerId;
    }

    public long getCoupleId() {
        WorldPartition.checkOwner();
        return coupleId;
    }

    public void setCoupleId(long coupleId) {
        WorldPartition.checkOwner();
        this.coupleId = coupleId;
    }

    public boolean isEngageRequest() {
        WorldPartition.checkOwner();
        return engageRequest;
    }

    public int getEngageId() {
        WorldPartition.checkOwner();
        return engageId;
    }

    public boolean isMarryRequest() {
        WorldPartition.checkOwner();
        return marryRequest;
    }

    public void setMarryRequest(boolean marryRequest) {
        WorldPartition.checkOwner();
        this.marryRequest = marryRequest;
    }

    public boolean isMarryAccepted() {
        WorldPartition.checkOwner();
        return marryAccepted;
    }

    public void setMarryAccepted(boolean marryAccepted) {
        WorldPartition.checkOwner();
        this.marryAccepted = marryAccepted;
    }

    public int getLastPetitionGmId() {
        WorldPartition.checkOwner();
        return lastPetitionGmId;
    }

    public void setLastPetitionGmId(int lastPetitionGmId) {
        WorldPartition.checkOwner();
        this.lastPetitionGmId = lastPetitionGmId;
    }

    public AutoPlaySettingsHolder getAutoPlaySettings() {
        WorldPartition.checkOwner();
        return autoPlaySettings;
    }

    public AutoUseSettingsHolder getAutoUseSettings() {
        WorldPartition.checkOwner();
        return autoUseSettings;
    }

    public void startPvPFlag() {
        WorldPartition.checkOwner();
        updatePvPFlag(1);
        PvpFlagTaskManager.getInstance().add(this);
    }

    public void stopPvpRegTask() {
        WorldPartition.checkOwner();
        PvpFlagTaskManager.getInstance().remove(this);
    }

    public void stopPvPFlag() {
        WorldPartition.checkOwner();
        stopPvpRegTask();
        updatePvPFlag(0);
    }

    private boolean married = false;

    private int partnerId = 0;

    private long coupleId = 0;

    private boolean engageRequest = false;

    private int engageId = 0;

    private boolean marryRequest = false;

    private boolean marryAccepted = false;

    private static final String GAME_POINTS_VAR = "PRIME_POINTS"; // Keep compatibility with later clients.

    // The GM who answered the last petition, for the petition feedback; 0 for none.
    private int lastPetitionGmId = 0;

    private boolean hasCharmOfCourage = false;

    private final AutoPlaySettingsHolder autoPlaySettings = new AutoPlaySettingsHolder();

    private final AutoUseSettingsHolder autoUseSettings = new AutoUseSettingsHolder();

    private boolean autoPlaying;

    private final List<QuestTimer> questTimers = new ArrayList<>();
    private final List<TimerHolder<?>> timerHolders = new ArrayList<>();

    private boolean sellingBuffs = false;

    private final ObjectArrayList<SellBuffHolder> sellBuffs = new ObjectArrayList<>();

    public boolean isSellingBuffs() {
        WorldPartition.checkOwner();
        return sellingBuffs;
    }

    public void setSellingBuffs(boolean sellingBuffs) {
        WorldPartition.checkOwner();
        this.sellingBuffs = sellingBuffs;
    }

    public List<SellBuffHolder> getSellingBuffs() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(sellBuffs));
    }

    public boolean hasSellingBuffs() {
        WorldPartition.checkOwner();
        return !sellBuffs.isEmpty();
    }

    public int getSellingBuffCount() {
        WorldPartition.checkOwner();
        return sellBuffs.size();
    }

    public SellBuffHolder getSellingBuff(int skillId) {
        WorldPartition.checkOwner();
        for (SellBuffHolder holder : sellBuffs) {
            if (holder.getSkillId() == skillId) {
                return holder;
            }
        }

        return null;
    }

    public void addSellingBuff(SellBuffHolder holder) {
        WorldPartition.checkOwner();
        sellBuffs.add(holder);
    }

    public boolean removeSellingBuff(SellBuffHolder holder) {
        WorldPartition.checkOwner();
        return sellBuffs.remove(holder);
    }

    /** @return the new player, or {@code null} when the insert failed */
    public static Player create(PlayerTemplate template, UUID accountId, String name, PlayerAppearance app) {
        WorldPartition.checkOwner();
        final Player player = new Player(template, accountId, app);

        player.setName(name);

        player.setCreateDate(ClockBridge.calendar());

        player.setBaseClass(player.getPlayerClass());

        player.setNewbie(1);

        player.setRecomLeft(20);

        return player.createDb() ? player : null;
    }

    /** @return {@code null} for a player without a client (an offline trader restored at start) */
    public String getAccountName() {
        WorldPartition.checkOwner();
        return client == null ? null : client.getAccountName();
    }

    public int getRelation(Player target) {
        WorldPartition.checkOwner();
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
            for (int i = 0; i < party.getMemberCount(); i++) {
                if (party.getMember(i) != this) {
                    continue;
                }

                switch (i) {
                    case 0: {
                        result |= RelationChanged.RELATION_PARTYLEADER;
                        break;
                    }
                    case 1: {
                        result |= RelationChanged.RELATION_PARTY4;
                        break;
                    }
                    case 2: {
                        result |= RelationChanged.RELATION_PARTY3
                                + RelationChanged.RELATION_PARTY2
                                + RelationChanged.RELATION_PARTY1;
                        break;
                    }
                    case 3: {
                        result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY2;
                        break;
                    }
                    case 4: {
                        result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY1;
                        break;
                    }
                    case 5: {
                        result |= RelationChanged.RELATION_PARTY3;
                        break;
                    }
                    case 6: {
                        result |= RelationChanged.RELATION_PARTY2 + RelationChanged.RELATION_PARTY1;
                        break;
                    }
                    case 7: {
                        result |= RelationChanged.RELATION_PARTY2;
                        break;
                    }
                    case 8: {
                        result |= RelationChanged.RELATION_PARTY1;
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

    public static Player load(int objectId) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return (PlayerStat) uncheckedStat();
    }

    @Override
    public void initCharStat() {
        WorldPartition.checkOwner();
        setStat(new PlayerStat(this, statAccess()));
    }

    @Override
    public PlayerStatus getStatus() {
        WorldPartition.checkOwner();
        return (PlayerStatus) uncheckedStatus();
    }

    @Override
    public void initCharStatus() {
        WorldPartition.checkOwner();
        setStatus(new PlayerStatus(this));
    }

    public PlayerTemplate getBaseTemplate() {
        WorldPartition.checkOwner();
        return PlayerTemplateData.getInstance().getTemplate(baseClass);
    }

    @Override
    public PlayerTemplate getTemplate() {
        WorldPartition.checkOwner();
        return (PlayerTemplate) uncheckedTemplate();
    }

    public void setTemplate(PlayerClass newclass) {
        WorldPartition.checkOwner();
        super.setTemplate(PlayerTemplateData.getInstance().getTemplate(newclass));
    }

    @Override
    protected CreatureAI initAI() {
        return new PlayerAI(this);
    }

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return uncheckedStat().getLevel();
    }

    @Override
    public double getLevelMod() {
        WorldPartition.checkOwner();
        if (isTransformed()) {
            final double levelMod = transformation.getLevelMod(this);
            if (levelMod > -1) {
                return levelMod;
            }
        }

        return super.getLevelMod();
    }

    public void setBaseClass(int baseClass) {
        WorldPartition.checkOwner();
        this.baseClass = baseClass;
    }

    public void setBaseClass(PlayerClass playerClass) {
        WorldPartition.checkOwner();
        baseClass = playerClass.getId();
    }

    public boolean isInStoreMode() {
        WorldPartition.checkOwner();
        return privateStoreType != PrivateStoreType.NONE;
    }

    public Collection<RecipeList> getCommonRecipeBook() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(commonRecipeBook.values()));
    }

    public Collection<RecipeList> getDwarvenRecipeBook() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(dwarvenRecipeBook.values()));
    }

    public int getCommonRecipeCount() {
        WorldPartition.checkOwner();
        return commonRecipeBook.size();
    }

    public int getDwarvenRecipeCount() {
        WorldPartition.checkOwner();
        return dwarvenRecipeBook.size();
    }

    public void registerCommonRecipeList(RecipeList recipe, boolean saveToDb) {
        WorldPartition.checkOwner();
        commonRecipeBook.put(recipe.getId(), recipe);

        if (saveToDb) {
            insertNewRecipeData(recipe.getId(), false);
        }
    }

    public void registerDwarvenRecipeList(RecipeList recipe, boolean saveToDb) {
        WorldPartition.checkOwner();
        dwarvenRecipeBook.put(recipe.getId(), recipe);

        if (saveToDb) {
            insertNewRecipeData(recipe.getId(), true);
        }
    }

    public boolean hasRecipeList(int recipeId) {
        WorldPartition.checkOwner();
        return dwarvenRecipeBook.containsKey(recipeId) || commonRecipeBook.containsKey(recipeId);
    }

    public void unregisterRecipeList(int recipeId) {
        WorldPartition.checkOwner();
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
        try {
            PlayerPersistenceManager.getInstance()
                    .insertRecipe(
                            getObjectId(),
                            CharacterRecipe.builder()
                                    .recipeId(recipeId)
                                    .classIndex(isDwarf ? classIndex : 0)
                                    .dwarven(isDwarf)
                                    .build());
        } catch (DataAccessException e) {
            log.warn("Could not insert recipe: {} from character {}", recipeId, getObjectId(), e);
        }
    }

    private void deleteRecipeData(int recipeId, boolean isDwarf) {
        try {
            PlayerPersistenceManager.getInstance().deleteRecipe(getObjectId(), isDwarf ? classIndex : 0, recipeId);
        } catch (DataAccessException e) {
            log.warn("Could not delete recipe: {} from character {}", recipeId, getObjectId(), e);
        }
    }

    public QuestState getQuestState(String quest) {
        WorldPartition.checkOwner();
        return quests.get(quest);
    }

    public void setQuestState(QuestState qs) {
        WorldPartition.checkOwner();
        quests.put(qs.getQuestName(), qs);
    }

    public boolean hasQuestState(String quest) {
        WorldPartition.checkOwner();
        return quests.containsKey(quest);
    }

    public void delQuestState(String quest) {
        WorldPartition.checkOwner();
        quests.remove(quest);
    }

    public List<Quest> getAllActiveQuests() {
        WorldPartition.checkOwner();
        final List<Quest> quests = new ArrayList<>();
        for (QuestState qs : this.quests.values()) {
            if ((qs == null) || (qs.getQuest() == null) || !qs.isStarted()) {
                continue;
            }

            final int questId = qs.getQuest().getId();
            if ((questId > 19999) || (questId < 1)) {
                continue;
            }

            quests.add(qs.getQuest());
        }

        return quests;
    }

    public void processScriptEvent(String scriptName, String event) {
        WorldPartition.checkOwner();
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

    private final ObjectLinkedOpenHashSet<QuestState> notifyQuestOfDeath = new ObjectLinkedOpenHashSet<>();

    public Collection<QuestState> getNotifyQuestOfDeath() {
        WorldPartition.checkOwner();
        return ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(notifyQuestOfDeath));
    }

    public void addNotifyQuestOfDeath(QuestState qs) {
        WorldPartition.checkOwner();
        if (qs == null) {
            return;
        }

        notifyQuestOfDeath.add(qs);
    }

    public void removeNotifyQuestOfDeath(QuestState qs) {
        WorldPartition.checkOwner();
        if (qs == null) {
            return;
        }

        notifyQuestOfDeath.remove(qs);
    }

    public boolean isNotifyQuestOfDeathEmpty() {
        WorldPartition.checkOwner();
        return notifyQuestOfDeath.isEmpty();
    }

    private void restoreShortcuts() {
        shortcuts.restoreMe();
    }

    public Collection<Shortcut> getAllShortcuts() {
        WorldPartition.checkOwner();
        return shortcuts.getAllShortcuts();
    }

    public Shortcut getShortcut(int slot, int page) {
        WorldPartition.checkOwner();
        return shortcuts.getShortcut(slot, page);
    }

    public void registerShortcut(Shortcut shortcut) {
        WorldPartition.checkOwner();
        shortcuts.registerShortcut(shortcut);
    }

    public void updateShortcuts(int skillId, int skillLevel) {
        WorldPartition.checkOwner();
        shortcuts.updateShortcuts(skillId, skillLevel);
    }

    public void deleteShortcut(int slot, int page) {
        WorldPartition.checkOwner();
        shortcuts.deleteShortcut(slot, page);
    }

    public void registerMacro(Macro macro) {
        WorldPartition.checkOwner();
        macros.registerMacro(macro);
    }

    public void deleteMacro(int id) {
        WorldPartition.checkOwner();
        macros.deleteMacro(id);
    }

    public boolean isRegisteredOnThisSiegeField(int value) {
        WorldPartition.checkOwner();
        return (siegeSide == value) || ((siegeSide >= 81) && (siegeSide <= 89));
    }

    public boolean isSiegeFriend(WorldObject target) {
        WorldPartition.checkOwner();
        if ((siegeState == 0) || !isInsideZone(ZoneId.SIEGE)) {
            return false;
        }

        final Castle castle = CastleManager.getInstance().getCastleById(siegeSide);
        if (castle == null) {
            return false;
        }

        final Player targetPlayer = target.asPlayer();
        if ((targetPlayer == null) || (targetPlayer == this)) {
            return false;
        }

        if ((targetPlayer.getSiegeSide() != siegeSide) || (siegeState != targetPlayer.getSiegeState())) {
            return false;
        }

        if (siegeState == 1) {
            if (!castle.isFirstMidVictory() && (siegeState == targetPlayer.getSiegeState())) {
                return true;
            }

            return castle.getOwner() == null;
        }

        return true;
    }

    public void setPvpFlag(int pvpFlag) {
        WorldPartition.checkOwner();
        this.pvpFlag = (byte) pvpFlag;
    }

    @Override
    public void updatePvPFlag(int value) {
        WorldPartition.checkOwner();
        if (pvpFlag == value) {
            return;
        }

        setPvpFlag(value);

        updateUserInfo();

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
        WorldPartition.checkOwner();
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

    public boolean hasDwarvenCraft() {
        WorldPartition.checkOwner();
        return getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()) >= 1;
    }

    public int getDwarvenCraft() {
        WorldPartition.checkOwner();
        return getSkillLevel(CommonSkill.CREATE_DWARVEN.getId());
    }

    public boolean hasCommonCraft() {
        WorldPartition.checkOwner();
        return getSkillLevel(CommonSkill.CREATE_COMMON.getId()) >= 1;
    }

    public int getCommonCraft() {
        WorldPartition.checkOwner();
        return getSkillLevel(CommonSkill.CREATE_COMMON.getId());
    }

    public void setPkKills(int pkKills) {
        WorldPartition.checkOwner();
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

    protected void incRecomHave() {
        if (recomHave < 255) {
            recomHave++;
        }
    }

    public void setRecomHave(int value) {
        WorldPartition.checkOwner();
        recomHave = Math.clamp(value, 0, 255);
    }

    public void setRecomLeft(int value) {
        WorldPartition.checkOwner();
        recomLeft = Math.clamp(value, 0, 255);
    }

    protected void decRecomLeft() {
        if (recomLeft > 0) {
            recomLeft--;
        }
    }

    public void giveRecom(Player target) {
        WorldPartition.checkOwner();
        target.incRecomHave();
        decRecomLeft();
    }

    @Override
    public void setKarma(int value) {
        WorldPartition.checkOwner();
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
            setKarmaFlag();
        }

        super.setKarma(karma);
        broadcastKarma();
    }

    public int getWeightPenalty() {
        WorldPartition.checkOwner();
        return dietMode ? 0 : curWeightPenalty;
    }

    public void refreshOverloaded() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        if (!PlayerConfig.EXPERTISE_PENALTY) {
            return;
        }

        final int expertiseLevel = getExpertiseLevel();
        int armorPenalty = 0;
        int weaponPenalty = 0;
        int crystaltype;
        for (int slot = 0; slot < Inventory.PAPERDOLL_TOTALSLOTS; slot++) {
            final Item item = inventory.getPaperdollItem(slot);
            if ((item != null)
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
        WorldPartition.checkOwner();
        if (item == null) {
            return;
        }

        if (item.getOwnerId() != getObjectId()) {
            return;
        }

        final ItemLocation itemLocation = item.getItemLocation();
        if ((itemLocation != ItemLocation.INVENTORY) && (itemLocation != ItemLocation.PAPERDOLL)) {
            return;
        }

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

                item.decreaseMana(false);

                if ((bodyPart == BodyPart.R_HAND) || (bodyPart == BodyPart.LR_HAND)) {
                    rechargeShots(true, true);
                }

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

    public void setPvpKills(int pvpKills) {
        WorldPartition.checkOwner();
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

    public void setFame(int fame) {
        WorldPartition.checkOwner();
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

    public PlayerClass getPlayerClass() {
        WorldPartition.checkOwner();
        return getTemplate().getPlayerClass();
    }

    public void setPlayerClass(int id) {
        WorldPartition.checkOwner();
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

                final SystemMessage msg = new SystemMessage(SystemMessageId.CLAN_MEMBER_S1_HAS_BEEN_EXPELLED);
                msg.addPcName(this);
                clan.broadcastToOnlineMembers(msg);
                clan.broadcastToOnlineMembers(new PledgeShowMemberListDelete(getName()));
                clan.removeClanMember(getObjectId(), 0);
                sendPacket(
                        SystemMessageId
                                .CONGRATULATIONS_YOU_WILL_NOW_GRADUATE_FROM_THE_CLAN_ACADEMY_AND_LEAVE_YOUR_CURRENT_CLAN_AS_A_GRADUATE_OF_THE_ACADEMY_YOU_CAN_IMMEDIATELY_JOIN_A_CLAN_AS_A_REGULAR_MEMBER_WITHOUT_BEING_SUBJECT_TO_ANY_PENALTIES);

                inventory.addItem(ItemProcessType.REWARD, 8181, 1, this, null); // give academy circlet
            }

            if (isSubClassActive()) {
                subClasses.get(classIndex).setPlayerClass(id);
            }

            setTarget(this);
            broadcastSkillPacket(new MagicSkillUse(this, 5103, 1, 0, 0), this);
            setClassTemplate(id);
            if (getPlayerClass().level() == 3) {
                sendPacket(SystemMessageId.CONGRATULATIONS_YOU_VE_COMPLETED_YOUR_THIRD_CLASS_TRANSFER_QUEST);
            } else {
                sendPacket(SystemMessageId.CONGRATULATIONS_YOU_VE_COMPLETED_A_CLASS_TRANSFER);
            }

            for (int slot = 1; slot < 4; slot++) {
                final Henna henna = getHenna(slot);
                if ((henna != null) && !henna.isAllowedClass(getPlayerClass())) {
                    removeHenna(slot);
                }
            }

            if (isInParty()) {
                party.broadcastPacket(new PartySmallWindowUpdate(this));
            }

            if (clan != null) {
                clan.broadcastToOnlineMembers(new PledgeShowMemberListUpdate(this));
            }

            rewardSkills();

            if (!isGM() && PlayerConfig.DECREASE_SKILL_LEVEL) {
                checkPlayerSkills();
            }
        } finally {
            changingClass = false;

            WorldPartition.loop()
                    .schedule(
                            () -> {
                                getInventory().applyItemSkills();
                                sendSkillList();
                            },
                            100,
                            TimeUnit.MILLISECONDS);
        }
    }

    private PlayerClass learningClass = getPlayerClass();

    public PlayerClass getLearningClass() {
        WorldPartition.checkOwner();
        return learningClass;
    }

    public void setLearningClass(PlayerClass learningClass) {
        WorldPartition.checkOwner();
        this.learningClass = learningClass;
    }

    public long getExp() {
        WorldPartition.checkOwner();
        return uncheckedStat().getExp();
    }

    public void setActiveEnchantItemId(int objectId) {
        WorldPartition.checkOwner();
        if (objectId == ID_NONE) {
            setActiveEnchantSupportItemId(ID_NONE);
            setActiveEnchantTimestamp(0);
            setEnchanting(false);
        }

        activeEnchantItemId = objectId;
    }

    public Weapon findFistsWeaponItem(int classId) {
        WorldPartition.checkOwner();
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
        } else {
            weaponItem = (Weapon) ItemData.getInstance().getTemplate(246 /* Human Fighter Fist */);
        }

        return weaponItem;
    }

    public void rewardSkills() {
        WorldPartition.checkOwner();
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

    public void regiveTemporarySkills() {
        WorldPartition.checkOwner();
        // Do not call this on enterworld or char load.

        if (noble) {
            setNoble(true);
        }

        if (hero) {
            setHero(true);
        }

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

        getInventory().reloadEquippedItems();

        restoreDeathPenaltyBuffLevel();
    }

    /** @return the number of skills newly learned */
    public int giveAvailableSkills(boolean includeByFs, boolean includeAutoGet, boolean includeRequiredItems) {
        WorldPartition.checkOwner();
        int skillCounter = 0;

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

    public void giveAvailableAutoGetSkills() {
        WorldPartition.checkOwner();
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

    public void setExp(long exp) {
        WorldPartition.checkOwner();
        getStat().setExp(Math.max(0, exp));
    }

    @Override
    public Race getRace() {
        WorldPartition.checkOwner();
        if (!isSubClassActive()) {
            return getTemplate().getRace();
        }

        return PlayerTemplateData.getInstance().getTemplate(baseClass).getRace();
    }

    public long getSp() {
        WorldPartition.checkOwner();
        return uncheckedStat().getSp();
    }

    public void setSp(long sp) {
        WorldPartition.checkOwner();
        uncheckedStat().setSp(Math.max(0, sp));
    }

    public boolean isCastleLord(int castleId) {
        WorldPartition.checkOwner();
        if ((clan != null) && (clan.getLeader().getPlayer() == this)) {
            final Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
            if ((castle != null) && (castle == CastleManager.getInstance().getCastleById(castleId))) {
                return true;
            }
        }

        return false;
    }

    public int getClanCrestId() {
        WorldPartition.checkOwner();
        return clan != null ? clan.getCrestId() : 0;
    }

    public int getClanCrestLargeId() {
        WorldPartition.checkOwner();
        if ((clan != null) && ((clan.getCastleId() != 0) || (clan.getHideoutId() != 0))) {
            return clan.getCrestLargeId();
        }

        return 0;
    }

    public void setOnlineTime(long time) {
        WorldPartition.checkOwner();
        onlineTime = time;
        onlineBeginTime = ClockBridge.gameMillis();
    }

    public int getOnlineTimeMillis() {
        WorldPartition.checkOwner();
        return (int) (ClockBridge.gameMillis() - onlineBeginTime);
    }

    public void removeItemFromShortcut(int objectId) {
        WorldPartition.checkOwner();
        shortcuts.deleteShortcutByObjectId(objectId);
    }

    public void setSitting(boolean value) {
        WorldPartition.checkOwner();
        if (value) {
            if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_SIT, this)) {
                EventDispatcher.getInstance().notifyEventAsync(new OnPlayerSit(this), this);
            }
        }

        sitting = value;
    }

    public void setSittingProgress(boolean value) {
        WorldPartition.checkOwner();
        sittingInProgress = value;
    }

    public void sitDown() {
        WorldPartition.checkOwner();
        sitDown(true);
    }

    public void sitDown(boolean checkCast) {
        WorldPartition.checkOwner();
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

        // 2500 ms: the sit animation must finish first.
        WorldPartition.loop().schedule(new SitDownTask(this), 2500, TimeUnit.MILLISECONDS);
    }

    public void standUp() {
        WorldPartition.checkOwner();
        if (sittingInProgress) {
            return;
        }

        if (sitting && !isInStoreMode() && !isAlikeDead()) {
            setSittingProgress(true);
            if (getEffectList().isAffected(EffectFlag.RELAXING)) {
                stopEffects(EffectType.RELAXING);
            }

            broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_STANDING));

            // 2500 ms: the stand animation must finish first.
            WorldPartition.loop().schedule(new StandUpTask(this), 2500, TimeUnit.MILLISECONDS);
        }
    }

    public boolean hasRefund() {
        WorldPartition.checkOwner();
        return (refund != null) && (refund.getSize() > 0) && GeneralConfig.ALLOW_REFUND;
    }

    public PlayerRefund getRefund() {
        WorldPartition.checkOwner();
        if (refund == null) {
            refund = new PlayerRefund(this);
        }

        return refund;
    }

    public void clearRefund() {
        WorldPartition.checkOwner();
        if (refund != null) {
            refund.deleteMe();
        }

        refund = null;
    }

    public long getAdena() {
        WorldPartition.checkOwner();
        return inventory.getAdena();
    }

    public long getAncientAdena() {
        WorldPartition.checkOwner();
        return inventory.getAncientAdena();
    }

    public void addAdena(ItemProcessType process, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        final long currentAdena = inventory.getAdena();
        final long limitRemaining = PlayerConfig.MAX_ADENA - currentAdena;
        final long amountToAdd = count;

        if (count > limitRemaining) {
            count = limitRemaining;
        }

        if (sendMessage) {
            if ((count == 0) && (amountToAdd > 0)) {
                final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_EARN_S1_ADENA);
                sm.addLong(amountToAdd);
                sendPacket(sm);
            } else {
                final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S1_ADENA);
                sm.addLong(count);
                sendPacket(sm);
            }
        }

        if (count > 0) {
            inventory.addAdena(process, count, this, reference);

            if (count == getAdena()) {
                sendItemList(false);
            } else {
                final InventoryUpdate iu = new InventoryUpdate();
                iu.addModifiedItem(inventory.getAdenaInstance());
                sendInventoryUpdate(iu);
            }
        }

        if ((inventory.getAdena() == PlayerConfig.MAX_ADENA) && sendMessage) {
            sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_YOUR_OUT_OF_POCKET_ADENA_LIMIT);
        }
    }

    public boolean reduceAdena(ItemProcessType process, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
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

    public void addAncientAdena(ItemProcessType process, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
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

    public boolean reduceAncientAdena(ItemProcessType process, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
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

    public void addItem(ItemProcessType process, Item item, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        if (item.getCount() > 0) {
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

            final Item newitem = inventory.addItem(process, item, this, reference);

            final InventoryUpdate playerIU = new InventoryUpdate();
            playerIU.addItem(newitem);
            sendInventoryUpdate(playerIU);

            final StatusUpdate su = new StatusUpdate(this);
            su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
            sendPacket(su);

            if (!isGM()
                    && !inventory.validateCapacity(0, item.isQuestItem())
                    && newitem.isDropable()
                    && (!newitem.isStackable() || (newitem.getLastChange() != Item.MODIFIED))) {
                dropItem(ItemProcessType.DROP, newitem, null, true, true);
            } else if (CursedWeaponsManager.getInstance().isCursed(newitem.getId())) {
                CursedWeaponsManager.getInstance().activate(this, newitem);
            } else if (FortSiegeManager.getInstance().isCombat(item.getId())) {
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

    public Item addItem(ItemProcessType process, int itemId, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        return addItem(process, itemId, count, -1, reference, sendMessage);
    }

    public Item addItem(
            ItemProcessType process,
            int itemId,
            long count,
            int enchantLevel,
            WorldObject reference,
            boolean sendMessage) {
        WorldPartition.checkOwner();
        if (count > 0) {
            final ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
            if (item == null) {
                log.error("Item doesn't exist so cannot be added. Item ID: {}", itemId);
                return null;
            }

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

            if (item.hasExImmediateEffect() && item.isEtcItem()) {
                for (SkillHolder skillHolder : item.getSkills()) {
                    doSimultaneousCast(skillHolder.getSkill());
                }

                broadcastInfo();
            } else {
                final Item createdItem = inventory.addItem(process, itemId, count, this, reference);
                if (enchantLevel > -1) {
                    createdItem.setEnchantLevel(enchantLevel);

                    final InventoryUpdate playerIU = new InventoryUpdate();
                    if (createdItem.isStackable() && (createdItem.getCount() > count)) {
                        playerIU.addModifiedItem(createdItem);
                    } else {
                        playerIU.addNewItem(createdItem);
                    }
                    sendInventoryUpdate(playerIU);
                }

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

    public void addItem(ItemProcessType process, ItemHolder item, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        addItem(process, item.getId(), item.getCount(), reference, sendMessage);
    }

    public boolean destroyItem(ItemProcessType process, Item item, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        return destroyItem(process, item, item.getCount(), reference, sendMessage);
    }

    public boolean destroyItem(
            ItemProcessType process, Item item, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        final boolean wasEquipped = item.isEquipped();
        final Item destoyedItem = inventory.destroyItem(process, item, count, this, reference);
        if (destoyedItem == null) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

        final InventoryUpdate playerIU = new InventoryUpdate();
        playerIU.addItem(destoyedItem);
        sendInventoryUpdate(playerIU);

        if (wasEquipped && (destoyedItem.getCount() == 0)) {
            // The client leaves the item in the paperdoll when the last one is used up, so the full list is sent as
            // well.
            sendItemList(false);
        }

        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(su);

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

    /** A {@code null} process skips item logging. */
    @Override
    public boolean destroyItem(
            ItemProcessType process, int objectId, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        final Item item = inventory.getItemByObjectId(objectId);
        if ((item == null) || (item.getCount() < count)) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

        return destroyItem(process, item, count, reference, sendMessage);
    }

    @Override
    public boolean destroyItemByItemId(
            ItemProcessType process, int itemId, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
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

        final InventoryUpdate playerIU = new InventoryUpdate();
        playerIU.addItem(item);
        sendInventoryUpdate(playerIU);

        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(su);

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

    public Item transferItem(
            ItemProcessType process, int objectId, long count, Inventory target, WorldObject reference) {
        WorldPartition.checkOwner();
        final Item oldItem = checkItemManipulation(objectId, count, "transfer");
        if (oldItem == null) {
            return null;
        }

        final Item newItem = inventory.transferItem(process, objectId, count, target, this, reference);
        if (newItem == null) {
            return null;
        }

        final InventoryUpdate playerIU = new InventoryUpdate();
        if ((oldItem.getCount() > 0) && (oldItem != newItem)) {
            playerIU.addModifiedItem(oldItem);
        } else {
            playerIU.addRemovedItem(oldItem);
        }

        sendInventoryUpdate(playerIU);

        StatusUpdate playerSU = new StatusUpdate(this);
        playerSU.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(playerSU);

        if (target instanceof PlayerInventory) {
            final Player targetPlayer = ((PlayerInventory) target).getOwner();
            final InventoryUpdate targetIU = new InventoryUpdate();
            if (newItem.getCount() > count) {
                targetIU.addModifiedItem(newItem);
            } else {
                targetIU.addNewItem(newItem);
            }

            targetPlayer.sendInventoryUpdate(targetIU);

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

    /** Validates slots and weight limit before exchanging, which separate add and destroy calls do not. */
    public boolean exchangeItemsById(
            ItemProcessType process,
            WorldObject reference,
            int coinId,
            long cost,
            int rewardId,
            long count,
            boolean sendMessage) {
        WorldPartition.checkOwner();
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

    public boolean dropItem(
            ItemProcessType process, Item item, WorldObject reference, boolean sendMessage, boolean protectItem) {
        WorldPartition.checkOwner();
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

        if (GeneralConfig.DESTROY_DROPPED_PLAYER_ITEM) {
            droppedItem.setProtected(droppedItem.isEquipable()
                    && (!droppedItem.isEquipable() || !GeneralConfig.DESTROY_EQUIPABLE_PLAYER_ITEM));
        } else {
            droppedItem.setProtected(true);
        }

        if (protectItem) {
            droppedItem.getDropProtection().protect(this);
        }

        final InventoryUpdate playerIU = new InventoryUpdate();
        playerIU.addItem(droppedItem);
        sendInventoryUpdate(playerIU);

        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(su);

        if (sendMessage) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_DROPPED_S1);
            sm.addItemName(droppedItem);
            sendPacket(sm);
        }

        return true;
    }

    public boolean dropItem(ItemProcessType process, Item item, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        return dropItem(process, item, reference, sendMessage, false);
    }

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
        WorldPartition.checkOwner();
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

        if (protectItem) {
            item.getDropProtection().protect(this);
        }

        final InventoryUpdate playerIU = new InventoryUpdate();
        playerIU.addItem(invitem);
        sendInventoryUpdate(playerIU);

        final StatusUpdate su = new StatusUpdate(this);
        su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
        sendPacket(su);

        if (sendMessage) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_DROPPED_S1);
            sm.addItemName(item);
            sendPacket(sm);
        }

        return item;
    }

    public Item checkItemManipulation(int objectId, long count, String action) {
        WorldPartition.checkOwner();
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

        if ((hasSummon() && (summon.getControlObjectId() == objectId)) || (mountObjectID == objectId)) {
            return null;
        }

        if (activeEnchantItemId == objectId) {
            return null;
        }

        // Exploit guard: no augmented weapon into the warehouse while casting.
        if (item.isAugmented() && (isCastingNow() || isCastingSimultaneouslyNow())) {
            return null;
        }

        return item;
    }

    public boolean isSpawnProtected() {
        WorldPartition.checkOwner();
        return (spawnProtectEndTime != 0) && (spawnProtectEndTime > ClockBridge.gameMillis());
    }

    public boolean isTeleportProtected() {
        WorldPartition.checkOwner();
        return (teleportProtectEndTime != 0) && (teleportProtectEndTime > ClockBridge.gameMillis());
    }

    public void setSpawnProtection(boolean protect) {
        WorldPartition.checkOwner();
        spawnProtectEndTime = protect ? ClockBridge.gameMillis() + (PlayerConfig.PLAYER_SPAWN_PROTECTION * 1000) : 0;
    }

    public void setTeleportProtection(boolean protect) {
        WorldPartition.checkOwner();
        teleportProtectEndTime =
                protect ? ClockBridge.gameMillis() + (PlayerConfig.PLAYER_TELEPORT_PROTECTION * 1000) : 0;
    }

    public void setRecentFakeDeath(boolean protect) {
        WorldPartition.checkOwner();
        recentFakeDeathEndTime = protect
                ? GameTimeTaskManager.getInstance().getGameTicks()
                        + (PlayerConfig.PLAYER_FAKEDEATH_UP_PROTECTION * GameTimeTaskManager.TICKS_PER_SECOND)
                : 0;
    }

    public boolean isRecentFakeDeath() {
        WorldPartition.checkOwner();
        return recentFakeDeathEndTime > GameTimeTaskManager.getInstance().getGameTicks();
    }

    @Override
    public boolean isAlikeDead() {
        WorldPartition.checkOwner();
        return super.isAlikeDead() || fakeDeath;
    }

    public void setClient(GameClient client) {
        WorldPartition.checkOwner();
        this.client = client;
        if ((this.client != null) && (this.client.getIp() != null)) {
            ipAddress = this.client.getIp();
        }
    }

    public String getIPAddress() {
        WorldPartition.checkOwner();
        return ipAddress;
    }

    @Override
    public void enableSkill(Skill skill) {
        WorldPartition.checkOwner();
        super.enableSkill(skill);
        removeTimeStamp(skill);
    }

    @Override
    public boolean checkDoCastConditions(Skill skill) {
        WorldPartition.checkOwner();
        if (!super.checkDoCastConditions(skill) || observerMode) {
            return false;
        }

        if (inOlympiadMode && skill.isBlockedInOlympiad()) {
            sendPacket(SystemMessageId.YOU_CANNOT_USE_THAT_SKILL_IN_A_GRAND_OLYMPIAD_MATCH);
            return false;
        }

        if ((charges < skill.getChargeConsumeCount())
                || (isInAirShip() && !skill.hasEffectType(EffectType.REFUEL_AIRSHIP))) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
            sm.addSkillName(skill);
            sendPacket(sm);
            return false;
        }

        return true;
    }

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

    // The party gets HP, MP and level only; the status listeners get no HP or MP from here.
    @Override
    public void broadcastStatusUpdate() {
        WorldPartition.checkOwner();
        if (broadcastStatusUpdateTask == null) {
            broadcastStatusUpdateTask = WorldPartition.loop()
                    .schedule(
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
                                    DuelManager.getInstance()
                                            .broadcastToOppositTeam(this, new ExDuelUpdateUserInfo(this));
                                }

                                broadcastStatusUpdateTask = null;
                            },
                            50,
                            TimeUnit.MILLISECONDS);
        }
    }

    // UserInfo carries private data (max HP, STR, DEX), so other players only get CharInfo.
    public void broadcastUserInfo() {
        WorldPartition.checkOwner();
        updateUserInfo();

        broadcastCharInfo();
    }

    public void updateUserInfo() {
        WorldPartition.checkOwner();
        sendPacket(new UserInfo(this));
        sendPacket(new ExBrExtraUserInfo(this));
    }

    public void broadcastCharInfo() {
        WorldPartition.checkOwner();
        if (isOnlineInt() == 0) {
            return;
        }

        if (broadcastCharInfoTask == null) {
            broadcastCharInfoTask = WorldPartition.loop()
                    .schedule(
                            () -> {
                                final CharInfo charInfo = new CharInfo(this, false);
                                charInfo.sendInBroadcast();
                                try {
                                    World.forEachVisibleObject(this, Player.class, player -> {
                                        if (isVisibleFor(player)) {
                                            if (isInvisible() && isGM()) {
                                                player.sendPacket(new CharInfo(this, true));
                                            } else {
                                                player.sendPacket(charInfo);
                                            }

                                            player.sendPacket(new ExBrExtraUserInfo(this));

                                            final int relation = getRelation(player);
                                            final boolean isAutoAttackable = isAutoAttackable(player);
                                            final RelationCache oldrelation = getKnownRelation(player.getObjectId());
                                            if ((oldrelation == null)
                                                    || (oldrelation.relation() != relation)
                                                    || (oldrelation.autoAttackable() != isAutoAttackable)) {
                                                player.sendPacket(
                                                        new RelationChanged(this, relation, isAutoAttackable));
                                                if (hasSummon()) {
                                                    player.sendPacket(
                                                            new RelationChanged(summon, relation, isAutoAttackable));
                                                }

                                                putKnownRelation(
                                                        player.getObjectId(),
                                                        new RelationCache(relation, isAutoAttackable));
                                            }
                                        }
                                    });
                                } finally {
                                    charInfo.releaseBroadcast();
                                }
                                broadcastCharInfoTask = null;
                            },
                            100,
                            TimeUnit.MILLISECONDS);
        }
    }

    public void broadcastTitleInfo() {
        WorldPartition.checkOwner();
        updateUserInfo();

        broadcastPacket(new NicknameChanged(this));
    }

    @Override
    public void broadcastPacket(ServerPacket packet) {
        WorldPartition.checkOwner();
        packet.sendInBroadcast();
        try {
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
                    final RelationCache cache = getKnownRelation(player.getObjectId());
                    if ((cache == null)
                            || (cache.relation() != relation)
                            || (cache.autoAttackable() != isAutoAttackable)) {
                        player.sendPacket(new RelationChanged(this, relation, isAutoAttackable));
                        if (hasSummon()) {
                            player.sendPacket(new RelationChanged(summon, relation, isAutoAttackable));
                        }

                        putKnownRelation(player.getObjectId(), new RelationCache(relation, isAutoAttackable));
                    }
                }
            });
        } finally {
            packet.releaseBroadcast();
        }
    }

    public List<ClientPort> observerPorts(boolean includeSelf) {
        WorldPartition.checkOwner();
        final List<ClientPort> ports = new ArrayList<>();
        if (includeSelf) {
            ports.add(getPort());
        }
        World.forEachVisibleObject(this, Player.class, player -> {
            if (isVisibleFor(player)) {
                ports.add(player.getPort());
            }
        });
        return ports;
    }

    @Override
    public int getAllyId() {
        WorldPartition.checkOwner();
        return clan == null ? 0 : clan.getAllyId();
    }

    public int getAllyCrestId() {
        WorldPartition.checkOwner();
        return getAllyId() == 0 ? 0 : clan.getAllyCrestId();
    }

    @Override
    public void sendPacket(ServerPacket packet) {
        WorldPartition.checkOwner();
        if (client != null) {
            client.sendPacket(packet);
        }
    }

    /** {@link ClientPort#DETACHED} without a connected client: offline trade and offline play write nothing. */
    public ClientPort getPort() {
        WorldPartition.checkOwner();
        return ((client == null) || client.isDetached()) ? ClientPort.DETACHED : client.port();
    }

    @Override
    public void sendPacket(SystemMessageId id) {
        WorldPartition.checkOwner();
        sendPacket(new SystemMessage(id));
    }

    public void doInteract(Creature target) {
        WorldPartition.checkOwner();
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
        } else {
            target.onAction(this);
        }
    }

    public void doAutoLoot(Attackable target, int itemId, long itemCount) {
        WorldPartition.checkOwner();
        if (isInParty() && !ItemData.getInstance().getTemplate(itemId).hasExImmediateEffect()) {
            party.distributeItem(this, itemId, itemCount, false, target);
        } else if (itemId == Inventory.ADENA_ID) {
            addAdena(ItemProcessType.LOOT, itemCount, target, true);
        } else {
            addItem(ItemProcessType.LOOT, itemId, itemCount, target, true);
        }
    }

    public void doAutoLoot(Attackable target, ItemHolder item) {
        WorldPartition.checkOwner();
        doAutoLoot(target, item.getId(), item.getCount());
    }

    @Override
    public void doPickupItem(WorldObject object) {
        WorldPartition.checkOwner();
        if (isAlikeDead() || fakeDeath) {
            return;
        }

        if (getActiveTradeList() != null) {
            sendPacket(SystemMessageId.YOU_CANNOT_PICK_UP_OR_USE_ITEMS_WHILE_TRADING);
            sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        getAI().setIntentionIdle();

        if (!object.isItem()) {
            log.warn("{} trying to pickup wrong target.{}", this, getTarget());
            return;
        }

        final Item target = (Item) object;
        sendPacket(new StopMove(this));
        SystemMessage smsg = null;
        if (!target.isSpawned()) {
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

        if (((isInParty() && (party.getDistributionType() == PartyDistributionType.FINDERS_KEEPERS)) || !isInParty())
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

        target.pickupMe(this);
        if (GeneralConfig.SAVE_DROPPED_ITEM) {
            ItemsOnGroundManager.getInstance().removeObject(target);
        }

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

            if (isInParty()) {
                party.distributeItem(this, target);
            } else if ((target.getId() == Inventory.ADENA_ID) && (inventory.getAdenaInstance() != null)) {
                addAdena(ItemProcessType.PICKUP, target.getCount(), null, true);
                ItemManager.destroyItem(ItemProcessType.PICKUP, target, this, null);
            } else {
                addItem(ItemProcessType.PICKUP, target, null, true);

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
        WorldPartition.checkOwner();
        super.doAttack(target);
        setRecentFakeDeath(false);
        if (target.isFakePlayer() && !FakePlayersConfig.FAKE_PLAYER_AUTO_ATTACKABLE) {
            updatePvPStatus();
        }
    }

    @Override
    public void doCast(Skill skill) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return sitting ? PrivateStoreRangeConfig.SHOP_MIN_RANGE_FROM_PLAYER : 0;
    }

    public void tryOpenPrivateBuyStore() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return (transformation != null) && !transformation.isStance();
    }

    public boolean isInStance() {
        WorldPartition.checkOwner();
        return (transformation != null) && transformation.isStance();
    }

    public void transform(Transform transformation) {
        WorldPartition.checkOwner();
        if (transformation.isFlying() && (getX() > World.GRACIA_MAX_X)) {
            return;
        }

        if (!FeatureConfig.ALLOW_MOUNTS_DURING_SIEGE && transformation.isRiding() && isInsideZone(ZoneId.SIEGE)) {
            return;
        }

        if (this.transformation != null) {
            sendPacket(new SystemMessage(SystemMessageId.YOU_ALREADY_POLYMORPHED_AND_CANNOT_POLYMORPH_AGAIN));
            return;
        }

        setQueuedSkill(null, false, false);
        if (isMounted()) {
            dismount();
        }

        this.transformation = transformation;
        getEffectList().stopAllToggles();
        transformation.onTransform(this);
        sendSkillList();
        sendPacket(new SkillCoolTime(this));
        broadcastUserInfo();

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_TRANSFORM, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerTransform(this, transformation.getId()), this);
        }
    }

    @Override
    public void untransform() {
        WorldPartition.checkOwner();
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

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_TRANSFORM, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerTransform(this, 0), this);
        }
    }

    /** A transformation from a picked-up cursed weapon wins over any earlier one (Zariche over Buffalo). */
    public int getTransformationId() {
        WorldPartition.checkOwner();
        return isTransformed() ? transformation.getId() : 0;
    }

    public int getTransformationDisplayId() {
        WorldPartition.checkOwner();
        return isTransformed() ? transformation.getDisplayId() : 0;
    }

    @Override
    public void setTarget(WorldObject worldObject) {
        WorldPartition.checkOwner();
        WorldObject newTarget = worldObject;
        if (newTarget != null) {
            final boolean isInParty = newTarget.isPlayer() && isInParty() && party.containsPlayer(newTarget.asPlayer());

            // Prevents /target exploiting.
            if (!isInParty && (Math.abs(newTarget.getZ() - getZ()) > 3000)) {
                newTarget = null;
            }

            if ((newTarget != null) && !isInParty && !newTarget.isSpawned()) {
                newTarget = null;
            }

            if (!isGM() && (newTarget instanceof Vehicle)) {
                newTarget = null;
            }
        }

        final WorldObject oldTarget = getTarget();
        if (oldTarget != null) {
            if (oldTarget.equals(newTarget)) {
                if ((newTarget != null) && (newTarget.getObjectId() != getObjectId())) {
                    sendPacket(new ValidateLocation(newTarget));
                }
                return;
            }

            oldTarget.removeStatusListener(this);
        }

        if ((newTarget != null) && newTarget.isCreature()) {
            final Creature target = newTarget.asCreature();

            if (newTarget.getObjectId() != getObjectId()) {
                sendPacket(new ValidateLocation(target));
            }

            sendPacket(new MyTargetSelected(this, target));

            target.addStatusListener(this);

            final StatusUpdate su = new StatusUpdate(target);
            su.addAttribute(StatusUpdate.MAX_HP, target.getMaxHp());
            su.addAttribute(StatusUpdate.CUR_HP, (int) target.getCurrentHp());
            sendPacket(su);

            World.broadcastToVisiblePlayers(
                    this, new TargetSelected(getObjectId(), newTarget.getObjectId(), getX(), getY(), getZ()));
        }

        if ((newTarget == null) && (getTarget() != null)) {
            broadcastPacket(new TargetUnselected(this));
        }

        super.setTarget(newTarget);
    }

    @Override
    public Item getActiveWeaponInstance() {
        WorldPartition.checkOwner();
        return inventory.getPaperdollItem(Inventory.PAPERDOLL_RHAND);
    }

    @Override
    public Weapon getActiveWeaponItem() {
        WorldPartition.checkOwner();
        final Item weapon = getActiveWeaponInstance();
        return weapon == null ? fistsWeaponItem : (Weapon) weapon.getTemplate();
    }

    public Item getChestArmorInstance() {
        WorldPartition.checkOwner();
        return inventory.getPaperdollItem(Inventory.PAPERDOLL_CHEST);
    }

    public Item getLegsArmorInstance() {
        WorldPartition.checkOwner();
        return inventory.getPaperdollItem(Inventory.PAPERDOLL_LEGS);
    }

    public Armor getActiveChestArmorItem() {
        WorldPartition.checkOwner();
        final Item armor = getChestArmorInstance();
        return armor == null ? null : (Armor) armor.getTemplate();
    }

    public Armor getActiveLegsArmorItem() {
        WorldPartition.checkOwner();
        final Item legs = getLegsArmorInstance();
        return legs == null ? null : (Armor) legs.getTemplate();
    }

    public boolean isWearingHeavyArmor() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        engageRequest = state;
        engageId = playerid;
    }

    public void engageAnswer(int answer) {
        WorldPartition.checkOwner();
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

    @Override
    public Item getSecondaryWeaponInstance() {
        WorldPartition.checkOwner();
        return inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
    }

    @Override
    public ItemTemplate getSecondaryWeaponItem() {
        WorldPartition.checkOwner();
        final Item item = inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
        return item != null ? item.getTemplate() : null;
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        if (isMounted()) {
            stopFeed();
        }

        if (fakeDeath) {
            stopFakeDeath(true);
        }

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

                    if (!(PvpRewardItemConfig.DISABLE_REWARDS_IN_INSTANCES && (getInstanceId() != 0))
                            && //
                            !(PvpRewardItemConfig.DISABLE_REWARDS_IN_PVP_ZONES && isInsideZone(ZoneId.PVP))) {
                        if (PvpRewardItemConfig.REWARD_PVP_ITEM && (pvpFlag != 0)) {
                            pk.addItem(
                                    ItemProcessType.REWARD,
                                    PvpRewardItemConfig.REWARD_PVP_ITEM_ID,
                                    PvpRewardItemConfig.REWARD_PVP_ITEM_AMOUNT,
                                    this,
                                    PvpRewardItemConfig.REWARD_PVP_ITEM_MESSAGE);
                        }

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

            setExpBeforeDeath(0);

            if (isCursedWeaponEquipped()) {
                CursedWeaponsManager.getInstance().drop(cursedWeaponEquippedId, killer);
            } else if (combatFlagEquipped) {
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
                    onDieDropItem(killer);
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

        if (!cubics.isEmpty()) {
            try (Snapshot<Cubic> snapshot = ScratchBuffers.snapshot(cubics.values())) {
                for (Cubic cubic : snapshot) {
                    cubic.stopAction();
                    cubic.cancelDisappear();
                }
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
        if (getKarma() > 0) {
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
                && (pk.getClan().isAtWarWith(clanId))) {
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
                try (Snapshot<Item> droppable = ScratchBuffers.empty()) {
                    inventory.collectItems(droppable, this::isDroppableOnDeath);
                    for (Item itemDrop : droppable) {
                        if (itemDrop.isEquipped()) {
                            itemDropPercent = itemDrop.getTemplate().getType2() == ItemTemplate.TYPE2_WEAPON
                                    ? dropEquipWeapon
                                    : dropEquip;
                        } else {
                            itemDropPercent = dropItem;
                        }

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
                                        "{} dropped id = {}, count = {}",
                                        getName(),
                                        itemDrop.getId(),
                                        itemDrop.getCount());
                            }

                            if (++dropCount >= dropLimit) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isDroppableOnDeath(Item item) {
        return !item.isShadowItem()
                && !item.isTimeLimitedItem()
                && item.isDropable()
                && (item.getId() != Inventory.ADENA_ID)
                && (item.getTemplate().getType2() != ItemTemplate.TYPE2_QUEST)
                && !(hasSummon() && (summon.getControlObjectId() == item.getObjectId()))
                && (Arrays.binarySearch(PvpConfig.KARMA_LIST_NONDROPPABLE_ITEMS, item.getId()) < 0)
                && (Arrays.binarySearch(PvpConfig.KARMA_LIST_NONDROPPABLE_PET_ITEMS, item.getId()) < 0);
    }

    public void onKillUpdatePvPKarma(Creature target) {
        WorldPartition.checkOwner();
        if ((target == null) || !target.isPlayable()) {
            return;
        }

        final Player killedPlayer = target.asPlayer();
        if ((killedPlayer == null) || (killedPlayer == this)) {
            return;
        }

        if (isCursedWeaponEquipped() && target.isPlayer()) {
            CursedWeaponsManager.getInstance().increaseKills(cursedWeaponEquippedId);
            return;
        }

        if (isInOlympiadMode() || killedPlayer.isInOlympiadMode()) {
            return;
        }

        if (isInDuel() && killedPlayer.isInDuel()) {
            return;
        }

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

        if (isInsideZone(ZoneId.PVP) || target.isInsideZone(ZoneId.PVP)) {
            return;
        }

        if (checkIfPvP(target) && (killedPlayer.getPvpFlag() != 0)) {
            increasePvpKills(target);
        } else {
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
            if (killedPlayer.getKarma() > 0) {
                if (PvpConfig.KARMA_AWARD_PK_KILL) {
                    increasePvpKills(target);
                }
            } else if (killedPlayer.getPvpFlag() == 0) {
                if (FactionSystemConfig.FACTION_SYSTEM_ENABLED) {
                    if ((good && killedPlayer.isGood()) || (evil && killedPlayer.isEvil())) {
                        increasePkKillsAndKarma(target);
                    }
                } else {
                    increasePkKillsAndKarma(target);
                }

                checkItemRestriction();
            }
        }
    }

    public void increasePvpKills(Creature target) {
        WorldPartition.checkOwner();
        if (target.isPlayer() && AntiFeedManager.getInstance().check(this, target)) {
            setPvpKills(pvpKills + 1);
            updatePvpTitleAndColor(true);

            updateUserInfo();
        }
    }

    public void increasePkKillsAndKarma(Creature target) {
        WorldPartition.checkOwner();
        // Only playables can increase karma/pk.
        if ((target == null) || !target.isPlayable()) {
            return;
        }

        // The karma gain depends on pkKills, so it is computed before the count is raised.
        setKarma(getKarma() + Formulas.calculateKarmaGain(pkKills, target.isSummon()));

        if (target.isPlayer()) {
            setPkKills(pkKills + 1);
        }

        updateUserInfo();
    }

    public void updatePvpTitleAndColor(boolean broadcastInfo) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        if (isInsideZone(ZoneId.PVP)) {
            return;
        }

        setPvpFlagLasts(ClockBridge.gameMillis() + PvpConfig.PVP_NORMAL_TIME);
        if (pvpFlag == 0) {
            startPvPFlag();
        }
    }

    public void updatePvPStatus(Creature target) {
        WorldPartition.checkOwner();
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

    public boolean isLucky() {
        WorldPartition.checkOwner();
        return (getLevel() <= 9) && isAffectedBySkill(CommonSkill.LUCKY.getId());
    }

    public void restoreExp(double restorePercent) {
        WorldPartition.checkOwner();
        if (expBeforeDeath > 0) {
            getStat().addExp(Math.round(((expBeforeDeath - getExp()) * restorePercent) / 100));
            setExpBeforeDeath(0);
        }
    }

    public void calculateDeathExpPenalty(Creature killer, boolean atWar) {
        WorldPartition.checkOwner();
        final int level = getLevel();
        double percentLost = ExperienceLossData.getInstance().getPercentLost(getLevel());

        if (killer != null) {
            if (killer.isRaid()) {
                percentLost *= calcStat(Stat.REDUCE_EXP_LOST_BY_RAID, 1);
            } else if (killer.isMonster()) {
                percentLost *= calcStat(Stat.REDUCE_EXP_LOST_BY_MOB, 1);
            } else if (killer.isPlayable()) {
                percentLost *= calcStat(Stat.REDUCE_EXP_LOST_BY_PVP, 1);
            }
        }

        if (getKarma() > 0) {
            percentLost *= RatesConfig.RATE_KARMA_EXP_LOST;
        }

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

        long lostExp = 0;
        if (currentLevelExp > 0) {
            lostExp = Math.round((currentLevelExp * percentLost) / 100);

            // Loss is capped at 10% of the current level's experience.
            lostExp = Math.min(lostExp, currentLevelExp / 10);

            if (isFestivalParticipant() || atWar) {
                lostExp /= 4;
            }

            if (nevitSystem.isAdventBlessingActive()) {
                lostExp = 0;
            }
        }

        setExpBeforeDeath(getExp());
        getStat().removeExp(lostExp);
    }

    public boolean isPartyWaiting() {
        WorldPartition.checkOwner();
        return PartyMatchWaitingList.getInstance().contains(this);
    }

    public boolean isInPartyMatchRoom() {
        WorldPartition.checkOwner();
        return partyRoom > 0;
    }

    public void stopAllTimers() {
        WorldPartition.checkOwner();
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

    public boolean hasSummon() {
        WorldPartition.checkOwner();
        return summon != null;
    }

    public boolean hasPet() {
        WorldPartition.checkOwner();
        return (summon != null) && summon.isPet();
    }

    public boolean hasServitor() {
        WorldPartition.checkOwner();
        return (summon != null) && summon.isServitor();
    }

    public Summon getSummon() {
        WorldPartition.checkOwner();
        return summon;
    }

    public void setPet(Summon summon) {
        WorldPartition.checkOwner();
        this.summon = summon;
    }

    public void addTrainedBeast(TamedBeast tamedBeast) {
        WorldPartition.checkOwner();
        if (trainedBeasts == null) {
            trainedBeasts = new ObjectLinkedOpenHashSet<>();
        }

        trainedBeasts.add(tamedBeast);
    }

    public Player getActiveRequester() {
        WorldPartition.checkOwner();
        if ((activeRequester != null) && activeRequester.isRequestExpired() && (activeTradeList == null)) {
            activeRequester = null;
        }

        return activeRequester;
    }

    public boolean isProcessingRequest() {
        WorldPartition.checkOwner();
        return (getActiveRequester() != null)
                || (requestExpireTime > GameTimeTaskManager.getInstance().getGameTicks());
    }

    public boolean isProcessingTransaction() {
        WorldPartition.checkOwner();
        return (getActiveRequester() != null)
                || (activeTradeList != null)
                || (requestExpireTime > GameTimeTaskManager.getInstance().getGameTicks());
    }

    public void onTransactionRequest(Player partner) {
        WorldPartition.checkOwner();
        requestExpireTime = GameTimeTaskManager.getInstance().getGameTicks()
                + (REQUEST_TIMEOUT * GameTimeTaskManager.TICKS_PER_SECOND);
        partner.setActiveRequester(this);
    }

    public boolean isRequestExpired() {
        WorldPartition.checkOwner();
        return requestExpireTime <= GameTimeTaskManager.getInstance().getGameTicks();
    }

    public void onTransactionResponse() {
        WorldPartition.checkOwner();
        requestExpireTime = 0;
    }

    public void onTradeStart(Player partner) {
        WorldPartition.checkOwner();
        activeTradeList = new TradeList(this);
        activeTradeList.setPartner(partner);

        final SystemMessage msg = new SystemMessage(SystemMessageId.YOU_BEGIN_TRADING_WITH_C1);
        msg.addPcName(partner);
        sendPacket(msg);
        sendPacket(new TradeStart(this));
    }

    public void onTradeConfirm(Player partner) {
        WorldPartition.checkOwner();
        final SystemMessage msg = new SystemMessage(SystemMessageId.C1_HAS_CONFIRMED_THE_TRADE);
        msg.addPcName(partner);
        sendPacket(msg);
        sendPacket(TradeOtherDone.STATIC_PACKET);
    }

    public void onTradeCancel(Player partner) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        activeTradeList = null;
        sendPacket(new TradeDone(1));
        if (successfull) {
            sendPacket(SystemMessageId.YOUR_TRADE_WAS_SUCCESSFUL);
        }
    }

    public void startTrade(Player partner) {
        WorldPartition.checkOwner();
        onTradeStart(partner);
        partner.onTradeStart(this);
    }

    public void cancelActiveTrade() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return !manufactureItems.isEmpty();
    }

    /** @return the manufacture items by recipe id, in the order they were set */
    public Map<Integer, ManufactureItem> getManufactureItems() {
        WorldPartition.checkOwner();
        return Int2ObjectMaps.unmodifiable(new Int2ObjectLinkedOpenHashMap<>(manufactureItems));
    }

    /** @return the manufacture item of the given recipe, {@code null} for none */
    public ManufactureItem getManufactureItem(int recipeId) {
        WorldPartition.checkOwner();
        return manufactureItems.get(recipeId);
    }

    public void addManufactureItem(ManufactureItem item) {
        WorldPartition.checkOwner();
        manufactureItems.put(item.getRecipeId(), item);
    }

    public void clearManufactureItems() {
        WorldPartition.checkOwner();
        manufactureItems.clear();
    }

    public void removeManufactureItemsIf(Predicate<ManufactureItem> filter) {
        WorldPartition.checkOwner();
        try (Snapshot<ManufactureItem> items = ScratchBuffers.snapshot(manufactureItems.values())) {
            for (ManufactureItem item : items) {
                if (filter.test(item)) {
                    manufactureItems.remove(item.getRecipeId());
                }
            }
        }
    }

    public void setStoreName(String name) {
        WorldPartition.checkOwner();
        storeName = name == null ? "" : name;
    }

    public TradeList getSellList() {
        WorldPartition.checkOwner();
        if (sellList == null) {
            sellList = new TradeList(this);
        }

        return sellList;
    }

    public TradeList getBuyList() {
        WorldPartition.checkOwner();
        if (buyList == null) {
            buyList = new TradeList(this);
        }

        return buyList;
    }

    public void setPrivateStoreType(PrivateStoreType privateStoreType) {
        WorldPartition.checkOwner();
        this.privateStoreType = privateStoreType;
        if (OfflineTradeConfig.OFFLINE_DISCONNECT_FINISHED
                && (privateStoreType == PrivateStoreType.NONE)
                && ((client == null) || client.isDetached())) {
            OfflineTraderTable.getInstance().removeTrader(getObjectId());
            Disconnection.of(this).storeAndDelete();
        }
    }

    public void setClan(Clan clan) {
        WorldPartition.checkOwner();
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
            setClan(null);
            return;
        }

        clanId = clan.getId();
    }

    public boolean isClanLeader() {
        WorldPartition.checkOwner();
        return (clan != null) && (getObjectId() == clan.getLeaderId());
    }

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

        if (arrows.getCount() > 1) {
            arrows.changeCount(null, -1, this, null);
            arrows.setLastChange(Item.MODIFIED);
            inventory.refreshWeight();
        } else {
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

    @Override
    protected boolean checkAndEquipArrows() {
        if (inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND) == null) {
            arrowItem = inventory.findArrowForBow(getActiveWeaponItem());
            if (arrowItem != null) {
                inventory.setPaperdollItem(Inventory.PAPERDOLL_LHAND, arrowItem);

                sendItemList(false);
            }
        } else {
            arrowItem = inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
        }

        return arrowItem != null;
    }

    @Override
    protected boolean checkAndEquipBolts() {
        if (inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND) == null) {
            boltItem = inventory.findBoltForCrossBow(getActiveWeaponItem());
            if (boltItem != null) {
                inventory.setPaperdollItem(Inventory.PAPERDOLL_LHAND, boltItem);

                sendItemList(false);
            }
        } else {
            boltItem = inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
        }

        return boltItem != null;
    }

    public boolean disarmWeapons() {
        WorldPartition.checkOwner();
        final Item wpn = inventory.getPaperdollItem(Inventory.PAPERDOLL_RHAND);
        if (wpn == null) {
            return true;
        }

        if (isCursedWeaponEquipped()) {
            return false;
        }

        if (combatFlagEquipped) {
            return false;
        }

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

    public boolean disarmShield() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        if (!FeatureConfig.ALLOW_MOUNTS_DURING_SIEGE && isInsideZone(ZoneId.SIEGE)) {
            final MountType type = MountType.findByNpcId(pet.getId());

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

        broadcastUserInfo();

        pet.unSummon(this);
        return true;
    }

    public boolean mount(int npcId, int controlItemObjId, boolean useFood) {
        WorldPartition.checkOwner();
        if (!disarmWeapons() || !disarmShield() || isTransformed()) {
            return false;
        }

        getEffectList().stopAllToggles();
        setMount(npcId, getLevel());
        setMountObjectID(controlItemObjId);
        broadcastPacket(new Ride(this));

        broadcastUserInfo();
        if (useFood) {
            startFeed(npcId);
        }

        return true;
    }

    public boolean mountPlayer(Summon pet) {
        WorldPartition.checkOwner();
        if ((pet != null) && pet.isMountable() && !isMounted() && !isBetrayed()) {
            if (isDead()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_STRIDER_CANNOT_BE_RIDDEN_WHEN_DEAD);
                return false;
            } else if (pet.isDead()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_DEAD_STRIDER_CANNOT_BE_RIDDEN);
                return false;
            } else if (pet.isInCombat() || pet.isRooted()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_STRIDER_IN_BATTLE_CANNOT_BE_RIDDEN);
                return false;
            } else if (isInCombat()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_STRIDER_CANNOT_BE_RIDDEN_WHILE_IN_BATTLE);
                return false;
            } else if (sitting) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.A_STRIDER_CAN_BE_RIDDEN_ONLY_WHEN_STANDING);
                return false;
            } else if (fishing) {
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
        WorldPartition.checkOwner();
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
            WorldPartition.loop()
                    .schedule(
                            () -> {
                                if (isInWater()) {
                                    broadcastUserInfo();
                                }
                            },
                            1500,
                            TimeUnit.MILLISECONDS);
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

        broadcastUserInfo();

        return true;
    }

    public long getUptime() {
        WorldPartition.checkOwner();
        return ClockBridge.gameMillis() - uptime;
    }

    @Override
    public boolean isInvul() {
        WorldPartition.checkOwner();
        return super.isInvul() || isTeleportProtected();
    }

    @Override
    public boolean isInParty() {
        WorldPartition.checkOwner();
        return party != null;
    }

    public void joinParty(Party party) {
        WorldPartition.checkOwner();
        if (party != null) {
            // Set first so Creature.updateEffectIcons() treats the player as in a party.
            this.party = party;
            party.addPartyMember(this);
        }
    }

    public void leaveParty() {
        WorldPartition.checkOwner();
        if (isInParty()) {
            party.removePartyMember(this, PartyMessageType.DISCONNECTED);
            party = null;
        }
    }

    @Override
    public boolean isGM() {
        WorldPartition.checkOwner();
        return getAccessLevel().isGm();
    }

    public void setAccessLevel(int level) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        LoginService.getInstance().changeAccessLevel(getAccountId(), level);
    }

    @Override
    public AccessLevel getAccessLevel() {
        WorldPartition.checkOwner();
        if (GeneralConfig.EVERYBODY_HAS_ADMIN_RIGHTS) {
            return AdminData.getInstance().getMasterAccessLevel();
        }

        if (accessLevel == null) {
            setAccessLevel(0);
        }

        return accessLevel;
    }

    public void updateAndBroadcastStatus() {
        WorldPartition.checkOwner();
        if (updateAndBroadcastStatusTask == null) {
            updateAndBroadcastStatusTask = WorldPartition.loop()
                    .schedule(
                            () -> {
                                refreshOverloaded();
                                refreshExpertisePenalty();

                                broadcastUserInfo();

                                updateAndBroadcastStatusTask = null;
                            },
                            50,
                            TimeUnit.MILLISECONDS);
        }
    }

    public void setKarmaFlag() {
        WorldPartition.checkOwner();
        updateUserInfo();
        World.forEachVisibleObject(this, Player.class, player -> {
            player.sendPacket(new RelationChanged(this, getRelation(player), isAutoAttackable(player)));
            if (hasSummon()) {
                player.sendPacket(new RelationChanged(summon, getRelation(player), isAutoAttackable(player)));
            }
        });
    }

    public void broadcastKarma() {
        WorldPartition.checkOwner();
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

    public void setOnlineStatus(boolean isOnline, boolean updateInDb) {
        WorldPartition.checkOwner();
        if (online != isOnline) {
            online = isOnline;
        }

        if (updateInDb) {
            updateOnlineStatus();
        }
    }

    public void updateOnlineStatus() {
        WorldPartition.checkOwner();
        try {
            PlayerPersistenceManager.getInstance()
                    .updateOnlineStatus(getObjectId(), getOnlineStatus(), ClockBridge.wallMillis());
        } catch (DataAccessException e) {
            log.error("Failed updating character online status.", e);
        }
    }

    private OnlineStatus getOnlineStatus() {
        return switch (isOnlineInt()) {
            case 1 -> OnlineStatus.ONLINE;
            case 2 -> OnlineStatus.DETACHED;
            default -> OnlineStatus.OFFLINE;
        };
    }

    private boolean createDb() {
        final long now = ClockBridge.wallMillis();
        try {
            PlayerPersistenceManager.getInstance()
                    .insertCharacter(toCharacterRow().toBuilder()
                            .accountId(accountId)
                            .createdAt(instantOrNull(createDate.getTimeInMillis()))
                            .lastAccessAt(Instant.ofEpochMilli(now))
                            .build());
        } catch (DataAccessException e) {
            log.error("Could not insert char data: {}", e.getMessage(), e);
            return false;
        }

        return true;
    }

    /** Excludes the account, creation time and last access: the insert sets those. */
    private CharacterRow toCharacterRow() {
        long totalOnlineTime = onlineTime;
        if (onlineBeginTime > 0) {
            totalOnlineTime += (ClockBridge.gameMillis() - onlineBeginTime) / 1000;
        }

        final Faction faction;
        if (evil) {
            faction = Faction.EVIL;
        } else if (good) {
            faction = Faction.GOOD;
        } else {
            faction = null;
        }

        return CharacterRow.builder()
                .objectId(getObjectId())
                .name(getName())
                .title(getTitle())
                .titleColor(appearance.getTitleColor())
                .race(getRace())
                .sex(appearance.isFemale() ? Sex.FEMALE : Sex.MALE)
                .face(appearance.getFace())
                .hairStyle(appearance.getHairStyle())
                .hairColor(appearance.getHairColor())
                .classId(getPlayerClass().getId())
                .baseClassId(baseClass)
                .level(getStat().getBaseLevel())
                .exp(getStat().getBaseExp())
                .expBeforeDeath(expBeforeDeath)
                .sp(getStat().getBaseSp())
                .maxHp(getMaxHp())
                .currentHp(getCurrentHp())
                .maxCp(getMaxCp())
                .currentCp(getCurrentCp())
                .maxMp(getMaxMp())
                .currentMp(getCurrentMp())
                .x(observerMode ? lastLocation.getX() : getX())
                .y(observerMode ? lastLocation.getY() : getY())
                .z(observerMode ? lastLocation.getZ() : getZ())
                .heading(getHeading())
                .karma(getKarma())
                .fame(fame)
                .pvpKills(pvpKills)
                .pkKills(pkKills)
                .accessLevel(getAccessLevel().getLevel())
                .onlineStatus(getOnlineStatus())
                .onlineTimeSeconds(offlineStartTime > 0 ? onlineTime : totalOnlineTime)
                .newbie(newbie)
                .noble(noble)
                .inSevenSignsDungeon(in7sDungeon)
                .faction(faction)
                .language(lang)
                .vitalityPoints(getVitalityPoints())
                .deathPenaltyLevel(deathPenaltyBuffLevel)
                .bookmarkSlots(bookmarkSlot)
                .pcCafePoints(pcCafePoints)
                .clanId(clanId)
                .clanPrivileges(clanPrivileges.getMask())
                .pledgeType(pledgeType)
                .powerGrade(powerGrade)
                .academyJoinLevel(lvlJoinedAcademy)
                .apprenticeId(apprentice)
                .sponsorId(sponsor)
                .wantsPeace(wantsPeace == 1)
                .clanJoinPenaltyEndsAt(instantOrNull(clanJoinExpiryTime))
                .clanCreatePenaltyEndsAt(instantOrNull(clanCreateExpiryTime))
                .deleteAt(instantOrNull(deleteTimer))
                .build();
    }

    private static Player restore(int objectId) {
        Player player = null;
        double currentCp = 0;
        double currentHp = 0;
        double currentMp = 0;
        try {
            final Optional<CharacterRow> stored =
                    PlayerPersistenceManager.getInstance().findCharacter(objectId);
            if (stored.isPresent()) {
                final CharacterRow character = stored.get();
                final int activeClassId = character.classId();
                final boolean female = character.sex() != Sex.MALE;
                final PlayerTemplate template = PlayerTemplateData.getInstance().getTemplate(activeClassId);
                final PlayerAppearance app = new PlayerAppearance(
                        (byte) character.face(), (byte) character.hairColor(), (byte) character.hairStyle(), female);
                player = new Player(objectId, template, character.accountId(), app);
                player.setName(character.name());
                player.setLastAccess(epochMillisOrZero(character.lastAccessAt()));

                final PlayerStat stat = player.getStat();
                stat.setExp(character.exp());
                player.setExpBeforeDeath(character.expBeforeDeath());
                stat.setLevel((byte) character.level());
                stat.setSp(character.sp());

                player.setWantsPeace(character.wantsPeace() ? 1 : 0);

                player.setHeading(character.heading());

                player.setKarma(character.karma());
                player.setFame(character.fame());
                player.setPvpKills(character.pvpKills());
                player.setPkKills(character.pkKills());
                player.setOnlineTime(character.onlineTimeSeconds());
                player.setNewbie(character.newbie());
                player.setNoble(character.noble());

                if (character.faction() == Faction.GOOD) {
                    player.setGood();
                } else if (character.faction() == Faction.EVIL) {
                    player.setEvil();
                }

                player.setClanJoinExpiryTime(epochMillisOrZero(character.clanJoinPenaltyEndsAt()));
                if (player.getClanJoinExpiryTime() < ClockBridge.wallMillis()) {
                    player.setClanJoinExpiryTime(0);
                }

                player.setClanCreateExpiryTime(epochMillisOrZero(character.clanCreatePenaltyEndsAt()));
                if (player.getClanCreateExpiryTime() < ClockBridge.wallMillis()) {
                    player.setClanCreateExpiryTime(0);
                }

                player.setPcCafePoints(character.pcCafePoints());
                player.setPowerGrade(character.powerGrade());
                player.setPledgeType(character.pledgeType());
                player.setDeleteTimer(epochMillisOrZero(character.deleteAt()));
                player.setTitle(character.title());
                player.setAccessLevel(character.accessLevel());
                final int titleColor = character.titleColor();
                if (titleColor != PlayerAppearance.DEFAULT_TITLE_COLOR) {
                    player.getAppearance().setTitleColor(titleColor);
                }

                player.setFistsWeaponItem(player.findFistsWeaponItem(activeClassId));
                player.setUptime(ClockBridge.gameMillis());

                currentHp = character.currentHp();
                currentCp = character.currentCp();
                currentMp = character.currentMp();
                player.classIndex = 0;
                try {
                    player.setBaseClass(character.baseClassId());
                } catch (Exception e) {
                    player.setBaseClass(activeClassId);
                }

                // Sub-class data cannot be restored earlier in this function.
                if (restoreSubClassData(player) && (activeClassId != player.getBaseClass())) {
                    for (SubClassHolder subClass : player.subClasses.values()) {
                        if (subClass.getId() == activeClassId) {
                            player.classIndex = subClass.getClassIndex();
                        }
                    }
                }

                if ((player.getClassIndex() == 0) && (activeClassId != player.getBaseClass())) {
                    // Active sub-class missing in the DB (possible restart-while-modifysubclass cheat).
                    player.setPlayerClass(player.getBaseClass());
                    log.warn(
                            "{} reverted to base class. Possibly has tried a relogin exploit while subclassing.",
                            player);
                } else {
                    player.activeClass = activeClassId;
                }

                player.setApprentice(character.apprenticeId());
                player.setSponsor(character.sponsorId());
                player.setLvlJoinedAcademy(character.academyJoinLevel());
                player.setIn7sDungeon(character.inSevenSignsDungeon());
                CursedWeaponsManager.getInstance().checkPlayer(player);

                player.setDeathPenaltyBuffLevel(character.deathPenaltyLevel());

                player.setVitalityPoints(character.vitalityPoints(), true);

                final int x = character.x();
                final int y = character.y();
                final int z = GeoEngine.getInstance().getHeight(x, y, character.z());
                player.setXYZInvisible(x, y, z);
                player.setLastServerPosition(x, y, z);

                player.setBookmarkSlot(character.bookmarkSlots());

                player.getCreateDate().setTimeInMillis(epochMillisOrZero(character.createdAt()));

                player.setLang(character.language());

                player.setHero(Hero.getInstance().isHero(objectId));

                final int clanId = character.clanId();
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

                player.accountChars.putAll(PlayerPersistenceManager.getInstance()
                        .findOtherCharacterNames(character.accountId(), objectId));
            }
        } catch (RuntimeException e) {
            log.error("Failed loading character.", e);
        }

        if (player == null) {
            return null;
        }

        player.getInventory().restore();
        player.getWarehouse().restore();
        player.getFreight().restore();

        player.restoreSkills();

        player.getMacros().restoreMe();

        player.restoreShortcuts();

        player.restoreHenna();

        player.restoreTeleportBookmark();

        player.restoreRecipeBook(true);

        if (PlayerConfig.STORE_RECIPE_SHOPLIST) {
            player.restoreRecipeShopList();
        }

        player.loadPremiumItemList();

        player.restorePetInventoryItems();

        player.rewardSkills();

        player.restoreItemReuse();

        player.setCurrentCp(currentCp);
        player.setCurrentHp(currentHp);
        player.setCurrentMp(currentMp);

        player.setOriginalCpHpMp(currentCp, currentHp, currentMp);
        if (currentHp < 0.5) {
            player.setDead(true);
            player.stopHpMpRegeneration();
        }

        player.setPet(World.getPet(player.getObjectId()));
        if (player.hasSummon()) {
            player.getSummon().setOwner(player);
        }

        player.refreshOverloaded();

        player.refreshExpertisePenalty();

        player.restoreFriendList();

        player.setOnlineStatus(true, false);

        PlayerAutoSaveTaskManager.getInstance().add(player);

        return player;
    }

    public Forum getMail() {
        WorldPartition.checkOwner();
        if (forumMail == null) {
            final Forum root = ForumsBBSManager.getInstance().getForumByName("MailRoot");
            setMail(root.getChildByOwner(getObjectId()));
            if (forumMail == null) {
                setMail(ForumsBBSManager.getInstance()
                        .createNewForum(root, Forum.MAIL, Forum.OWNERONLY, getObjectId()));
            }
        }

        return forumMail;
    }

    public void setMail(Forum forum) {
        WorldPartition.checkOwner();
        forumMail = forum;
    }

    public Forum getMemo() {
        WorldPartition.checkOwner();
        if (forumMemo == null) {
            final Forum root = ForumsBBSManager.getInstance().getForumByName("MemoRoot");
            setMemo(root.getChildByAccount(accountId));
            if (forumMemo == null) {
                setMemo(ForumsBBSManager.getInstance().createMemoForum(root, accountId));
            }
        }

        return forumMemo;
    }

    public void setMemo(Forum forum) {
        WorldPartition.checkOwner();
        forumMemo = forum;
    }

    private static boolean restoreSubClassData(Player player) {
        try {
            for (CharacterSubclass stored :
                    PlayerPersistenceManager.getInstance().findSubclasses(player.getObjectId())) {
                final SubClassHolder subClass = new SubClassHolder();
                subClass.setPlayerClass(stored.classId());
                subClass.setLevel((byte) stored.level());
                subClass.setExp(stored.exp());
                subClass.setSp(stored.sp());
                subClass.setClassIndex(stored.classIndex());

                player.subClasses.put(subClass.getClassIndex(), subClass);
            }
        } catch (DataAccessException e) {
            log.warn("Could not restore classes for {}: {}", player.getName(), e.getMessage(), e);
        }

        return true;
    }

    private void restoreRecipeBook(boolean loadCommon) {
        try {
            final RecipeData rd = RecipeData.getInstance();
            if (loadCommon) {
                final List<CharacterRecipe> recipes =
                        PlayerPersistenceManager.getInstance().findRecipes(getObjectId());
                dwarvenRecipeBook.clear();
                for (CharacterRecipe recipe : recipes) {
                    if (!recipe.dwarven()) {
                        registerCommonRecipeList(rd.getRecipeList(recipe.recipeId()), false);
                    } else if (recipe.classIndex() == classIndex) {
                        registerDwarvenRecipeList(rd.getRecipeList(recipe.recipeId()), false);
                    }
                }
            } else {
                final List<Integer> recipeIds =
                        PlayerPersistenceManager.getInstance().findDwarvenRecipeIds(getObjectId(), classIndex);
                dwarvenRecipeBook.clear();
                for (int recipeId : recipeIds) {
                    registerDwarvenRecipeList(rd.getRecipeList(recipeId), false);
                }
            }
        } catch (DataAccessException e) {
            log.error("Could not restore recipe book data:{}", e.getMessage(), e);
        }
    }

    private void loadPremiumItemList() {
        try {
            for (CharacterPremiumItem item :
                    PlayerPersistenceManager.getInstance().findPremiumItems(getObjectId())) {
                premiumItemList.put(
                        item.itemNum(), new PremiumItem(item.itemTemplateId(), item.count(), item.senderName()));
            }
        } catch (DataAccessException e) {
            log.error("Could not restore premium items: {}", e.getMessage(), e);
        }
    }

    public void updatePremiumItem(int itemNum, long newcount) {
        WorldPartition.checkOwner();
        try {
            PlayerPersistenceManager.getInstance().updatePremiumItemCount(getObjectId(), itemNum, newcount);
        } catch (DataAccessException e) {
            log.error("Could not update premium items: {}", e.getMessage(), e);
        }
    }

    public void deletePremiumItem(int itemNum) {
        WorldPartition.checkOwner();
        try {
            PlayerPersistenceManager.getInstance().deletePremiumItem(getObjectId(), itemNum);
        } catch (DataAccessException e) {
            log.error("Could not delete premium item", e);
        }
    }

    public void store(boolean storeActiveEffects) {
        WorldPartition.checkOwner();
        storeCharacter();
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
        WorldPartition.checkOwner();
        store(true);
    }

    private void storeCharacter() {
        final List<CharacterSubclass> subclasses = new ArrayList<>(subClasses.size());
        for (SubClassHolder subClass : subClasses.values()) {
            subclasses.add(CharacterSubclass.builder()
                    .classIndex(subClass.getClassIndex())
                    .classId(subClass.getId())
                    .exp(subClass.getExp())
                    .sp(subClass.getSp())
                    .level(subClass.getLevel())
                    .build());
        }

        try {
            PlayerPersistenceManager.getInstance().saveCharacter(toCharacterRow(), subclasses);
        } catch (DataAccessException e) {
            log.warn("Could not store char base data: {} - {}", this, e.getMessage(), e);
        }
    }

    @Override
    public void storeEffect(boolean storeEffects) {
        WorldPartition.checkOwner();
        if (!PlayerConfig.STORE_SKILL_COOLTIME) {
            return;
        }

        final List<CharacterSkillSave> saves = new ArrayList<>();
        final List<Integer> storedSkills = new ArrayList<>();
        final GameClock clock = ClockBridge.clock();
        final long now = clock.gameMillis();

        if (storeEffects) {
            for (BuffInfo info : getEffectList().getEffects()) {
                if (info == null) {
                    continue;
                }

                final Skill skill = info.getSkill();

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
                saves.add(CharacterSkillSave.builder()
                        .skillId(skill.getId())
                        .skillLevel(skill.getLevel())
                        .restoreType(SkillRestoreType.EFFECT)
                        .remainingMs(stored.remainingMillis())
                        .tickPhaseMs(stored.tickPhaseMillis())
                        .reuseDelayMs(underReuse ? t.getReuse() : 0)
                        .reuseEndsAt(underReuse ? Instant.ofEpochMilli(clock.wallMillisAt(t.getStamp())) : null)
                        .build());
            }
        }

        for (Entry<Integer, TimeStamp> ts : getSkillReuseTimeStamps().entrySet()) {
            final int hash = ts.getKey();
            if (storedSkills.contains(hash)) {
                continue;
            }

            final TimeStamp t = ts.getValue();
            if ((t != null) && (now < t.getStamp())) {
                storedSkills.add(hash);
                saves.add(CharacterSkillSave.builder()
                        .skillId(t.getSkillId())
                        .skillLevel(t.getSkillLevel())
                        .restoreType(SkillRestoreType.REUSE)
                        .reuseDelayMs(t.getReuse())
                        .reuseEndsAt(Instant.ofEpochMilli(clock.wallMillisAt(t.getStamp())))
                        .build());
            }
        }

        try {
            PlayerPersistenceManager.getInstance().replaceSkillSaves(getObjectId(), classIndex, saves);
        } catch (DataAccessException e) {
            log.warn("Could not store char effect data: ", e);
        }
    }

    private void storeItemReuseDelay() {
        final List<CharacterItemReuse> reuses = new ArrayList<>();
        final GameClock clock = ClockBridge.clock();
        final long now = clock.gameMillis();
        for (TimeStamp ts : getItemReuseTimeStamps().values()) {
            if ((ts != null) && (now < ts.getStamp())) {
                reuses.add(CharacterItemReuse.builder()
                        .itemObjectId(ts.getItemObjectId())
                        .itemTemplateId(ts.getItemId())
                        .reuseDelayMs(ts.getReuse())
                        .reuseEndsAt(Instant.ofEpochMilli(clock.wallMillisAt(ts.getStamp())))
                        .build());
            }
        }

        try {
            PlayerPersistenceManager.getInstance().replaceItemReuses(getObjectId(), reuses);
        } catch (DataAccessException e) {
            log.warn("Could not store char item reuse data: ", e);
        }
    }

    public int isOnlineInt() {
        WorldPartition.checkOwner();
        if (online && (client != null)) {
            return client.isDetached() ? 2 : 1;
        }

        return 0;
    }

    public void startOfflinePlay() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        enteredWorld = true;
    }

    public boolean hasEnteredWorld() {
        WorldPartition.checkOwner();
        return enteredWorld;
    }

    public boolean isInOfflineMode() {
        WorldPartition.checkOwner();
        return (client == null) || client.isDetached();
    }

    @Override
    public Skill addSkill(Skill newSkill) {
        WorldPartition.checkOwner();
        addCustomSkill(newSkill);
        return super.addSkill(newSkill);
    }

    /** @return the skill replaced, or {@code null} when the skill is new */
    public Skill addSkill(Skill newSkill, boolean store) {
        WorldPartition.checkOwner();
        final Skill oldSkill = addSkill(newSkill);

        if (store) {
            storeSkill(newSkill, oldSkill, -1);
        }

        return oldSkill;
    }

    @Override
    public Skill removeSkill(Skill skill, boolean store) {
        WorldPartition.checkOwner();
        removeCustomSkill(skill);
        return store ? removeSkill(skill) : super.removeSkill(skill, true);
    }

    public Skill removeSkill(Skill skill, boolean store, boolean cancelEffect) {
        WorldPartition.checkOwner();
        removeCustomSkill(skill);
        return store ? removeSkill(skill) : super.removeSkill(skill, cancelEffect);
    }

    public Skill removeSkill(Skill skill) {
        WorldPartition.checkOwner();
        removeCustomSkill(skill);

        final Skill oldSkill = super.removeSkill(skill, true);
        if (oldSkill != null) {
            try {
                PlayerPersistenceManager.getInstance().deleteSkill(getObjectId(), classIndex, oldSkill.getId());
            } catch (DataAccessException e) {
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

    /** A {@code newClassIndex} above -1 stores the skill under that class index instead of the current one. */
    private void storeSkill(Skill newSkill, Skill oldSkill, int newClassIndex) {
        final int classIndex = (newClassIndex > -1) ? newClassIndex : this.classIndex;
        try {
            if ((oldSkill != null) && (newSkill != null)) {
                PlayerPersistenceManager.getInstance()
                        .updateSkillLevel(getObjectId(), classIndex, oldSkill.getId(), newSkill.getLevel());
            } else if (newSkill != null) {
                PlayerPersistenceManager.getInstance().saveSkill(getObjectId(), classIndex, toCharacterSkill(newSkill));
            }
        } catch (DataAccessException e) {
            log.warn("Error could not store char skills: {}", e.getMessage(), e);
        }
    }

    private static CharacterSkill toCharacterSkill(Skill skill) {
        return CharacterSkill.builder()
                .skillId(skill.getId())
                .skillLevel(skill.getLevel())
                .build();
    }

    private void storeSkills(List<Skill> newSkills, int newClassIndex) {
        if (newSkills.isEmpty()) {
            return;
        }

        final int classIndex = (newClassIndex > -1) ? newClassIndex : this.classIndex;
        try {
            PlayerPersistenceManager.getInstance()
                    .saveSkills(
                            getObjectId(),
                            classIndex,
                            newSkills.stream().map(Player::toCharacterSkill).toList());
        } catch (DataAccessException e) {
            log.warn("Error could not store char skills: {}", e.getMessage(), e);
        }
    }

    private void restoreSkills() {
        try {
            for (CharacterSkill stored : PlayerPersistenceManager.getInstance().findSkills(getObjectId(), classIndex)) {
                final int id = stored.skillId();
                final int level = stored.skillLevel();

                final Skill skill = SkillData.getInstance().getSkill(id, level);
                if (skill == null) {
                    log.warn(
                            "Skipped null skill Id: {} Level: {} while restoring player skills for playerObjId: {}",
                            id,
                            level,
                            getObjectId());
                    continue;
                }

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
        } catch (DataAccessException e) {
            log.warn("Could not restore character {} skills: {}", this, e.getMessage(), e);
        }
    }

    @Override
    public void restoreEffects() {
        WorldPartition.checkOwner();
        try {
            final GameClock clock = ClockBridge.clock();
            for (CharacterSkillSave save :
                    PlayerPersistenceManager.getInstance().findSkillSaves(getObjectId(), classIndex)) {
                final Skill skill = SkillData.getInstance().getSkill(save.skillId(), save.skillLevel());
                if (skill == null) {
                    continue;
                }

                if (save.reuseEndsAt() != null) {
                    final long endsAt =
                            clock.gameMillisAtWall(save.reuseEndsAt().toEpochMilli());
                    final long remaining = endsAt - clock.gameMillis();
                    if (remaining > 10) {
                        disableSkill(skill, remaining);
                        addTimeStamp(skill, save.reuseDelayMs(), endsAt);
                    }
                }

                // The remaining skills lost effect upon logout but were still under a high reuse delay.
                if (save.restoreType() == SkillRestoreType.REUSE) {
                    continue;
                }

                // Effects still active at logout; self-cast ones may carry a long reuse delay that is restored too.
                StoredEffectRows.read(
                                save.remainingMs(),
                                save.tickPhaseMs() == null ? 0 : save.tickPhaseMs(),
                                getObjectId(),
                                save.skillId())
                        .ifPresent(stored -> skill.applyStoredEffects(this, stored));
            }

            PlayerPersistenceManager.getInstance().deleteSkillSaves(getObjectId(), classIndex);
        } catch (DataAccessException e) {
            log.warn("Could not restore {} active effect data: {}", this, e.getMessage(), e);
        }
    }

    private void restoreItemReuse() {
        try {
            final GameClock clock = ClockBridge.clock();
            for (CharacterItemReuse reuse :
                    PlayerPersistenceManager.getInstance().findItemReuses(getObjectId())) {
                final int itemId = reuse.itemTemplateId();
                final long reuseDelay = reuse.reuseDelayMs();
                final long endsAt = clock.gameMillisAtWall(reuse.reuseEndsAt().toEpochMilli());
                boolean isInInventory = true;

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
                                sendPacket(
                                        new ExUseSharedGroupItem(itemId, group, (int) remainingTime, (int) reuseDelay));
                            }
                        }
                    }
                }
            }

            PlayerPersistenceManager.getInstance().deleteItemReuses(getObjectId());
        } catch (DataAccessException e) {
            log.warn("Could not restore {} Item Reuse data: {}", this, e.getMessage(), e);
        }
    }

    private void restoreHenna() {
        for (int i = 0; i < 3; i++) {
            hennaList[i] = null;
        }

        try {
            for (CharacterHenna henna : PlayerPersistenceManager.getInstance().findHennas(getObjectId(), classIndex)) {
                final int slot = henna.slot();
                if ((slot < 1) || (slot > 3) || (henna.hennaId() == 0)) {
                    continue;
                }

                hennaList[slot - 1] = HennaData.getInstance().getHenna(henna.hennaId());
            }
        } catch (DataAccessException e) {
            log.error("Failed restoing character {} hennas.", this, e);
        }

        recalcHennaStats();
    }

    public int getHennaEmptySlots() {
        WorldPartition.checkOwner();
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

    public boolean removeHenna(int slot) {
        WorldPartition.checkOwner();
        if ((slot < 1) || (slot > 3)) {
            return false;
        }

        final Henna henna = hennaList[slot - 1];
        if (henna == null) {
            return false;
        }

        hennaList[slot - 1] = null;

        try {
            PlayerPersistenceManager.getInstance().deleteHenna(getObjectId(), classIndex, slot);
        } catch (DataAccessException e) {
            log.error("Failed removing character henna.", e);
        }

        recalcHennaStats();

        sendPacket(new HennaInfo(this));

        updateUserInfo();

        inventory.addItem(ItemProcessType.RESTORE, henna.getDyeItemId(), henna.getCancelCount(), this, null);
        reduceAdena(ItemProcessType.FEE, henna.getCancelFee(), this, false);

        final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S2_S1_S);
        sm.addItemName(henna.getDyeItemId());
        sm.addLong(henna.getCancelCount());
        sendPacket(sm);
        sendPacket(SystemMessageId.THE_SYMBOL_HAS_BEEN_DELETED);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_HENNA_REMOVE, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerHennaRemove(this, henna), this);
        }

        return true;
    }

    public boolean addHenna(Henna henna) {
        WorldPartition.checkOwner();
        for (int i = 0; i < 3; i++) {
            if (hennaList[i] == null) {
                hennaList[i] = henna;

                recalcHennaStats();

                try {
                    PlayerPersistenceManager.getInstance()
                            .insertHenna(
                                    getObjectId(),
                                    classIndex,
                                    CharacterHenna.builder()
                                            .slot(i + 1)
                                            .hennaId(henna.getDyeId())
                                            .build());
                } catch (DataAccessException e) {
                    log.error("Failed saving character henna.", e);
                }

                sendPacket(new HennaInfo(this));

                updateUserInfo();

                if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_HENNA_REMOVE, this)) {
                    EventDispatcher.getInstance().notifyEventAsync(new OnPlayerHennaRemove(this, henna), this);
                }

                return true;
            }
        }

        return false;
    }

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

    public Henna getHenna(int slot) {
        WorldPartition.checkOwner();
        return (slot < 1) || (slot > 3) ? null : hennaList[slot - 1];
    }

    public boolean hasHennas() {
        WorldPartition.checkOwner();
        for (Henna henna : hennaList) {
            if (henna != null) {
                return true;
            }
        }

        return false;
    }

    public void autoSave() {
        WorldPartition.checkOwner();
        storeMe();
        storeRecommendations(false);

        if (GeneralConfig.UPDATE_ITEMS_ON_CHAR_STORE) {
            getInventory().updateDatabase();
            getWarehouse().updateDatabase();
            getFreight().updateDatabase();
        }
    }

    public boolean canLogout() {
        WorldPartition.checkOwner();
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

        // A festival participant cannot log out while the festival runs; otherwise the party is told they left it.
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

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        if (attacker == null) {
            return false;
        }

        if (isInvisible() && isGM()) {
            return false;
        }

        if ((attacker == this) || (attacker == summon)) {
            return false;
        }

        if (attacker instanceof FriendlyMob) {
            return false;
        }

        if (attacker.isMonster()) {
            return true;
        }

        final Player attackerPlayer = attacker.asPlayer();
        if (attacker.isPlayable()
                && (duelState == Duel.DUELSTATE_DUELLING)
                && (getDuelId() == attackerPlayer.getDuelId())) {
            return true;
        }

        // The party check precedes the olympiad check so party members cannot autoattack each other in olympiad.
        if (isInParty() && attacker.isPlayer() && party.containsPlayer(attacker.asPlayer())) {
            return false;
        }

        if (attacker.isPlayer() && attackerPlayer.isInOlympiadMode()) {
            return inOlympiadMode && olympiadStart && (attacker.asPlayer().getOlympiadGameId() == getOlympiadGameId());
        }

        if (isOnEvent()) {
            return isOnSoloEvent() || (getTeam() != attacker.getTeam());
        }

        if (attacker.isPlayable()) {
            if (isInsideZone(ZoneId.PEACE) || isInsideZone(ZoneId.NO_PVP)) {
                return false;
            }

            final Clan clan = getClan();
            final Clan attackerClan = attackerPlayer.getClan();
            if ((clan != null) && (attackerClan != null)) {
                if (clan != attackerClan) {
                    final Siege siege = SiegeManager.getInstance().getSiege(getX(), getY(), getZ());
                    if (siege != null) {
                        if (siege.checkIsDefender(attackerClan) && siege.checkIsDefender(clan)) {
                            return false;
                        }

                        if (siege.checkIsAttacker(attackerClan) && siege.checkIsAttacker(clan)) {
                            // If first mid victory is achieved, attackers can attack attackers.
                            final Castle castle = CastleManager.getInstance().getCastleById(siegeSide);
                            return (castle != null) && castle.isFirstMidVictory();
                        }
                    }
                }

                if ((getWantsPeace() == 0)
                        && (attackerPlayer.getWantsPeace() == 0)
                        && !isAcademyMember()
                        && clan.isAtWarWith(attackerPlayer.getClanId())
                        && attackerClan.isAtWarWith(getClanId())) {
                    return true;
                }
            }

            // Arenas (not siege zones) are checked after party but before clan/ally: there, clan/ally members can
            // autoattack unless partied.
            if ((isInsideZone(ZoneId.PVP) && attackerPlayer.isInsideZone(ZoneId.PVP))
                    && !(isInsideZone(ZoneId.SIEGE) && attackerPlayer.isInsideZone(ZoneId.SIEGE))) {
                return true;
            }

            if ((clan != null) && clan.isMember(attacker.getObjectId())) {
                return false;
            }

            if (attacker.isPlayer() && (getAllyId() != 0) && (getAllyId() == attackerPlayer.getAllyId())) {
                return false;
            }

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

        if ((getKarma() > 0) || (pvpFlag > 0)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean useMagic(Skill skill, boolean forceUse, boolean dontMove) {
        WorldPartition.checkOwner();
        if (skill.isPassive()) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (isCastingNow()) {
            if ((currentSkill != null) && (skill.getId() == currentSkill.getSkillId())) {
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            } else if (isSkillDisabled(skill)) {
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            setQueuedSkill(skill, forceUse, dontMove);
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        setCastingNow(true);

        setCurrentSkill(skill, forceUse, dontMove);
        if (queuedSkill != null) {
            setQueuedSkill(null, false, false);
        }

        if (!checkUseMagicConditions(skill, forceUse, dontMove)) {
            setCastingNow(false);
            return false;
        }

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
                target = skill.getFirstOfTargetList(this);
                break;
            }
        }

        getAI().setIntentionCast(skill, target);
        return true;
    }

    private boolean checkUseMagicConditions(Skill skill, boolean forceUse, boolean dontMove) {
        // Abnormal effects(ex : Stun, Sleep...) are checked in Creature useMagic().
        if (isOutOfControl() || isParalyzed() || isStunned() || isSleeping()) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (isDead()) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (fishing && !skill.hasEffectType(EffectType.FISHING, EffectType.FISHING_START)) {
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

        if (sitting) {
            sendPacket(SystemMessageId.YOU_CANNOT_MOVE_WHILE_SITTING);

            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (skill.isToggle() && isAffectedBySkill(skill.getId())) {
            stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // Must follow the toggle reset above.
        if (fakeDeath) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        WorldObject target = null;
        final TargetType sklTargetType = skill.getTargetType();
        if ((sklTargetType == TargetType.GROUND) && (currentSkillWorldPosition == null)) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        switch (sklTargetType) {
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

        if (target == null) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        // Skills can be used on walls and doors only during siege.
        if (target.isDoor()) {
            final Door door = target.asDoor();
            if (door.isInsideZone(ZoneId.SIEGE)) {
                final Castle castle = door.getCastle();
                if ((castle != null)
                        && (castle.getResidenceId() > 0)
                        && !castle.getSiege().isInProgress()) {
                    sendPacket(SystemMessageId.INVALID_TARGET);
                    return false;
                }

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

        if (inDuel && target.isPlayable()) {
            final Player cha = target.asPlayer();
            if (cha.getDuelId() != getDuelId()) {
                sendMessage("You cannot do this while duelling.");
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }
        }

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

        if (!skill.checkCondition(this, target, false)) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (skill.hasNegativeEffect()) {
            if (isInsidePeaceZone(this, target) && !getAccessLevel().allowPeaceAttack()) {
                sendPacket(SystemMessageId.YOU_MAY_NOT_ATTACK_THIS_TARGET_IN_A_PEACEFUL_ZONE);
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            if (inOlympiadMode && !olympiadStart) {
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
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

            if ((target instanceof EventMonster) && ((EventMonster) target).eventSkillAttackBlocked()) {
                sendPacket(ActionFailed.STATIC_PACKET);
                return false;
            }

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
                    default: {
                        sendPacket(ActionFailed.STATIC_PACKET);
                        return false;
                    }
                }
            }

            if (dontMove) {
                if (sklTargetType == TargetType.GROUND) {
                    if (!isInsideRadius2D(
                            currentSkillWorldPosition.getX(),
                            currentSkillWorldPosition.getY(),
                            currentSkillWorldPosition.getZ(),
                            skill.getCastRange() + getTemplate().getCollisionRadius())) {
                        sendPacket(SystemMessageId.YOUR_TARGET_IS_OUT_OF_RANGE);

                        sendPacket(ActionFailed.STATIC_PACKET);
                        return false;
                    }
                } else if ((skill.getCastRange() > 0)
                        && !isInsideRadius2D(
                                target, skill.getCastRange() + getTemplate().getCollisionRadius())) {
                    sendPacket(SystemMessageId.YOUR_TARGET_IS_OUT_OF_RANGE);

                    sendPacket(ActionFailed.STATIC_PACKET);
                    return false;
                }
            }
        }

        if ((skill.getEffectPoint() > 0) && target.isMonster() && !forceUse) {
            sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        switch (sklTargetType) {
            case PARTY:
            case CLAN: // For such skills, checkPvpSkill() is called from Skill.getTargetList().
            case PARTY_CLAN:
            case AURA:
            case FRONT_AURA:
            case BEHIND_AURA:
            case AREA_SUMMON:
            case GROUND:
            case SELF: {
                break;
            }
            default: {
                if (target.isPlayable() && !getAccessLevel().allowPeaceAttack() && !checkPvpSkill(target, skill)) {
                    sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);

                    sendPacket(ActionFailed.STATIC_PACKET);
                    return false;
                }
            }
        }

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

        return true;
    }

    public boolean isInLooterParty(int looterId) {
        WorldPartition.checkOwner();
        final Player looter = World.getPlayer(looterId);

        if (isInParty() && party.isInCommandChannel() && (looter != null)) {
            return party.getCommandChannel().containsPlayer(looter);
        }

        return isInParty() && (looter != null) && party.containsPlayer(looter);
    }

    public boolean checkPvpSkill(WorldObject target, Skill skill) {
        WorldPartition.checkOwner();
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

            if (isInDuel() && targetPlayer.isInDuel() && (getDuelId() == targetPlayer.getDuelId())) {
                return true;
            }

            if (isInOlympiadMode()
                    && targetPlayer.isInOlympiadMode()
                    && (getOlympiadGameId() == targetPlayer.getOlympiadGameId())) {
                return true;
            }

            if (target.isInsideZone(ZoneId.PEACE)) {
                return false;
            }

            if (skill.isPvPOnly()
                    && ((targetPlayer.getPvpFlag() == 0) && (targetPlayer.getKarma() == 0))
                    && !isInsideZone(ZoneId.PVP)
                    && !isInsideZone(ZoneId.SIEGE)) {
                return false;
            }

            if (isSiegeFriend(targetPlayer)) {
                sendPacket(SystemMessageId.FORCE_ATTACK_IS_IMPOSSIBLE_AGAINST_A_TEMPORARY_ALLIED_MEMBER_DURING_A_SIEGE);
                return false;
            }

            final boolean isCtrlPressed = (currentSkill != null) && currentSkill.isCtrlPressed();
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
                    return true;
                } else if ((getClanId() == targetPlayer.getClanId())
                        || ((getAllyId() > 0) && (getAllyId() == targetPlayer.getAllyId()))) {
                    return (skill.getEffectRange() > 0) && isCtrlPressed && (getTarget() == target) && skill.isDamage();
                }
            }

            // On retail, it is impossible to debuff a "peaceful" player.
            if ((targetPlayer.getPvpFlag() == 0) && (targetPlayer.getKarma() == 0)) {
                return (skill.getEffectRange() > 0) && isCtrlPressed && (getTarget() == target) && skill.isDamage();
            }

            return (targetPlayer.getPvpFlag() > 0) || (targetPlayer.getKarma() > 0);
        }

        return true;
    }

    public boolean isMageClass() {
        WorldPartition.checkOwner();
        return getPlayerClass().isMage();
    }

    public boolean isMounted() {
        WorldPartition.checkOwner();
        return mountType != MountType.NONE;
    }

    public boolean checkLandingState() {
        WorldPartition.checkOwner();
        if (isInsideZone(ZoneId.NO_LANDING)) {
            return true;
        } else
        // In a sieged castle only the leader of the owning clan may land.
        if (isInsideZone(ZoneId.SIEGE)
                && ((getClan() == null)
                        || (CastleManager.getInstance().getCastle(this)
                                != CastleManager.getInstance().getCastleByOwner(getClan()))
                        || (this != getClan().getLeader().getPlayer()))) {
            return true;
        }

        return false;
    }

    public void setMount(int npcId, int npcLevel) {
        WorldPartition.checkOwner();
        final MountType type = MountType.findByNpcId(npcId);
        switch (type) {
            case NONE: {
                if (isFlying()) {
                    removeSkill(CommonSkill.WYVERN_BREATH.getSkill().getId(), false);
                    setFlying(false);
                    sendSkillList();
                }
                break;
            }
            case WYVERN: {
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
        WorldPartition.checkOwner();
        super.stopAllEffects();
        updateAndBroadcastStatus();
    }

    @Override
    public void stopAllEffectsExceptThoseThatLastThroughDeath() {
        WorldPartition.checkOwner();
        super.stopAllEffectsExceptThoseThatLastThroughDeath();
        updateAndBroadcastStatus();
    }

    public void stopAllEffectsNotStayOnSubclassChange() {
        WorldPartition.checkOwner();
        getEffectList().stopAllEffectsNotStayOnSubclassChange();
        updateAndBroadcastStatus();
    }

    public void stopCubics() {
        WorldPartition.checkOwner();
        if (cubics.isEmpty()) {
            return;
        }

        try (Snapshot<Cubic> snapshot = ScratchBuffers.snapshot(cubics.values())) {
            for (Cubic cubic : snapshot) {
                cubic.stopAction();
                cubic.cancelDisappear();
            }
        }

        cubics.clear();
        cubicOrder.clear();
        broadcastUserInfo();
    }

    public void stopCubicsByOthers() {
        WorldPartition.checkOwner();
        if (cubics.isEmpty()) {
            return;
        }

        boolean broadcast = false;
        try (Snapshot<Cubic> snapshot = ScratchBuffers.snapshot(cubics.values())) {
            for (Cubic cubic : snapshot) {
                if (cubic.givenByOther()) {
                    removeCubic(cubic.getId());
                    broadcast = true;
                }
            }
        }

        if (broadcast) {
            broadcastUserInfo();
        }
    }

    @Override
    public void updateAbnormalEffect() {
        WorldPartition.checkOwner();
        broadcastUserInfo();
    }

    public void setInventoryBlockingStatus(boolean value) {
        WorldPartition.checkOwner();
        inventoryDisabled = value;
        if (value) {
            WorldPartition.loop().schedule(new InventoryEnableTask(this), 1500, TimeUnit.MILLISECONDS);
        }
    }

    /** @return the cubic with the same id that was replaced, or {@code null} */
    public Cubic addCubic(
            int cubicId,
            int level,
            double cubicPower,
            int cubicDelay,
            int cubicSkillChance,
            int cubicMaxCount,
            int cubicDuration,
            boolean givenByOther) {
        WorldPartition.checkOwner();
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

    public Cubic removeCubic(int cubicId) {
        WorldPartition.checkOwner();
        final Cubic cubic = cubics.remove(cubicId);
        cubicOrder.remove(cubicId);
        if (cubic != null) {
            cubic.stopAction();
            cubic.cancelDisappear();
        }
        return cubic;
    }

    public Cubic removeFirstCubic() {
        WorldPartition.checkOwner();
        while (!cubicOrder.isEmpty()) {
            final Cubic cubic = cubics.remove(cubicOrder.removeFirstInt());
            if (cubic != null) {
                cubic.stopAction();
                cubic.cancelDisappear();
                return cubic;
            }
        }
        return null;
    }

    public Map<Integer, Cubic> getCubics() {
        WorldPartition.checkOwner();
        return Int2ObjectSortedMaps.unmodifiable(new Int2ObjectRBTreeMap<>(cubics));
    }

    public int getCubicCount() {
        WorldPartition.checkOwner();
        return cubics.size();
    }

    /** @return a copy of the cubic ids in ascending order, as the user and char info packets list them */
    public int[] getCubicIds() {
        WorldPartition.checkOwner();
        return cubics.isEmpty() ? IntArrays.EMPTY_ARRAY : cubics.keySet().toIntArray();
    }

    public Cubic getCubicById(int cubicId) {
        WorldPartition.checkOwner();
        return cubics.get(cubicId);
    }

    public int getEnchantEffect() {
        WorldPartition.checkOwner();
        final Item wpn = getActiveWeaponInstance();
        return wpn == null ? 0 : Math.min(127, wpn.getEnchantLevel());
    }

    public boolean isFestivalParticipant() {
        WorldPartition.checkOwner();
        return SevenSignsFestival.getInstance().isParticipant(this);
    }

    public void addAutoSoulShot(int itemId) {
        WorldPartition.checkOwner();
        autoSoulShot.add(itemId);
    }

    public boolean removeAutoSoulShot(int itemId) {
        WorldPartition.checkOwner();
        return autoSoulShot.remove(itemId);
    }

    @Override
    public void rechargeShots(boolean physical, boolean magic) {
        WorldPartition.checkOwner();
        chargeAutoShots(physical ? ActionType.SOULSHOT : null, magic ? ActionType.SPIRITSHOT : null);
    }

    /** Unchecked: the player's and its summon's recharge call it after their own check. */
    void chargeAutoShots(ActionType soulshot, ActionType spiritshot) {
        if (autoSoulShot.isEmpty()) {
            return;
        }

        // A shot handler may drop its shot from the set, so the walk runs over a copy.
        try (IntSnapshot shots = ScratchBuffers.intSnapshot(autoSoulShot)) {
            for (int i = 0; i < shots.size(); i++) {
                final int itemId = shots.getInt(i);
                final Item item = inventory.getItemByItemId(itemId);
                if (item == null) {
                    autoSoulShot.remove(itemId);
                    continue;
                }

                final ActionType action = item.getTemplate().getDefaultAction();
                if (((soulshot != null) && (action == soulshot)) || ((spiritshot != null) && (action == spiritshot))) {
                    final IItemHandler handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
                    if (handler != null) {
                        handler.onItemUse(this, item, false);
                    }
                }
            }
        }
    }

    public void disableAutoShotByCrystalType(int crystalType) {
        WorldPartition.checkOwner();
        disableAutoShots(itemId ->
                ItemData.getInstance().getTemplate(itemId).getCrystalType().getLevel() == crystalType);
    }

    /** Unchecked: its callers have checked the owner. */
    void disableAutoShots(IntPredicate filter) {
        // Disabling a shot drops it from the set, so the walk runs over a copy.
        try (IntSnapshot shots = ScratchBuffers.intSnapshot(autoSoulShot)) {
            for (int i = 0; i < shots.size(); i++) {
                final int itemId = shots.getInt(i);
                if (filter.test(itemId)) {
                    disableAutoShot(itemId);
                }
            }
        }
    }

    public boolean disableAutoShot(int itemId) {
        WorldPartition.checkOwner();
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

    public void disableAutoShotsAll() {
        WorldPartition.checkOwner();
        try (IntSnapshot shots = ScratchBuffers.intSnapshot(autoSoulShot)) {
            for (int i = 0; i < shots.size(); i++) {
                final int itemId = shots.getInt(i);
                sendPacket(new ExAutoSoulShot(itemId, 0));
                final SystemMessage sm =
                        new SystemMessage(SystemMessageId.THE_AUTOMATIC_USE_OF_S1_HAS_BEEN_DEACTIVATED);
                sm.addItemName(itemId);
                sendPacket(sm);
            }
        }

        autoSoulShot.clear();
    }

    public void setClanPrivileges(ClanPrivileges clanPrivileges) {
        WorldPartition.checkOwner();
        this.clanPrivileges = clanPrivileges.clone();
    }

    public boolean hasAccess(ClanAccess access) {
        WorldPartition.checkOwner();
        return clanPrivileges.hasMinimumPrivileges(access);
    }

    public void setPledgeClass(int id) {
        WorldPartition.checkOwner();
        pledgeClass = id;
        checkItemRestriction();
    }

    @Override
    public void sendMessage(String message) {
        WorldPartition.checkOwner();
        sendPacket(new SystemMessage(SendMessageLocalisationData.getLocalisation(this, message)));
    }

    /** With the GM startup builder-hide config enabled the message goes out as a localized say packet. */
    public void sendSysMessage(String message) {
        WorldPartition.checkOwner();
        if (GeneralConfig.GM_STARTUP_BUILDER_HIDE) {
            sendPacket(new CreatureSay(
                    null, ChatType.GENERAL, "SYS", SendMessageLocalisationData.getLocalisation(this, message)));
        } else {
            sendMessage(message);
        }
    }

    /** Hiding means invisible, invulnerable and silenced; applies to GMs only. */
    public boolean setHiding(boolean hide) {
        WorldPartition.checkOwner();
        if (!isGM()) {
            return false;
        }

        if (hasEnteredWorld()) {
            if (isInvisible() && hide) {
                return false;
            }

            if (!isInvisible() && !hide) {
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
        WorldPartition.checkOwner();
        setLastLocation();

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
        WorldPartition.checkOwner();
        lastLocation.setXYZ(getX(), getY(), getZ());
    }

    public void unsetLastLocation() {
        WorldPartition.checkOwner();
        lastLocation.setXYZ(0, 0, 0);
    }

    public void enterOlympiadObserverMode(Location loc, int id, int instanceId) {
        WorldPartition.checkOwner();
        if (hasSummon()) {
            summon.unSummon(this);
        }

        getEffectList().stopSkillEffects(SkillFinishType.REMOVED, AbnormalType.HIDE);
        if (!cubics.isEmpty()) {
            try (Snapshot<Cubic> snapshot = ScratchBuffers.snapshot(cubics.values())) {
                for (Cubic cubic : snapshot) {
                    cubic.stopAction();
                    cubic.cancelDisappear();
                }
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
        WorldPartition.checkOwner();
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

        setFalling(); // Suppresses falling damage.
        observerMode = false;
        broadcastUserInfo();
    }

    public void leaveOlympiadObserverMode() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return observerMode;
    }

    public void setLoto(int i, int value) {
        WorldPartition.checkOwner();
        loto[i] = value;
    }

    public int getLoto(int i) {
        WorldPartition.checkOwner();
        return loto[i];
    }

    public void setRaceTicket(int i, int value) {
        WorldPartition.checkOwner();
        raceTickets[i] = value;
    }

    public int getRaceTicket(int i) {
        WorldPartition.checkOwner();
        return raceTickets[i];
    }

    public boolean getMessageRefusal() {
        WorldPartition.checkOwner();
        return messageRefusal;
    }

    public void setMessageRefusal(boolean mode) {
        WorldPartition.checkOwner();
        messageRefusal = mode;
        sendPacket(new EtcStatusUpdate(this));
    }

    public boolean getDietMode() {
        WorldPartition.checkOwner();
        return dietMode;
    }

    public boolean getTradeRefusal() {
        WorldPartition.checkOwner();
        return tradeRefusal;
    }

    public boolean getExchangeRefusal() {
        WorldPartition.checkOwner();
        return exchangeRefusal;
    }

    public void setHero(boolean hero) {
        WorldPartition.checkOwner();
        if (hero && (baseClass == activeClass)) {
            for (Skill skill : SkillTreeData.getInstance().getHeroSkillTree().values()) {
                addSkill(skill, false); // Don't persist hero skills into database.
            }
        } else {
            for (Skill skill : SkillTreeData.getInstance().getHeroSkillTree().values()) {
                removeSkill(skill, false, true);
            }
        }

        this.hero = hero;
        sendSkillList();
    }

    public void setStartingDuel() {
        WorldPartition.checkOwner();
        startingDuel = true;
    }

    /** @param duelId 0 for not in a duel */
    public void setInDuel(int duelId) {
        WorldPartition.checkOwner();
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

    public SystemMessage getNoDuelReason() {
        WorldPartition.checkOwner();
        final SystemMessage sm = new SystemMessage(noDuelReason);
        sm.addPcName(this);
        noDuelReason = SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL;
        return sm;
    }

    /** On {@code false}, {@link #getNoDuelReason()} gives the reason. */
    public boolean canDuel() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return lvlJoinedAcademy > 0;
    }

    @Override
    public void setTeam(Team team) {
        WorldPartition.checkOwner();
        super.setTeam(team);
        broadcastUserInfo();
        if (hasSummon()) {
            summon.broadcastStatusUpdate();
        }
    }

    public void sendSkillList() {
        WorldPartition.checkOwner();
        if (skillListTask == null) {
            skillListTask = WorldPartition.loop()
                    .schedule(
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

                                    boolean isEnchantable =
                                            SkillData.getInstance().isEnchantable(skill.getId());
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
                                                transformSkill.getKey(),
                                                transformSkill.getValue(),
                                                false,
                                                false,
                                                false);
                                    }
                                }

                                sendPacket(skillList);
                                skillListTask = null;
                            },
                            300,
                            TimeUnit.MILLISECONDS);
        }
    }

    public boolean addSubClass(int classId, int classIndex) {
        WorldPartition.checkOwner();
        if (changingClass) {
            return false;
        }

        changingClass = true;

        try {
            if ((getTotalSubClasses() == PlayerConfig.MAX_SUBCLASS) || (classIndex == 0)) {
                return false;
            }

            if (subClasses.containsKey(classIndex)) {
                return false;
            }

            // classIndex may be changed only by setActiveClass().

            final SubClassHolder newClass = new SubClassHolder();
            newClass.setPlayerClass(classId);
            newClass.setClassIndex(classIndex);

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
                }
            }

            try {
                PlayerPersistenceManager.getInstance()
                        .insertSubclass(
                                getObjectId(),
                                CharacterSubclass.builder()
                                        .classIndex(newClass.getClassIndex())
                                        .classId(newClass.getId())
                                        .exp(newClass.getExp())
                                        .sp(newClass.getSp())
                                        .level(newClass.getLevel())
                                        .build(),
                                prevSkillList.values().stream()
                                        .map(Player::toCharacterSkill)
                                        .toList());
            } catch (DataAccessException e) {
                log.warn("WARNING: Could not add character sub class for {}: {}", getName(), e.getMessage(), e);
                return false;
            }

            // Only after the INSERT succeeded, so a thrown exception leaves no phantom sub-class.
            subClasses.put(newClass.getClassIndex(), newClass);

            return true;
        } finally {
            changingClass = false;
        }
    }

    /** On an exception the player is reverted to the base class. */
    public boolean modifySubClass(int classIndex, int newClassId) {
        WorldPartition.checkOwner();
        final SubClassHolder subClass = subClasses.get(classIndex);
        if (subClass == null) {
            return false;
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_PROFESSION_CANCEL, this)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerProfessionCancel(this, subClass.getId()), this);
        }

        subClasses.remove(classIndex);

        try {
            PlayerPersistenceManager.getInstance().deleteSubclass(getObjectId(), classIndex);
        } catch (DataAccessException e) {
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
        WorldPartition.checkOwner();
        return classIndex > 0;
    }

    public int getTotalSubClasses() {
        WorldPartition.checkOwner();
        return subClasses.size();
    }

    private void setClassTemplate(int classId) {
        activeClass = classId;

        final PlayerTemplate pcTemplate = PlayerTemplateData.getInstance().getTemplate(classId);
        if (pcTemplate == null) {
            log.error("Missing template for classId: {}", classId);
            throw new Error();
        }

        setTemplate(pcTemplate);

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

    /** Index 0 is the base class, 1-3 the sub-classes. */
    public void setActiveClass(int classIndex) {
        WorldPartition.checkOwner();
        if (changingClass) {
            return;
        }

        changingClass = true;

        try {
            if (transformation != null) {
                return;
            }

            // Item skills go before saving: the worn items can differ next time this class is chosen.
            for (Item item : inventory.getAugmentedItems()) {
                if ((item != null) && item.isEquipped()) {
                    item.getAugmentation().removeBonus(this);
                }
            }

            abortCast();

            if (isChannelized()) {
                getSkillChannelized().abortChannelization();
            }

            // store() runs before classIndex changes, to avoid skill effects rolling over.
            store(PlayerConfig.SUBCLASS_STORE_SKILL_COOLTIME);

            resetTimeStamps();

            charges = 0;
            stopChargeTask();

            if (hasServitor()) {
                summon.unSummon(this);
            }

            if (classIndex == 0) {
                setClassTemplate(baseClass);
            } else {
                try {
                    setClassTemplate(subClasses.get(classIndex).getId());
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

            autoUseSettings.clearAutoSkills();
            autoUseSettings.clearAutoBuffs();
            for (Skill oldSkill : getAllSkills()) {
                removeSkill(oldSkill, false, true);
            }

            stopAllEffectsExceptThoseThatLastThroughDeath();
            stopAllEffectsNotStayOnSubclassChange();
            getEffectList().stopAllToggles();
            stopCubics();
            restoreRecipeBook(false);

            restoreDeathPenaltyBuffLevel();

            restoreSkills();
            rewardSkills();
            regiveTemporarySkills();
            getInventory().applyItemSkills();

            // Avoids issues when switching between sub-classes that share skills.
            resetDisabledSkills();

            restoreEffects();

            sendPacket(new EtcStatusUpdate(this));

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
        WorldPartition.checkOwner();
        if (taskWarnUserTakeBreak == null) {
            return;
        }

        taskWarnUserTakeBreak.cancel(true);
        taskWarnUserTakeBreak = null;
    }

    public void startWarnUserTakeBreak() {
        WorldPartition.checkOwner();
        if (taskWarnUserTakeBreak == null) {
            taskWarnUserTakeBreak = WorldPartition.loop()
                    .scheduleAtFixedRate(new WarnUserTakeBreakTask(this), 7200000, 7200000, TimeUnit.MILLISECONDS);
        }
    }

    public void stopRentPet() {
        WorldPartition.checkOwner();
        if (taskRentPet != null) {
            // If the rent of a wyvern expires while over a flying zone, tp to down before unmounting.
            if (checkLandingState() && (mountType == MountType.WYVERN)) {
                teleToLocation(TeleportWhereType.TOWN);
            }

            if (dismount()) {
                taskRentPet.cancel(true);
                taskRentPet = null;
            }
        }
    }

    public void startRentPet(int seconds) {
        WorldPartition.checkOwner();
        if (taskRentPet == null) {
            taskRentPet = WorldPartition.loop()
                    .scheduleAtFixedRate(new RentPetTask(this), seconds * 1000, seconds * 1000, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isRentedPet() {
        WorldPartition.checkOwner();
        return taskRentPet != null;
    }

    public void stopWaterTask() {
        WorldPartition.checkOwner();
        if (taskWater == null) {
            return;
        }

        taskWater.cancel(false);
        taskWater = null;
        sendPacket(new SetupGauge(getObjectId(), 2, 0));
    }

    public void startWaterTask() {
        WorldPartition.checkOwner();
        if (isDead() || (taskWater != null)) {
            return;
        }

        final int baseTimeInWater = 60000;

        final double breathPercentage = calcStat(Stat.BREATH, getBaseTemplate().getBaseBreath()) / 100d;

        final int timeInWater = (int) (baseTimeInWater * breathPercentage);

        sendPacket(new SetupGauge(getObjectId(), 2, timeInWater));

        taskWater = WorldPartition.loop()
                .scheduleAtFixedRate(new WaterTask(this), timeInWater, 1000, TimeUnit.MILLISECONDS);
    }

    public boolean isInWater() {
        WorldPartition.checkOwner();
        return taskWater != null;
    }

    public void checkWaterState() {
        WorldPartition.checkOwner();
        if (isInsideZone(ZoneId.WATER)) {
            startWaterTask();
        } else {
            stopWaterTask();
        }
    }

    public void onPlayerEnter() {
        WorldPartition.checkOwner();
        startWarnUserTakeBreak();

        if (SevenSigns.getInstance().isSealValidationPeriod()
                || SevenSigns.getInstance().isCompResultsPeriod()) {
            if (!isGM()
                    && isIn7sDungeon()
                    && (SevenSigns.getInstance().getPlayerCabal(getObjectId())
                            != SevenSigns.getInstance().getCabalHighestScore())) {
                teleToLocation(TeleportWhereType.TOWN);
                setIn7sDungeon(false);
                sendMessage("You have been teleported to the nearest town due to the beginning of the Seal Validation"
                        + " period.");
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

        if (!isDead()) {
            WorldPartition.loop()
                    .schedule(
                            () -> {
                                setCurrentHp(originalHp);
                                setCurrentMp(originalMp);
                                setCurrentCp(originalCp);
                            },
                            300,
                            TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void fullRestore() {
        WorldPartition.checkOwner();
        setCurrentHp(getMaxHp());
        setCurrentMp(getMaxMp());
        setCurrentCp(getMaxCp());
    }

    @Override
    public void doRevive() {
        WorldPartition.checkOwner();
        super.doRevive();

        DecayTaskManager.getInstance().cancel(this);

        updateEffectIcons();
        sendPacket(new EtcStatusUpdate(this));
        revivingPet = false;
        reviveRequested = 0;
        revivePower = 0;

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
        WorldPartition.checkOwner();
        super.setName(value);
        CharInfoTable.getInstance().addName(this);
    }

    @Override
    public void doRevive(double revivePower) {
        WorldPartition.checkOwner();
        doRevive();
        restoreExp(revivePower);
    }

    public void reviveRequest(Player reviver, boolean pet, int power) {
        WorldPartition.checkOwner();
        if (isResurrectionBlocked()) {
            return;
        }

        if (reviveRequested == 1) {
            if (revivingPet == pet) {
                reviver.sendPacket(SystemMessageId.RESURRECTION_HAS_ALREADY_BEEN_PROPOSED);
            } else if (pet) {
                reviver.sendPacket(
                        SystemMessageId.A_PET_CANNOT_BE_RESURRECTED_WHILE_IT_S_OWNER_IS_IN_THE_PROCESS_OF_RESURRECTING);
            } else {
                reviver.sendPacket(
                        SystemMessageId.WHILE_A_PET_IS_BEING_RESURRECTED_IT_CANNOT_HELP_IN_RESURRECTING_ITS_MASTER);
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return reviveRequested == 1;
    }

    public void removeReviving() {
        WorldPartition.checkOwner();
        reviveRequested = 0;
        revivePower = 0;
    }

    public void onActionRequest() {
        WorldPartition.checkOwner();
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

    /** 0=none, 1=D, 2=C, 3=B, 4=A, 5=S, 6=S80, 7=S84. */
    public int getExpertiseLevel() {
        WorldPartition.checkOwner();
        return getSkillLevel(239);
    }

    @Override
    public void teleToLocation(ILocational loc, boolean allowRandomOffset) {
        WorldPartition.checkOwner();
        if ((vehicle != null) && !vehicle.isTeleporting()) {
            setVehicle(null);
        }

        if (isFlyingMounted() && (loc.getZ() < -1005)) {
            super.teleToLocation(loc.getX(), loc.getY(), -1005, loc.getHeading(), loc.getInstanceId());
        }

        super.teleToLocation(loc, allowRandomOffset);
    }

    @Override
    public void onTeleported() {
        WorldPartition.checkOwner();
        super.onTeleported();

        if (isInAirShip()) {
            getAirShip().sendInfo(this);
        } else {
            setLastServerPosition(getX(), getY(), getZ());
        }

        revalidateZone(true);

        checkItemRestriction();

        if ((PlayerConfig.PLAYER_TELEPORT_PROTECTION > 0) && !inOlympiadMode) {
            setTeleportProtection(true);
        }

        // Trained beast is lost after teleport; a deleted beast leaves the set itself, so the walk runs over a copy.
        if (trainedBeasts != null) {
            try (Snapshot<TamedBeast> beasts = ScratchBuffers.snapshot(trainedBeasts)) {
                for (TamedBeast tamedBeast : beasts) {
                    tamedBeast.deleteMe();
                }
            }

            trainedBeasts.clear();
        }

        if (summon != null) {
            summon.setFollowStatus(false);
            summon.teleToLocation(getLocation(), false);
            ((SummonAI) summon.getAI()).setStartFollowController(true);
            summon.setFollowStatus(true);
            summon.updateAndBroadcastStatus(0);
        }

        if (movieHolder != null) {
            sendPacket(new ExStartScenePlayer(movieHolder.getMovie()));
        }

        if (AutoPlayConfig.ENABLE_AUTO_PLAY) {
            AutoPlayTaskManager.getInstance().stopAutoPlay(this);
            AutoUseTaskManager.getInstance().stopAutoUseTask(this);
        }

        broadcastInfo();
    }

    @Override
    public void setTeleporting(boolean teleport) {
        WorldPartition.checkOwner();
        setTeleporting(teleport, true);
    }

    public void setTeleporting(boolean teleport, boolean useWatchDog) {
        WorldPartition.checkOwner();
        super.setTeleporting(teleport);
        if (!useWatchDog) {
            return;
        }

        if (teleport) {
            if ((teleportWatchdog == null) && (PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT > 0)) {
                teleportWatchdog = WorldPartition.loop()
                        .schedule(
                                new TeleportWatchdogTask(this),
                                PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT * 1000,
                                TimeUnit.MILLISECONDS);
            }
        } else if (teleportWatchdog != null) {
            teleportWatchdog.cancel(false);
            teleportWatchdog = null;
        }
    }

    public void setLastServerPosition(int x, int y, int z) {
        WorldPartition.checkOwner();
        lastServerPosition.setXYZ(x, y, z);
    }

    public void setBlinkActive(boolean value) {
        WorldPartition.checkOwner();
        blinkActive = value;
    }

    public boolean isBlinkActive() {
        WorldPartition.checkOwner();
        return blinkActive;
    }

    @Override
    public void addExpAndSp(double addToExp, double addToSp) {
        WorldPartition.checkOwner();
        getStat().addExpAndSp(addToExp, addToSp, false);
    }

    public void addExpAndSp(double addToExp, double addToSp, boolean useVitality) {
        WorldPartition.checkOwner();
        getStat().addExpAndSp(addToExp, addToSp, useVitality);
    }

    public void removeExpAndSp(long removeExp, long removeSp) {
        WorldPartition.checkOwner();
        getStat().removeExpAndSp(removeExp, removeSp, true);
    }

    public void removeExpAndSp(long removeExp, long removeSp, boolean sendMessage) {
        WorldPartition.checkOwner();
        getStat().removeExpAndSp(removeExp, removeSp, sendMessage);
    }

    @Override
    public void reduceCurrentHp(double value, Creature attacker, boolean awake, boolean isDOT, Skill skill) {
        WorldPartition.checkOwner();
        if (skill != null) {
            getStatus().reduceHp(value, attacker, awake, isDOT, skill.isToggle(), skill.getDmgDirectlyToHP());
        } else {
            getStatus().reduceHp(value, attacker, awake, isDOT, false, false);
        }

        if ((trainedBeasts != null) && !trainedBeasts.isEmpty()) {
            try (Snapshot<TamedBeast> beasts = ScratchBuffers.snapshot(trainedBeasts)) {
                for (TamedBeast tamedBeast : beasts) {
                    tamedBeast.onOwnerGotAttacked(attacker);
                }
            }
        }
    }

    public void broadcastSnoop(ChatType type, String name, String text, CreatureSay cs) {
        WorldPartition.checkOwner();
        if (!snoopListener.isEmpty()) {
            snoop(new Snoop(getObjectId(), getName(), type, name, text), port -> port.legacy(cs));
        }
    }

    public void broadcastSnoop(ChatMessage message) {
        WorldPartition.checkOwner();
        if (!snoopListener.isEmpty()) {
            snoop(new Snoop(getObjectId(), getName(), message), port -> port.emit(message));
        }
    }

    // The snooper's port runs no send-time side effect, so two GMs snooping each other cannot loop.
    private void snoop(Snoop line, Consumer<ClientPort> forward) {
        try (Snapshot<Player> snoopers = ScratchBuffers.snapshot(snoopListener)) {
            for (Player snooper : snoopers) {
                final ClientPort port = snooper.getPort();
                forward.accept(port);
                port.legacy(line);
            }
        }
    }

    public void addSnooper(Player pci) {
        WorldPartition.checkOwner();
        snoopListener.add(pci);
    }

    public void removeSnooper(Player pci) {
        WorldPartition.checkOwner();
        snoopListener.remove(pci);
    }

    public void addSnooped(Player pci) {
        WorldPartition.checkOwner();
        snoopedPlayer.add(pci);
    }

    public void removeSnooped(Player pci) {
        WorldPartition.checkOwner();
        snoopedPlayer.remove(pci);
    }

    public void addHtmlAction(HtmlActionScope scope, String action) {
        WorldPartition.checkOwner();
        htmlActionCaches[scope.ordinal()].add(action);
    }

    public void clearHtmlActions(HtmlActionScope scope) {
        WorldPartition.checkOwner();
        htmlActionCaches[scope.ordinal()].clear();
    }

    public void setHtmlActionOriginObjectId(HtmlActionScope scope, int npcObjId) {
        WorldPartition.checkOwner();
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

    /** @return -1 if the action was not sent in an html packet, 0 if not bound to an NPC (no range check), else the NPC object id */
    public int validateHtmlAction(String action) {
        WorldPartition.checkOwner();
        for (int i = 0; i < htmlActionCaches.length; ++i) {
            if (validateHtmlAction(htmlActionCaches[i], action)) {
                lastHtmlActionOriginId = htmlActionOriginObjectIds[i];
                return lastHtmlActionOriginId;
            }
        }

        return -1;
    }

    public boolean validateItemManipulation(int objectId, ItemProcessType itemProcessType) {
        WorldPartition.checkOwner();
        final Item item = inventory.getItemByObjectId(objectId);
        if ((item == null) || (item.getOwnerId() != getObjectId())) {
            log.trace("{}: player tried to {} item he is not owner of.", getObjectId(), itemProcessType);
            return false;
        }

        if ((hasSummon() && (summon.getControlObjectId() == objectId)) || (mountObjectID == objectId)) {
            return false;
        }

        if (activeEnchantItemId == objectId) {
            return false;
        }

        if (CursedWeaponsManager.getInstance().isCursed(item.getId())) {
            return false;
        }

        return true;
    }

    public boolean isInBoat() {
        WorldPartition.checkOwner();
        return (vehicle != null) && vehicle.isBoat();
    }

    public Boat getBoat() {
        WorldPartition.checkOwner();
        return (Boat) vehicle;
    }

    public boolean isInAirShip() {
        WorldPartition.checkOwner();
        return (vehicle != null) && vehicle.isAirShip();
    }

    public AirShip getAirShip() {
        WorldPartition.checkOwner();
        return (AirShip) vehicle;
    }

    public void setVehicle(Vehicle v) {
        WorldPartition.checkOwner();
        if ((v == null) && (vehicle != null)) {
            vehicle.removePassenger(this);
        }

        vehicle = v;
    }

    public boolean isInVehicle() {
        WorldPartition.checkOwner();
        return vehicle != null;
    }

    /** Only erases the player from the world; to disconnect a player use {@link Disconnection}. */
    @Override
    public boolean deleteMe() {
        WorldPartition.checkOwner();
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

        getVariables().saveNow();

        getAccountVariables().saveNow();

        // Recommendations must be saved before task (timer) is canceled.
        try {
            storeRecommendations(false);
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

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

        try {
            RecipeManager.getInstance().requestMakeItemAbort(this);
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            setTarget(null);
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        if (isChannelized()) {
            getSkillChannelized().abortChannelization();
        }

        getEffectList().stopAllToggles();

        final ZoneRegion region = ZoneManager.getInstance().getRegion(this);
        if (region != null) {
            region.removeFromZones(this);
        }

        if (isInParty()) {
            try {
                leaveParty();
            } catch (Exception e) {
                log.error("deleteMe()", e);
            }
        }

        stopCubics();

        try {
            decayMe();
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        if (OlympiadManager.getInstance().isRegistered(this) || (getOlympiadGameId() != -1)) {
            OlympiadManager.getInstance().removeDisconnectedCompetitor(this);
        }

        if (hasSummon()) {
            try {
                summon.setRestoreSummon(true);

                summon.unSummon(this);

                // A dead pet is not unsummoned: broadcast its npcinfo so it shows without the (offline) owner name.
                if (hasSummon()) {
                    summon.broadcastNpcInfo(0);
                }
            } catch (Exception e) {
                log.error("deleteMe()", e);
            }
        }

        if (clan != null) {
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

        if (isGM()) {
            try {
                AdminData.getInstance().deleteGm(this);
            } catch (Exception e) {
                log.error("deleteMe()", e);
            }
        }

        try {
            if (observerMode) {
                setLocationInvisible(lastLocation);
            }

            if (vehicle != null) {
                vehicle.oustPlayer(this);
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

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
                        if (hasSummon()) {
                            summon.teleToLocation(loc, true);
                            summon.setInstanceId(0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        try {
            inventory.deleteMe();
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

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
        }

        try (Snapshot<Player> snooped = ScratchBuffers.snapshot(snoopedPlayer)) {
            for (Player player : snooped) {
                player.removeSnooper(this);
            }
        }

        try (Snapshot<Player> snoopers = ScratchBuffers.snapshot(snoopListener)) {
            for (Player player : snoopers) {
                player.removeSnooped(this);
            }
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

    public void startFishing(int x, int y, int z) {
        WorldPartition.checkOwner();
        stopMove(null);
        setImmobilized(true);
        fishing = true;
        fishX = x;
        fishY = y;
        fishZ = z;

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

        broadcastPacket(new ExFishingStart(this, this.fish.getFishGroup(), x, y, z, lure.isNightLure()));
        sendPacket(new PlaySound(1, "SF_P_01", 0, 0, 0, 0, 0));
        startLookingForFishTask();
    }

    public void stopLookingForFishTask() {
        WorldPartition.checkOwner();
        if (taskForFish == null) {
            return;
        }

        taskForFish.cancel(false);
        taskForFish = null;
    }

    public void startLookingForFishTask() {
        WorldPartition.checkOwner();
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

            // TODO: lures outside the lists above have no guts-check period (#77); fishing ends instead of hanging.
            if (checkDelay <= 0) {
                endFishing(false);
                return;
            }

            taskForFish = WorldPartition.loop()
                    .scheduleAtFixedRate(
                            new LookingForFishTask(
                                    this,
                                    fish.getStartCombatTime(),
                                    fish.getFishGuts(),
                                    fish.getFishGroup(),
                                    isNoob,
                                    isUpperGrade),
                            10000,
                            checkDelay,
                            TimeUnit.MILLISECONDS);
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
        WorldPartition.checkOwner();
        // Every night lure fights like a night catch, the fish turns deceptive and reverses reeling and pumping.
        fishCombat = new Fishing(this, fish, isNoob, isUpperGrade || lure.isNightLure(), lure.getId());
    }

    public void endFishing(boolean win) {
        WorldPartition.checkOwner();
        fishing = false;
        fishX = 0;
        fishY = 0;
        fishZ = 0;

        if (fishCombat == null) {
            sendPacket(SystemMessageId.THE_BAIT_HAS_BEEN_LOST_BECAUSE_THE_FISH_GOT_AWAY);
        }

        fishCombat = null;
        lure = null;

        broadcastPacket(new ExFishingEnd(win, this));
        sendPacket(SystemMessageId.YOU_REEL_YOUR_LINE_IN_AND_STOP_FISHING);
        setImmobilized(false);
        stopLookingForFishTask();
    }

    public int getInventoryLimit() {
        WorldPartition.checkOwner();
        int ivlim;
        if (isGM()) {
            ivlim = PlayerConfig.INVENTORY_MAXIMUM_GM;
        } else {
            ivlim = getRace() == Race.DWARF
                    ? PlayerConfig.INVENTORY_MAXIMUM_DWARF
                    : PlayerConfig.INVENTORY_MAXIMUM_NO_DWARF;
        }

        return ivlim += (int) calcStat(Stat.INV_LIM, 0, null, null);
    }

    public int getWareHouseLimit() {
        WorldPartition.checkOwner();
        return (getRace() == Race.DWARF ? PlayerConfig.WAREHOUSE_SLOTS_DWARF : PlayerConfig.WAREHOUSE_SLOTS_NO_DWARF)
                + (int) calcStat(Stat.WH_LIM, 0, null, null);
    }

    public int getPrivateSellStoreLimit() {
        WorldPartition.checkOwner();
        return (getRace() == Race.DWARF
                        ? PlayerConfig.MAX_PVTSTORESELL_SLOTS_DWARF
                        : PlayerConfig.MAX_PVTSTORESELL_SLOTS_OTHER)
                + (int) calcStat(Stat.P_SELL_LIM, 0, null, null);
    }

    public int getPrivateBuyStoreLimit() {
        WorldPartition.checkOwner();
        return (getRace() == Race.DWARF
                        ? PlayerConfig.MAX_PVTSTOREBUY_SLOTS_DWARF
                        : PlayerConfig.MAX_PVTSTOREBUY_SLOTS_OTHER)
                + (int) calcStat(Stat.P_BUY_LIM, 0, null, null);
    }

    public int getDwarfRecipeLimit() {
        WorldPartition.checkOwner();
        return PlayerConfig.DWARF_RECIPE_LIMIT + (int) calcStat(Stat.REC_D_LIM, 0, null, null);
    }

    public int getCommonRecipeLimit() {
        WorldPartition.checkOwner();
        return PlayerConfig.COMMON_RECIPE_LIMIT + (int) calcStat(Stat.REC_C_LIM, 0, null, null);
    }

    public void setCurrentSkill(Skill currentSkill, boolean ctrlPressed, boolean shiftPressed) {
        WorldPartition.checkOwner();
        if (currentSkill == null) {
            this.currentSkill = null;
            return;
        }

        this.currentSkill = new SkillUseHolder(currentSkill, ctrlPressed, shiftPressed);
    }

    public void setCurrentPetSkill(Skill currentSkill, boolean ctrlPressed, boolean shiftPressed) {
        WorldPartition.checkOwner();
        if (currentSkill == null) {
            currentPetSkill = null;
            return;
        }

        currentPetSkill = new SkillUseHolder(currentSkill, ctrlPressed, shiftPressed);
    }

    public void setQueuedSkill(Skill queuedSkill, boolean ctrlPressed, boolean shiftPressed) {
        WorldPartition.checkOwner();
        if (queuedSkill == null) {
            this.queuedSkill = null;
            return;
        }

        this.queuedSkill = new SkillUseHolder(queuedSkill, ctrlPressed, shiftPressed);
    }

    public boolean isJailed() {
        WorldPartition.checkOwner();
        return PunishmentManager.getInstance()
                        .hasPunishment(getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.JAIL) //
                || PunishmentManager.getInstance()
                        .hasPunishment(accountId, PunishmentAffect.ACCOUNT, PunishmentType.JAIL) //
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

    public boolean isChatBanned() {
        WorldPartition.checkOwner();
        return PunishmentManager.getInstance()
                        .hasPunishment(getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.CHAT_BAN) //
                || PunishmentManager.getInstance()
                        .hasPunishment(accountId, PunishmentAffect.ACCOUNT, PunishmentType.CHAT_BAN) //
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
        WorldPartition.checkOwner();
        if (!PlayerConfig.FAME_SYSTEM_ENABLED) {
            return;
        }

        if ((getLevel() < 40) || (getPlayerClass().level() < 2)) {
            return;
        }

        if (fameTask == null) {
            fameTask = WorldPartition.loop()
                    .scheduleAtFixedRate(new FameTask(this, fameFixRate), delay, delay, TimeUnit.MILLISECONDS);
        }
    }

    public void stopFameTask() {
        WorldPartition.checkOwner();
        if (fameTask == null) {
            return;
        }

        fameTask.cancel(false);
        fameTask = null;
    }

    public void startVitalityTask() {
        WorldPartition.checkOwner();
        if (PlayerConfig.ENABLE_VITALITY && (vitalityTask == null)) {
            vitalityTask = WorldPartition.loop()
                    .scheduleAtFixedRate(new VitalityTask(this), 1000, 60000, TimeUnit.MILLISECONDS);
        }
    }

    public void stopVitalityTask() {
        WorldPartition.checkOwner();
        if (vitalityTask != null) {
            vitalityTask.cancel(false);
            vitalityTask = null;
        }
    }

    public boolean isCursedWeaponEquipped() {
        WorldPartition.checkOwner();
        return cursedWeaponEquippedId != 0;
    }

    public void increaseSouls(int count) {
        WorldPartition.checkOwner();
        chargedSouls += count;
        final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_SOUL_COUNT_HAS_INCREASED_BY_S1_IT_IS_NOW_AT_S2);
        sm.addInt(count);
        sm.addInt(chargedSouls);
        sendPacket(sm);
        restartSoulTask();
        sendPacket(new EtcStatusUpdate(this));
    }

    public boolean decreaseSouls(int count) {
        WorldPartition.checkOwner();
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

    public void clearSouls() {
        WorldPartition.checkOwner();
        chargedSouls = 0;
        stopSoulTask();
        sendPacket(new EtcStatusUpdate(this));
    }

    private void restartSoulTask() {
        if (soulTask != null) {
            soulTask.cancel(false);
            soulTask = null;
        }

        soulTask = WorldPartition.loop().schedule(new ResetSoulsTask(this), 600000, TimeUnit.MILLISECONDS);
    }

    public void stopSoulTask() {
        WorldPartition.checkOwner();
        if (soulTask == null) {
            return;
        }

        soulTask.cancel(false);
        soulTask = null;
    }

    public void calculateDeathPenaltyBuffLevel(Creature killer) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        if (deathPenaltyBuffLevel > 0) {
            addSkill(SkillData.getInstance().getSkill(5076, getDeathPenaltyBuffLevel()), false);
        }
    }

    @Override
    public void sendDamageMessage(Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return ((PlayerStat) uncheckedStat()).getVitalityPoints();
    }

    public int getVitalityLevel() {
        WorldPartition.checkOwner();
        return ((PlayerStat) uncheckedStat()).getVitalityLevel();
    }

    public void setVitalityPoints(int points, boolean quiet) {
        WorldPartition.checkOwner();
        getStat().setVitalityPoints(points, quiet);
    }

    public void updateVitalityPoints(float points, boolean useRates, boolean quiet) {
        WorldPartition.checkOwner();
        getStat().updateVitalityPoints(points, useRates, quiet);
    }

    public void checkItemRestriction() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        transformSkills.put(sk.getId(), sk);
        if (sk.isPassive()) {
            addSkill(sk, false);
        }
    }

    public Skill getTransformSkill(int id) {
        WorldPartition.checkOwner();
        return transformSkills.get(id);
    }

    public boolean hasTransformSkill(int id) {
        WorldPartition.checkOwner();
        return transformSkills.containsKey(id);
    }

    public void removeAllTransformSkills() {
        WorldPartition.checkOwner();
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
                mountFeedTask = WorldPartition.loop()
                        .scheduleAtFixedRate(new PetFeedTask(this), 10000, 10000, TimeUnit.MILLISECONDS);
            }
        } else if (canFeed) {
            setCurrentFeed(getMaxFeed());
            sendPacket(new SetupGauge(
                    getObjectId(),
                    3,
                    (currentFeed * 10000) / getFeedConsume(),
                    (getMaxFeed() * 10000) / getFeedConsume()));
            if (!isDead()) {
                mountFeedTask = WorldPartition.loop()
                        .scheduleAtFixedRate(new PetFeedTask(this), 10000, 10000, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void stopFeed() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        if (isAttackingNow()) {
            return getPetLevelData(mountNpcId).getPetFeedBattle();
        }

        return getPetLevelData(mountNpcId).getPetFeedNormal();
    }

    public void setCurrentFeed(int num) {
        WorldPartition.checkOwner();
        final boolean lastHungryState = isHungry();
        currentFeed = num > getMaxFeed() ? getMaxFeed() : num;
        sendPacket(new SetupGauge(
                getObjectId(), 3, (currentFeed * 10000) / getFeedConsume(), (getMaxFeed() * 10000) / getFeedConsume()));

        if (lastHungryState != isHungry()) {
            broadcastUserInfo();
        }
    }

    private int getMaxFeed() {
        return getPetLevelData(mountNpcId).getPetMaxFeed();
    }

    public boolean isHungry() {
        WorldPartition.checkOwner();
        return canFeed
                && (getCurrentFeed()
                        < ((PetDataTable.getInstance()
                                                .getPetData(getMountNpcId())
                                                .getHungryLimit()
                                        / 100f)
                                * getPetLevelData(getMountNpcId()).getPetMaxFeed()));
    }

    public void enteredNoLanding(int delay) {
        WorldPartition.checkOwner();
        dismountTask = WorldPartition.loop().schedule(new DismountTask(this), delay * 1000, TimeUnit.MILLISECONDS);
    }

    public void exitedNoLanding() {
        WorldPartition.checkOwner();
        if (dismountTask == null) {
            return;
        }

        dismountTask.cancel(true);
        dismountTask = null;
    }

    public void storePetFood(int petId) {
        WorldPartition.checkOwner();
        if ((controlItemId == 0) || (petId == 0)) {
            return;
        }

        try {
            CharSummonTable.getInstance().updatePetFed(controlItemId, currentFeed);
            controlItemId = 0;
        } catch (DataAccessException e) {
            log.error("Failed to store Pet [NpcId: {}] data", petId, e);
        }
    }

    public boolean isFlyingMounted() {
        WorldPartition.checkOwner();
        return isTransformed() && transformation.isFlying();
    }

    public int getCharges() {
        WorldPartition.checkOwner();
        return charges;
    }

    public void increaseCharges(int count, int max) {
        WorldPartition.checkOwner();
        if (charges >= max) {
            sendPacket(SystemMessageId.YOUR_FORCE_HAS_REACHED_MAXIMUM_CAPACITY);
            return;
        }

        restartChargeTask();

        charges += count;
        if (charges >= max) {
            charges = max;
            sendPacket(SystemMessageId.YOUR_FORCE_HAS_REACHED_MAXIMUM_CAPACITY);
        } else {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_FORCE_HAS_INCREASED_TO_LEVEL_S1);
            sm.addInt(charges);
            sendPacket(sm);
        }

        sendPacket(new EtcStatusUpdate(this));
    }

    public boolean decreaseCharges(int count) {
        WorldPartition.checkOwner();
        if (charges < count) {
            return false;
        }

        charges -= count;
        if (charges == 0) {
            stopChargeTask();
        } else {
            restartChargeTask();
        }

        sendPacket(new EtcStatusUpdate(this));
        return true;
    }

    public void clearCharges() {
        WorldPartition.checkOwner();
        charges = 0;
        sendPacket(new EtcStatusUpdate(this));
    }

    private void restartChargeTask() {
        if (chargeTask != null) {
            chargeTask.cancel(false);
        }

        chargeTask = WorldPartition.loop().schedule(new ResetChargesTask(this), 600000, TimeUnit.MILLISECONDS);
    }

    public void stopChargeTask() {
        WorldPartition.checkOwner();
        if (chargeTask == null) {
            return;
        }

        chargeTask.cancel(false);
        chargeTask = null;
    }

    public void teleportBookmarkModify(int id, int icon, String tag, String name) {
        WorldPartition.checkOwner();
        if (StringUtils.isBlank(name)) {
            return;
        }

        final TeleportBookmark bookmark = tpbookmarks.get(id);
        if (bookmark != null) {
            bookmark.setIcon(icon);
            bookmark.setTag(tag);
            bookmark.setName(name);

            try {
                PlayerPersistenceManager.getInstance().updateBookmark(getObjectId(), id, icon, tag, name);
            } catch (DataAccessException e) {
                log.warn("Could not update character teleport bookmark data: {}", e.getMessage(), e);
            }
        }

        sendPacket(new ExGetBookMarkInfoPacket(this));
    }

    public void teleportBookmarkDelete(int id) {
        WorldPartition.checkOwner();
        if (tpbookmarks.remove(id) == null) {
            return;
        }

        try {
            PlayerPersistenceManager.getInstance().deleteBookmark(getObjectId(), id);
        } catch (DataAccessException e) {
            log.warn("Could not delete character teleport bookmark data: {}", e.getMessage(), e);
        }

        sendPacket(new ExGetBookMarkInfoPacket(this));
    }

    public void teleportBookmarkGo(int id) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        if (StringUtils.isBlank(name) || !teleportBookmarkCondition(1)) {
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

        try {
            PlayerPersistenceManager.getInstance()
                    .insertBookmark(
                            getObjectId(),
                            CharacterTeleportBookmark.builder()
                                    .bookmarkId(id)
                                    .x(x)
                                    .y(y)
                                    .z(z)
                                    .icon(icon)
                                    .tag(tag)
                                    .name(name)
                                    .build());
        } catch (DataAccessException e) {
            log.warn("Could not insert character teleport bookmark data: {}", e.getMessage(), e);
        }

        sendPacket(new ExGetBookMarkInfoPacket(this));
    }

    public void restoreTeleportBookmark() {
        WorldPartition.checkOwner();
        try {
            for (CharacterTeleportBookmark bookmark :
                    PlayerPersistenceManager.getInstance().findBookmarks(getObjectId())) {
                tpbookmarks.put(
                        bookmark.bookmarkId(),
                        new TeleportBookmark(
                                bookmark.bookmarkId(),
                                bookmark.x(),
                                bookmark.y(),
                                bookmark.z(),
                                bookmark.icon(),
                                bookmark.tag(),
                                bookmark.name()));
            }
        } catch (DataAccessException e) {
            log.error("Failed restoing character teleport bookmark.", e);
        }
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
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
        final RelationCache cache = getKnownRelation(player.getObjectId());
        if ((cache == null) || (cache.relation() != relation) || (cache.autoAttackable() != isAutoAttackable)) {
            player.sendPacket(new RelationChanged(this, relation, isAutoAttackable));
            if (hasSummon()) {
                player.sendPacket(new RelationChanged(summon, relation, isAutoAttackable));
            }

            putKnownRelation(player.getObjectId(), new RelationCache(relation, isAutoAttackable));
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        setMovieHolder(null);
    }

    public boolean isAllowedToEnchantSkills() {
        WorldPartition.checkOwner();
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

    public int checkBirthDay() {
        WorldPartition.checkOwner();
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

    /** Friend object ids, in the order they were added. */
    private final IntLinkedOpenHashSet friendList = new IntLinkedOpenHashSet();

    public Set<Integer> getFriendList() {
        WorldPartition.checkOwner();
        return IntSets.unmodifiable(new IntLinkedOpenHashSet(friendList));
    }

    public boolean isFriend(int objectId) {
        WorldPartition.checkOwner();
        return friendList.contains(objectId);
    }

    public int getFriendCount() {
        WorldPartition.checkOwner();
        return friendList.size();
    }

    public void addFriend(int objectId) {
        WorldPartition.checkOwner();
        friendList.add(objectId);
    }

    public void removeFriend(int objectId) {
        WorldPartition.checkOwner();
        friendList.remove(objectId);
    }

    public void restoreFriendList() {
        WorldPartition.checkOwner();
        friendList.clear();

        try {
            for (int friendId : CharacterRelationsManager.getInstance().findFriendIds(getObjectId())) {
                if (friendId != getObjectId()) {
                    friendList.add(friendId);
                }
            }
        } catch (DataAccessException e) {
            log.warn("Error found in {}'s FriendList: {}", getName(), e.getMessage(), e);
        }
    }

    private void notifyFriends() {
        final FriendStatusPacket pkt = new FriendStatusPacket(getObjectId());
        try (IntSnapshot friends = ScratchBuffers.intSnapshot(friendList)) {
            for (int i = 0; i < friends.size(); i++) {
                final Player friend = World.getPlayer(friends.getInt(i));
                if (friend != null) {
                    friend.sendPacket(pkt);
                }
            }
        }
    }

    public boolean isSilenceMode(int playerObjId) {
        WorldPartition.checkOwner();
        if (PlayerConfig.SILENCE_MODE_EXCLUDE && silenceMode && (silenceModeExcluded != null)) {
            return !silenceModeExcluded.contains(playerObjId);
        }

        return silenceMode;
    }

    public void setSilenceMode(boolean mode) {
        WorldPartition.checkOwner();
        silenceMode = mode;
        if (silenceModeExcluded != null) {
            silenceModeExcluded.clear();
        }

        sendPacket(new EtcStatusUpdate(this));
    }

    public void addSilenceModeExcluded(int playerObjId) {
        WorldPartition.checkOwner();
        if (silenceModeExcluded == null) {
            silenceModeExcluded = new ArrayList<>(1);
        }

        silenceModeExcluded.add(playerObjId);
    }

    private void storeRecipeShopList() {
        if (hasManufactureShop()) {
            final List<CharacterRecipeShopItem> items = new ArrayList<>(manufactureItems.size());
            for (ManufactureItem item : manufactureItems.values()) {
                items.add(CharacterRecipeShopItem.builder()
                        .recipeId(item.getRecipeId())
                        .price(item.getCost())
                        .build());
            }

            try {
                PlayerPersistenceManager.getInstance().replaceRecipeShopItems(getObjectId(), items);
            } catch (DataAccessException e) {
                log.error("Could not store recipe shop for playerId {}: ", getObjectId(), e);
            }
        }
    }

    private void restoreRecipeShopList() {
        manufactureItems.clear();

        try {
            for (CharacterRecipeShopItem item :
                    PlayerPersistenceManager.getInstance().findRecipeShopItems(getObjectId())) {
                manufactureItems.put(item.recipeId(), new ManufactureItem(item.recipeId(), item.price()));
            }
        } catch (DataAccessException e) {
            log.error("Could not restore recipe shop list data for playerId: {}", getObjectId(), e);
        }
    }

    public float getCollisionRadius() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
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

    /** @return {@code true} while falling, but {@code false} on the first tick of a fall, for correct coordinate sync */
    public boolean isFalling(int z) {
        WorldPartition.checkOwner();
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

        fallingDamageTask = WorldPartition.loop()
                .schedule(
                        () -> {
                            if ((fallingDamage > 0) && !isInvul()) {
                                reduceCurrentHp(Math.min(fallingDamage, getCurrentHp() - 1), null, false, true, null);
                                final SystemMessage sm =
                                        new SystemMessage(SystemMessageId.YOU_RECEIVED_S1_FALLING_DAMAGE);
                                sm.addInt(fallingDamage);
                                sendPacket(sm);
                            }

                            fallingDamage = 0;
                            fallingDamageTask = null;
                        },
                        1500,
                        TimeUnit.MILLISECONDS);

        // Prevent falling under ground.
        sendPacket(new ValidateLocation(this));
        setFalling();

        return false;
    }

    public void setFalling() {
        WorldPartition.checkOwner();
        fallingTimestamp = ClockBridge.gameMillis() + FALLING_VALIDATION_DELAY;
    }

    public void updateLastItemAuctionRequest() {
        WorldPartition.checkOwner();
        lastItemAuctionInfoRequest = ClockBridge.gameMillis();
    }

    /** @return {@code true} when the last item auction request was less than 2 seconds ago */
    public boolean isItemAuctionPolling() {
        WorldPartition.checkOwner();
        return (ClockBridge.gameMillis() - lastItemAuctionInfoRequest) < 2000;
    }

    @Override
    public boolean isMovementDisabled() {
        WorldPartition.checkOwner();
        return super.isMovementDisabled() || (movieHolder != null);
    }

    public String getHtmlPrefix() {
        WorldPartition.checkOwner();
        return MultilingualSupportConfig.MULTILANG_ENABLE ? htmlPrefix : "";
    }

    public boolean setLang(String lang) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        pcCafePoints = count < PremiumSystemConfig.PC_CAFE_MAX_POINTS ? count : PremiumSystemConfig.PC_CAFE_MAX_POINTS;
    }

    public void removeFromBossZone() {
        WorldPartition.checkOwner();
        try {
            for (BossZone zone : GrandBossManager.getInstance().getZones().values()) {
                zone.removePlayer(this);
            }
        } catch (Exception e) {
            log.warn("Exception on removeFromBossZone(): {}", e.getMessage(), e);
        }
    }

    /** Lowers a skill to the next possible level once player level falls below its learn level - 9. */
    public void checkPlayerSkills() {
        WorldPartition.checkOwner();
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
                nextLevel = sl.getSkillLevel();
            }
        }

        if (nextLevel == -1) {
            log.info("Removing skill {} from {}", skill, this);
            removeSkill(skill, true);
        } else {
            log.info("Decreasing skill {} to {} for {}", skill, nextLevel, this);
            addSkill(SkillData.getInstance().getSkill(skill.getId(), nextLevel), true);
        }
    }

    public boolean canMakeSocialAction() {
        WorldPartition.checkOwner();
        return (privateStoreType == PrivateStoreType.NONE)
                && (getActiveRequester() == null)
                && !isAlikeDead()
                && !isAllSkillsDisabled()
                && !isCastingNow()
                && !isCastingSimultaneouslyNow()
                && (getAI().getIntention() == Intention.IDLE);
    }

    public void setMultiSocialAction(int id, int targetId) {
        WorldPartition.checkOwner();
        multiSociaAction = id;
        multiSocialTarget = targetId;
    }

    public Collection<TeleportBookmark> getTeleportBookmarks() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(tpbookmarks.values()));
    }

    public void setBookmarkSlot(int slot) {
        WorldPartition.checkOwner();
        bookmarkSlot = slot;
        sendPacket(new ExGetBookMarkInfoPacket(this));
    }

    public int getQuestInventoryLimit() {
        WorldPartition.checkOwner();
        return PlayerConfig.INVENTORY_MAXIMUM_QUEST_ITEMS;
    }

    public boolean canAttackCreature(Creature creature) {
        WorldPartition.checkOwner();
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

    public boolean isInventoryUnder90(boolean includeQuestInv) {
        WorldPartition.checkOwner();
        return (includeQuestInv ? inventory.getSize() : inventory.getNonQuestSize()) <= (getInventoryLimit() * 0.9);
    }

    public boolean isInventoryUnder80(boolean includeQuestInv) {
        WorldPartition.checkOwner();
        return (includeQuestInv ? inventory.getSize() : inventory.getNonQuestSize()) <= (getInventoryLimit() * 0.8);
    }

    public boolean havePetInvItems() {
        WorldPartition.checkOwner();
        return petItems;
    }

    public void setPetInvItems(boolean haveit) {
        WorldPartition.checkOwner();
        petItems = haveit;
    }

    private void restorePetInventoryItems() {
        try {
            setPetInvItems(ItemManager.getInstance().hasPetItems(getObjectId()));
        } catch (DataAccessException e) {
            log.error("Could not check Items in Pet Inventory for playerId: {}", getObjectId(), e);
        }
    }

    private long loadRecommendations() {
        try {
            final Optional<CharacterRecommendation> stored =
                    PlayerPersistenceManager.getInstance().findRecommendation(getObjectId());
            if (stored.isEmpty()) {
                return 3600000;
            }

            setRecomHave(stored.get().received());
            setRecomLeft(stored.get().leftToGive());
            return stored.get().bonusTimeLeftMs();
        } catch (DataAccessException e) {
            log.error("Could not restore Recommendations for player: {}", getObjectId(), e);
            return 0;
        }
    }

    public void storeRecommendations(boolean cancelRecoTask) {
        WorldPartition.checkOwner();
        long recoTaskEnd = 0;
        if ((nevitHourglassTask != null) && !nevitHourglassTask.isDone()) {
            recoTaskEnd = nevitHourglassTask.getDelay(TimeUnit.MILLISECONDS);
            if (cancelRecoTask) {
                nevitHourglassTask.cancel(false);
                nevitHourglassTask = null;
            }
        }

        try {
            // A paused hourglass keeps the stored bonus time; reloading it would also roll back the counters.
            if (getStat().hasPausedNevitHourglass()) {
                PlayerPersistenceManager.getInstance().saveRecommendationCounts(getObjectId(), recomHave, recomLeft);
            } else {
                PlayerPersistenceManager.getInstance()
                        .saveRecommendation(
                                getObjectId(),
                                CharacterRecommendation.builder()
                                        .received(recomHave)
                                        .leftToGive(recomLeft)
                                        .bonusTimeLeftMs(recoTaskEnd)
                                        .build());
            }
        } catch (DataAccessException e) {
            log.error("Could not update Recommendations for player: {}", getObjectId(), e);
        }
    }

    public void storeRecommendationValues() {
        WorldPartition.checkOwner();
        try {
            PlayerPersistenceManager.getInstance().saveRecommendationCounts(getObjectId(), recomHave, recomLeft);
        } catch (DataAccessException e) {
            log.error("Could not update Recommendations for player: {}", getObjectId(), e);
        }
    }

    public void checkRecoBonusTask() {
        WorldPartition.checkOwner();
        startNevitHourglassTask();

        recoGiveTask = WorldPartition.loop()
                .scheduleAtFixedRate(new RecoGiveTask(this), 7200000, 3600000, TimeUnit.MILLISECONDS);

        storeRecommendations(false);
    }

    public void startNevitHourglassTask() {
        WorldPartition.checkOwner();
        if (nevitHourglassTask == null) {
            final long taskTime = loadRecommendations();
            if (taskTime > 0) {
                nevitHourglassTask =
                        WorldPartition.loop().schedule(new RecoBonusTaskEnd(this), taskTime, TimeUnit.MILLISECONDS);
            }

            sendPacket(new ExVoteSystemInfo(this));
        }
    }

    public void stopNevitHourglassTask() {
        WorldPartition.checkOwner();
        if (nevitHourglassTask != null) {
            nevitHourglassTask.cancel(false);
            nevitHourglassTask = null;
        }
    }

    public void stopRecoGiveTask() {
        WorldPartition.checkOwner();
        if (recoGiveTask != null) {
            recoGiveTask.cancel(false);
            recoGiveTask = null;
        }
    }

    public int getNevitHourglassTime() {
        WorldPartition.checkOwner();
        if (nevitHourglassTask != null) {
            return (int) Math.max(0, nevitHourglassTask.getDelay(TimeUnit.SECONDS));
        }

        return 0;
    }

    public boolean isNevitHourglassActive() {
        WorldPartition.checkOwner();
        return (getNevitHourglassTime() > 0) || getStat().hasPausedNevitHourglass();
    }

    public int getNevitHourglassStatus() {
        WorldPartition.checkOwner();
        if (getStat().hasPausedNevitHourglass()) {
            return NEVIT_HOURGLASS_PAUSED;
        } else if (getNevitHourglassTime() > 0) {
            return NEVIT_HOURGLASS_ACTIVE;
        } else {
            return NEVIT_HOURGLASS_MAINTAIN;
        }
    }

    public int getNevitHourglassBonus() {
        WorldPartition.checkOwner();
        if (online && (recomHave != 0) && isNevitHourglassActive()) {
            final int lvl = getLevel() / 10;
            final int exp = (Math.min(100, recomHave) - 1) / 10;
            return NEVIT_HOURGLASS_BONUS[lvl][exp];
        }

        return 0;
    }

    public double getNevitHourglassMultiplier() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && premiumStatus;
    }

    public void updateNotMoveUntil() {
        WorldPartition.checkOwner();
        notMoveUntil = ClockBridge.gameMillis() + PlayerConfig.PLAYER_MOVEMENT_BLOCK_TIME;
    }

    @Override
    public boolean isPlayer() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public Player asPlayer() {
        WorldPartition.checkOwner();
        return this;
    }

    @Override
    public boolean isChargedShot(ShotType type) {
        WorldPartition.checkOwner();
        final Item weapon = getActiveWeaponInstance();
        return (weapon != null) && weapon.isChargedShot(type);
    }

    @Override
    public void setChargedShot(ShotType type, boolean charged) {
        WorldPartition.checkOwner();
        final Item weapon = getActiveWeaponInstance();
        if (weapon != null) {
            weapon.setChargedShot(type, charged);
        }
    }

    public Skill getCustomSkill(int skillId) {
        WorldPartition.checkOwner();
        return (customSkills != null) ? customSkills.get(skillId) : null;
    }

    private void addCustomSkill(Skill skill) {
        if ((skill == null) || (skill.getDisplayId() == skill.getId())) {
            return;
        }

        if (customSkills == null) {
            customSkills = new Int2ObjectLinkedOpenHashMap<>();
        }

        customSkills.put(skill.getDisplayId(), skill);
    }

    private void removeCustomSkill(Skill skill) {
        if ((skill != null) && (customSkills != null) && (skill.getDisplayId() != skill.getId())) {
            customSkills.remove(skill.getDisplayId());
        }
    }

    /** Whether the death dialog offers the 'To Village' button. */
    @Override
    public boolean canRevive() {
        WorldPartition.checkOwner();
        return canRevive;
    }

    public boolean isRegisteredOnEvent() {
        WorldPartition.checkOwner();
        return registeredOnEvent || onEvent;
    }

    public int getBlockCheckerArena() {
        WorldPartition.checkOwner();
        return blockCheckerArena;
    }

    public int getUCKills() {
        WorldPartition.checkOwner();
        return ucKills;
    }

    public void addKillCountUC() {
        WorldPartition.checkOwner();
        ucKills++;
    }

    public int getUCDeaths() {
        WorldPartition.checkOwner();
        return ucDeaths;
    }

    public void addDeathCountUC() {
        WorldPartition.checkOwner();
        ucDeaths++;
    }

    public void cleanUCStats() {
        WorldPartition.checkOwner();
        ucDeaths = 0;
        ucKills = 0;
    }

    public void setUCState(int state) {
        WorldPartition.checkOwner();
        setRegisteredOnEvent(state != UC_STATE_NONE);
        ucState = state;
    }

    public int getUCState() {
        WorldPartition.checkOwner();
        return ucState;
    }

    public void setOriginalCpHpMp(double cp, double hp, double mp) {
        WorldPartition.checkOwner();
        originalCp = cp;
        originalHp = hp;
        originalMp = mp;
    }

    public boolean hasVariables() {
        WorldPartition.checkOwner();
        return getScript(PlayerVariables.class) != null;
    }

    public PlayerVariables getVariables() {
        WorldPartition.checkOwner();
        final PlayerVariables vars = getScript(PlayerVariables.class);
        return vars != null ? vars : addScript(new PlayerVariables(getObjectId()));
    }

    public boolean hasAccountVariables() {
        WorldPartition.checkOwner();
        return getScript(AccountVariables.class) != null;
    }

    public AccountVariables getAccountVariables() {
        WorldPartition.checkOwner();
        final AccountVariables vars = getScript(AccountVariables.class);
        return vars != null ? vars : addScript(new AccountVariables(accountId));
    }

    @Override
    public int getId() {
        WorldPartition.checkOwner();
        return getPlayerClass().getId();
    }

    public boolean isPartyBanned() {
        WorldPartition.checkOwner();
        return PunishmentManager.getInstance()
                .hasPunishment(getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.PARTY_BAN);
    }

    public boolean addAction(PlayerAction act) {
        WorldPartition.checkOwner();
        if (!hasAction(act)) {
            actionMask |= act.getMask();
            return true;
        }

        return false;
    }

    public boolean removeAction(PlayerAction act) {
        WorldPartition.checkOwner();
        if (hasAction(act)) {
            actionMask &= ~act.getMask();
            return true;
        }

        return false;
    }

    public boolean hasAction(PlayerAction act) {
        WorldPartition.checkOwner();
        return (actionMask & act.getMask()) == act.getMask();
    }

    private final Nevit nevitSystem = new Nevit(this);

    public Nevit getNevitSystem() {
        WorldPartition.checkOwner();
        return nevitSystem;
    }

    public void setCharmOfCourage(boolean value) {
        WorldPartition.checkOwner();
        hasCharmOfCourage = value;
    }

    public boolean hasCharmOfCourage() {
        WorldPartition.checkOwner();
        return hasCharmOfCourage;
    }

    public void setGood() {
        WorldPartition.checkOwner();
        good = true;
        evil = false;
    }

    public void setEvil() {
        WorldPartition.checkOwner();
        good = false;
        evil = true;
    }

    public boolean isAtWarWith(Creature target) {
        WorldPartition.checkOwner();
        if (target == null) {
            return false;
        }

        if ((clan != null) && !isAcademyMember() && (target.getClan() != null) && !target.isAcademyMember()) {
            return clan.isAtWarWith(target.getClan());
        }

        return false;
    }

    public boolean isInPartyWith(Creature target) {
        WorldPartition.checkOwner();
        return isInParty()
                && target.isInParty()
                && (getParty().getLeaderObjectId() == target.getParty().getLeaderObjectId());
    }

    public boolean isInCommandChannelWith(Creature target) {
        WorldPartition.checkOwner();
        return isInParty()
                && target.isInParty()
                && getParty().isInCommandChannel()
                && target.getParty().isInCommandChannel()
                && (getParty().getCommandChannel().getLeaderObjectId()
                        == target.getParty().getCommandChannel().getLeaderObjectId());
    }

    public boolean isInClanWith(Creature target) {
        WorldPartition.checkOwner();
        return (getClanId() != 0) && (target.getClanId() != 0) && (getClanId() == target.getClanId());
    }

    public boolean isInAllyWith(Creature target) {
        WorldPartition.checkOwner();
        return (getAllyId() != 0) && (target.getAllyId() != 0) && (getAllyId() == target.getAllyId());
    }

    public boolean isInDuelWith(Creature target) {
        WorldPartition.checkOwner();
        return isInDuel() && target.isInDuel() && (getDuelId() == target.getDuelId());
    }

    public boolean isOnSameSiegeSideWith(Creature target) {
        WorldPartition.checkOwner();
        return (getSiegeState() > 0)
                && isInsideZone(ZoneId.SIEGE)
                && (getSiegeState() == target.getSiegeState())
                && (getSiegeSide() == target.getSiegeSide());
    }

    public long getGamePoints() {
        WorldPartition.checkOwner();
        return getAccountVariables().getInt(GAME_POINTS_VAR, 0);
    }

    public void setGamePoints(long points) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        addListener(new FunctionEventListener(
                this, EventType.ON_PLAYABLE_EXP_CHANGED, (OnPlayableExpChanged _) -> onExperienceReceived(), this));
    }

    public void enableExpGain() {
        WorldPartition.checkOwner();
        removeListenerIf(EventType.ON_PLAYABLE_EXP_CHANGED, listener -> listener.getOwner() == this);
    }

    public void sendInventoryUpdate(InventoryUpdate iu) {
        WorldPartition.checkOwner();
        if (inventoryUpdateTask != null) {
            inventoryUpdateTask.cancel(false);
        }

        inventoryUpdate.putAll(iu.getItemEntries());

        inventoryUpdateTask = WorldPartition.loop()
                .schedule(
                        () -> {
                            sendPacket(inventoryUpdate);
                        },
                        100,
                        TimeUnit.MILLISECONDS);
    }

    /** Sends the pending inventory update now instead of waiting for the scheduled task. */
    public void flushInventoryUpdate() {
        WorldPartition.checkOwner();
        if (inventoryUpdateTask != null) {
            inventoryUpdateTask.cancel(false);
            inventoryUpdateTask = null;
        }

        sendPacket(inventoryUpdate);
    }

    public void sendItemList(boolean open) {
        WorldPartition.checkOwner();
        if (itemListTask != null) {
            itemListTask.cancel(false);
        }

        itemListTask = WorldPartition.loop()
                .schedule(
                        () -> {
                            sendPacket(new ItemList(this, open));
                        },
                        250,
                        TimeUnit.MILLISECONDS);
    }

    public void stopAllTasks() {
        WorldPartition.checkOwner();
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

        // A cancelled timer may unregister itself, so the walks run over copies.
        try (Snapshot<QuestTimer> timers = ScratchBuffers.snapshot(questTimers)) {
            for (QuestTimer timer : timers) {
                timer.cancelTask();
            }
        }

        questTimers.clear();

        try (Snapshot<TimerHolder<?>> timers = ScratchBuffers.snapshot(timerHolders)) {
            for (TimerHolder<?> timer : timers) {
                timer.cancelTask();
            }
        }

        timerHolders.clear();
    }

    public void addQuestTimer(QuestTimer questTimer) {
        WorldPartition.checkOwner();
        questTimers.add(questTimer);
    }

    public void removeQuestTimer(QuestTimer questTimer) {
        WorldPartition.checkOwner();
        questTimers.remove(questTimer);
    }

    public void addTimerHolder(TimerHolder<?> timer) {
        WorldPartition.checkOwner();
        timerHolders.add(timer);
    }

    public void removeTimerHolder(TimerHolder<?> timer) {
        WorldPartition.checkOwner();
        timerHolders.remove(timer);
    }

    public double getServitorShareBonus(Stat stat) {
        WorldPartition.checkOwner();
        if (servitorShare == null) {
            return 1.0d;
        }

        final Double val = servitorShare.get(stat);
        if (val == null) {
            return 1.0d;
        }

        return val;
    }

    public void setAutoPlaying(boolean value) {
        WorldPartition.checkOwner();
        autoPlaying = value;

        if (!value && offlinePlay && OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS) {
            OfflinePlayTable.getInstance().removeOfflinePlay(this);
        }
    }

    public boolean isAutoPlaying() {
        WorldPartition.checkOwner();
        return autoPlaying;
    }
}
