/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.instancezone;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.holders.npc.MinionHolder;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.StatSet;
import java.util.List;
import java.util.Map;

/**
 * Instance script parameters, wrapping a private {@link StatSet} because the shared {@code StatSet} cannot carry the
 * ownership check. Method names and signatures mirror {@code StatSet} for the existing scripts.
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

    public long getLong(String key) {
        WorldPartition.checkOwner();
        return values.getLong(key);
    }

    public long getLong(String key, long defaultValue) {
        WorldPartition.checkOwner();
        return values.getLong(key, defaultValue);
    }

    public String getString(String key) {
        WorldPartition.checkOwner();
        return values.getString(key);
    }

    public String getString(String key, String defaultValue) {
        WorldPartition.checkOwner();
        return values.getString(key, defaultValue);
    }

    public <A> A getObject(String name, Class<A> type) {
        WorldPartition.checkOwner();
        return values.getObject(name, type);
    }

    public <A> A getObject(String name, Class<A> type, A defaultValue) {
        WorldPartition.checkOwner();
        return values.getObject(name, type, defaultValue);
    }

    public SkillHolder getSkillHolder(String key) {
        WorldPartition.checkOwner();
        return values.getSkillHolder(key);
    }

    public Location getLocation(String key) {
        WorldPartition.checkOwner();
        return values.getLocation(key);
    }

    public List<MinionHolder> getMinionList(String key) {
        WorldPartition.checkOwner();
        return values.getMinionList(key);
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

    public void set(String name, boolean value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, int value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, long value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, String value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void remove(String key) {
        WorldPartition.checkOwner();
        values.remove(key);
    }

    public boolean contains(String name) {
        WorldPartition.checkOwner();
        return values.contains(name);
    }
}
