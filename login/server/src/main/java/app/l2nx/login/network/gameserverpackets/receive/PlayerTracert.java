/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
package app.l2nx.login.network.gameserverpackets.receive;

import app.l2nx.login.controller.LoginController;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.network.packet.SimpleReadablePacket;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

/**
 * @author mrTJO
 */
public class PlayerTracert extends SimpleReadablePacket {

    public PlayerTracert(byte[] decrypt, GameServerLink server) {
        super(decrypt);
        readByte(); // Packet id, it is already processed.

        final UUID accountId = new UUID(readLong(), readLong());
        final String pcIp = StringUtils.trimToNull(readString());
        final String hop1 = StringUtils.trimToNull(readString());
        final String hop2 = StringUtils.trimToNull(readString());
        final String hop3 = StringUtils.trimToNull(readString());
        final String hop4 = StringUtils.trimToNull(readString());
        if (server.hasAccountOnGameServer(accountId)) {
            LoginController.getInstance().setAccountLastTracert(accountId, pcIp, hop1, hop2, hop3, hop4);
        }
    }
}
