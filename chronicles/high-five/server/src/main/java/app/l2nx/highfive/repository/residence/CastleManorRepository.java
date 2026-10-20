/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import app.l2nx.highfive.mechanics.siege.manor.CropProcure;
import app.l2nx.highfive.mechanics.siege.manor.SeedProduction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class CastleManorRepository {
    private static final String SELECT_PRODUCTIONS = """
        SELECT seed_item_template_id, amount, start_amount, price
        FROM castle_manor_productions
        WHERE castle_id = ? AND next_period = ?
        ORDER BY seed_item_template_id\
        """;
    private static final String SELECT_PROCUREMENTS = """
        SELECT crop_item_template_id, amount, start_amount, price, reward_type
        FROM castle_manor_procurements
        WHERE castle_id = ? AND next_period = ?
        ORDER BY crop_item_template_id\
        """;
    private static final String INSERT_PRODUCTION = """
        INSERT INTO castle_manor_productions
            (castle_id, seed_item_template_id, next_period, amount, start_amount, price)
        VALUES (?, ?, ?, ?, ?, ?)\
        """;
    private static final String INSERT_PROCUREMENT = """
        INSERT INTO castle_manor_procurements
            (castle_id, crop_item_template_id, next_period, amount, start_amount, price, reward_type)
        VALUES (?, ?, ?, ?, ?, ?, ?)\
        """;
    private static final String UPDATE_CURRENT_PRODUCTION_AMOUNT = """
        UPDATE castle_manor_productions SET amount = ?
        WHERE castle_id = ? AND seed_item_template_id = ? AND NOT next_period\
        """;
    private static final String UPDATE_CURRENT_PROCUREMENT_AMOUNT = """
        UPDATE castle_manor_procurements SET amount = ?
        WHERE castle_id = ? AND crop_item_template_id = ? AND NOT next_period\
        """;
    private static final String DELETE_NEXT_PRODUCTIONS =
            "DELETE FROM castle_manor_productions WHERE castle_id = ? AND next_period";
    private static final String DELETE_NEXT_PROCUREMENTS =
            "DELETE FROM castle_manor_procurements WHERE castle_id = ? AND next_period";
    private static final String DELETE_PRODUCTIONS_BY_CASTLE =
            "DELETE FROM castle_manor_productions WHERE castle_id = ?";
    private static final String DELETE_PROCUREMENTS_BY_CASTLE =
            "DELETE FROM castle_manor_procurements WHERE castle_id = ?";
    private static final String DELETE_ALL_PRODUCTIONS = "DELETE FROM castle_manor_productions";
    private static final String DELETE_ALL_PROCUREMENTS = "DELETE FROM castle_manor_procurements";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public List<SeedProduction> findProductions(int castleId, boolean nextPeriod) {
        return jdbcClient
                .sql(SELECT_PRODUCTIONS)
                .param(castleId)
                .param(nextPeriod)
                .query((rs, rowNum) -> new SeedProduction(
                        rs.getInt("seed_item_template_id"),
                        rs.getLong("amount"),
                        rs.getLong("price"),
                        rs.getLong("start_amount")))
                .list();
    }

    public List<CropProcure> findProcurements(int castleId, boolean nextPeriod) {
        return jdbcClient
                .sql(SELECT_PROCUREMENTS)
                .param(castleId)
                .param(nextPeriod)
                .query((rs, rowNum) -> new CropProcure(
                        rs.getInt("crop_item_template_id"),
                        rs.getLong("amount"),
                        rs.getInt("reward_type"),
                        rs.getLong("start_amount"),
                        rs.getLong("price")))
                .list();
    }

    @Transactional
    public void replaceNextProductions(int castleId, Collection<SeedProduction> productions) {
        jdbcClient.sql(DELETE_NEXT_PRODUCTIONS).param(castleId).update();
        insertProductions(castleId, true, productions);
    }

    @Transactional
    public void replaceNextProcurements(int castleId, Collection<CropProcure> procurements) {
        jdbcClient.sql(DELETE_NEXT_PROCUREMENTS).param(castleId).update();
        insertProcurements(castleId, true, procurements);
    }

    public void updateCurrentProductionAmounts(int castleId, Collection<SeedProduction> productions) {
        final List<Object[]> rows = new ArrayList<>(productions.size());
        for (SeedProduction production : productions) {
            rows.add(new Object[] {production.getAmount(), castleId, production.getId()});
        }

        jdbcTemplate.batchUpdate(UPDATE_CURRENT_PRODUCTION_AMOUNT, rows);
    }

    public void updateCurrentProcurementAmounts(int castleId, Collection<CropProcure> procurements) {
        final List<Object[]> rows = new ArrayList<>(procurements.size());
        for (CropProcure procurement : procurements) {
            rows.add(new Object[] {procurement.getAmount(), castleId, procurement.getId()});
        }

        jdbcTemplate.batchUpdate(UPDATE_CURRENT_PROCUREMENT_AMOUNT, rows);
    }

    @Transactional
    public void replaceAll(
            Map<Integer, List<SeedProduction>> production,
            Map<Integer, List<SeedProduction>> productionNext,
            Map<Integer, List<CropProcure>> procure,
            Map<Integer, List<CropProcure>> procureNext) {
        jdbcClient.sql(DELETE_ALL_PRODUCTIONS).update();
        jdbcClient.sql(DELETE_ALL_PROCUREMENTS).update();
        production.forEach((castleId, list) -> insertProductions(castleId, false, list));
        productionNext.forEach((castleId, list) -> insertProductions(castleId, true, list));
        procure.forEach((castleId, list) -> insertProcurements(castleId, false, list));
        procureNext.forEach((castleId, list) -> insertProcurements(castleId, true, list));
    }

    @Transactional
    public void deleteByCastle(int castleId) {
        jdbcClient.sql(DELETE_PRODUCTIONS_BY_CASTLE).param(castleId).update();
        jdbcClient.sql(DELETE_PROCUREMENTS_BY_CASTLE).param(castleId).update();
    }

    private void insertProductions(int castleId, boolean nextPeriod, Collection<SeedProduction> productions) {
        final List<Object[]> rows = new ArrayList<>(productions.size());
        for (SeedProduction production : productions) {
            rows.add(new Object[] {
                castleId,
                production.getId(),
                nextPeriod,
                production.getAmount(),
                production.getStartAmount(),
                production.getPrice()
            });
        }

        jdbcTemplate.batchUpdate(INSERT_PRODUCTION, rows);
    }

    private void insertProcurements(int castleId, boolean nextPeriod, Collection<CropProcure> procurements) {
        final List<Object[]> rows = new ArrayList<>(procurements.size());
        for (CropProcure procurement : procurements) {
            rows.add(new Object[] {
                castleId,
                procurement.getId(),
                nextPeriod,
                procurement.getAmount(),
                procurement.getStartAmount(),
                procurement.getPrice(),
                procurement.getReward()
            });
        }

        jdbcTemplate.batchUpdate(INSERT_PROCUREMENT, rows);
    }
}
