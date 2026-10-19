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
package app.l2nx.highfive;

import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import app.l2nx.commons.config.InterfaceConfig;
import app.l2nx.commons.database.DatabaseFactory;
import app.l2nx.commons.network.ConnectionManager;
import app.l2nx.commons.threads.ThreadPool;
import app.l2nx.commons.time.TimeUtil;
import app.l2nx.commons.util.StringUtil;
import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.config.ConfigLoader;
import app.l2nx.highfive.config.DevelopmentConfig;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.config.custom.CustomMailManagerConfig;
import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.config.custom.SellBuffsConfig;
import app.l2nx.highfive.config.custom.WeddingConfig;
import app.l2nx.highfive.data.AugmentationData;
import app.l2nx.highfive.data.BotReportTable;
import app.l2nx.highfive.data.MerchantPriceConfigTable;
import app.l2nx.highfive.data.SchemeBufferTable;
import app.l2nx.highfive.data.sql.AnnouncementsTable;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.sql.CharSummonTable;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.sql.CrestTable;
import app.l2nx.highfive.data.sql.OfflinePlayTable;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.data.xml.ArmorSetData;
import app.l2nx.highfive.data.xml.BuyListData;
import app.l2nx.highfive.data.xml.CategoryData;
import app.l2nx.highfive.data.xml.ClassListData;
import app.l2nx.highfive.data.xml.CubicData;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.data.xml.DynamicExpRateData;
import app.l2nx.highfive.data.xml.ElementalAttributeData;
import app.l2nx.highfive.data.xml.EnchantItemData;
import app.l2nx.highfive.data.xml.EnchantItemGroupsData;
import app.l2nx.highfive.data.xml.EnchantItemHPBonusData;
import app.l2nx.highfive.data.xml.EnchantItemOptionsData;
import app.l2nx.highfive.data.xml.EnchantSkillGroupsData;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.data.xml.ExperienceLossData;
import app.l2nx.highfive.data.xml.FenceData;
import app.l2nx.highfive.data.xml.FishData;
import app.l2nx.highfive.data.xml.FishingMonstersData;
import app.l2nx.highfive.data.xml.FishingRodsData;
import app.l2nx.highfive.data.xml.HennaData;
import app.l2nx.highfive.data.xml.HitConditionBonusData;
import app.l2nx.highfive.data.xml.InitialEquipmentData;
import app.l2nx.highfive.data.xml.InitialShortcutData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.KarmaLossData;
import app.l2nx.highfive.data.xml.LevelUpCrystalData;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.data.xml.MultisellData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.NpcNameLocalisationData;
import app.l2nx.highfive.data.xml.OptionData;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.data.xml.PetSkillData;
import app.l2nx.highfive.data.xml.PlayerTemplateData;
import app.l2nx.highfive.data.xml.PrimeShopData;
import app.l2nx.highfive.data.xml.RecipeData;
import app.l2nx.highfive.data.xml.SecondaryAuthData;
import app.l2nx.highfive.data.xml.SendMessageLocalisationData;
import app.l2nx.highfive.data.xml.SiegeScheduleData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillLearnData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.data.xml.SpawnData;
import app.l2nx.highfive.data.xml.StaticObjectData;
import app.l2nx.highfive.data.xml.TeleporterData;
import app.l2nx.highfive.data.xml.TransformData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoomList;
import app.l2nx.highfive.entity.groups.matching.PartyMatchWaitingList;
import app.l2nx.highfive.entity.spawns.AutoSpawnHandler;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.handler.EffectHandler;
import app.l2nx.highfive.managers.AirShipManager;
import app.l2nx.highfive.managers.AntiFeedManager;
import app.l2nx.highfive.managers.BoatManager;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CaptchaManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.CastleManorManager;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.managers.CoupleManager;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.managers.CustomMailManager;
import app.l2nx.highfive.managers.DailyResetManager;
import app.l2nx.highfive.managers.DayNightSpawnManager;
import app.l2nx.highfive.managers.DimensionalRiftManager;
import app.l2nx.highfive.managers.EventDropManager;
import app.l2nx.highfive.managers.FakePlayerChatManager;
import app.l2nx.highfive.managers.FishingChampionshipManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.GlobalVariablesManager;
import app.l2nx.highfive.managers.GrandBossManager;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.managers.ItemAuctionManager;
import app.l2nx.highfive.managers.ItemsOnGroundManager;
import app.l2nx.highfive.managers.MailManager;
import app.l2nx.highfive.managers.MercTicketManager;
import app.l2nx.highfive.managers.PcCafePointsManager;
import app.l2nx.highfive.managers.PetitionManager;
import app.l2nx.highfive.managers.PrecautionaryRestartManager;
import app.l2nx.highfive.managers.PremiumManager;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.managers.RaidBossPointsManager;
import app.l2nx.highfive.managers.RaidBossSpawnManager;
import app.l2nx.highfive.managers.ScriptManager;
import app.l2nx.highfive.managers.SeedOfDestructionManager;
import app.l2nx.highfive.managers.SeedOfInfinityManager;
import app.l2nx.highfive.managers.SellBuffsManager;
import app.l2nx.highfive.managers.ServerRestartManager;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.WalkingManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.managers.games.KrateisCubeManager;
import app.l2nx.highfive.managers.games.LotteryManager;
import app.l2nx.highfive.managers.games.MonsterRaceManager;
import app.l2nx.highfive.managers.games.UndergroundColiseumManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.OnServerStart;
import app.l2nx.highfive.mechanics.olympiad.Hero;
import app.l2nx.highfive.mechanics.olympiad.Olympiad;
import app.l2nx.highfive.mechanics.sevensigns.SevenSigns;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.GamePacketHandler;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.scripting.ScriptEngine;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.taskmanagers.ItemLifeTimeTaskManager;
import app.l2nx.highfive.taskmanagers.ItemsAutoDestroyTaskManager;
import app.l2nx.highfive.ui.Gui;
import app.l2nx.highfive.util.DeadlockWatcher;

