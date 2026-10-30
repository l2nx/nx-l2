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
package app.l2nx.login.session.model;

import app.l2nx.login.LoginServer;
import lombok.Getter;

/**
 * Session key used by the client to authenticate against the gameserver.<br>
 * It holds two 32-bit pairs: PlayOk and LoginOk.
 * <ul>
 * <li>Equality may require both pairs depending on {@code l2nx.login.show-licence}.</li>
 * <li>Lightweight container with no I/O.</li>
 * <li>Immutable fields for thread safety.</li>
 * </ul>
 * @author BazookaRpm
 */
public class SessionKey {
    // Constants.
    private static final String LABEL_PLAY_OK = "PlayOk:";
    private static final String LABEL_LOGIN_OK = "LoginOk:";
    private static final boolean CHECK_LOGIN_PAIR =
            LoginServer.getInstance().getProperties().showLicence();
    // Session key parts.
    @Getter
    private final int playOkID1;

    @Getter
    private final int playOkID2;

    @Getter
    private final int loginOkID1;

    @Getter
    private final int loginOkID2;

    /**
     * Creates a session key with both pairs.
     * @param loginOk1
     * @param loginOk2
     * @param playOk1
     * @param playOk2
     */
    public SessionKey(int loginOk1, int loginOk2, int playOk1, int playOk2) {
        playOkID1 = playOk1;
        playOkID2 = playOk2;
        loginOkID1 = loginOk1;
        loginOkID2 = loginOk2;
    }

    /**
     * Returns a textual representation of the key.<br>
     * Do not write this value to production logs to avoid exposing session identifiers.
     * @return String with both pairs.
     */
    @Override
    public String toString() {
        return LABEL_PLAY_OK + " " + playOkID1 + " " + playOkID2 + " " + LABEL_LOGIN_OK + loginOkID1 + " " + loginOkID2;
    }

    /**
     * Checks if the given pair matches the stored LoginOk pair.
     * @param loginOk1
     * @param loginOk2
     * @return {@code true} if both values match.
     */
    public boolean checkLoginPair(int loginOk1, int loginOk2) {
        return (loginOkID1 == loginOk1) && (loginOkID2 == loginOk2);
    }

    /**
     * Compares equality according to the captured licence policy.<br>
     * If {@code CHECK_LOGIN_PAIR} is {@code true}, both PlayOk and LoginOk must match; otherwise, only PlayOk.
     * @param object
     * @return {@code true} if keys match according to policy.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof SessionKey)) {
            return false;
        }

        final SessionKey key = (SessionKey) object;
        if ((playOkID1 != key.playOkID1) || (playOkID2 != key.playOkID2)) {
            return false;
        }

        if (CHECK_LOGIN_PAIR) {
            return (loginOkID1 == key.loginOkID1) && (loginOkID2 == key.loginOkID2);
        }

        return true;
    }

    /**
     * Hash code consistent with {@link #equals(Object)}.
     * @return Hash value.
     */
    @Override
    public int hashCode() {
        int h = 17;
        h = (31 * h) + playOkID1;
        h = (31 * h) + playOkID2;

        if (CHECK_LOGIN_PAIR) {
            h = (31 * h) + loginOkID1;
            h = (31 * h) + loginOkID2;
        }

        return h;
    }
}
