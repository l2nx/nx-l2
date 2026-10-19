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
package app.l2nx.highfive.mechanics.effects;

import app.l2nx.highfive.mechanics.skill.BuffInfo;
import lombok.Getter;

/**
 * Effect tick task.
 * @author Zoey76
 */
public class EffectTickTask implements Runnable {
    @Getter
    private final BuffInfo buffInfo;

    @Getter
    private final AbstractEffect effect;

    /**
     * EffectTickTask constructor.
     * @param info the buff info
     * @param effect the effect
     */
    public EffectTickTask(BuffInfo info, AbstractEffect effect) {
        buffInfo = info;
        this.effect = effect;
    }

    @Override
    public void run() {
        buffInfo.onTick(effect);
    }
}
