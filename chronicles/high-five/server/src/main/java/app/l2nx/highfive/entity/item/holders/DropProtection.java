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
package app.l2nx.highfive.entity.item.holders;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author DrHouse
 */
@SingleWriter
public class DropProtection implements Runnable {
    private boolean protectedDrop = false;

    private Creature owner = null;

    private ScheduledFuture<?> task = null;

    private static final long PROTECTED_MILLIS_TIME = 15000;

    @Override
    public void run() {
        WorldPartition.checkOwner();
        protectedDrop = false;
        owner = null;
        task = null;
    }

    public Creature getOwner() {
        WorldPartition.checkOwner();
        return owner;
    }

    public boolean isProtected() {
        WorldPartition.checkOwner();
        return protectedDrop;
    }

    public boolean tryPickUp(Player actor) {
        WorldPartition.checkOwner();
        return !protectedDrop
                || (owner == actor)
                || ((owner.getParty() != null) && (owner.getParty() == actor.getParty()));
    }

    public boolean tryPickUp(Pet pet) {
        WorldPartition.checkOwner();
        return tryPickUp(pet.getOwner());
    }

    public void unprotect() {
        WorldPartition.checkOwner();
        if (task != null) {
            task.cancel(false);
        }

        protectedDrop = false;
        owner = null;
        task = null;
    }

    public void protect(Creature creature) {
        WorldPartition.checkOwner();
        unprotect();

        protectedDrop = true;
        owner = creature;
        if (owner == null) {
            throw new NullPointerException("Trying to protect dropped item to null owner");
        }

        task = WorldPartition.loop().schedule(this, PROTECTED_MILLIS_TIME, TimeUnit.MILLISECONDS);
    }
}
