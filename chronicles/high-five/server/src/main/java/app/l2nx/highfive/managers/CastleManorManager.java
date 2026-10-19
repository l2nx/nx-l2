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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.manor.CropProcure;
import app.l2nx.highfive.mechanics.siege.manor.ManorMode;
import app.l2nx.highfive.mechanics.siege.manor.Seed;
import app.l2nx.highfive.mechanics.siege.manor.SeedProduction;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Castle manor system.
 * @author malyelfik, Stayway
 */
@Slf4j
public class CastleManorManager implements IXmlReader {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    // SQL queries
    private static final String INSERT_PRODUCT = "INSERT INTO castle_manor_production VALUES (?, ?, ?, ?, ?, ?)";
    private static final String INSERT_CROP = "INSERT INTO castle_manor_procure VALUES (?, ?, ?, ?, ?, ?, ?)";

    // Current manor status
    private ManorMode mode = ManorMode.APPROVED;

    // Temporary date
    private Calendar nextModeChange = null;

    // Seeds holder
    private static final Map<Integer, Seed> seeds = new HashMap<>();

    // Manor period settings
    private final Map<Integer, List<CropProcure>> procure = new HashMap<>();
    private final Map<Integer, List<CropProcure>> procureNext = new HashMap<>();
    private final Map<Integer, List<SeedProduction>> production = new HashMap<>();
    private final Map<Integer, List<SeedProduction>> productionNext = new HashMap<>();

    public CastleManorManager() {
        if (GeneralConfig.ALLOW_MANOR) {
            // Load seed data from XML and castle manor data from the database.
            load();
            loadDb();

            // Set the initial manor mode based on the current time.
            final Calendar currentTime = Calendar.getInstance();
            final int hour = currentTime.get(Calendar.HOUR_OF_DAY);
            final int min = currentTime.get(Calendar.MINUTE);
            final int maintenanceMin = GeneralConfig.ALT_MANOR_REFRESH_MIN + GeneralConfig.ALT_MANOR_MAINTENANCE_MIN;
            if (((hour >= GeneralConfig.ALT_MANOR_REFRESH_TIME) && (min >= maintenanceMin))
                    || (hour < GeneralConfig.ALT_MANOR_APPROVE_TIME)
                    || ((hour == GeneralConfig.ALT_MANOR_APPROVE_TIME)
                            && (min <= GeneralConfig.ALT_MANOR_APPROVE_MIN))) {
                mode = ManorMode.MODIFIABLE;
            } else if ((hour == GeneralConfig.ALT_MANOR_REFRESH_TIME)
                    && (min >= GeneralConfig.ALT_MANOR_REFRESH_MIN)
                    && (min < maintenanceMin)) {
                mode = ManorMode.MAINTENANCE;
            } else {
                mode = ManorMode.APPROVED;
            }

            // Schedule the mode change task.
            scheduleModeChange();

            // Schedule auto-save if not saving all actions.
            if (!GeneralConfig.ALT_MANOR_SAVE_ALL_ACTIONS) {
                ThreadPool.scheduleAtFixedRate(
                        this::storeMe,
                        GeneralConfig.ALT_MANOR_SAVE_PERIOD_RATE * 60 * 60 * 1000,
                        GeneralConfig.ALT_MANOR_SAVE_PERIOD_RATE * 60 * 60 * 1000);
            }
        } else {
            mode = ManorMode.DISABLED;
            nextModeChange = null;
            log.info("Manor system is deactivated.");
        }
    }

    @Override
    public void load() {
        parseDatapackFile("data/Seeds.xml");
        log.info("Loaded {} seeds.", seeds.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        StatSet set;
        NamedNodeMap attrs;
        Node att;
        for (Node n = document.getFirstChild(); n != null; n = n.getNextSibling()) {
            if ("list".equalsIgnoreCase(n.getNodeName())) {
                for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                    if ("castle".equalsIgnoreCase(d.getNodeName())) {
                        final int castleId = parseInteger(d.getAttributes(), "id");
                        for (Node c = d.getFirstChild(); c != null; c = c.getNextSibling()) {
                            if ("crop".equalsIgnoreCase(c.getNodeName())) {
                                set = new StatSet();
                                set.set("castleId", castleId);
                                attrs = c.getAttributes();
                                for (int i = 0; i < attrs.getLength(); i++) {
                                    att = attrs.item(i);
                                    set.set(att.getNodeName(), att.getNodeValue());
                                }

                                seeds.put(set.getInt("seedId"), new Seed(set));
                            }
                        }
                    }
                }
            }
        }
    }

