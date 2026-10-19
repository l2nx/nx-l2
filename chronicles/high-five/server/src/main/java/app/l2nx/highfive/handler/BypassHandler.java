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

import java.util.HashMap;
import java.util.Map;

/**
 * @author nBd
 */
public class BypassHandler implements IHandler<IBypassHandler, String> {
    private final Map<String, IBypassHandler> datatable;

    protected BypassHandler() {
        datatable = new HashMap<>();
    }

    @Override
    public void registerHandler(IBypassHandler handler) {
        for (String element : handler.getCommandList()) {
            datatable.put(element.toLowerCase(), handler);
        }
    }

    @Override
    public synchronized void removeHandler(IBypassHandler handler) {
        for (String element : handler.getCommandList()) {
            datatable.remove(element.toLowerCase());
        }
    }

    @Override
    public IBypassHandler getHandler(String commandValue) {
        String command = commandValue;
        if (command.contains(" ")) {
            command = command.substring(0, command.indexOf(' '));
        }

        return datatable.get(command.toLowerCase());
    }

    @Override
    public int size() {
        return datatable.size();
    }

    public static BypassHandler getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final BypassHandler INSTANCE = new BypassHandler();
    }
}