public class GameServer
{
	private static final Logger LOGGER = Logger.getLogger(GameServer.class.getName());
	
	private static final long START_TIME = System.currentTimeMillis();
	private long _sectionStartTime = START_TIME;
	private String _previousSectionName = null;
	
	public GameServer() throws Exception
	{
		// GUI
		InterfaceConfig.load();
		if (InterfaceConfig.ENABLE_GUI)
		{
			System.out.println("GameServer: Running in GUI mode.");
			new Gui();
		}
		
		// Create log folder
		final File logFolder = new File(".", "log");
		logFolder.mkdir();
		
		// Create input stream for log file -- or store file data into memory.
		try (InputStream is = new FileInputStream(new File("./log.cfg")))
		{
			LogManager.getLogManager().readConfiguration(is);
		}
		
		// Initialize config
		ConfigLoader.init();
		
		printSection("Database");
		DatabaseFactory.init();
		
		printSection("ThreadPool");
		ThreadPool.init();
		
		// Start game time task manager early.
		GameTimeTaskManager.getInstance();
		
		printSection("IdManager");
		IdManager.getInstance();
		
		printSection("Scripting Engine");
		EventDispatcher.getInstance();
		ScriptEngine.getInstance();
		
		printSection("World");
		InstanceManager.getInstance();
		World.init();
		MapRegionData.getInstance();
		AnnouncementsTable.getInstance();
		GlobalVariablesManager.getInstance();
		
		printSection("Data");
		CategoryData.getInstance();
		CubicData.getInstance();
		DynamicExpRateData.getInstance();
		SecondaryAuthData.getInstance();
		
		printSection("Skills");
		EffectHandler.getInstance().executeScript();
		EnchantSkillGroupsData.getInstance();
		SkillTreeData.getInstance();
		SkillData.getInstance();
		PetSkillData.getInstance();
		
		printSection("Items");
		ItemData.getInstance();
		EnchantItemGroupsData.getInstance();
		EnchantItemData.getInstance();
		EnchantItemOptionsData.getInstance();
		ElementalAttributeData.getInstance();
		OptionData.getInstance();
		EnchantItemHPBonusData.getInstance();
		MerchantPriceConfigTable.getInstance().loadInstances();
		BuyListData.getInstance();
		MultisellData.getInstance();
		RecipeData.getInstance();
		ArmorSetData.getInstance();
		FishData.getInstance();
		FishingMonstersData.getInstance();
		FishingRodsData.getInstance();
		HennaData.getInstance();
		PrimeShopData.getInstance();
		PcCafePointsManager.getInstance();
		ItemLifeTimeTaskManager.getInstance();
		
		printSection("Characters");
		ClassListData.getInstance();
		InitialEquipmentData.getInstance();
		InitialShortcutData.getInstance();
		ExperienceData.getInstance();
		ExperienceLossData.getInstance();
		KarmaLossData.getInstance();
		HitConditionBonusData.getInstance();
		PlayerTemplateData.getInstance();
		CharInfoTable.getInstance();
		AdminData.getInstance();
		RaidBossPointsManager.getInstance();
		PetDataTable.getInstance();
		CharSummonTable.getInstance().init();
		CaptchaManager.getInstance();
		
		if (PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED)
		{
			LOGGER.info("PremiumManager: Premium system is enabled.");
			PremiumManager.getInstance();
		}
		
		printSection("Clans");
		ClanTable.getInstance();
		CHSiegeManager.getInstance();
		ClanHallTable.getInstance();
		ClanHallAuctionManager.getInstance();
		
		printSection("Geodata");
		GeoEngine.getInstance();
		
		printSection("NPCs");
		DoorData.getInstance();
		FenceData.getInstance();
		SkillLearnData.getInstance();
		NpcData.getInstance();
		LevelUpCrystalData.getInstance();
		FakePlayerChatManager.getInstance();
		WalkingManager.getInstance();
		StaticObjectData.getInstance();
		ItemAuctionManager.getInstance();
		CastleManager.getInstance().loadInstances();
		SchemeBufferTable.getInstance();
		ZoneManager.getInstance();
		GrandBossManager.getInstance().initZones();
		EventDropManager.getInstance();
		
		printSection("Olympiad");
		Olympiad.getInstance();
		Hero.getInstance();
		
		printSection("Seven Signs");
		SevenSigns.getInstance();
		
		// Call to load caches.
		printSection("Cache");
		HtmCache.getInstance();
		CrestTable.getInstance();
		TeleporterData.getInstance();
		PartyMatchWaitingList.getInstance();
		PartyMatchRoomList.getInstance();
		PetitionManager.getInstance();
		AugmentationData.getInstance();
		CursedWeaponsManager.getInstance();
		TransformData.getInstance();
		BotReportTable.getInstance();
		
		if (SellBuffsConfig.SELLBUFF_ENABLED)
		{
			SellBuffsManager.getInstance();
		}
		
		if (MultilingualSupportConfig.MULTILANG_ENABLE)
		{
			SystemMessageId.loadLocalisations();
			NpcStringId.loadLocalisations();
			SendMessageLocalisationData.getInstance();
			NpcNameLocalisationData.getInstance();
		}
		
		printSection("Scripts");
		ScriptManager.getInstance();
		BoatManager.getInstance();
		AirShipManager.getInstance();
		SeedOfDestructionManager.getInstance();
		SeedOfInfinityManager.getInstance();
		
		try
		{
			LOGGER.info("Loading server scripts...");
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			ScriptEngine.getInstance().executeScriptList();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "Failed to execute script list!", e);
		}
		
