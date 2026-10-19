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
package app.l2nx.highfive.mechanics.siege;

import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Class managing periodical events with castle
 * @author Vice
 */
@Slf4j
public class FortUpdater implements Runnable {

    private final Clan clan;
    private final Fort fort;

    @Getter
    private int runCount;

    private final FortUpdaterType updaterType;

    public FortUpdater(Fort fort, Clan clan, int runCount, FortUpdaterType ut) {
        this.fort = fort;
        this.clan = clan;
        this.runCount = runCount;
        updaterType = ut;
    }

    @Override
    public void run() {
        try {
            switch (updaterType) {
                case PERIODIC_UPDATE: {
                    runCount++;
                    if ((fort.getOwnerClan() == null) || (fort.getOwnerClan() != clan)) {
                        return;
                    }

                    fort.getOwnerClan().increaseBloodOathCount();

                    if (fort.getFortState() == 2) {
                        if (clan.getWarehouse().getAdena() >= FeatureConfig.FS_FEE_FOR_CASTLE) {
                            clan.getWarehouse()
                                    .destroyItemByItemId(
                                            ItemProcessType.FEE,
                                            Inventory.ADENA_ID,
                                            FeatureConfig.FS_FEE_FOR_CASTLE,
                                            null,
                                            null);
                            fort.getContractedCastle().addToTreasuryNoTax(FeatureConfig.FS_FEE_FOR_CASTLE);
                            fort.raiseSupplyLeveL();
                        } else {
                            fort.setFortState(1, 0);
                        }
                    }

                    fort.saveFortVariables();
                    break;
                }
                case MAX_OWN_TIME: {
                    if ((fort.getOwnerClan() == null) || (fort.getOwnerClan() != clan)) {
                        return;
                    }

                    if (fort.getOwnedTime() > (FeatureConfig.FS_MAX_OWN_TIME * 3600)) {
                        fort.removeOwner(true);
                        fort.setFortState(0, 0);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("run: ", e);
        }
    }
}
