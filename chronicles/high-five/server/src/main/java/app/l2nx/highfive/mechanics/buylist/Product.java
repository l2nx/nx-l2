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
package app.l2nx.highfive.mechanics.buylist;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.xml.BuyListData;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.repository.item.model.BuylistStock;
import app.l2nx.highfive.taskmanagers.BuyListTaskManager;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author NosBit
 */
@Slf4j
public class Product {

    @Getter
    private final int buyListId;

    @Getter
    private final ItemTemplate item;

    private final long price;

    @Getter
    private final long restockDelay;

    @Getter
    private final long maxCount;

    private AtomicLong count = null;

    public Product(int buyListId, ItemTemplate item, long price, long restockDelay, long maxCount) {
        this.buyListId = buyListId;
        this.item = item;
        this.price = price;
        this.restockDelay = restockDelay * 60000;
        this.maxCount = maxCount;
        if (hasLimitedStock()) {
            count = new AtomicLong(maxCount);
        }
    }

    public int getItemId() {
        return item.getId();
    }

    public long getPrice() {
        return price < 0 ? item.getReferencePrice() : price;
    }

    public long getCount() {
        if (this.count == null) {
            return 0;
        }

        final long count = this.count.get();
        return count > 0 ? count : 0;
    }

    public void setCount(long currentCount) {
        if (count == null) {
            count = new AtomicLong();
        }

        count.set(currentCount);
    }

    public boolean decreaseCount(long value) {
        if (count == null) {
            return false;
        }

        BuyListTaskManager.getInstance().add(this, ClockBridge.wallMillis() + restockDelay);

        final boolean result = count.addAndGet(-value) >= 0;
        save();
        return result;
    }

    public boolean hasLimitedStock() {
        return maxCount > -1;
    }

    public void restartRestockTask(long nextRestockTime) {
        if (nextRestockTime > ClockBridge.wallMillis()) {
            BuyListTaskManager.getInstance().update(this, nextRestockTime);
        } else {
            restock();
        }
    }

    public void restock() {
        setCount(maxCount);
        save();
    }

    private void save() {
        try {
            BuyListData.getInstance()
                    .saveStock(BuylistStock.builder()
                            .buyListId(buyListId)
                            .itemId(item.getId())
                            .count(getCount())
                            .nextRestockTime(instantOrNull(
                                    BuyListTaskManager.getInstance().getRestockDelay(this)))
                            .build());
        } catch (DataAccessException e) {
            log.warn("Failed to save Product buylist_id:{} item_id:{}", buyListId, item.getId(), e);
        }
    }
}
