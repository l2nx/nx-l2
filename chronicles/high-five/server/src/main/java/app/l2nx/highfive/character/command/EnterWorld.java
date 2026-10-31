/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import app.l2nx.engine.core.session.command.Command;
import java.net.Inet4Address;
import java.util.List;

public record EnterWorld(List<Inet4Address> route) implements Command {
    public EnterWorld {
        route = List.copyOf(route);
    }
}
