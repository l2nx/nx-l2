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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.character.StoredCharacter;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.config.custom.ScreenWelcomeMessageConfig;
import app.l2nx.highfive.config.custom.WeddingConfig;
import app.l2nx.highfive.data.sql.AnnouncementsTable;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.data.xml.EnchantItemGroupsData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.appearance.PlayerAppearance;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.IllegalActionPunishmentType;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.holders.player.Couple;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.residences.AuctionableHall;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.mail.WorldMail;
import app.l2nx.highfive.managers.AntiFeedManager;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.CoupleManager;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.managers.DimensionalRiftManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.managers.PcCafePointsManager;
import app.l2nx.highfive.managers.PetitionManager;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.managers.ServerRestartManager;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.mechanics.sevensigns.SevenSigns;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.FortSiege;
import app.l2nx.highfive.mechanics.siege.Siege;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.mechanics.skill.CommonSkill;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.variables.AccountVariables;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.holders.ClientHardwareInfoHolder;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Die;
import app.l2nx.highfive.network.protocol.p267.serverpackets.EtcStatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExBasicActionList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExGetBookMarkInfoPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExNotifyPremiumItem;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExRotation;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExShowContactList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExShowScreenMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExStorageMaxCount;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExVoteSystemInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.FriendList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.HennaInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ItemList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LeaveWorld;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowMemberListAll;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowMemberListUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeSkillList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeStatusChanged;
import app.l2nx.highfive.network.protocol.p267.serverpackets.QuestList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ShortcutInit;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SkillCoolTime;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.UserInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ValidateLocation;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import java.net.Inet4Address;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public final class PlayerWorldEntry {
    private static final int TRACE_HWINFO_MAX = 10_000;

    // Least recently used trace goes first, so a stream of new routes cannot grow the map without bound.
    private static final LinkedHashMap<String, ClientHardwareInfoHolder> TRACE_HWINFO =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ClientHardwareInfoHolder> eldest) {
                    return size() > TRACE_HWINFO_MAX;
                }
            };

    private PlayerWorldEntry() {}

    public static void enter(Player player, List<Inet4Address> route) {
        WorldPartition.checkOwner();
        if (player.hasEnteredWorld()) {
            return;
        }

        try {
            enterWorld(player, route);
        } finally {
            player.clearStoredWorldEntry();
            // A failed step after the spawn leaves the player in the world: writers waiting for it decide by the world.
            if (World.getPlayer(player.getObjectId()) == player) {
                IdManager.getInstance()
                        .findId(player.getObjectId())
                        .ifPresent(id -> WorldPartition.loadedCharacters().entered(id, player));
            }
        }
    }

    private static void enterWorld(Player player, List<Inet4Address> route) {
        final GameClient client = player.getClient();
        final StoredCharacter.WorldEntry stored = player.getStoredWorldEntry();

        // The legacy client state and hardware-info comparison read each hop as unsigned octets.
        final int[][] tracert = new int[route.size()][];
        for (int i = 0; i < tracert.length; i++) {
            final byte[] octets = route.get(i).getAddress();
            tracert[i] = new int[octets.length];
            for (int j = 0; j < octets.length; j++) {
                tracert[i][j] = octets[j] & 0xFF;
            }
        }

        final List<String> addresses =
                route.stream().map(Inet4Address::getHostAddress).toList();
        LoginService.getInstance().reportRoute(client.getAccountId(), addresses);
        client.setClientTracert(tracert);

        player.sendPacket(new UserInfo(player));

        if (GeneralConfig.RESTORE_PLAYER_INSTANCE) {
            player.setInstanceId(InstanceManager.getInstance().getPlayer(player.getObjectId()));
        } else {
            final int instanceId = InstanceManager.getInstance().getPlayer(player.getObjectId());
            if (instanceId > 0) {
                InstanceManager.getInstance().getInstance(instanceId).removePlayer(player.getObjectId());
            }
        }

        if (!player.isGM()) {
            player.updatePvpTitleAndColor(false);
        } else {
            gmStartupProcess:
            {
                if (GeneralConfig.GM_STARTUP_BUILDER_HIDE
                        && AdminData.getInstance().hasAccess("admin_hide", player.getAccessLevel())) {
                    player.setHiding(true);
                    player.sendSysMessage("hide is default for builder.");
                    player.sendSysMessage("FriendAddOff is default for builder.");
                    player.sendSysMessage("whisperoff is default for builder.");

                    // It isn't recommend to use the below custom L2J GMStartup functions together with retail-like
                    // GMStartupBuilderHide, so breaking the process at that stage.
                    break gmStartupProcess;
                }

                if (GeneralConfig.GM_STARTUP_INVULNERABLE
                        && AdminData.getInstance().hasAccess("admin_invul", player.getAccessLevel())) {
                    player.setInvul(true);
                }

                if (GeneralConfig.GM_STARTUP_INVISIBLE
                        && AdminData.getInstance().hasAccess("admin_invisible", player.getAccessLevel())) {
                    player.setInvisible(true);
                }

                if (GeneralConfig.GM_STARTUP_SILENCE
                        && AdminData.getInstance().hasAccess("admin_silence", player.getAccessLevel())) {
                    player.setSilenceMode(true);
                }

                if (GeneralConfig.GM_STARTUP_DIET_MODE
                        && AdminData.getInstance().hasAccess("admin_diet", player.getAccessLevel())) {
                    player.setDietMode(true);
                    player.refreshOverloaded();
                }
            }

            if (GeneralConfig.GM_STARTUP_AUTO_LIST
                    && AdminData.getInstance().hasAccess("admin_gmliston", player.getAccessLevel())) {
                AdminData.getInstance().addGm(player, false);
            } else {
                AdminData.getInstance().addGm(player, true);
            }

            if (GeneralConfig.GM_GIVE_SPECIAL_SKILLS) {
                SkillTreeData.getInstance().addSkills(player, false);
            }

            if (GeneralConfig.GM_GIVE_SPECIAL_AURA_SKILLS) {
                SkillTreeData.getInstance().addSkills(player, true);
            }
        }

        if (player.getCurrentHp() < 0.5) {
            player.setDead(true);
        }

        boolean showClanNotice = false;

        final Clan clan = player.getClan();
        if (clan != null) {
            player.sendPacket(new PledgeSkillList(clan));
            notifyClanMembers(player);

            notifySponsorOrApprentice(player);

            final AuctionableHall clanHall = ClanHallTable.getInstance().getClanHallByOwner(clan);
            if ((clanHall != null) && !clanHall.getPaid()) {
                final SystemMessage sm = new SystemMessage(
                        SystemMessageId
                                .PAYMENT_FOR_YOUR_CLAN_HALL_HAS_NOT_BEEN_MADE_PLEASE_MAKE_PAYMENT_TO_YOUR_CLAN_WAREHOUSE_BY_S1_TOMORROW);
                sm.addInt(clanHall.getLease());
                player.sendPacket(sm);
            }

            for (Siege siege : SiegeManager.getInstance().getSieges()) {
                if (!siege.isInProgress()) {
                    continue;
                }

                if (siege.checkIsAttacker(clan)) {
                    player.setSiegeState((byte) 1);
                    player.setSiegeSide(siege.getCastle().getResidenceId());
                } else if (siege.checkIsDefender(clan)) {
                    player.setSiegeState((byte) 2);
                    player.setSiegeSide(siege.getCastle().getResidenceId());
                }
            }

            for (FortSiege siege : FortSiegeManager.getInstance().getSieges()) {
                if (!siege.isInProgress()) {
                    continue;
                }

                if (siege.checkIsAttacker(clan)) {
                    player.setSiegeState((byte) 1);
                    player.setSiegeSide(siege.getFort().getResidenceId());
                } else if (siege.checkIsDefender(clan)) {
                    player.setSiegeState((byte) 2);
                    player.setSiegeSide(siege.getFort().getResidenceId());
                }
            }

            for (SiegableHall hall :
                    CHSiegeManager.getInstance().getConquerableHalls().values()) {
                if (!hall.isInSiege()) {
                    continue;
                }

                if (hall.isRegistered(clan)) {
                    player.setSiegeState((byte) 1);
                    player.setSiegeSide(hall.getId());
                    player.setInHideoutSiege(true);
                }
            }

            player.sendPacket(new PledgeShowMemberListAll(clan, player));
            player.sendPacket(new PledgeStatusChanged(clan));

            if (clan.getCastleId() > 0) {
                final Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
                if (castle != null) {
                    castle.giveResidentialSkills(player);
                }
            }

            if (clan.getFortId() > 0) {
                final Fort fort = FortManager.getInstance().getFortByOwner(clan);
                if (fort != null) {
                    fort.giveResidentialSkills(player);
                }
            }

            showClanNotice = clan.isNoticeEnabled();
        }

        if (TerritoryWarManager.getInstance().getRegisteredTerritoryId(player) > 0) {
            if (TerritoryWarManager.getInstance().isTWInProgress()) {
                player.setSiegeState((byte) 1);
            }

            player.setSiegeSide(TerritoryWarManager.getInstance().getRegisteredTerritoryId(player));
        }

        if (SevenSigns.getInstance().isSealValidationPeriod()
                && (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE) != SevenSigns.CABAL_NULL)) {
            final int cabal = SevenSigns.getInstance().getPlayerCabal(player.getObjectId());
            if (cabal != SevenSigns.CABAL_NULL) {
                if (cabal == SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE)) {
                    player.addSkill(CommonSkill.THE_VICTOR_OF_WAR.getSkill());
                } else {
                    player.addSkill(CommonSkill.THE_VANQUISHED_OF_WAR.getSkill());
                }
            }
        } else {
            player.removeSkill(CommonSkill.THE_VICTOR_OF_WAR.getSkill());
            player.removeSkill(CommonSkill.THE_VANQUISHED_OF_WAR.getSkill());
        }

        if (PlayerConfig.ENABLE_VITALITY && PlayerConfig.RECOVER_VITALITY_ON_RECONNECT) {
            final float points =
                    (RatesConfig.RATE_RECOVERY_ON_RECONNECT * (ClockBridge.wallMillis() - player.getLastAccess()))
                            / 60000;
            if (points > 0) {
                player.updateVitalityPoints(points, false, true);
            }
        }

        if (PlayerConfig.NEVIT_ENABLED) {
            player.checkRecoBonusTask(stored == null ? null : stored.recommendation());
        }

        player.getMacros().sendUpdate();

        player.sendPacket(new ItemList(player, false));

        player.sendPacket(new ExGetBookMarkInfoPacket(player));

        player.sendPacket(new ShortcutInit(player));

        player.sendPacket(ExBasicActionList.STATIC_PACKET);

        player.sendPacket(new HennaInfo(player));
        Quest.playerEnter(
                player,
                stored == null ? List.of() : stored.questStates(),
                stored == null ? List.of() : stored.questVariables());

        if (FactionSystemConfig.FACTION_SYSTEM_ENABLED) {
            final PlayerAppearance appearance = player.getAppearance();
            if (player.isGood()) {
                appearance.setNameColor(FactionSystemConfig.FACTION_GOOD_NAME_COLOR);
                appearance.setTitleColor(FactionSystemConfig.FACTION_GOOD_NAME_COLOR);
                player.sendMessage("Welcome " + player.getName() + ", you are fighting for the "
                        + FactionSystemConfig.FACTION_GOOD_TEAM_NAME + " faction.");
                player.sendPacket(new ExShowScreenMessage(
                        "Welcome " + player.getName() + ", you are fighting for the "
                                + FactionSystemConfig.FACTION_GOOD_TEAM_NAME + " faction.",
                        10000));
                player.updateUserInfo(); // for seeing self name color
            } else if (player.isEvil()) {
                appearance.setNameColor(FactionSystemConfig.FACTION_EVIL_NAME_COLOR);
                appearance.setTitleColor(FactionSystemConfig.FACTION_EVIL_NAME_COLOR);
                player.sendMessage("Welcome " + player.getName() + ", you are fighting for the "
                        + FactionSystemConfig.FACTION_EVIL_TEAM_NAME + " faction.");
                player.sendPacket(new ExShowScreenMessage(
                        "Welcome " + player.getName() + ", you are fighting for the "
                                + FactionSystemConfig.FACTION_EVIL_TEAM_NAME + " faction.",
                        10000));
                player.updateUserInfo(); // for seeing self name color
            }
        }

        player.sendPacket(new QuestList(player));
        if (PlayerConfig.PLAYER_SPAWN_PROTECTION > 0) {
            player.setSpawnProtection(true);
        }

        player.spawnMe(player.getX(), player.getY(), player.getZ());
        player.sendPacket(new ExRotation(player.getObjectId(), player.getHeading()));

        if (player.isCursedWeaponEquipped()) {
            CursedWeaponsManager.getInstance()
                    .getCursedWeapon(player.getCursedWeaponEquippedId())
                    .cursedOnLogin();
        }

        player.updateEffectIcons();

        player.sendPacket(new EtcStatusUpdate(player));

        player.sendPacket(new ExStorageMaxCount(player));
        player.sendPacket(new FriendList(player));
        SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_FRIEND_S1_JUST_LOGGED_IN);
        sm.addString(player.getName());
        for (int id : player.getFriendList()) {
            final WorldObject obj = World.findObject(id);
            if (obj != null) {
                obj.sendPacket(sm);
            }
        }

        player.sendPacket(SystemMessageId.WELCOME_TO_THE_WORLD_OF_LINEAGE_II);

        SevenSigns.getInstance().sendCurrentPeriodMsg(player);
        AnnouncementsTable.getInstance().showAnnouncements(player);

        if ((ServerConfig.SERVER_RESTART_SCHEDULE_ENABLED) && (ServerConfig.SERVER_RESTART_SCHEDULE_MESSAGE)) {
            player.sendPacket(new CreatureSay(
                    null,
                    ChatType.BATTLEFIELD,
                    "[SERVER]",
                    "Next restart is scheduled at "
                            + ServerRestartManager.getInstance().getNextRestartTime() + "."));
        }

        if (showClanNotice) {
            final NpcHtmlMessage notice = new NpcHtmlMessage();
            notice.setFile(player, "data/html/clanNotice.htm");
            notice.replace("%clan_name%", player.getClan().getName());
            final String text = player.getClan().getNotice();
            notice.replace("%notice_text%", text == null ? null : text.replaceAll("(\r\n|\n)", "<br>"));
            notice.disableValidation();
            player.sendPacket(notice);
        } else if (GeneralConfig.SERVER_NEWS) {
            final String serverNews = HtmCache.getInstance().getHtm(player, "data/html/servnews.htm");
            if (serverNews != null) {
                player.sendPacket(new NpcHtmlMessage(serverNews));
            }
        }

        if (PlayerConfig.PETITIONING_ALLOWED) {
            PetitionManager.getInstance().checkPetitionMessages(player);
        }

        if (player.isAlikeDead()) {
            // No broadcast needed since the player will already spawn dead to others.
            player.sendPacket(new Die(player));
        }

        player.onPlayerEnter();

        player.getInventory().applyItemSkills();

        player.sendSkillList();

        player.sendPacket(new SkillCoolTime(player));
        if (PlayerConfig.NEVIT_ENABLED) {
            player.sendPacket(new ExVoteSystemInfo(player));
        }

        player.sendPacket(new ExShowContactList(player));
        try (Snapshot<Item> items = ScratchBuffers.empty()) {
            player.getInventory()
                    .collectItems(items, i -> i.isTimeLimitedItem() || (i.isShadowItem() && i.isEquipped()));
            for (Item i : items) {
                if (i.isTimeLimitedItem()) {
                    i.scheduleLifeTimeTask();
                }

                if (i.isShadowItem() && i.isEquipped()) {
                    i.decreaseMana(false);
                }
            }

            items.clear();
            player.getWarehouse().collectItems(items, Item::isTimeLimitedItem);
            for (Item i : items) {
                i.scheduleLifeTimeTask();
            }
        }

        if (DimensionalRiftManager.getInstance()
                .checkIfInRiftZone(player.getX(), player.getY(), player.getZ(), false)) {
            DimensionalRiftManager.getInstance().teleportToWaitingRoom(player);
        }

        if (player.getClanJoinExpiryTime() > ClockBridge.wallMillis()) {
            player.sendPacket(
                    SystemMessageId
                            .YOU_HAVE_RECENTLY_BEEN_DISMISSED_FROM_A_CLAN_YOU_ARE_NOT_ALLOWED_TO_JOIN_ANOTHER_CLAN_FOR_24_HOURS);
        }

        if (player.getInventory().getItemByItemId(9819) != null) {
            final Fort fort = FortManager.getInstance().getFort(player);
            if (fort != null) {
                FortSiegeManager.getInstance().dropCombatFlag(player, fort.getResidenceId());
            } else {
                final BodyPart bodyPart =
                        BodyPart.fromItem(player.getInventory().getItemByItemId(9819));
                player.getInventory().unEquipItemInBodySlot(bodyPart);
                player.destroyItem(
                        ItemProcessType.DESTROY, player.getInventory().getItemByItemId(9819), null, true);
            }
        }

        if (!player.isGM()
                && player.isInsideZone(ZoneId.SIEGE)
                && (!player.isInSiege() || (player.getSiegeState() < 2))) {
            player.teleToLocation(TeleportWhereType.TOWN);
        }

        if (PlayerConfig.OVER_ENCHANT_PROTECTION && !player.isGM()) {
            boolean punish = false;
            try (Snapshot<Item> overEnchanted = ScratchBuffers.empty()) {
                player.getInventory().collectItems(overEnchanted, PlayerWorldEntry::isOverEnchanted);
                for (Item item : overEnchanted) {
                    log.info("Over-enchanted (+{}) {} has been removed from {}", item.getEnchantLevel(), item, player);
                    player.getInventory().destroyItem(ItemProcessType.DESTROY, item, player, null);
                    punish = true;
                }
            }

            if (punish && (PlayerConfig.OVER_ENCHANT_PUNISHMENT != IllegalActionPunishmentType.NONE)) {
                player.sendMessage("[Server]: You have over-enchanted items!");
                player.sendMessage("[Server]: Respect our server rules.");
                player.sendPacket(new ExShowScreenMessage("You have over-enchanted items!", 6000));
                PunishmentManager.handleIllegalPlayerAction(
                        player, player.getName() + " has over-enchanted items.", PlayerConfig.OVER_ENCHANT_PUNISHMENT);
            }
        }

        if ((player.getInventory().getItemByItemId(8190) != null) && !player.isCursedWeaponEquipped()) {
            player.destroyItem(ItemProcessType.DESTROY, player.getInventory().getItemByItemId(8190), null, true);
        }

        if ((player.getInventory().getItemByItemId(8689) != null) && !player.isCursedWeaponEquipped()) {
            player.destroyItem(ItemProcessType.DESTROY, player.getInventory().getItemByItemId(8689), null, true);
        }

        WorldMail.getInstance().onWorldEntered(player);

        if (ScreenWelcomeMessageConfig.WELCOME_MESSAGE_ENABLED) {
            player.sendPacket(new ExShowScreenMessage(
                    ScreenWelcomeMessageConfig.WELCOME_MESSAGE_TEXT, ScreenWelcomeMessageConfig.WELCOME_MESSAGE_TIME));
        }

        final int birthday = player.checkBirthDay();
        if (birthday == 0) {
            player.sendPacket(SystemMessageId.HAPPY_BIRTHDAY_ALEGRIA_HAS_SENT_YOU_A_BIRTHDAY_GIFT);
        } else if (birthday != -1) {
            sm = new SystemMessage(
                    SystemMessageId
                            .THERE_ARE_S1_DAYS_REMAINING_UNTIL_YOUR_BIRTHDAY_ON_YOUR_BIRTHDAY_YOU_WILL_RECEIVE_A_GIFT_THAT_ALEGRIA_HAS_CAREFULLY_PREPARED);
            sm.addString(Integer.toString(birthday));
            player.sendPacket(sm);
        }

        if (player.hasPremiumItems()) {
            player.sendPacket(ExNotifyPremiumItem.STATIC_PACKET);
        }

        if ((OfflineTradeConfig.OFFLINE_TRADE_ENABLE || OfflineTradeConfig.OFFLINE_CRAFT_ENABLE)
                && OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME) {
            OfflineTraderTable.getInstance().onTransaction(player, true);
        }

        if (player.getVariables().getBoolean("EXPOFF", false)) {
            player.disableExpGain();
            player.sendMessage("Experience gain is disabled.");
        }

        player.broadcastUserInfo();

        player.sendPacket(new ValidateLocation(player));

        // Unstuck players that had client open when server crashed.
        player.sendPacket(ActionFailed.STATIC_PACKET);

        if (ServerConfig.HARDWARE_INFO_ENABLED) {
            WorldPartition.loop()
                    .schedule(
                            () -> {
                                final StringBuilder sb = new StringBuilder();
                                for (int[] i : tracert) {
                                    for (int j : i) {
                                        sb.append(j);
                                        sb.append('.');
                                    }
                                }

                                final String trace = sb.toString();

                                // Whether a hardware info packet was received during this session, before any fallback.
                                final ClientHardwareInfoHolder freshHwInfo = client.getHardwareInfo();

                                ClientHardwareInfoHolder hwInfo = freshHwInfo;
                                if (hwInfo != null) {
                                    hwInfo.store(player);
                                    TRACE_HWINFO.put(trace, hwInfo);
                                } else {
                                    hwInfo = TRACE_HWINFO.get(trace);
                                    if (hwInfo != null) {
                                        hwInfo.store(player);
                                        client.setHardwareInfo(hwInfo);
                                    } else {
                                        final String storedInfo =
                                                player.getAccountVariables().getString(AccountVariables.HWID, "");
                                        if (!storedInfo.isEmpty()) {
                                            try {
                                                hwInfo = new ClientHardwareInfoHolder(storedInfo);
                                                TRACE_HWINFO.put(trace, hwInfo);
                                                client.setHardwareInfo(hwInfo);
                                            } catch (Exception e) {
                                                // Malformed or legacy stored hardware info; treat as missing.
                                                log.warn(
                                                        "Could not parse stored hardware info for account {}: {}",
                                                        player.getAccountName(),
                                                        e.getMessage());
                                            }
                                        }
                                    }
                                }

                                if ((hwInfo != null)
                                        && PunishmentManager.getInstance()
                                                .hasPunishment(
                                                        hwInfo.getMacAddress(),
                                                        PunishmentAffect.HWID,
                                                        PunishmentType.BAN)) {
                                    Disconnection.of(client).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                                    return;
                                }

                                // With RequireFreshHWID a packet must arrive this session; otherwise a stored value
                                // passes.
                                if (ServerConfig.KICK_MISSING_HWID
                                        && (ServerConfig.REQUIRE_FRESH_HWID
                                                ? (freshHwInfo == null)
                                                : (hwInfo == null))) {
                                    Disconnection.of(client).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                                } else if (ServerConfig.MAX_PLAYERS_PER_HWID > 0) {
                                    int count = 0;
                                    for (Player plr : World.getPlayers()) {
                                        if (plr.isOnlineInt() == 1) {
                                            final ClientHardwareInfoHolder hwi =
                                                    plr.getClient().getHardwareInfo();
                                            if ((hwi != null) && hwi.equals(hwInfo)) {
                                                count++;
                                            }
                                        }
                                    }

                                    if (count > ServerConfig.MAX_PLAYERS_PER_HWID) {
                                        Disconnection.of(client).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                                    }
                                }
                            },
                            ServerConfig.HWID_CHECK_DELAY,
                            TimeUnit.MILLISECONDS);
        }

        if ((player.getRace() == Race.DARK_ELF)) {
            final Skill shadowSense = player.getKnownSkill(CommonSkill.SHADOW_SENSE.getId());
            if (shadowSense != null) {
                boolean isNight = GameTimeTaskManager.getInstance().isNight();
                if (isNight) {
                    player.sendPacket(
                            new SystemMessage(SystemMessageId.IT_IS_NOW_MIDNIGHT_AND_THE_EFFECT_OF_S1_CAN_BE_FELT)
                                    .addSkillName(shadowSense));
                } else {
                    player.sendPacket(
                            new SystemMessage(SystemMessageId.IT_IS_DAWN_AND_THE_EFFECT_OF_S1_WILL_NOW_DISAPPEAR)
                                    .addSkillName(shadowSense));
                }
            }
        }

        AntiFeedManager.getInstance().removePlayer(AntiFeedManager.OFFLINE_PLAY, player);

        player.setEnteredWorld();

        if (WeddingConfig.ALLOW_WEDDING) {
            final int playerObjectId = player.getObjectId();
            for (Couple couple : CoupleManager.getInstance().getCouples()) {
                if ((couple.getPlayer1Id() == playerObjectId) || (couple.getPlayer2Id() == playerObjectId)) {
                    if (couple.getMaried()) {
                        player.setMarried(true);
                    }

                    player.setCoupleId(couple.getId());

                    if (couple.getPlayer1Id() == playerObjectId) {
                        player.setPartnerId(couple.getPlayer2Id());
                    } else {
                        player.setPartnerId(couple.getPlayer1Id());
                    }
                }
            }

            final int partnerId = player.getPartnerId();
            if (partnerId != 0) {
                final Player partner = World.getPlayer(partnerId);
                if (partner != null) {
                    partner.sendMessage("Your partner has logged in.");
                }
            }
        }

        if ((player.hasPremiumStatus() || !PremiumSystemConfig.PC_CAFE_ONLY_PREMIUM)
                && PremiumSystemConfig.PC_CAFE_RETAIL_LIKE) {
            PcCafePointsManager.getInstance().run(player);
        }
    }

    private static boolean isOverEnchanted(Item item) {
        if (!item.isEquipable()) {
            return false;
        }

        final EnchantItemGroupsData limits = EnchantItemGroupsData.getInstance();
        final boolean accessory = item.getTemplate().getType2() == ItemTemplate.TYPE2_ACCESSORY;
        return (item.isWeapon() && (item.getEnchantLevel() > limits.getMaxWeaponEnchant()))
                || (accessory && (item.getEnchantLevel() > limits.getMaxAccessoryEnchant()))
                || (item.isArmor() && !accessory && (item.getEnchantLevel() > limits.getMaxArmorEnchant()));
    }

    private static void notifyClanMembers(Player player) {
        final Clan clan = player.getClan();
        if (clan != null) {
            clan.getClanMember(player.getObjectId()).setPlayer(player);

            final SystemMessage msg = new SystemMessage(SystemMessageId.CLAN_MEMBER_S1_HAS_LOGGED_INTO_GAME);
            msg.addString(player.getName());
            clan.broadcastToOtherOnlineMembers(msg, player);
            clan.broadcastToOtherOnlineMembers(new PledgeShowMemberListUpdate(player), player);
        }
    }

    private static void notifySponsorOrApprentice(Player player) {
        if (player.getSponsor() != 0) {
            final Player sponsor = World.getPlayer(player.getSponsor());
            if (sponsor != null) {
                final SystemMessage msg = new SystemMessage(SystemMessageId.YOUR_APPRENTICE_S1_HAS_LOGGED_IN);
                msg.addString(player.getName());
                sponsor.sendPacket(msg);
            }
        } else if (player.getApprentice() != 0) {
            final Player apprentice = World.getPlayer(player.getApprentice());
            if (apprentice != null) {
                final SystemMessage msg = new SystemMessage(SystemMessageId.YOUR_SPONSOR_C1_HAS_LOGGED_IN);
                msg.addString(player.getName());
                apprentice.sendPacket(msg);
            }
        }
    }
}
