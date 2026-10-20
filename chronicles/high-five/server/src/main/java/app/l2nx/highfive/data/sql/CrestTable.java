/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
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
package app.l2nx.highfive.data.sql;

import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.Crest;
import app.l2nx.highfive.entity.clan.enums.CrestType;
import app.l2nx.highfive.repository.crest.CrestRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Loads and saves crests from database.
 * @author NosBit
 */
@Slf4j
@Component
public class CrestTable {
    private static volatile CrestTable instance;

    private final Map<Integer, Crest> crests = new ConcurrentHashMap<>();

    private final CrestRepository crestRepository;

    public CrestTable(CrestRepository crestRepository) {
        this.crestRepository = crestRepository;
        instance = this;
    }

    public synchronized void load() {
        crests.clear();
        final Set<Integer> crestsInUse = new HashSet<>();
        for (Clan clan : ClanTable.getInstance().getClans()) {
            if (clan.getCrestId() != 0) {
                crestsInUse.add(clan.getCrestId());
            }

            if (clan.getCrestLargeId() != 0) {
                crestsInUse.add(clan.getCrestLargeId());
            }

            if (clan.getAllyCrestId() != 0) {
                crestsInUse.add(clan.getAllyCrestId());
            }
        }

        for (Crest crest : crestRepository.findAll()) {
            if (crestsInUse.contains(crest.getId())) {
                crests.put(crest.getId(), crest);
            } else {
                removeCrest(crest.getId());
            }
        }

        log.info("Loaded {} Crests.", crests.size());

        for (Clan clan : ClanTable.getInstance().getClans()) {
            if ((clan.getCrestId() != 0) && (getCrest(clan.getCrestId()) == null)) {
                log.info(
                        "Removing non-existent crest for clan {} [{}], crestId:{}",
                        clan.getName(),
                        clan.getId(),
                        clan.getCrestId());
                clan.setCrestId(0);
                clan.changeClanCrest(0);
            }

            if ((clan.getCrestLargeId() != 0) && (getCrest(clan.getCrestLargeId()) == null)) {
                log.info(
                        "Removing non-existent large crest for clan {} [{}], crestLargeId:{}",
                        clan.getName(),
                        clan.getId(),
                        clan.getCrestLargeId());
                clan.setCrestLargeId(0);
                clan.changeLargeCrest(0);
            }

            if ((clan.getAllyCrestId() != 0) && (getCrest(clan.getAllyCrestId()) == null)) {
                log.info(
                        "Removing non-existent ally crest for clan {} [{}], allyCrestId:{}",
                        clan.getName(),
                        clan.getId(),
                        clan.getAllyCrestId());
                clan.setAllyCrestId(0);
                clan.changeAllyCrest(0, true);
            }
        }
    }

    /**
     * @param crestId The crest id
     * @return {@code Crest} if crest is found, {@code null} if crest was not found.
     */
    public Crest getCrest(int crestId) {
        return crests.get(crestId);
    }

    /**
     * Creates a {@code Crest} object and inserts it in database and cache.
     * @param data
     * @param crestType
     * @return {@code Crest} on success, {@code null} on failure.
     */
    public Crest createCrest(byte[] data, CrestType crestType) {
        try {
            final Crest crest = crestRepository.create(data, crestType);
            crests.put(crest.getId(), crest);
            return crest;
        } catch (DataAccessException e) {
            log.warn("There was an error while saving crest in database:", e);
        }

        return null;
    }

    /**
     * Removes crest from database and cache.
     * @param crestId the id of crest to be removed.
     */
    public void removeCrest(int crestId) {
        crests.remove(crestId);
        try {
            crestRepository.delete(crestId);
        } catch (DataAccessException e) {
            log.warn("There was an error while deleting crest from database:", e);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CrestTable getInstance() {
        final CrestTable current = instance;
        if (current == null) {
            throw new IllegalStateException("CrestTable is used before the Spring context created it.");
        }
        return current;
    }
}
