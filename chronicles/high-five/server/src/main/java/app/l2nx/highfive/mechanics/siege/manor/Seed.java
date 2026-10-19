/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.mechanics.siege.manor;

import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;

public class Seed {
    @Getter
    private final int seedId;

    @Getter
    private final int cropId; // crop type

    @Getter
    private final int level; // seed level

    @Getter
    private final int matureId; // mature crop type

    private final int reward1;
    private final int reward2;

    @Getter
    private final int castleId; // id of manor (castle id) where seed can be farmed

    @Getter
    private final boolean alternative;

    private final int limitSeeds;
    private final int limitCrops;

    @Getter
    private final int seedReferencePrice;

    @Getter
    private final int cropReferencePrice;

    public Seed(StatSet set) {
        cropId = set.getInt("id");
        seedId = set.getInt("seedId");
        level = set.getInt("level");
        matureId = set.getInt("mature_Id");
        reward1 = set.getInt("reward1");
        reward2 = set.getInt("reward2");
        castleId = set.getInt("castleId");
        alternative = set.getBoolean("alternative");
        limitCrops = set.getInt("limit_crops");
        limitSeeds = set.getInt("limit_seed");

        // Set prices
        ItemTemplate item = ItemData.getInstance().getTemplate(cropId);
        cropReferencePrice = (item != null) ? item.getReferencePrice() : 1;
        item = ItemData.getInstance().getTemplate(seedId);
        seedReferencePrice = (item != null) ? item.getReferencePrice() : 1;
    }

    public int getReward(int type) {
        return (type == 1) ? reward1 : reward2;
    }

    public int getSeedLimit() {
        return limitSeeds * RatesConfig.RATE_DROP_MANOR;
    }

    public int getCropLimit() {
        return limitCrops * RatesConfig.RATE_DROP_MANOR;
    }

    public int getSeedMaxPrice() {
        return seedReferencePrice * 10;
    }

    public int getSeedMinPrice() {
        return (int) (seedReferencePrice * 0.6);
    }

    public int getCropMaxPrice() {
        return cropReferencePrice * 10;
    }

    public int getCropMinPrice() {
        return (int) (cropReferencePrice * 0.6);
    }

    @Override
    public String toString() {
        return "SeedData [_id=" + seedId + ", _level=" + level + ", _crop=" + cropId + ", _mature=" + matureId
                + ", _type1=" + reward1 + ", _type2=" + reward2 + ", _manorId=" + castleId + ", _isAlternative="
                + alternative + ", _limitSeeds=" + limitSeeds + ", _limitCrops=" + limitCrops + "]";
    }
}