    private void loadDb() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement stProduction =
                        con.prepareStatement("SELECT * FROM castle_manor_production WHERE castle_id=?");
                PreparedStatement stProcure =
                        con.prepareStatement("SELECT * FROM castle_manor_procure WHERE castle_id=?")) {
            for (Castle castle : CastleManager.getInstance().getCastles()) {
                final int castleId = castle.getResidenceId();

                // Clear parameters for reuse.
                stProduction.clearParameters();
                stProcure.clearParameters();

                // Load seed production data for the castle.
                final List<SeedProduction> currentProduction = new ArrayList<>();
                final List<SeedProduction> nextProduction = new ArrayList<>();
                stProduction.setInt(1, castleId);

                try (ResultSet rs = stProduction.executeQuery()) {
                    while (rs.next()) {
                        final int seedId = rs.getInt("seed_id");
                        if (seeds.containsKey(seedId)) {
                            final SeedProduction seedProduction = new SeedProduction(
                                    seedId, rs.getLong("amount"), rs.getLong("price"), rs.getInt("start_amount"));

                            // Separate current and next period productions.
                            if (rs.getBoolean("next_period")) {
                                nextProduction.add(seedProduction);
                            } else {
                                currentProduction.add(seedProduction);
                            }
                        } else {
                            log.warn("Unknown seed ID: {}", seedId);
                        }
                    }
                }

                production.put(castleId, currentProduction);
                productionNext.put(castleId, nextProduction);

                // Load crop procure data for the castle.
                final List<CropProcure> currentProcure = new ArrayList<>();
                final List<CropProcure> nextProcure = new ArrayList<>();
                stProcure.setInt(1, castleId);

                try (ResultSet rs = stProcure.executeQuery()) {
                    final Set<Integer> knownCropIds = getCropIds();
                    while (rs.next()) {
                        final int cropId = rs.getInt("crop_id");
                        if (knownCropIds.contains(cropId)) {
                            final CropProcure cropProcure = new CropProcure(
                                    cropId,
                                    rs.getLong("amount"),
                                    rs.getInt("reward_type"),
                                    rs.getLong("start_amount"),
                                    rs.getLong("price"));

                            // Separate current and next period procurements.
                            if (rs.getBoolean("next_period")) {
                                nextProcure.add(cropProcure);
                            } else {
                                currentProcure.add(cropProcure);
                            }
                        } else {
                            log.warn("Unknown crop ID: {}", cropId);
                        }
                    }
                }

                procure.put(castleId, currentProcure);
                procureNext.put(castleId, nextProcure);
            }

            log.info("Manor data loaded.");
        } catch (Exception e) {
            log.warn("Unable to load manor data!", e);
        }
    }

    private void scheduleModeChange() {
        // Initialize next mode change time to the current date with seconds reset to zero.
        nextModeChange = Calendar.getInstance();
        nextModeChange.set(Calendar.SECOND, 0);

        // Determine mode-specific time for the next change.
        switch (mode) {
            case MODIFIABLE: {
                nextModeChange.set(Calendar.HOUR_OF_DAY, GeneralConfig.ALT_MANOR_APPROVE_TIME);
                nextModeChange.set(Calendar.MINUTE, GeneralConfig.ALT_MANOR_APPROVE_MIN);

                // If the time has already passed today, schedule for tomorrow.
                if (nextModeChange.before(Calendar.getInstance())) {
                    nextModeChange.add(Calendar.DATE, 1);
                }
                break;
            }
            case MAINTENANCE: {
                nextModeChange.set(Calendar.HOUR_OF_DAY, GeneralConfig.ALT_MANOR_REFRESH_TIME);
                nextModeChange.set(
                        Calendar.MINUTE, GeneralConfig.ALT_MANOR_REFRESH_MIN + GeneralConfig.ALT_MANOR_MAINTENANCE_MIN);

                // If the time has already passed today, schedule for tomorrow.
                if (nextModeChange.before(Calendar.getInstance())) {
                    nextModeChange.add(Calendar.DATE, 1);
                }
                break;
            }
            case APPROVED: {
                nextModeChange.set(Calendar.HOUR_OF_DAY, GeneralConfig.ALT_MANOR_REFRESH_TIME);
                nextModeChange.set(Calendar.MINUTE, GeneralConfig.ALT_MANOR_REFRESH_MIN);

                // If the time has already passed today, schedule for tomorrow.
                if (nextModeChange.before(Calendar.getInstance())) {
                    nextModeChange.add(Calendar.DATE, 1);
                }
                break;
            }
            case DISABLED: {
                // If mode is DISABLED, don't schedule.
                return;
            }
            default: {
                // For any unexpected mode, don't schedule.
                return;
            }
        }

        // Schedule mode change.
        long delay = nextModeChange.getTimeInMillis() - System.currentTimeMillis();

        // Ensure delay is not negative (minimum 1 second).
        if (delay < 1000) {
            delay = 1000; // Schedule for at least 1 second from now.
            nextModeChange.setTimeInMillis(System.currentTimeMillis() + delay);
        }

        ThreadPool.schedule(this::changeMode, delay);
    }

    public void changeMode() {
        switch (mode) {
            case APPROVED: {
                // Transition to maintenance mode.
                mode = ManorMode.MAINTENANCE;

                // Update manor period for each castle.
                for (Castle castle : CastleManager.getInstance().getCastles()) {
                    final Clan owner = castle.getOwner();
                    if (owner == null) {
                        continue;
                    }

                    final int castleId = castle.getResidenceId();
                    final ItemContainer cwh = owner.getWarehouse();

                    // Process crop procurement and treasury updates.
                    for (CropProcure crop : procure.get(castleId)) {
                        if (crop.getStartAmount() > 0) {
                            // Adding bought crops to clan warehouse.
                            if (crop.getStartAmount() != crop.getAmount()) {
                                long harvestedAmount = (long) ((crop.getStartAmount() - crop.getAmount()) * 0.9);
                                if ((harvestedAmount < 1)
                                        && (ThreadLocalRandom.current().nextInt(99) < 90)) {
                                    harvestedAmount = 1;
                                }

                                // Add harvested crops to the clan warehouse.
                                if (harvestedAmount > 0) {
                                    cwh.addItem(
                                            ItemProcessType.REWARD,
                                            getSeedByCrop(crop.getId()).getMatureId(),
                                            harvestedAmount,
                                            null,
                                            null);
                                }
                            }

                            // Add unspent amount to castle treasury.
                            if (crop.getAmount() > 0) {
                                castle.addToTreasuryNoTax(crop.getAmount() * crop.getPrice());
                            }
                        }
                    }

                    // Update current production and procurements to the next period's data.
                    production.put(castleId, productionNext.get(castleId));
                    procure.put(castleId, procureNext.get(castleId));

                    // Prepare data for the upcoming period, depending on treasury funds.
                    if (castle.getTreasury() < getManorCost(castleId, false)) {
                        productionNext.put(castleId, Collections.emptyList());
                        procureNext.put(castleId, Collections.emptyList());
                    } else {
                        // Reset production and procurement amounts for the next period.
                        final List<SeedProduction> productionList = new ArrayList<>(productionNext.get(castleId));
                        for (SeedProduction seed : productionList) {
                            seed.setAmount(seed.getStartAmount());
                        }

                        productionNext.put(castleId, productionList);

                        final List<CropProcure> procureList = new ArrayList<>(procureNext.get(castleId));
                        for (CropProcure crop : procureList) {
                            crop.setAmount(crop.getStartAmount());
                        }

                        procureNext.put(castleId, procureList);
                    }
                }

                // Save the updated manor data.
                storeMe();
                break;
            }
            case MAINTENANCE: {
                // Notify clan leaders of the manor mode change.
                for (Castle castle : CastleManager.getInstance().getCastles()) {
                    final Clan owner = castle.getOwner();
                    if (owner != null) {
                        final ClanMember clanLeader = owner.getLeader();
                        if ((clanLeader != null) && clanLeader.isOnline()) {
                            clanLeader.getPlayer().sendPacket(SystemMessageId.THE_MANOR_INFORMATION_HAS_BEEN_UPDATED);
                        }
                    }
                }

                // Transition to modifiable mode.
                mode = ManorMode.MODIFIABLE;
                break;
            }
            case MODIFIABLE: {
                // Transition to approved mode.
                mode = ManorMode.APPROVED;

                // Validate each castle's funds and warehouse capacity.
                for (Castle castle : CastleManager.getInstance().getCastles()) {
                    final Clan owner = castle.getOwner();
                    if (owner == null) {
                        continue;
                    }

                    final int castleId = castle.getResidenceId();
                    final ItemContainer cwh = owner.getWarehouse();

                    // Calculate the necessary slots in the warehouse for new crops.
                    int requiredSlots = 0;
                    for (CropProcure crop : procureNext.get(castleId)) {
                        if ((crop.getStartAmount() > 0)
                                && (cwh.getAllItemsByItemId(
                                                getSeedByCrop(crop.getId()).getMatureId())
                                        == null)) {
                            requiredSlots++;
                        }
                    }

                    final long manorCost = getManorCost(castleId, true);

                    // Check if there's enough capacity and funds.
                    if (!cwh.validateCapacity(requiredSlots) && (castle.getTreasury() < manorCost)) {
                        // Clear next period data if insufficient resources.
                        productionNext.get(castleId).clear();
                        procureNext.get(castleId).clear();

                        // Notify clan leader.
                        final ClanMember clanLeader = owner.getLeader();
                        if ((clanLeader != null) && clanLeader.isOnline()) {
                            clanLeader
                                    .getPlayer()
                                    .sendPacket(
                                            SystemMessageId
                                                    .YOU_DO_NOT_HAVE_ENOUGH_FUNDS_IN_THE_CLAN_WAREHOUSE_FOR_THE_MANOR_TO_OPERATE);
                        }
                    } else // Deduct manor cost from treasury.
                    {
                        castle.addToTreasuryNoTax(-manorCost);
                    }
                }

                // Store changes if configured to save all actions.
                if (GeneralConfig.ALT_MANOR_SAVE_ALL_ACTIONS) {
                    storeMe();
                }
                break;
            }
            case DISABLED: {
                // Do nothing if disabled.
                return;
            }
        }

        // Schedule the next mode change.
        scheduleModeChange();
    }

    public void setNextSeedProduction(List<SeedProduction> list, int castleId) {
        productionNext.put(castleId, list);

        // Save actions to the database if configured to do so.
        if (GeneralConfig.ALT_MANOR_SAVE_ALL_ACTIONS) {
            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement deleteStmt = con.prepareStatement(
                            "DELETE FROM castle_manor_production WHERE castle_id = ? AND next_period = 1");
                    PreparedStatement insertStmt = con.prepareStatement(INSERT_PRODUCT)) {
                // Delete existing production data for the next period.
                deleteStmt.setInt(1, castleId);
                deleteStmt.executeUpdate();

                // Insert new production data if list is not empty.
                for (SeedProduction sp : list) {
                    insertStmt.setInt(1, castleId);
                    insertStmt.setInt(2, sp.getId());
                    insertStmt.setLong(3, sp.getAmount());
                    insertStmt.setLong(4, sp.getStartAmount());
                    insertStmt.setLong(5, sp.getPrice());
                    insertStmt.setBoolean(6, true);
                    insertStmt.addBatch();
                }

                // Execute batch insert for new production data.
                if (!list.isEmpty()) {
                    insertStmt.executeBatch();
                }
            } catch (Exception e) {
                log.error("Unable to store manor data!", e);
            }
        }
    }

    public void setNextCropProcure(List<CropProcure> list, int castleId) {
        procureNext.put(castleId, list);

        // Save actions to the database if configured to do so.
        if (GeneralConfig.ALT_MANOR_SAVE_ALL_ACTIONS) {
            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement deleteStmt = con.prepareStatement(
                            "DELETE FROM castle_manor_procure WHERE castle_id = ? AND next_period = 1");
                    PreparedStatement insertStmt = con.prepareStatement(INSERT_CROP)) {
                // Delete existing procure data for the next period.
                deleteStmt.setInt(1, castleId);
                deleteStmt.executeUpdate();

                // Insert new procure data if list is not empty.
                for (CropProcure cp : list) {
                    insertStmt.setInt(1, castleId);
                    insertStmt.setInt(2, cp.getId());
                    insertStmt.setLong(3, cp.getAmount());
                    insertStmt.setLong(4, cp.getStartAmount());
                    insertStmt.setLong(5, cp.getPrice());
                    insertStmt.setInt(6, cp.getReward());
                    insertStmt.setBoolean(7, true);
                    insertStmt.addBatch();
                }

                // Execute batch insert for new procure data.
                if (!list.isEmpty()) {
                    insertStmt.executeBatch();
                }
            } catch (Exception e) {
                log.error("Unable to store manor data!", e);
            }
        }
    }

    public void updateCurrentProduction(int castleId, Collection<SeedProduction> items) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE castle_manor_production SET amount = ? WHERE castle_id = ? AND seed_id = ? AND next_period = 0")) {
            for (SeedProduction sp : items) {
                ps.setLong(1, sp.getAmount());
                ps.setInt(2, castleId);
                ps.setInt(3, sp.getId());
                ps.addBatch();
            }

            ps.executeBatch();
        } catch (Exception e) {
            log.info("Unable to update current production data!", e);
        }
    }

    public void updateCurrentProcure(int castleId, Collection<CropProcure> items) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE castle_manor_procure SET amount = ? WHERE castle_id = ? AND crop_id = ? AND next_period = 0")) {
            for (CropProcure cp : items) {
                ps.setLong(1, cp.getAmount());
                ps.setInt(2, castleId);
                ps.setInt(3, cp.getId());
                ps.addBatch();
            }

            ps.executeBatch();
        } catch (Exception e) {
            log.info("Unable to update current procure data!", e);
        }
    }

    public List<SeedProduction> getSeedProduction(int castleId, boolean nextPeriod) {
        return nextPeriod ? productionNext.get(castleId) : production.get(castleId);
    }

    public SeedProduction getSeedProduct(int castleId, int seedId, boolean nextPeriod) {
        for (SeedProduction sp : getSeedProduction(castleId, nextPeriod)) {
            if (sp.getId() == seedId) {
                return sp;
            }
        }

        return null;
    }

    public List<CropProcure> getCropProcure(int castleId, boolean nextPeriod) {
        return nextPeriod ? procureNext.get(castleId) : procure.get(castleId);
    }

    public CropProcure getCropProcure(int castleId, int cropId, boolean nextPeriod) {
        for (CropProcure cp : getCropProcure(castleId, nextPeriod)) {
            if (cp.getId() == cropId) {
                return cp;
            }
        }

        return null;
    }

    public long getManorCost(int castleId, boolean nextPeriod) {
        final List<CropProcure> procureList = getCropProcure(castleId, nextPeriod);
        final List<SeedProduction> productionList = getSeedProduction(castleId, nextPeriod);
        long totalCost = 0;

        for (SeedProduction seed : productionList) {
            Seed s = getSeed(seed.getId());
            totalCost += (s != null) ? (s.getSeedReferencePrice() * seed.getStartAmount()) : 1;
        }

        for (CropProcure crop : procureList) {
            totalCost += crop.getPrice() * crop.getStartAmount();
        }

        return totalCost;
    }

    public boolean storeMe() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement deleteProductionStmt = con.prepareStatement("DELETE FROM castle_manor_production");
                PreparedStatement insertProductionStmt = con.prepareStatement(INSERT_PRODUCT);
                PreparedStatement deleteProcureStmt = con.prepareStatement("DELETE FROM castle_manor_procure");
                PreparedStatement insertProcureStmt = con.prepareStatement(INSERT_CROP)) {
            // Delete old production data.
            deleteProductionStmt.executeUpdate();

            // Insert current production data.
            for (Entry<Integer, List<SeedProduction>> entry : production.entrySet()) {
                for (SeedProduction sp : entry.getValue()) {
                    insertProductionStmt.setInt(1, entry.getKey());
                    insertProductionStmt.setInt(2, sp.getId());
                    insertProductionStmt.setLong(3, sp.getAmount());
                    insertProductionStmt.setLong(4, sp.getStartAmount());
                    insertProductionStmt.setLong(5, sp.getPrice());
                    insertProductionStmt.setBoolean(6, false);
                    insertProductionStmt.addBatch();
                }
            }

            // Insert next period production data.
            for (Entry<Integer, List<SeedProduction>> entry : productionNext.entrySet()) {
                for (SeedProduction sp : entry.getValue()) {
                    insertProductionStmt.setInt(1, entry.getKey());
                    insertProductionStmt.setInt(2, sp.getId());
                    insertProductionStmt.setLong(3, sp.getAmount());
                    insertProductionStmt.setLong(4, sp.getStartAmount());
                    insertProductionStmt.setLong(5, sp.getPrice());
                    insertProductionStmt.setBoolean(6, true);
                    insertProductionStmt.addBatch();
                }
            }

            // Execute batch insert for production data.
            insertProductionStmt.executeBatch();

            // Delete old procure data.
            deleteProcureStmt.executeUpdate();

            // Insert current procure data.
            for (Entry<Integer, List<CropProcure>> entry : procure.entrySet()) {
                for (CropProcure cp : entry.getValue()) {
                    insertProcureStmt.setInt(1, entry.getKey());
                    insertProcureStmt.setInt(2, cp.getId());
                    insertProcureStmt.setLong(3, cp.getAmount());
                    insertProcureStmt.setLong(4, cp.getStartAmount());
                    insertProcureStmt.setLong(5, cp.getPrice());
                    insertProcureStmt.setInt(6, cp.getReward());
                    insertProcureStmt.setBoolean(7, false);
                    insertProcureStmt.addBatch();
                }
            }

            // Insert next period procure data.
            for (Entry<Integer, List<CropProcure>> entry : procureNext.entrySet()) {
                for (CropProcure cp : entry.getValue()) {
                    insertProcureStmt.setInt(1, entry.getKey());
                    insertProcureStmt.setInt(2, cp.getId());
                    insertProcureStmt.setLong(3, cp.getAmount());
                    insertProcureStmt.setLong(4, cp.getStartAmount());
                    insertProcureStmt.setLong(5, cp.getPrice());
                    insertProcureStmt.setInt(6, cp.getReward());
                    insertProcureStmt.setBoolean(7, true);
                    insertProcureStmt.addBatch();
                }
            }

            // Execute batch insert for procure data.
            insertProcureStmt.executeBatch();

            return true;
        } catch (Exception e) {
            log.error("Unable to store manor data!", e);
            return false;
        }
    }

    public void resetManorData(int castleId) {
        if (!GeneralConfig.ALLOW_MANOR) {
            return;
        }

        // Clear existing production and procurement data for the specified castle.
        procure.get(castleId).clear();
        procureNext.get(castleId).clear();
        production.get(castleId).clear();
        productionNext.get(castleId).clear();

        // Save changes to the database if configured to do so.
        if (GeneralConfig.ALT_MANOR_SAVE_ALL_ACTIONS) {
            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement deleteProductionStmt =
                            con.prepareStatement("DELETE FROM castle_manor_production WHERE castle_id = ?");
                    PreparedStatement deleteProcureStmt =
                            con.prepareStatement("DELETE FROM castle_manor_procure WHERE castle_id = ?")) {
                // Delete seed production data.
                deleteProductionStmt.setInt(1, castleId);
                deleteProductionStmt.executeUpdate();

                // Delete procurement data.
                deleteProcureStmt.setInt(1, castleId);
                deleteProcureStmt.executeUpdate();
            } catch (Exception e) {
                log.error("Unable to store manor data!", e);
            }
        }
    }

    public boolean isUnderMaintenance() {
        return mode == ManorMode.MAINTENANCE;
    }

    public boolean isManorApproved() {
        return mode == ManorMode.APPROVED;
    }

    public boolean isModifiablePeriod() {
        return mode == ManorMode.MODIFIABLE;
    }

    public String getCurrentModeName() {
        return mode.toString();
    }

    public String getNextModeChange() {
        if (nextModeChange == null) {
            return "Disabled";
        }

        return DATE_FORMAT.format(
                Instant.ofEpochMilli(nextModeChange.getTimeInMillis()).atZone(ZoneId.systemDefault()));
    }

    public List<Seed> getCrops() {
        final List<Seed> seeds = new ArrayList<>();
        final List<Integer> cropIds = new ArrayList<>();
        for (Seed seed : CastleManorManager.seeds.values()) {
            if (!cropIds.contains(seed.getCropId())) {
                seeds.add(seed);
                cropIds.add(seed.getCropId());
            }
        }

        cropIds.clear();
        return seeds;
    }

    public Set<Seed> getSeedsForCastle(int castleId) {
        Set<Seed> result = new HashSet<>();
        for (Seed seed : seeds.values()) {
            if (seed.getCastleId() == castleId) {
                result.add(seed);
            }
        }

        return result;
    }

    public Set<Integer> getSeedIds() {
        return seeds.keySet();
    }

    public Set<Integer> getCropIds() {
        final Set<Integer> result = new HashSet<>();
        for (Seed seed : seeds.values()) {
            result.add(seed.getCropId());
        }

        return result;
    }

    public Seed getSeed(int seedId) {
        return seeds.get(seedId);
    }

    public Seed getSeedByCrop(int cropId, int castleId) {
        for (Seed seed : getSeedsForCastle(castleId)) {
            if (seed.getCropId() == cropId) {
                return seed;
            }
        }

        return null;
    }

    public Seed getSeedByCrop(int cropId) {
        for (Seed seed : seeds.values()) {
            if (seed.getCropId() == cropId) {
                return seed;
            }
        }

        return null;
    }

    public static CastleManorManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final CastleManorManager INSTANCE = new CastleManorManager();
    }
}
