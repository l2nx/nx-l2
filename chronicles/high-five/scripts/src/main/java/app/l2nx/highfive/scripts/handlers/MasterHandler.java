/*
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
package app.l2nx.highfive.scripts.handlers;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import app.l2nx.highfive.config.custom.AutoPlayConfig;
import app.l2nx.highfive.config.custom.AutoPotionsConfig;
import app.l2nx.highfive.config.custom.BankingConfig;
import app.l2nx.highfive.config.custom.ChatModerationConfig;
import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.config.custom.OnlineInfoConfig;
import app.l2nx.highfive.config.custom.PasswordChangeConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.config.custom.WeddingConfig;
import app.l2nx.highfive.handler.ActionClickHandler;
import app.l2nx.highfive.handler.ActionShiftHandler;
import app.l2nx.highfive.handler.AdminCommandHandler;
import app.l2nx.highfive.handler.BypassHandler;
import app.l2nx.highfive.handler.ChatHandler;
import app.l2nx.highfive.handler.CommunityBoardHandler;
import app.l2nx.highfive.handler.IHandler;
import app.l2nx.highfive.handler.ItemHandler;
import app.l2nx.highfive.handler.PunishmentHandler;
import app.l2nx.highfive.handler.TargetHandler;
import app.l2nx.highfive.handler.UserCommandHandler;
import app.l2nx.highfive.handler.VoicedCommandHandler;

import app.l2nx.highfive.scripts.handlers.actions.click.ArtefactClick;
import app.l2nx.highfive.scripts.handlers.actions.click.DecoyClick;
import app.l2nx.highfive.scripts.handlers.actions.click.DoorClick;
import app.l2nx.highfive.scripts.handlers.actions.click.ItemClick;
import app.l2nx.highfive.scripts.handlers.actions.click.NpcClick;
import app.l2nx.highfive.scripts.handlers.actions.click.PetClick;
import app.l2nx.highfive.scripts.handlers.actions.click.PlayerClick;
import app.l2nx.highfive.scripts.handlers.actions.click.StaticObjectClick;
import app.l2nx.highfive.scripts.handlers.actions.click.SummonClick;
import app.l2nx.highfive.scripts.handlers.actions.click.TrapClick;
import app.l2nx.highfive.scripts.handlers.actions.shiftclick.DoorShiftClick;
import app.l2nx.highfive.scripts.handlers.actions.shiftclick.ItemShiftClick;
import app.l2nx.highfive.scripts.handlers.actions.shiftclick.NpcShiftClick;
import app.l2nx.highfive.scripts.handlers.actions.shiftclick.PlayerShiftClick;
import app.l2nx.highfive.scripts.handlers.actions.shiftclick.StaticObjectShiftClick;
import app.l2nx.highfive.scripts.handlers.actions.shiftclick.SummonShiftClick;
import app.l2nx.highfive.scripts.handlers.bypass.communityboard.ClanBoard;
import app.l2nx.highfive.scripts.handlers.bypass.communityboard.DropSearchBoard;
import app.l2nx.highfive.scripts.handlers.bypass.communityboard.FavoriteBoard;
import app.l2nx.highfive.scripts.handlers.bypass.communityboard.FriendsBoard;
import app.l2nx.highfive.scripts.handlers.bypass.communityboard.HomeBoard;
import app.l2nx.highfive.scripts.handlers.bypass.communityboard.HomepageBoard;
import app.l2nx.highfive.scripts.handlers.bypass.communityboard.MailBoard;
import app.l2nx.highfive.scripts.handlers.bypass.communityboard.MemoBoard;
import app.l2nx.highfive.scripts.handlers.bypass.communityboard.RegionBoard;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Augment;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Buy;
import app.l2nx.highfive.scripts.handlers.bypass.npc.BuyShadowItem;
import app.l2nx.highfive.scripts.handlers.bypass.npc.ChatLink;
import app.l2nx.highfive.scripts.handlers.bypass.npc.ClanWarehouse;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Festival;
import app.l2nx.highfive.scripts.handlers.bypass.npc.FindPvP;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Freight;
import app.l2nx.highfive.scripts.handlers.bypass.npc.ItemAuctionLink;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Link;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Loto;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Multisell;
import app.l2nx.highfive.scripts.handlers.bypass.npc.NpcViewMod;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Observation;
import app.l2nx.highfive.scripts.handlers.bypass.npc.OlympiadManagerLink;
import app.l2nx.highfive.scripts.handlers.bypass.npc.OlympiadObservation;
import app.l2nx.highfive.scripts.handlers.bypass.npc.PlayerHelp;
import app.l2nx.highfive.scripts.handlers.bypass.npc.PrivateWarehouse;
import app.l2nx.highfive.scripts.handlers.bypass.npc.QuestList;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Rebirth;
import app.l2nx.highfive.scripts.handlers.bypass.npc.ReceivePremium;
import app.l2nx.highfive.scripts.handlers.bypass.npc.ReleaseAttribute;
import app.l2nx.highfive.scripts.handlers.bypass.npc.RentPet;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Rift;
import app.l2nx.highfive.scripts.handlers.bypass.npc.ScriptLink;
import app.l2nx.highfive.scripts.handlers.bypass.npc.SkillList;
import app.l2nx.highfive.scripts.handlers.bypass.npc.SupportBlessing;
import app.l2nx.highfive.scripts.handlers.bypass.npc.SupportMagic;
import app.l2nx.highfive.scripts.handlers.bypass.npc.TerritoryStatus;
import app.l2nx.highfive.scripts.handlers.bypass.npc.TutorialClose;
import app.l2nx.highfive.scripts.handlers.bypass.npc.VoiceCommand;
import app.l2nx.highfive.scripts.handlers.bypass.npc.Wear;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatAlliance;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatBattlefield;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatClan;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatGeneral;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatHeroVoice;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatParty;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatPartyMatchRoom;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatPartyRoomAll;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatPartyRoomCommander;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatPetition;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatShout;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatTrade;
import app.l2nx.highfive.scripts.handlers.chat.channels.ChatWhisper;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminAdmin;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminAnnouncements;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminAugment;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminBuffs;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminCHSiege;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminCamera;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminChangeAccessLevel;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminClan;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminCreateItem;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminCursedWeapons;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminDebug;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminDelete;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminDestroyItems;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminDisconnect;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminDoorControl;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminEditChar;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminEffects;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminElement;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminEnchant;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminEvents;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminExpSp;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminFakePlayers;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminFence;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminFightCalculator;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminFortSiege;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminGamePoints;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminGeodata;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminGm;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminGmChat;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminGmSpeed;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminGoto;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminGraciaSeeds;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminGrandBoss;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminHeal;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminHelp;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminHide;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminHtml;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminHwid;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminInstance;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminInstanceZone;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminInvul;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminKick;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminKill;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminLevel;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminLogin;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminMammon;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminManor;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminMenu;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminMessages;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminOnline;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminPForge;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminPathNode;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminPcCafePoints;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminPetition;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminPledge;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminPremium;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminPunishment;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminQuest;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminRegions;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminReload;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminRepairChar;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminRes;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminRide;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminScan;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminSearch;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminServerInfo;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminShop;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminShowQuests;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminShutdown;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminSiege;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminSkill;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminSpawn;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminSummon;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminSuperHaste;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminTarget;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminTargetSay;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminTeleport;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminTerritoryWar;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminTest;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminTransform;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminVitality;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminZone;
import app.l2nx.highfive.scripts.handlers.chat.commands.admin.AdminZoneBuild;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.ChannelDelete;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.ChannelInfo;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.ChannelLeave;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.ClanPenalty;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.ClanWarsList;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.Dismount;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.InstanceZone;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.Loc;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.Mount;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.MyBirthday;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.OlympiadStat;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.PartyInfo;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.SiegeStatus;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.Time;
import app.l2nx.highfive.scripts.handlers.chat.commands.user.Unstuck;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.AutoPlay;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.AutoPotion;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.Banking;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.ChangePassword;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.ChatAdmin;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.ExperienceGain;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.Lang;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.Offline;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.OfflinePlay;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.Online;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.Premium;
import app.l2nx.highfive.scripts.handlers.chat.commands.voiced.Wedding;
import app.l2nx.highfive.scripts.handlers.items.BeastSoulShot;
import app.l2nx.highfive.scripts.handlers.items.BeastSpiritShot;
import app.l2nx.highfive.scripts.handlers.items.BlessedSpiritShot;
import app.l2nx.highfive.scripts.handlers.items.Book;
import app.l2nx.highfive.scripts.handlers.items.Bypass;
import app.l2nx.highfive.scripts.handlers.items.Calculator;
import app.l2nx.highfive.scripts.handlers.items.CharmOfCourage;
import app.l2nx.highfive.scripts.handlers.items.Disguise;
import app.l2nx.highfive.scripts.handlers.items.Elixir;
import app.l2nx.highfive.scripts.handlers.items.EnchantAttribute;
import app.l2nx.highfive.scripts.handlers.items.EnchantScrolls;
import app.l2nx.highfive.scripts.handlers.items.EventItem;
import app.l2nx.highfive.scripts.handlers.items.ExtractableItems;
import app.l2nx.highfive.scripts.handlers.items.FishShots;
import app.l2nx.highfive.scripts.handlers.items.Harvester;
import app.l2nx.highfive.scripts.handlers.items.ItemSkills;
import app.l2nx.highfive.scripts.handlers.items.ItemSkillsTemplate;
import app.l2nx.highfive.scripts.handlers.items.Maps;
import app.l2nx.highfive.scripts.handlers.items.MercTicket;
import app.l2nx.highfive.scripts.handlers.items.NicknameColor;
import app.l2nx.highfive.scripts.handlers.items.PetFood;
import app.l2nx.highfive.scripts.handlers.items.Recipes;
import app.l2nx.highfive.scripts.handlers.items.RollingDice;
import app.l2nx.highfive.scripts.handlers.items.Seed;
import app.l2nx.highfive.scripts.handlers.items.SevenSignsRecord;
import app.l2nx.highfive.scripts.handlers.items.SoulShots;
import app.l2nx.highfive.scripts.handlers.items.SpecialXMas;
import app.l2nx.highfive.scripts.handlers.items.SpiritShot;
import app.l2nx.highfive.scripts.handlers.items.SummonItems;
import app.l2nx.highfive.scripts.handlers.items.TeleportBookmark;
import app.l2nx.highfive.scripts.handlers.punishments.BanHandler;
import app.l2nx.highfive.scripts.handlers.punishments.ChatBanHandler;
import app.l2nx.highfive.scripts.handlers.punishments.JailHandler;
import app.l2nx.highfive.scripts.handlers.skill.targets.Area;
import app.l2nx.highfive.scripts.handlers.skill.targets.AreaCorpseMob;
import app.l2nx.highfive.scripts.handlers.skill.targets.AreaFriendly;
import app.l2nx.highfive.scripts.handlers.skill.targets.AreaSummon;
import app.l2nx.highfive.scripts.handlers.skill.targets.Aura;
import app.l2nx.highfive.scripts.handlers.skill.targets.AuraCorpseMob;
import app.l2nx.highfive.scripts.handlers.skill.targets.AuraFriendly;
import app.l2nx.highfive.scripts.handlers.skill.targets.BehindArea;
import app.l2nx.highfive.scripts.handlers.skill.targets.BehindAura;
import app.l2nx.highfive.scripts.handlers.skill.targets.Clan;
import app.l2nx.highfive.scripts.handlers.skill.targets.ClanMember;
import app.l2nx.highfive.scripts.handlers.skill.targets.CommandChannel;
import app.l2nx.highfive.scripts.handlers.skill.targets.CorpseClan;
import app.l2nx.highfive.scripts.handlers.skill.targets.CorpseMob;
import app.l2nx.highfive.scripts.handlers.skill.targets.EnemySummon;
import app.l2nx.highfive.scripts.handlers.skill.targets.FlagPole;
import app.l2nx.highfive.scripts.handlers.skill.targets.FrontArea;
import app.l2nx.highfive.scripts.handlers.skill.targets.FrontAura;
import app.l2nx.highfive.scripts.handlers.skill.targets.Ground;
import app.l2nx.highfive.scripts.handlers.skill.targets.Holy;
import app.l2nx.highfive.scripts.handlers.skill.targets.One;
import app.l2nx.highfive.scripts.handlers.skill.targets.OwnerPet;
import app.l2nx.highfive.scripts.handlers.skill.targets.Party;
import app.l2nx.highfive.scripts.handlers.skill.targets.PartyClan;
import app.l2nx.highfive.scripts.handlers.skill.targets.PartyMember;
import app.l2nx.highfive.scripts.handlers.skill.targets.PartyNotMe;
import app.l2nx.highfive.scripts.handlers.skill.targets.PartyOther;
import app.l2nx.highfive.scripts.handlers.skill.targets.PcBody;
import app.l2nx.highfive.scripts.handlers.skill.targets.Pet;
import app.l2nx.highfive.scripts.handlers.skill.targets.Self;
import app.l2nx.highfive.scripts.handlers.skill.targets.Servitor;
import app.l2nx.highfive.scripts.handlers.skill.targets.Summon;
import app.l2nx.highfive.scripts.handlers.skill.targets.TargetParty;
import app.l2nx.highfive.scripts.handlers.skill.targets.Unlockable;

/**
 * Master handler.
 * @author UnAfraid
 */
