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

import lombok.Builder;

/**
 * The two key pairs the login server gives a client: LoginOk (after the login) and PlayOk (after the server selection).
 * The client shows both to the game server, which asks the login server to check them.
 */
@Builder(toBuilder = true)
public record SessionKey(int loginOk1, int loginOk2, int playOk1, int playOk2) {
    public boolean checkLoginPair(int loginOk1, int loginOk2) {
        return (this.loginOk1 == loginOk1) && (this.loginOk2 == loginOk2);
    }

    /**
     * @param checkLoginPair whether the LoginOk pair must match too: only a client that was shown the licence
     *     received it ({@code l2nx.login.show-licence})
     */
    public boolean matches(SessionKey presented, boolean checkLoginPair) {
        return (playOk1 == presented.playOk1)
                && (playOk2 == presented.playOk2)
                && (!checkLoginPair || checkLoginPair(presented.loginOk1, presented.loginOk2));
    }

    /** Never prints the key: it authenticates a game server entry. */
    @Override
    public String toString() {
        return "SessionKey";
    }
}