		SpawnData.getInstance();
		DayNightSpawnManager.getInstance().trim().notifyChangeMode();
		DimensionalRiftManager.getInstance();
		RaidBossSpawnManager.getInstance();
		
		printSection("Siege");
		SiegeManager.getInstance().getSieges();
		CastleManager.getInstance().activateInstances();
		FortManager.getInstance().loadInstances();
		FortManager.getInstance().activateInstances();
		FortSiegeManager.getInstance();
		SiegeScheduleData.getInstance();
		MerchantPriceConfigTable.getInstance().updateReferences();
		TerritoryWarManager.getInstance();
		CastleManorManager.getInstance();
		MercTicketManager.getInstance();
		ScriptManager.getInstance().report();
		
		if (GeneralConfig.SAVE_DROPPED_ITEM)
		{
			ItemsOnGroundManager.getInstance();
		}
		
		if ((GeneralConfig.AUTODESTROY_ITEM_AFTER > 0) || (GeneralConfig.HERB_AUTO_DESTROY_TIME > 0))
		{
			ItemsAutoDestroyTaskManager.getInstance();
		}
		
		MonsterRaceManager.getInstance();
		LotteryManager.getInstance();
		SevenSigns.getInstance().spawnSevenSignsNPC();
		SevenSignsFestival.getInstance();
		AutoSpawnHandler.getInstance();
		LOGGER.info("AutoSpawnHandler: Loaded " + AutoSpawnHandler.getInstance().size() + " handlers in total.");
		
		if (WeddingConfig.ALLOW_WEDDING)
		{
			CoupleManager.getInstance();
		}
		
		if (GeneralConfig.ALT_FISH_CHAMPIONSHIP_ENABLED)
		{
			FishingChampionshipManager.getInstance();
		}
		