public class MasterHandler
{
	private static final Logger LOGGER = Logger.getLogger(MasterHandler.class.getName());
	
	private static final IHandler<?, ?>[] LOAD_INSTANCES =
	{
		ActionClickHandler.getInstance(),
		ActionShiftHandler.getInstance(),
		AdminCommandHandler.getInstance(),
		BypassHandler.getInstance(),
		ChatHandler.getInstance(),
		CommunityBoardHandler.getInstance(),
		ItemHandler.getInstance(),
		PunishmentHandler.getInstance(),
		UserCommandHandler.getInstance(),
		VoicedCommandHandler.getInstance(),
		TargetHandler.getInstance(),
	};
	
	private static final Class<?>[][] HANDLERS =
	{
		{
			// Action Handlers
			ArtefactClick.class,
			DecoyClick.class,
			DoorClick.class,
			ItemClick.class,
			NpcClick.class,
			PlayerClick.class,
			PetClick.class,
			StaticObjectClick.class,
			SummonClick.class,
			TrapClick.class,
		},
		{
			// Action Shift Handlers
			DoorShiftClick.class,
			ItemShiftClick.class,
			NpcShiftClick.class,
			PlayerShiftClick.class,
			StaticObjectShiftClick.class,
			SummonShiftClick.class,
		},
		{
			// Admin Command Handlers
			AdminAdmin.class,
			AdminAnnouncements.class,
			AdminAugment.class,
			AdminBuffs.class,
			AdminCamera.class,
			AdminChangeAccessLevel.class,
			AdminCHSiege.class,
			AdminClan.class,
			AdminCreateItem.class,
			AdminCursedWeapons.class,
			AdminDebug.class,
			AdminDelete.class,
			AdminDestroyItems.class,
			AdminDisconnect.class,
			AdminDoorControl.class,
			AdminEditChar.class,
			AdminEffects.class,
			AdminElement.class,
			AdminEnchant.class,
			AdminEvents.class,
			AdminExpSp.class,
			AdminFakePlayers.class,
			AdminFence.class,
			AdminFightCalculator.class,
			AdminFortSiege.class,
			AdminGamePoints.class,
			AdminGeodata.class,
			AdminGm.class,
			AdminGmChat.class,
			AdminGmSpeed.class,
			AdminGoto.class,
			AdminGraciaSeeds.class,
			AdminGrandBoss.class,
			AdminHeal.class,
			AdminHelp.class,
			AdminHide.class,
			AdminHtml.class,
			AdminHwid.class,
			AdminInstance.class,
			AdminInstanceZone.class,
			AdminInvul.class,
			AdminKick.class,
			AdminKill.class,
			AdminLevel.class,
			AdminLogin.class,
			AdminMammon.class,
			AdminManor.class,
			AdminMenu.class,
			AdminMessages.class,
			AdminOnline.class,
			AdminPathNode.class,
			AdminPcCafePoints.class,
			AdminPetition.class,
			AdminPForge.class,
			AdminPledge.class,
			AdminPremium.class,
			AdminPunishment.class,
			AdminQuest.class,
			AdminRegions.class,
			AdminReload.class,
			AdminRepairChar.class,
			AdminRes.class,
			AdminRide.class,
			AdminScan.class,
			AdminSearch.class,
			AdminServerInfo.class,
			AdminShop.class,
			AdminShowQuests.class,
			AdminShutdown.class,
			AdminSiege.class,
			AdminSkill.class,
			AdminSpawn.class,
			AdminSummon.class,
			AdminSuperHaste.class,
			AdminTarget.class,
			AdminTargetSay.class,
			AdminTeleport.class,
			AdminTerritoryWar.class,
			AdminTest.class,
			AdminTransform.class,
			AdminVitality.class,
			AdminZone.class,
			AdminZoneBuild.class,
		},
		{
			// Bypass Handlers
			Augment.class,
			Buy.class,
			BuyShadowItem.class,
			ChatLink.class,
			ClanWarehouse.class,
			Festival.class,
			FindPvP.class,
			Freight.class,
			ItemAuctionLink.class,
			Link.class,
			Loto.class,
			Multisell.class,
			NpcViewMod.class,
			Observation.class,
			OlympiadObservation.class,
			OlympiadManagerLink.class,
			PlayerHelp.class,
			PrivateWarehouse.class,
			QuestList.class,
			ReceivePremium.class,
			ReleaseAttribute.class,
			RentPet.class,
			Rift.class,
			ScriptLink.class,
			SkillList.class,
			SupportBlessing.class,
			SupportMagic.class,
			TerritoryStatus.class,
			TutorialClose.class,
			VoiceCommand.class,
			Wear.class,
			Rebirth.class,
		},
		{
			// Chat Handlers
			ChatGeneral.class,
			ChatAlliance.class,
			ChatBattlefield.class,
			ChatClan.class,
			ChatHeroVoice.class,
			ChatParty.class,
			ChatPartyMatchRoom.class,
			ChatPartyRoomAll.class,
			ChatPartyRoomCommander.class,
			ChatPetition.class,
			ChatShout.class,
			ChatWhisper.class,
			ChatTrade.class,
		},
		{
			// Community Board
			ClanBoard.class,
			DropSearchBoard.class,
			FavoriteBoard.class,
			FriendsBoard.class,
			HomeBoard.class,
			HomepageBoard.class,
			MailBoard.class,
			MemoBoard.class,
			RegionBoard.class,
		},
		{
			// Item Handlers
			BeastSoulShot.class,
			BeastSpiritShot.class,
			BlessedSpiritShot.class,
			Book.class,
			Bypass.class,
			Calculator.class,
			CharmOfCourage.class,
			Disguise.class,
			Elixir.class,
			EnchantAttribute.class,
			EnchantScrolls.class,
			EventItem.class,
			ExtractableItems.class,
			FishShots.class,
			Harvester.class,
			ItemSkills.class,
			ItemSkillsTemplate.class,
			Maps.class,
			MercTicket.class,
			NicknameColor.class,
			PetFood.class,
			Recipes.class,
			RollingDice.class,
			Seed.class,
			SevenSignsRecord.class,
			SoulShots.class,
			SpecialXMas.class,
			SpiritShot.class,
			SummonItems.class,
			TeleportBookmark.class,
		},
		{
			// Punishment Handlers
			BanHandler.class,
			ChatBanHandler.class,
			JailHandler.class,
		},
		{
			// User Command Handlers
			ClanPenalty.class,
			ClanWarsList.class,
			Dismount.class,
			Unstuck.class,
			InstanceZone.class,
			Loc.class,
			Mount.class,
			PartyInfo.class,
			Time.class,
			OlympiadStat.class,
			ChannelLeave.class,
			ChannelDelete.class,
			ChannelInfo.class,
			MyBirthday.class,
			SiegeStatus.class,
		},
		{
			// TODO: Add configuration options for this voiced commands.
			// CastleHandler.class,
			// ClanHandler.class,
			ExperienceGain.class,
			WeddingConfig.ALLOW_WEDDING ? Wedding.class : null,
			AutoPlayConfig.ENABLE_AUTO_PLAY ? AutoPlay.class : null,
			BankingConfig.BANKING_SYSTEM_ENABLED ? Banking.class : null,
			ChatModerationConfig.CHAT_ADMIN ? ChatAdmin.class : null,
			MultilingualSupportConfig.MULTILANG_ENABLE && MultilingualSupportConfig.MULTILANG_VOICED_ALLOW ? Lang.class : null,
			PasswordChangeConfig.ALLOW_CHANGE_PASSWORD ? ChangePassword.class : null,
			OfflinePlayConfig.ENABLE_OFFLINE_PLAY_COMMAND ? OfflinePlay.class : null,
			OfflineTradeConfig.ENABLE_OFFLINE_COMMAND && (OfflineTradeConfig.OFFLINE_TRADE_ENABLE || OfflineTradeConfig.OFFLINE_CRAFT_ENABLE) ? Offline.class : null,
			OnlineInfoConfig.ENABLE_ONLINE_COMMAND ? Online.class : null,
			PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED ? Premium.class : null,
			AutoPotionsConfig.AUTO_POTIONS_ENABLED ? AutoPotion.class : null,
		},
		{
			// Target Handlers
			Area.class,
			AreaCorpseMob.class,
			AreaFriendly.class,
			AreaSummon.class,
			Aura.class,
			AuraCorpseMob.class,
			AuraFriendly.class,
			BehindArea.class,
			BehindAura.class,
			Clan.class,
			ClanMember.class,
			CommandChannel.class,
			CorpseClan.class,
			CorpseMob.class,
			EnemySummon.class,
			FlagPole.class,
			FrontArea.class,
			FrontAura.class,
			Ground.class,
			Holy.class,
			One.class,
			OwnerPet.class,
			Party.class,
			PartyClan.class,
			PartyMember.class,
			PartyNotMe.class,
			PartyOther.class,
			PcBody.class,
			Pet.class,
			Self.class,
			Servitor.class,
			Summon.class,
			TargetParty.class,
			Unlockable.class,
		},
	};
	
