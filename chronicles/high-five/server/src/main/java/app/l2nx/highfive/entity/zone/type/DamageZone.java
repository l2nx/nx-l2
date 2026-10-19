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
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.concurrent.Future;

/**
 * A damage zone
 * @author durgus
 */
public class DamageZone extends ZoneType {
    private int hpDamagePerSecond;
    private int mpDamagePerSecond;

    private int castleId;
    private Castle castle;

    private int startTask;
    private int reuseTask;
    protected volatile Future<?> task;

    public DamageZone(int id) {
        super(id);

        // Setup default damage
        hpDamagePerSecond = 200;
        mpDamagePerSecond = 0;

        // Setup default start / reuse time.
        startTask = 10;
        reuseTask = 5000;

        // no castle by default
        castleId = 0;
        castle = null;

        setTargetType(InstanceType.Playable); // default only playabale
    }

    @Override
    public void setParameter(String name, String value) {
        if (name.equals("dmgHPSec")) {
            hpDamagePerSecond = Integer.parseInt(value);
        } else if (name.equals("dmgMPSec")) {
            mpDamagePerSecond = Integer.parseInt(value);
        } else if (name.equals("castleId")) {
            castleId = Integer.parseInt(value);
        } else if (name.equalsIgnoreCase("initialDelay")) {
            startTask = Integer.parseInt(value);
        } else if (name.equalsIgnoreCase("reuse")) {
            reuseTask = Integer.parseInt(value);
        } else {
            super.setParameter(name, value);
        }
    }

    @Override
    protected void onEnter(Creature creature) {
        Future<?> task = this.task;
        if ((task == null) && ((hpDamagePerSecond != 0) || (mpDamagePerSecond != 0))) {
            final Player player = creature.asPlayer();
            final Castle castle = getCastle();
            if (castle != null) // Castle zone
            {
                if (!(castle.getSiege().isInProgress()
                        && (player != null)
                        && (player.getSiegeState() != 2))) // Siege and no defender
                {
                    return;
                }
            }

            synchronized (this) {
                task = this.task;
                if (task == null) {
                    this.task = task = ThreadPool.scheduleAtFixedRate(new ApplyDamage(), startTask, reuseTask);
                }
            }
        }
    }

    @Override
    protected void onExit(Creature creature) {
        if (getCharactersInside().isEmpty() && (task != null)) {
            task.cancel(true);
            task = null;
        }
    }

    protected int getHPDamagePerSecond() {
        return hpDamagePerSecond;
    }

    protected int getMPDamagePerSecond() {
        return mpDamagePerSecond;
    }

    protected Castle getCastle() {
        if ((castleId > 0) && (castle == null)) {
            castle = CastleManager.getInstance().getCastleById(castleId);
        }

        return castle;
    }

    private class ApplyDamage implements Runnable {
        private final Castle castle;

        protected ApplyDamage() {
            castle = getCastle();
        }

        @Override
        public void run() {
            if (getCharactersInside().isEmpty()) {
                if (task != null) {
                    task.cancel(false);
                    task = null;
                }
                return;
            }

            if (!isEnabled()) {
                return;
            }

            boolean siege = false;
            if (castle != null) {
                siege = castle.getSiege().isInProgress();

                // Castle zones active only during siege.
                if (!siege) {
                    task.cancel(false);
                    task = null;
                    return;
                }
            }

            for (Creature character : getCharactersInside()) {
                if ((character != null) && character.isPlayer() && !character.isDead()) {
                    if (siege) {
                        // During siege defenders not affected.
                        final Player player = character.asPlayer();
                        if ((player != null) && player.isInSiege() && (player.getSiegeState() == 2)) {
                            continue;
                        }
                    }

                    final double multiplier = 1 + (character.calcStat(Stat.DAMAGE_ZONE_VULN, 0, null, null) / 100);
                    if (getHPDamagePerSecond() != 0) {
                        character.reduceCurrentHp(getHPDamagePerSecond() * multiplier, character, null);
                    }

                    if (getMPDamagePerSecond() != 0) {
                        character.reduceCurrentMp(getMPDamagePerSecond() * multiplier);
                    }
                }
            }
        }
    }
}
