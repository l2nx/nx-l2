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

import app.l2nx.login.GameServerThread;
import app.l2nx.login.controller.LoginController;
import app.l2nx.network.packet.SimpleReadablePacket;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author mrTJO
 */
@Slf4j
public class RequestTempBan extends SimpleReadablePacket {
    private final UUID accountId;
    private final String ip;
    private final Duration duration;

    public RequestTempBan(byte[] decrypt, GameServerThread server) {
        super(decrypt);
        readByte(); // Packet id, it is already processed.

        accountId = new UUID(readLong(), readLong());
        ip = StringUtils.trimToNull(readString());
        duration = Duration.ofMinutes(readLong());
        final boolean haveReason = readByte() != 0;
        if (haveReason) {
            readString(); // _banReason
        }

        // The login server would read a non-positive length as a permanent address ban.
        if (!duration.isPositive()) {
            log.warn("Ignored a temporary ban of account {} with a non-positive length of {}.", accountId, duration);
            return;
        }

        // A game server may only ban an account it holds.
        if (server.hasAccountOnGameServer(accountId)) {
            banUser();
        }
    }

    private void banUser() {
        LoginController.getInstance().setAccountTempBan(accountId, duration);
        if (ip != null) {
            LoginController.getInstance().addBanForAddress(ip, duration.toMillis());
        }
    }
}