	public static void main(String[] args)
	{
		LOGGER.log(Level.INFO, "Loading Handlers...");
		
		final Map<IHandler<?, ?>, Method> registerHandlerMethods = new HashMap<>();
		for (IHandler<?, ?> loadInstance : LOAD_INSTANCES)
		{
			registerHandlerMethods.put(loadInstance, null);
			for (Method method : loadInstance.getClass().getMethods())
			{
				if (method.getName().equals("registerHandler") && !method.isBridge())
				{
					registerHandlerMethods.put(loadInstance, method);
				}
			}
		}
		
		registerHandlerMethods.entrySet().stream().filter(e -> e.getValue() == null).forEach(e -> LOGGER.log(Level.WARNING, "Failed loading handlers of: " + e.getKey().getClass().getSimpleName() + " seems registerHandler function does not exist."));
		
		for (Class<?>[] classes : HANDLERS)
		{
			for (Class<?> c : classes)
			{
				if (c == null)
				{
					continue; // Disabled handler
				}
				
				try
				{
					final Object handler = c.getDeclaredConstructor().newInstance();
					for (Entry<IHandler<?, ?>, Method> entry : registerHandlerMethods.entrySet())
					{
						if ((entry.getValue() != null) && entry.getValue().getParameterTypes()[0].isInstance(handler))
						{
							entry.getValue().invoke(entry.getKey(), handler);
						}
					}
				}
				catch (Exception e)
				{
					LOGGER.log(Level.WARNING, "Failed loading handler: " + c.getSimpleName(), e);
				}
			}
		}
		
		for (IHandler<?, ?> loadInstance : LOAD_INSTANCES)
		{
			LOGGER.log(Level.INFO, loadInstance.getClass().getSimpleName() + ": Loaded " + loadInstance.size() + " handlers.");
		}
		
		LOGGER.log(Level.INFO, "Handlers Loaded...");
	}
}
