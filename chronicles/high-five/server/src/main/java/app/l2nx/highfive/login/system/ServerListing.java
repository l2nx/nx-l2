/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.highfive.login.system;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.ListingAttribute;
import app.l2nx.highfive.login.ListingStatus;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * This server's entry in the login server's list. Bracket, type, age and the GM-only start are the {@code Server.ini}
 * and {@code General.ini} values the admin commands change at runtime; status and max players live here.
 */
@Component
public class ServerListing {
    @Getter
    @Setter
    private volatile ListingStatus status = ListingStatus.AUTO;

    @Getter
    @Setter
    private volatile String serverName;

    // Server.ini MaximumOnlineUsers until an admin sets it.
    private volatile Integer maxPlayers;

    /** The whole entry, sent each time the login server accepts the registration. */
    public Map<ListingAttribute, Integer> full() {
        final Map<ListingAttribute, Integer> attributes = new EnumMap<>(ListingAttribute.class);
        attributes.put(ListingAttribute.SQUARE_BRACKET, ServerConfig.SERVER_LIST_BRACKET ? 1 : 0);
        attributes.put(ListingAttribute.TYPE, ServerConfig.SERVER_LIST_TYPE);
        attributes.put(
                ListingAttribute.STATUS,
                (GeneralConfig.SERVER_GMONLY ? ListingStatus.GM_ONLY : ListingStatus.AUTO).code());
        attributes.put(ListingAttribute.AGE, ageCode(ServerConfig.SERVER_LIST_AGE));
        attributes.put(ListingAttribute.MAX_PLAYERS, getMaxPlayers());
        return attributes;
    }

    public int getMaxPlayers() {
        final Integer set = maxPlayers;
        return set != null ? set : ServerConfig.MAXIMUM_ONLINE_USERS;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public void setType(int type) {
        ServerConfig.SERVER_LIST_TYPE = type;
    }

    public void setAge(int age) {
        ServerConfig.SERVER_LIST_AGE = age;
    }

    /** The list knows the age limits 15 and 18; any other value shows as none. */
    public static int ageCode(int age) {
        return switch (age) {
            case 15, 18 -> age;
            default -> 0;
        };
    }
}
