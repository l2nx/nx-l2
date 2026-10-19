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
package app.l2nx.highfive.mechanics.options;

import app.l2nx.highfive.data.xml.OptionData;
import app.l2nx.highfive.entity.actor.Player;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Used to store an augmentation and its bonuses.
 * @author durgus
 */
public class Augmentation {
    @Getter
    private int augmentationId = 0;

    private AugmentationStatBonus bonus = null;

    public Augmentation(int effects) {
        augmentationId = effects;
        bonus = new AugmentationStatBonus(augmentationId);
    }

    @Slf4j
    public static class AugmentationStatBonus {
        private final List<Options> options = new ArrayList<>();
        private boolean active;

        public AugmentationStatBonus(int augmentationId) {
            active = false;
            final int[] stats = new int[2];
            stats[0] = 0x0000FFFF & augmentationId;
            stats[1] = (augmentationId >> 16);
            for (int stat : stats) {
                final Options op = OptionData.getInstance().getOptions(stat);
                if (op != null) {
                    options.add(op);
                } else {
                    log.warn("Couldn't find option: {}", stat);
                }
            }
        }

        public void applyBonus(Player player) {
            // make sure the bonuses are not applied twice..
            if (active) {
                return;
            }

            for (Options op : options) {
                op.apply(player);
            }

            active = true;
        }

        public void removeBonus(Player player) {
            // make sure the bonuses are not removed twice
            if (!active) {
                return;
            }

            for (Options op : options) {
                op.remove(player);
            }

            active = false;
        }
    }

    /**
     * Applies the bonuses to the player.
     * @param player
     */
    public void applyBonus(Player player) {
        bonus.applyBonus(player);
    }

    /**
     * Removes the augmentation bonuses from the player.
     * @param player
     */
    public void removeBonus(Player player) {
        bonus.removeBonus(player);
    }
}
