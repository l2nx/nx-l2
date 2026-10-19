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
package app.l2nx.highfive.config;

import app.l2nx.highfive.config.custom.AllowedPlayerRacesConfig;
import app.l2nx.highfive.config.custom.AutoPlayConfig;
import app.l2nx.highfive.config.custom.AutoPotionsConfig;
import app.l2nx.highfive.config.custom.BankingConfig;
import app.l2nx.highfive.config.custom.BossAnnouncementsConfig;
import app.l2nx.highfive.config.custom.CancelReturnConfig;
import app.l2nx.highfive.config.custom.CaptchaConfig;
import app.l2nx.highfive.config.custom.ChampionMonstersConfig;
import app.l2nx.highfive.config.custom.ChatModerationConfig;
import app.l2nx.highfive.config.custom.ClassBalanceConfig;
import app.l2nx.highfive.config.custom.CommunityBoardConfig;
import app.l2nx.highfive.config.custom.CustomMailManagerConfig;
import app.l2nx.highfive.config.custom.DelevelManagerConfig;
import app.l2nx.highfive.config.custom.DualboxCheckConfig;
import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.config.custom.FakePlayersConfig;
import app.l2nx.highfive.config.custom.FindPvpConfig;
import app.l2nx.highfive.config.custom.FreeMountsConfig;
import app.l2nx.highfive.config.custom.HellboundStatusConfig;
import app.l2nx.highfive.config.custom.MerchantZeroSellPriceConfig;
import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.config.custom.NoblessMasterConfig;
import app.l2nx.highfive.config.custom.NpcStatMultipliersConfig;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.config.custom.OnlineInfoConfig;
import app.l2nx.highfive.config.custom.PasswordChangeConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.config.custom.PrivateStoreRangeConfig;
import app.l2nx.highfive.config.custom.PvpAnnounceConfig;
import app.l2nx.highfive.config.custom.PvpRewardItemConfig;
import app.l2nx.highfive.config.custom.PvpTitleColorConfig;
import app.l2nx.highfive.config.custom.RandomSpawnsConfig;
import app.l2nx.highfive.config.custom.RebirthConfig;
import app.l2nx.highfive.config.custom.SchemeBufferConfig;
import app.l2nx.highfive.config.custom.ScreenWelcomeMessageConfig;
import app.l2nx.highfive.config.custom.SellBuffsConfig;
import app.l2nx.highfive.config.custom.ServerTimeConfig;
import app.l2nx.highfive.config.custom.StartingLocationConfig;
import app.l2nx.highfive.config.custom.StartingTitleConfig;
import app.l2nx.highfive.config.custom.TransmogConfig;
import app.l2nx.highfive.config.custom.WalkerBotProtectionConfig;
import app.l2nx.highfive.config.custom.WarehouseSortingConfig;
import app.l2nx.highfive.config.custom.WeddingConfig;

/**
 * Central configuration loader for initializing all server configuration components.<br>
 * This class serves as the entry point for loading all server configuration settings from various configuration files.<br>
 * The configurations are typically located in the config directory within the server root folder.
 * @author Mobius
 */
public class ConfigLoader {
    public static void init() {
        ServerConfig.load();
        NetworkConfig.load();

        // Main configurations.
        ConquerableHallSiegeConfig.load();
        DevelopmentConfig.load();
        FeatureConfig.load();
        FloodProtectorConfig.load();
        GeneralConfig.load();
        GeoEngineConfig.load();
        GraciaSeedsConfig.load();
        GrandBossConfig.load();
        IdManagerConfig.load();
        NpcConfig.load();
        OlympiadConfig.load();
        PlayerConfig.load();
        PvpConfig.load();
        RatesConfig.load();
        UndergroundColiseumConfig.load();

        // Custom configurations.
        AllowedPlayerRacesConfig.load();
        AutoPlayConfig.load();
        AutoPotionsConfig.load();
        BankingConfig.load();
        BossAnnouncementsConfig.load();
        CancelReturnConfig.load();
        CaptchaConfig.load();
        ChampionMonstersConfig.load();
        ChatModerationConfig.load();
        ClassBalanceConfig.load();
        CommunityBoardConfig.load();
        CustomMailManagerConfig.load();
        DelevelManagerConfig.load();
        DualboxCheckConfig.load();
        FactionSystemConfig.load();
        FakePlayersConfig.load();
        FindPvpConfig.load();
        FreeMountsConfig.load();
        HellboundStatusConfig.load();
        MerchantZeroSellPriceConfig.load();
        MultilingualSupportConfig.load();
        NoblessMasterConfig.load();
        NpcStatMultipliersConfig.load();
        OfflinePlayConfig.load();
        OfflineTradeConfig.load();
        OnlineInfoConfig.load();
        PasswordChangeConfig.load();
        PremiumSystemConfig.load();
        PrivateStoreRangeConfig.load();
        PvpAnnounceConfig.load();
        PvpRewardItemConfig.load();
        PvpTitleColorConfig.load();
        RandomSpawnsConfig.load();
        RebirthConfig.load();
        SchemeBufferConfig.load();
        ScreenWelcomeMessageConfig.load();
        SellBuffsConfig.load();
        ServerTimeConfig.load();
        StartingLocationConfig.load();
        StartingTitleConfig.load();
        TransmogConfig.load();
        WalkerBotProtectionConfig.load();
        WarehouseSortingConfig.load();
        WeddingConfig.load();
    }
}
