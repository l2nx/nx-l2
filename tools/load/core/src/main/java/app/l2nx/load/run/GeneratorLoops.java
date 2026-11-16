/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.injection.RunClock;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;

/** The event loops a run's load bots live on and the clock they keep, so a test can run both in virtual time. */
interface GeneratorLoops {
    RunClock clock();

    EventLoopGroup open(int threads, IoHandlerFactory ioHandlerFactory);

    /**
     * Returns once every task of the group has run and its loops terminated: the summary reads load bot state
     * written on them.
     */
    void close(EventLoopGroup group);
}
