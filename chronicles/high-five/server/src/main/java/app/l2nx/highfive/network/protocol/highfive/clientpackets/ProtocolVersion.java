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

import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.KeyPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 1.5.2.8.2.8 $ $Date: 2005/04/02 10:43:04 $
 */
public class ProtocolVersion extends ClientPacket {

    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    private int version;

    @Override
    protected void readImpl() {
        try {
            version = readInt();
        } catch (Exception e) {
            version = 0;
        }
    }

    @Override
    protected void runImpl() {
        // This packet is never encrypted.
        final GameClient client = getClient();
        if (version == -2) {
            // This is just a ping attempt from the new C2 client.
            client.disconnect();
        } else if (!ServerConfig.PROTOCOL_LIST.contains(version)) {
            ACCOUNTING_LOG.warn("Wrong protocol version {}, {}", version, client);
            client.setProtocolOk(false);
            client.close(new KeyPacket(client.enableCrypt(), 0));
        } else {
            client.setProtocolVersion(version);
            client.setProtocolOk(true);
            client.sendPacket(new KeyPacket(client.enableCrypt(), 1));
        }
    }
}
