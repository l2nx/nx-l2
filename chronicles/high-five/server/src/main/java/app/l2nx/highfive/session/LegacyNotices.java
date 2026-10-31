/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.session;

import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SSQInfo;
import java.util.Map;

// TODO: move into the Seven Signs and HTML dialog slices, which record the allowed actions before a dialog opens.
public final class LegacyNotices {
    private LegacyNotices() {}

    public static void sevenSignsSky(GameClient client) {
        client.sendPacket(new SSQInfo());
    }

    public static void htmlFile(GameClient client, String path, Map<String, String> replacements) {
        final NpcHtmlMessage message = new NpcHtmlMessage();
        message.setFile(null, path);
        replacements.forEach(message::replace);
        client.sendPacket(message);
    }
}
