/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.instancezone;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.StatSet;
import java.util.List;
import java.util.Map;

/**
 * Instance script parameters, wrapping a private {@link StatSet} because the shared {@code StatSet} cannot carry the
 * ownership check. Only the {@code StatSet} methods the scripts use, with the same names and signatures.
 */
@SingleWriter
public final class InstanceParameters {
    private final StatSet values = new StatSet();

    public boolean getBoolean(String key) {
        WorldPartition.checkOwner();
        return values.getBoolean(key);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        WorldPartition.checkOwner();
        return values.getBoolean(key, defaultValue);
    }

    public int getInt(String key) {
        WorldPartition.checkOwner();
        return values.getInt(key);
    }

    public int getInt(String key, int defaultValue) {
        WorldPartition.checkOwner();
        return values.getInt(key, defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        WorldPartition.checkOwner();
        return values.getLong(key, defaultValue);
    }

    public <A> A getObject(String name, Class<A> type) {
        WorldPartition.checkOwner();
        return values.getObject(name, type);
    }

    /** A copy; {@code null} when absent. */
    public Location getLocation(String key) {
        WorldPartition.checkOwner();
        final Location location = values.getLocation(key);
        return location == null ? null : location.clone();
    }

    public <T> List<T> getList(String key, Class<T> clazz) {
        WorldPartition.checkOwner();
        return values.getList(key, clazz);
    }

    public <T> List<T> getList(String key, Class<T> clazz, List<T> defaultValue) {
        WorldPartition.checkOwner();
        return values.getList(key, clazz, defaultValue);
    }

    public <K, V> Map<K, V> getMap(String key, Class<K> keyClass, Class<V> valueClass) {
        WorldPartition.checkOwner();
        return values.getMap(key, keyClass, valueClass);
    }

    public void set(String name, Object value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, int value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void remove(String key) {
        WorldPartition.checkOwner();
        values.remove(key);
    }
}
