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
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.StatSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persistent variables with change tracking, so a save writes only what changed since the last load or save.
 * <p>
 * The values sit in a private {@link StatSet} instead of this class extending it: static data shares
 * {@code StatSet}, so its public methods cannot carry the ownership check.
 * @author UnAfraid, Mobius
 */
@SingleWriter
public abstract class AbstractVariables {
    // Insertion order, so a save writes its rows in the same order on every run.
    private final StatSet values = new StatSet(LinkedHashMap::new);
    private final Set<String> added = new LinkedHashSet<>(4);
    private final Set<String> modified = new LinkedHashSet<>(4);
    private final Set<String> deleted = new LinkedHashSet<>(4);
    private boolean changed;

    public boolean getBoolean(String name) {
        WorldPartition.checkOwner();
        return values.getBoolean(name);
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        WorldPartition.checkOwner();
        return values.getBoolean(name, defaultValue);
    }

    public int getInt(String name) {
        WorldPartition.checkOwner();
        return values.getInt(name);
    }

    public int getInt(String name, int defaultValue) {
        WorldPartition.checkOwner();
        return values.getInt(name, defaultValue);
    }

    public long getLong(String name) {
        WorldPartition.checkOwner();
        return values.getLong(name);
    }

    public long getLong(String name, long defaultValue) {
        WorldPartition.checkOwner();
        return values.getLong(name, defaultValue);
    }

    public String getString(String name) {
        WorldPartition.checkOwner();
        return values.getString(name);
    }

    public String getString(String name, String defaultValue) {
        WorldPartition.checkOwner();
        return values.getString(name, defaultValue);
    }

    public byte[] getByteArray(String name, String splitOn) {
        WorldPartition.checkOwner();
        return values.getByteArray(name, splitOn);
    }

    public List<Integer> getIntegerList(String name) {
        WorldPartition.checkOwner();
        return values.getIntegerList(name);
    }

    public void set(String name, boolean value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    public void set(String name, byte value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    public void set(String name, short value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    public void set(String name, int value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    public void set(String name, long value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    public void set(String name, float value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    public void set(String name, double value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    public void set(String name, String value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    public void set(String name, Enum<?> value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    public void set(String name, Object value) {
        WorldPartition.checkOwner();
        put(name, value);
    }

    /**
     * Puts the value without marking it changed when {@code markAsChanged} is false, for values just restored from
     * the database.
     * @param name the name of the variable
     * @param value the value to set
     * @param markAsChanged whether the next save writes it
     */
    public void set(String name, String value, boolean markAsChanged) {
        WorldPartition.checkOwner();
        if (markAsChanged) {
            trackChange(name);
        }

        values.set(name, value);
    }

    /**
     * Stores the list as comma separated values, the form {@link #getIntegerList} reads; an empty list removes the
     * variable.
     * @param name the name of the variable
     * @param list the values
     */
    public void setIntegerList(String name, List<Integer> list) {
        WorldPartition.checkOwner();
        if (name == null) {
            return;
        }

        if ((list == null) || list.isEmpty()) {
            delete(name);
            return;
        }

        put(name, list.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    public boolean hasVariable(String name) {
        WorldPartition.checkOwner();
        return values.contains(name);
    }

    public boolean hasChanges() {
        WorldPartition.checkOwner();
        return changed;
    }

    public void remove(String name) {
        WorldPartition.checkOwner();
        delete(name);
    }

    private void put(String name, Object value) {
        trackChange(name);
        values.set(name, value);
    }

    // Tracks before the put, so a null value, which the put ignores, still counts as a change.
    private void trackChange(String name) {
        changed = true;
        if (values.contains(name)) {
            modified.add(name);
            deleted.remove(name);
        } else {
            added.add(name);
        }
    }

    private void delete(String name) {
        changed = true;
        if (values.getSet().remove(name) == null) {
            return;
        }

        // Added since the last save: the database has no row to delete.
        if (added.remove(name)) {
            return;
        }

        modified.remove(name);
        deleted.add(name);
    }

    protected Map<String, String> changedValues() {
        final Map<String, String> result = new LinkedHashMap<>();
        putValues(result, added);
        putValues(result, modified);
        return result;
    }

    private void putValues(Map<String, String> result, Set<String> names) {
        for (String name : names) {
            final Object value = values.getSet().get(name);
            if (value != null) {
                result.put(name, String.valueOf(value));
            }
        }
    }

    protected Set<String> deletedNames() {
        return new LinkedHashSet<>(deleted);
    }

    /**
     * @return a read-only view of all values; only for a synchronous save that replaces every row.
     */
    protected Map<String, Object> allValues() {
        return Collections.unmodifiableMap(values.getSet());
    }

    protected void clearValues() {
        values.getSet().clear();
    }

    protected void clearChangeTracking() {
        added.clear();
        modified.clear();
        deleted.clear();
    }

    protected void markSaved() {
        clearChangeTracking();
        changed = false;
    }
}
