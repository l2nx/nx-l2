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
package app.l2nx.highfive.handler;

import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import java.util.HashMap;
import java.util.Map;

/**
 * @author UnAfraid
 */
public class TargetHandler implements IHandler<ITargetTypeHandler, Enum<TargetType>> {
    private final Map<Enum<TargetType>, ITargetTypeHandler> datatable;

    protected TargetHandler() {
        datatable = new HashMap<>();
    }

    @Override
    public void registerHandler(ITargetTypeHandler handler) {
        datatable.put(handler.getTargetType(), handler);
    }

    @Override
    public synchronized void removeHandler(ITargetTypeHandler handler) {
        datatable.remove(handler.getTargetType());
    }

    @Override
    public ITargetTypeHandler getHandler(Enum<TargetType> targetType) {
        return datatable.get(targetType);
    }

    @Override
    public int size() {
        return datatable.size();
    }

    public static TargetHandler getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final TargetHandler INSTANCE = new TargetHandler();
    }
}
