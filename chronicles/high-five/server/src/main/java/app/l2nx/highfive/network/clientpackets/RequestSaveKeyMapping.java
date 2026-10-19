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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.variables.PlayerVariables;
import app.l2nx.highfive.network.ConnectionState;

/**
 * @author Mobius
 */
public class RequestSaveKeyMapping extends ClientPacket {
    public static final String SPLIT_VAR = "	";

    private byte[] uiKeyMapping;

    @Override
    protected void readImpl() {
        final int dataSize = readInt();
        if (dataSize > 0) {
            uiKeyMapping = readBytes(dataSize);
        }
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (!PlayerConfig.STORE_UI_SETTINGS
                || //
                (player == null)
                || //
                (uiKeyMapping == null)
                || //
                (getClient().getConnectionState() != ConnectionState.IN_GAME)) {
            return;
        }

        final StringBuilder sb = new StringBuilder(uiKeyMapping.length * 4);
        for (Byte b : uiKeyMapping) {
            sb.append(b).append(SPLIT_VAR);
        }

        player.getVariables().set(PlayerVariables.UI_KEY_MAPPING, sb.toString());
    }
}
