/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.highfive.mechanics.variables;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author UnAfraid, Mobius
 */
@Slf4j
@SingleWriter
public class ItemVariables extends AbstractVariables {

    public static final String TRANSMOG_ID = "transmogId";

    private final int objectId;

    public ItemVariables(int objectId) {
        this.objectId = objectId;
        restoreMe();
    }

    public ItemVariables(int objectId, Map<String, String> stored) {
        WorldPartition.checkOwner();
        this.objectId = objectId;
        stored.forEach((name, value) -> set(name, value, false));
        markSaved();
    }

    public static boolean hasVariables(int objectId) {
        WorldPartition.checkOwner();
        try {
            return ItemManager.getInstance().hasVariables(objectId);
        } catch (DataAccessException e) {
            log.warn("Could not select variables count for: {}", objectId, e);
            return false;
        }
    }

    public boolean restoreMe() {
        WorldPartition.checkOwner();
        try {
            ItemManager.getInstance().findVariables(objectId).forEach((name, value) -> set(name, value, false));
        } catch (DataAccessException e) {
            log.warn("Could not restore variables for: {}", objectId, e);
            return false;
        } finally {
            markSaved();
        }

        return true;
    }

    @Override
    protected void persist(Set<String> deleted, Map<String, String> changed) {
        ItemManager.getInstance().saveVariables(objectId, deleted, changed);
    }

    @Override
    protected String variablesKey() {
        return String.valueOf(objectId);
    }

    public boolean deleteMe() {
        WorldPartition.checkOwner();
        try {
            ItemManager.getInstance().deleteVariables(objectId);
        } catch (DataAccessException e) {
            log.warn("Could not delete variables for: {}", objectId, e);
            return false;
        }

        clearValues();
        clearChangeTracking();
        return true;
    }
}
