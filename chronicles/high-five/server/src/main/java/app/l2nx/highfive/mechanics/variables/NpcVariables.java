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
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.StatSet;

/**
 * In-memory script variables of an npc, never saved.
 * <p>
 * The values sit in a private {@link StatSet} instead of this class extending it: static data shares
 * {@code StatSet}, so its public methods cannot carry the ownership check.
 * @author GKR, Mobius
 */
@SingleWriter
public class NpcVariables {
    private final StatSet values = new StatSet();

    public boolean getBoolean(String name) {
        WorldPartition.checkOwner();
        return values.getBoolean(name);
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        WorldPartition.checkOwner();
        return values.getBoolean(name, defaultValue);
    }

    /**
     * Unlike {@link StatSet#getInt(String)}, a missing variable reads as 0 instead of throwing.
     * @param name the name of the variable
     * @return the value, or 0 when the variable is not set
     */
    public int getInt(String name) {
        WorldPartition.checkOwner();
        return values.getInt(name, 0);
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

    public <A> A getObject(String name, Class<A> type) {
        WorldPartition.checkOwner();
        return values.getObject(name, type);
    }

    public <A> A getObject(String name, Class<A> type, A defaultValue) {
        WorldPartition.checkOwner();
        return values.getObject(name, type, defaultValue);
    }

    public Player getPlayer(String name) {
        WorldPartition.checkOwner();
        return values.getObject(name, Player.class);
    }

    public Summon getSummon(String name) {
        WorldPartition.checkOwner();
        return values.getObject(name, Summon.class);
    }

    public void set(String name, boolean value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, byte value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, short value) {
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

    public void set(String name, float value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, double value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, String value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, Enum<?> value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void set(String name, Object value) {
        WorldPartition.checkOwner();
        values.set(name, value);
    }

    public void remove(String name) {
        WorldPartition.checkOwner();
        values.remove(name);
    }

    public boolean hasVariable(String name) {
        WorldPartition.checkOwner();
        return values.contains(name);
    }

    public void clear() {
        WorldPartition.checkOwner();
        values.getSet().clear();
    }
}
