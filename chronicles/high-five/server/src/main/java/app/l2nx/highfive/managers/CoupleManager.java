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
package app.l2nx.highfive.managers;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.Couple;
import app.l2nx.highfive.repository.world.CoupleRepository;
import app.l2nx.highfive.repository.world.model.CoupleRow;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * @author evill33t
 */
@Slf4j
@Component
public class CoupleManager {
    private static volatile CoupleManager instance;

    @Getter
    private final List<Couple> couples = new CopyOnWriteArrayList<>();

    private final CoupleRepository coupleRepository;

    public CoupleManager(CoupleRepository coupleRepository) {
        this.coupleRepository = coupleRepository;
        instance = this;
    }

    public void reload() {
        couples.clear();
        load();
    }

    public void load() {
        try {
            for (CoupleRow row : coupleRepository.findAll()) {
                couples.add(new Couple(row));
            }

            log.info("Loaded {} couples(s)", couples.size());
        } catch (DataAccessException e) {
            log.error("Exception: CoupleManager.load(): {}", e.getMessage(), e);
        }
    }

    /**
     * @param engagedAt epoch milliseconds
     */
    public long insert(int player1Id, int player2Id, long engagedAt) {
        return coupleRepository.insert(player1Id, player2Id, instantOrNull(engagedAt));
    }

    /**
     * @param marriedAt epoch milliseconds
     */
    public void updateMarried(long coupleId, long marriedAt) {
        coupleRepository.updateMarried(coupleId, instantOrNull(marriedAt));
    }

    public void delete(long coupleId) {
        coupleRepository.delete(coupleId);
    }

    public Couple getCouple(long coupleId) {
        final int index = getCoupleIndex(coupleId);
        return index >= 0 ? couples.get(index) : null;
    }

    public void createCouple(Player player1, Player player2) {
        if ((player1 == null) || (player2 == null) || (player1.getPartnerId() != 0) || (player2.getPartnerId() != 0)) {
            return;
        }

        final int player1id = player1.getObjectId();
        final int player2id = player2.getObjectId();
        final Couple couple;
        try {
            couple = new Couple(player1, player2);
        } catch (DataAccessException e) {
            log.error("Could not create couple: {}", e.getMessage(), e);
            return;
        }

        couples.add(couple);
        player1.setPartnerId(player2id);
        player2.setPartnerId(player1id);
        player1.setCoupleId(couple.getId());
        player2.setCoupleId(couple.getId());
    }

    public void deleteCouple(long coupleId) {
        final int index = getCoupleIndex(coupleId);
        if (index < 0) {
            return;
        }

        final Couple couple = couples.get(index);
        final Player player1 = World.getPlayer(couple.getPlayer1Id());
        final Player player2 = World.getPlayer(couple.getPlayer2Id());
        if (player1 != null) {
            player1.setPartnerId(0);
            player1.setMarried(false);
            player1.setCoupleId(0);
        }

        if (player2 != null) {
            player2.setPartnerId(0);
            player2.setMarried(false);
            player2.setCoupleId(0);
        }

        couple.divorce();
        couples.remove(index);
    }

    public int getCoupleIndex(long coupleId) {
        int i = 0;
        for (Couple temp : couples) {
            if ((temp != null) && (temp.getId() == coupleId)) {
                return i;
            }

            i++;
        }

        return -1;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CoupleManager getInstance() {
        final CoupleManager current = instance;
        if (current == null) {
            throw new IllegalStateException("CoupleManager is used before the Spring context created it.");
        }
        return current;
    }
}
