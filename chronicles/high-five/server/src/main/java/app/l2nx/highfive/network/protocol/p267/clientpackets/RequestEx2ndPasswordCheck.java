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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.data.xml.SecondaryAuthData;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Ex2ndPasswordCheck;

/**
 * Format: (ch)
 * @author mrTJO
 */
public class RequestEx2ndPasswordCheck extends ClientPacket {
    @Override
    protected void readImpl() {}

    @Override
    protected void runImpl() {
        final GameClient client = getClient();
        if (!SecondaryAuthData.getInstance().isEnabled()
                || client.getSecondaryAuth().isAuthed()) {
            client.sendPacket(new Ex2ndPasswordCheck(Ex2ndPasswordCheck.PASSWORD_OK));
            return;
        }

        client.getSecondaryAuth().openDialog();
    }
}
