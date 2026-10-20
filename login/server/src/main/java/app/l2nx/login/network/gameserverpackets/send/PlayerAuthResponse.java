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
package app.l2nx.login.network.gameserverpackets.send;

import app.l2nx.network.packet.SimpleWritablePacket;
import java.util.Objects;
import java.util.UUID;

/**
 * @author -Wooden-
 */
public class PlayerAuthResponse extends SimpleWritablePacket {
    private PlayerAuthResponse(String login, UUID accountId) {
        writeByte(0x03);
        writeString(login);
        writeByte(accountId != null);
        if (accountId != null) {
            writeLong(accountId.getMostSignificantBits());
            writeLong(accountId.getLeastSignificantBits());
        }
    }

    public static PlayerAuthResponse accepted(String login, UUID accountId) {
        return new PlayerAuthResponse(login, Objects.requireNonNull(accountId, "accountId"));
    }

    public static PlayerAuthResponse rejected(String login) {
        return new PlayerAuthResponse(login, null);
    }
}