		KrateisCubeManager.getInstance();
		UndergroundColiseumManager.getInstance();
		DailyResetManager.getInstance();
		AntiFeedManager.getInstance().registerEvent(AntiFeedManager.GAME_ID);
		
		if (OfflinePlayConfig.ENABLE_OFFLINE_PLAY_COMMAND)
		{
			AntiFeedManager.getInstance().registerEvent(AntiFeedManager.OFFLINE_PLAY);
		}
		
		if (GeneralConfig.ALLOW_MAIL)
		{
			MailManager.getInstance();
		}
		
		if (CustomMailManagerConfig.CUSTOM_MAIL_MANAGER_ENABLED)
		{
			CustomMailManager.getInstance();
		}
		
		if (EventDispatcher.getInstance().hasListener(EventType.ON_SERVER_START))
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnServerStart());
		}
		
		PunishmentManager.getInstance();
		
		Runtime.getRuntime().addShutdownHook(Shutdown.getInstance());
		LOGGER.info("IdManager: Free ObjectID's remaining: " + IdManager.getInstance().getAvailableIdCount());
		
		if ((OfflineTradeConfig.OFFLINE_TRADE_ENABLE || OfflineTradeConfig.OFFLINE_CRAFT_ENABLE) && OfflineTradeConfig.RESTORE_OFFLINERS)
		{
			OfflineTraderTable.getInstance().restoreOfflineTraders();
		}
		
		if (OfflinePlayConfig.ENABLE_OFFLINE_PLAY_COMMAND && OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS)
		{
			OfflinePlayTable.getInstance().restoreOfflinePlayers();
		}
		
		if (ServerConfig.SERVER_RESTART_SCHEDULE_ENABLED)
		{
			ServerRestartManager.getInstance();
		}
		
		if (ServerConfig.PRECAUTIONARY_RESTART_ENABLED)
		{
			PrecautionaryRestartManager.getInstance();
		}
		
		if (ServerConfig.DEADLOCK_WATCHER)
		{
			final DeadlockWatcher deadlockWatcher = new DeadlockWatcher(Duration.ofSeconds(ServerConfig.DEADLOCK_CHECK_INTERVAL), () ->
			{
				if (ServerConfig.RESTART_ON_DEADLOCK)
				{
					World.broadcastToAllOnlinePlayers("Server has stability issues - restarting now.");
					Shutdown.getInstance().startShutdown(null, 60, true);
				}
			});
			deadlockWatcher.setDaemon(true);
			deadlockWatcher.start();
		}
		
		System.gc();
		final long totalMem = Runtime.getRuntime().maxMemory() / 1048576;
		LOGGER.info(StringUtil.concat(getClass().getSimpleName(), ": Started, using ", getUsedMemoryMB(), " of ", totalMem, " MB total memory."));
		LOGGER.info(StringUtil.concat(getClass().getSimpleName(), ": Maximum number of connected players is ", ServerConfig.MAXIMUM_ONLINE_USERS, "."));
		LOGGER.info(StringUtil.concat(getClass().getSimpleName(), ": Server loaded in ", ((System.currentTimeMillis() - START_TIME) / 1000), " seconds."));
		
		new ConnectionManager<>(new InetSocketAddress(ServerConfig.PORT_GAME), GameClient::new, new GamePacketHandler());
		
		LoginServerThread.getInstance().start();
		
		Toolkit.getDefaultToolkit().beep();
	}
	
	private void printSection(String section)
	{
		if (DevelopmentConfig.LOG_SERVER_LOAD_TIMES)
		{
			// Calculate elapsed time for previous section.
			final long currentTime = System.currentTimeMillis();
			final long sectionElapsed = currentTime - _sectionStartTime;
			
			// Log elapsed time for previous section if not the first section.
			if (_previousSectionName != null)
			{
				LOGGER.info(StringUtil.concat("...section [ ", _previousSectionName, " ] loaded in ", TimeUtil.formatDuration(sectionElapsed), "."));
			}
			
			// Update for next measurement.
			_previousSectionName = section;
			_sectionStartTime = currentTime;
		}
		
		// Build and log the new section header.
		final StringBuilder sb = new StringBuilder(61);
		sb.append("=[ ").append(section).append(" ]");
		while (sb.length() < 61)
		{
			sb.insert(0, '-');
		}
		LOGGER.info(sb.toString());
	}
	
	public long getUsedMemoryMB()
	{
		return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
	}
	
	public static long getStartTime()
	{
		return START_TIME;
	}
	
	public static void main(String[] args) throws Exception
	{
		new GameServer();
	}
}
