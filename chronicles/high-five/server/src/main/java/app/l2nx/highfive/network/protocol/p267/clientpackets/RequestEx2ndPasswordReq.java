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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.data.xml.SecondaryAuthData;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.Ex2ndPasswordAck;
import app.l2nx.highfive.security.SecondaryPasswordAuth;

/**
 * (ch)cS{S} c: change pass? S: current password S: new password
 * @author mrTJO
 */
public class RequestEx2ndPasswordReq extends ClientPacket {
    private int changePass;
    private String password;
    private String newPassword;

    @Override
    protected void readImpl() {
        changePass = readByte();
        password = readString();
        if (changePass == 2) {
            newPassword = readString();
        }
    }

    @Override
    protected void runImpl() {
        if (!SecondaryAuthData.getInstance().isEnabled()) {
            return;
        }

        final GameClient client = getClient();
        final SecondaryPasswordAuth secondAuth = client.getSecondaryAuth();
        boolean success = false;
        if ((changePass == 0) && !secondAuth.passwordExist()) {
            success = secondAuth.savePassword(password);
        } else if ((changePass == 2) && secondAuth.passwordExist()) {
            success = secondAuth.changePassword(password, newPassword);
        }

        if (success) {
            client.sendPacket(new Ex2ndPasswordAck(Ex2ndPasswordAck.SUCCESS));
        }
    }
}
