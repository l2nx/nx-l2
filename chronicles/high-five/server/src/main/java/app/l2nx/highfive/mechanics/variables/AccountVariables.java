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

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.threads.ThreadPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * @author UnAfraid, Mobius
 */
@Slf4j
public class AccountVariables extends AbstractVariables {

    // SQL Queries.
    private static final String SELECT_QUERY = "SELECT * FROM account_gsdata WHERE account_name = ?";
    private static final String DELETE_QUERY = "DELETE FROM account_gsdata WHERE account_name = ? AND var = ?";
    private static final String DELETE_ALL_QUERY = "DELETE FROM account_gsdata WHERE account_name = ?";
    private static final String UPSERT_QUERY =
            "INSERT INTO account_gsdata (account_name, var, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)";
    private static final String DELETE_QUERY_VAR = "DELETE FROM account_gsdata WHERE var = ?";

    // Asynchronous persistence.
    private static final long SAVE_INTERVAL = 60000; // 1 minute.
    private static final boolean ASYNC_SAVE_ENABLED = true;

    // Public variables.
    public static final String HWID = "HWID";
    public static final String HWIDSLIT_VAR = "	";

    // Private variables.
    private final AtomicBoolean scheduledSave = new AtomicBoolean(false);
    private final String accountName;

    public AccountVariables(String accountName) {
        this.accountName = accountName;
        restoreMe();
    }

    public boolean restoreMe() {
        clearChangeTracking();

        // Restore previous variables.
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement st = con.prepareStatement(SELECT_QUERY)) {
            st.setString(1, accountName);
            try (ResultSet rset = st.executeQuery()) {
                while (rset.next()) {
                    set(rset.getString("var"), rset.getString("value"), false);
                }
            }
        } catch (SQLException e) {
            log.warn("Could not restore variables for: {}", accountName, e);
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

            ThreadPool.schedule(
                    () -> {
                        scheduledSave.set(false);
                        saveNow();
                    },
                    SAVE_INTERVAL);
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

        // FIXME: May store after server shutdown.
        // If async is enabled, offload to ThreadPool.
        // if (ASYNC_SAVE_ENABLED)
        // {
        // ThreadPool.execute(this::saveNowSync);
        // return true;
        // }

        return saveNowSync();
    }

    /**
     * Synchronous implementation of variable saving with optimized database operations.
     * @return true if successful, false otherwise.
     */
    private boolean saveNowSync() {
        saveLock.lock();

        try {
            try (Connection con = DatabaseFactory.getConnection()) {
                if (!deleted.isEmpty()) {
                    try (PreparedStatement st = con.prepareStatement(DELETE_QUERY)) {
                        final List<String> sortedDeleted = new ArrayList<>(deleted);
                        sortedDeleted.sort(null);
                        for (String name : sortedDeleted) {
                            st.setString(1, accountName);
                            st.setString(2, name);
                            st.addBatch();
                        }

                        st.executeBatch();
                    }
                }

                if (!added.isEmpty() || !modified.isEmpty()) {
                    final TreeSet<String> allChanges = new TreeSet<>();
                    allChanges.addAll(added);
                    allChanges.addAll(modified);
                    try (PreparedStatement st = con.prepareStatement(UPSERT_QUERY)) {
                        for (String name : allChanges) {
                            final Object value = getSet().get(name);
                            if (value != null) {
                                st.setString(1, accountName);
                                st.setString(2, name);
                                st.setString(3, String.valueOf(value));
                                st.addBatch();
                            }
                        }

                        st.executeBatch();
                    }
                }

                // Clear tracking after successful save.
                clearChangeTracking();
                compareAndSetChanges(true, false);
                return true;
            } catch (SQLException e) {
                log.warn("Could not update variables for: {}", accountName, e);
                return false;
            }
        } finally {
            saveLock.unlock();
        }
    }

    public boolean deleteMe() {
        saveLock.lock();

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement st = con.prepareStatement(DELETE_ALL_QUERY)) {
            st.setString(1, accountName);
            st.execute();
        } catch (Exception e) {
            log.warn("Could not delete variables for: {}", accountName, e);
            saveLock.unlock();
            return false;
        }

        // Clear all variables.
        getSet().clear();
        clearChangeTracking();

        saveLock.unlock();
        return true;
    }

    /**
     * Delete all entries for an requested var
     * @param var
     * @return success
     */
    public static boolean deleteVipPurchases(String var) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement st = con.prepareStatement(DELETE_QUERY_VAR)) {
            st.setString(1, var);
            st.execute();
        } catch (Exception e) {
            log.warn("Could not delete vip variables!", e);
            return false;
        }

        return true;
    }
}
