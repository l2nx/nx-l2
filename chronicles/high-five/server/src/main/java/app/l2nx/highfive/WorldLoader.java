/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
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

import app.l2nx.boot.ServerExit;
import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.clock.ClockBridge;
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
import app.l2nx.highfive.managers.PlayerPersistenceManager;
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
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.scripting.ScriptEngine;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.taskmanagers.ItemLifeTimeTaskManager;
import app.l2nx.highfive.taskmanagers.ItemsAutoDestroyTaskManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class WorldLoader {

    private static volatile long startTime;
    private long sectionStartTime;
    private String previousSectionName;

    private final ServerExit serverExit;

    public void load() {
        startTime = ClockBridge.gameMillis();
        sectionStartTime = startTime;
        Shutdown.bind(serverExit);

        ConfigLoader.init();
        startEngine();
    }

    private void startEngine() {
        GameTimeTaskManager.getInstance().start(WorldPartition.loop());

        printSection("Scripting Engine");
        EventDispatcher.getInstance();
        ScriptEngine.getInstance();

        printSection("World");
        InstanceManager.getInstance().load();
        World.init();
        MapRegionData.getInstance();
        AnnouncementsTable.getInstance().load();
        GlobalVariablesManager.getInstance().restoreMe();

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
        BuyListData.getInstance().load();
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
        PlayerPersistenceManager.getInstance().cleanUpAtStart(Instant.ofEpochMilli(ClockBridge.wallMillis()));
        CharInfoTable.getInstance().load();
        AdminData.getInstance();
        RaidBossPointsManager.getInstance().load();
        PetDataTable.getInstance();
        CharSummonTable.getInstance().init();
        CaptchaManager.getInstance();

        if (PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED) {
            log.info("PremiumManager: Premium system is enabled.");
            PremiumManager.getInstance().load();
        }

        printSection("Clans");
        ClanTable.getInstance().load();
        ClanHallAuctionManager.getInstance().load();
        ClanHallTable.getInstance().load();
        CHSiegeManager.getInstance().loadClanHalls();

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
        ItemAuctionManager.getInstance().load();
        CastleManager.getInstance().loadInstances();
        SiegeManager.getInstance().load();
        SchemeBufferTable.getInstance().load();
        ZoneManager.getInstance();
        GrandBossManager.getInstance().load();
        GrandBossManager.getInstance().initZones();
        EventDropManager.getInstance();

        printSection("Olympiad");
        Hero.getInstance().load();
        Olympiad.getInstance().load();

        printSection("Seven Signs");
        SevenSigns.getInstance().load();

        printSection("Cache");
        HtmCache.getInstance();
        CrestTable.getInstance().load();
        TeleporterData.getInstance();
        PartyMatchWaitingList.getInstance();
        PartyMatchRoomList.getInstance();
        PetitionManager.getInstance();
        AugmentationData.getInstance();
        CursedWeaponsManager.getInstance().init();
        TransformData.getInstance();
        BotReportTable.getInstance().load();

        if (SellBuffsConfig.SELLBUFF_ENABLED) {
            SellBuffsManager.getInstance();
        }

        if (MultilingualSupportConfig.MULTILANG_ENABLE) {
            SystemMessageId.loadLocalisations();
            NpcStringId.loadLocalisations();
            SendMessageLocalisationData.getInstance();
            NpcNameLocalisationData.getInstance();
        }

        printSection("Scripts");
        ScriptManager.getInstance();
        BoatManager.getInstance();
        AirShipManager.getInstance().load();
        SeedOfDestructionManager.getInstance();
        SeedOfInfinityManager.getInstance();
        TerritoryWarManager.getInstance().load();

        try {
            log.info("Loading server scripts...");
            ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER);
            ScriptEngine.getInstance().executeScriptList();
        } catch (Exception e) {
            log.warn("Failed to execute script list!", e);
        }

        SpawnData.getInstance();
        DayNightSpawnManager.getInstance().trim().notifyChangeMode();
        DimensionalRiftManager.getInstance();
        RaidBossSpawnManager.getInstance().load();

        printSection("Siege");
        SiegeManager.getInstance().getSieges();
        CastleManager.getInstance().activateInstances();
        FortManager.getInstance().loadInstances();
        FortSiegeManager.getInstance().load();
        FortManager.getInstance().activateInstances();
        SiegeScheduleData.getInstance();
        MerchantPriceConfigTable.getInstance().updateReferences();
        CastleManorManager.getInstance().init();
        MercTicketManager.getInstance().load();
        ScriptManager.getInstance().report();

        ItemsOnGroundManager.getInstance().load();

        if ((GeneralConfig.AUTODESTROY_ITEM_AFTER > 0) || (GeneralConfig.HERB_AUTO_DESTROY_TIME > 0)) {
            ItemsAutoDestroyTaskManager.getInstance();
        }

        MonsterRaceManager.getInstance().load();
        LotteryManager.getInstance().load();
        SevenSigns.getInstance().spawnSevenSignsNPC();
        SevenSignsFestival.getInstance().load();
        AutoSpawnHandler.getInstance();
        log.info(
                "AutoSpawnHandler: Loaded {} handlers in total.",
                AutoSpawnHandler.getInstance().size());

        if (WeddingConfig.ALLOW_WEDDING) {
            CoupleManager.getInstance().load();
        }

        if (GeneralConfig.ALT_FISH_CHAMPIONSHIP_ENABLED) {
            FishingChampionshipManager.getInstance().load();
        }

        KrateisCubeManager.getInstance();
        UndergroundColiseumManager.getInstance().load();
        DailyResetManager.getInstance().load();
        AntiFeedManager.getInstance().registerEvent(AntiFeedManager.GAME_ID);

        if (OfflinePlayConfig.ENABLE_OFFLINE_PLAY_COMMAND) {
            AntiFeedManager.getInstance().registerEvent(AntiFeedManager.OFFLINE_PLAY);
        }

        if (GeneralConfig.ALLOW_MAIL) {
            MailManager.getInstance().load();
        }

        if (CustomMailManagerConfig.CUSTOM_MAIL_MANAGER_ENABLED) {
            CustomMailManager.getInstance().load();
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_SERVER_START)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnServerStart());
        }

        PunishmentManager.getInstance().load();

        log.info("Object ids in use: {}", IdManager.getInstance().getAllocatedIdCount());

        if ((OfflineTradeConfig.OFFLINE_TRADE_ENABLE || OfflineTradeConfig.OFFLINE_CRAFT_ENABLE)
                && OfflineTradeConfig.RESTORE_OFFLINERS) {
            OfflineTraderTable.getInstance().restoreOfflineTraders();
        }

        if (OfflinePlayConfig.ENABLE_OFFLINE_PLAY_COMMAND && OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS) {
            OfflinePlayTable.getInstance().restoreOfflinePlayers();
        }

        if (ServerConfig.SERVER_RESTART_SCHEDULE_ENABLED) {
            ServerRestartManager.getInstance();
        }

        if (ServerConfig.PRECAUTIONARY_RESTART_ENABLED) {
            PrecautionaryRestartManager.getInstance();
        }

        System.gc();
        final long totalMem = Runtime.getRuntime().maxMemory() / 1048576;
        log.info("Started, using {} of {} MB total memory.", getUsedMemoryMB(), totalMem);
        log.info("Maximum number of connected players is {}.", ServerConfig.MAXIMUM_ONLINE_USERS);
        log.info("Server loaded in {} seconds.", ((ClockBridge.gameMillis() - startTime) / 1000));
    }

    private void printSection(String section) {
        if (DevelopmentConfig.LOG_SERVER_LOAD_TIMES) {
            final long currentTime = ClockBridge.gameMillis();
            final long sectionElapsed = currentTime - sectionStartTime;

            if (previousSectionName != null) {
                log.info("...section [ {} ] loaded in {} ms.", previousSectionName, sectionElapsed);
            }

            previousSectionName = section;
            sectionStartTime = currentTime;
        }

        final StringBuilder sb = new StringBuilder(61);
        sb.append("=[ ").append(section).append(" ]");
        while (sb.length() < 61) {
            sb.insert(0, '-');
        }
        log.info(sb.toString());
    }

    public long getUsedMemoryMB() {
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
    }

    public static long getStartTime() {
        return startTime;
    }
}
