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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.engine.core.session.ContextBound;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.GameContext;

/**
 * A legacy route: read on the IO thread, run from the session queue in order with the commands.
 */
public abstract class ClientPacket extends IncomingPacket<GameClient> implements ContextBound {
    private GameContext context = GameContext.WORLD;

    @Override
    protected final boolean read() {
        readImpl();
        return true;
    }

    protected abstract void readImpl();

    @Override
    public void run() {
        runImpl();
    }

    protected abstract void runImpl();

    final void requireContext(GameContext context) {
        this.context = context;
    }

    @Override
    public boolean hasContext() {
        return context.presentFor(getClient());
    }

    @Override
    public String requiredContext() {
        return context.name();
    }

    protected Player getPlayer() {
        return getClient().getPlayer();
    }
}
