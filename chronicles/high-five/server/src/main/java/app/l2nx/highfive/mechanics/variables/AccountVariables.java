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

import app.l2nx.highfive.managers.AccountVariablesManager;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author UnAfraid, Mobius
 */
@Slf4j
public class AccountVariables extends AbstractVariables {

    // Asynchronous persistence.
    private static final long SAVE_INTERVAL = 60000; // 1 minute.
    private static final boolean ASYNC_SAVE_ENABLED = true;

    // Public variables.
    public static final String HWID = "HWID";
    public static final String HWIDSLIT_VAR = "	";

    // Private variables.
    private final AtomicBoolean scheduledSave = new AtomicBoolean(false);
    private final UUID accountId;

    public AccountVariables(UUID accountId) {
        this.accountId = accountId;
        restoreMe();
    }

    public boolean restoreMe() {
        clearChangeTracking();

        // Restore previous variables.
        try {
            AccountVariablesManager.getInstance()
                    .findVariables(accountId)
                    .forEach((name, value) -> set(name, value, false));
        } catch (DataAccessException e) {
            log.warn("Could not restore variables for: {}", accountId, e);
            return false;
        } finally {
            compareAndSetChanges(true, false);
        }

        return true;
    }

    public boolean storeMe() {
        // No changes, nothing to store.
        if (!hasChanges()) {
            return false;
        }

        // If async saving is enabled and not already scheduled, schedule a save.
        if (ASYNC_SAVE_ENABLED && !scheduledSave.get()) {
            scheduledSave.set(true);

            WorldPartition.loop()
                    .schedule(
                            () -> {
                                scheduledSave.set(false);
                                saveNow();
                            },
                            SAVE_INTERVAL,
                            TimeUnit.MILLISECONDS);
            return true;
        }

        return saveNow();
    }

    /**
     * Force an immediate save of the variables.
     * @return true if successful, false otherwise.
     */
    public boolean saveNow() {
        if (!hasChanges()) {
            return false;
        }

        return saveNowSync();
    }

    /**
     * Synchronous implementation of variable saving with optimized database operations.
     * @return true if successful, false otherwise.
     */
    private boolean saveNowSync() {
        saveLock.lock();

        try {
            final Map<String, String> changed = new HashMap<>();
            for (String name : added) {
                putValue(changed, name);
            }

            for (String name : modified) {
                putValue(changed, name);
            }

            AccountVariablesManager.getInstance().saveVariables(accountId, new HashSet<>(deleted), changed);

            // Clear tracking after successful save.
            clearChangeTracking();
            compareAndSetChanges(true, false);
            return true;
        } catch (DataAccessException e) {
            log.warn("Could not update variables for: {}", accountId, e);
            return false;
        } finally {
            saveLock.unlock();
        }
    }

    private void putValue(Map<String, String> changed, String name) {
        final Object value = getSet().get(name);
        if (value != null) {
            changed.put(name, String.valueOf(value));
        }
    }

    public boolean deleteMe() {
        saveLock.lock();

        try {
            AccountVariablesManager.getInstance().deleteVariables(accountId);
        } catch (DataAccessException e) {
            log.warn("Could not delete variables for: {}", accountId, e);
            saveLock.unlock();
            return false;
        }

        // Clear all variables.
        getSet().clear();
        clearChangeTracking();

        saveLock.unlock();
        return true;
    }
}
