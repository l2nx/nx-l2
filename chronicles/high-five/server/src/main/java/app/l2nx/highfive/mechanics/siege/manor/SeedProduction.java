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
package app.l2nx.highfive.mechanics.siege.manor;

import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;

/**
 * @author xban1x
 */
public class SeedProduction {
    @Getter
    private final int id;

    @Getter
    private final long price;

    @Getter
    private final long startAmount;

    private final AtomicLong amount;

    public SeedProduction(int id, long amount, long price, long startAmount) {
        this.id = id;
        this.amount = new AtomicLong(amount);
        this.price = price;
        this.startAmount = startAmount;
    }

    public long getAmount() {
        return amount.get();
    }

    public void setAmount(long amount) {
        this.amount.set(amount);
    }

    public boolean decreaseAmount(long value) {
        long current;
        long next;
        do {
            current = amount.get();
            next = current - value;
            if (next < 0) {
                return false;
            }
        } while (!amount.compareAndSet(current, next));
        return true;
    }
}
