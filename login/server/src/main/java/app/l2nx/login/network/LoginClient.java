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
package app.l2nx.login.network;

import app.l2nx.login.controller.LoginController;
import app.l2nx.login.controller.SessionKey;
import app.l2nx.login.enums.AccountKickedReason;
import app.l2nx.login.enums.LoginFailReason;
import app.l2nx.login.enums.PlayFailReason;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.protocol.c621.ConnectionState;
import app.l2nx.login.protocol.c621.serverpackets.AccountKicked;
import app.l2nx.login.protocol.c621.serverpackets.LoginFail;
import app.l2nx.login.protocol.c621.serverpackets.LoginServerPacket;
import app.l2nx.login.protocol.c621.serverpackets.PlayFail;
import app.l2nx.login.protocol.c621.serverpackets.ServerList;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.guard.ConnectionDeadlines;
import app.l2nx.network.channel.guard.IdlePolicy;
import app.l2nx.network.exec.SerialExecutor;
import app.l2nx.network.metrics.model.DropReason;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;

/**
 * @author KenM, BazookaRpm
 */
public class LoginClient {
    public static final AttributeKey<LoginClient> KEY = AttributeKey.valueOf("l2nx.login.client");

    static final Duration CHARACTERS_WAIT = Duration.ofMillis(500);

    private final Channel channel;

    @Getter
    private final LoginVariant variant;

    private final LoginKeys keys;

    @Getter
    private final int sessionId;

    private final SerialExecutor tasks;

    @Getter
    private final String ip;

    @Getter
    private final long connectionStartTime;

    private final Map<Integer, Integer> charsOnServers = new ConcurrentHashMap<>();
    private final Map<Integer, long[]> charsToDelete = new ConcurrentHashMap<>();
    private final Set<Integer> charactersPending = ConcurrentHashMap.newKeySet();
    private volatile long charactersDeadline;

    private ScheduledFuture<?> serverListWait;

    @Getter
    @Setter
    private volatile String account;

    @Getter
    @Setter
    private volatile UUID accountId;

    @Getter
    @Setter
    private volatile int accessLevel;

    @Getter
    @Setter
    private volatile int lastServer;

    @Getter
    @Setter
    private volatile SessionKey sessionKey;

    @Setter
    private volatile boolean joinedGS;

    @Getter
    @Setter
    private volatile ConnectionState connectionState = ConnectionState.CONNECTED;

    public LoginClient(Channel channel, LoginVariant variant, LoginKeys keys, int sessionId, SerialExecutor tasks) {
        this.channel = channel;
        this.variant = variant;
        this.keys = keys;
        this.sessionId = sessionId;
        this.tasks = tasks;
        ip = channel.remoteAddress() instanceof InetSocketAddress inet
                ? inet.getAddress().getHostAddress()
                : Objects.toString(channel.remoteAddress(), null);
        connectionStartTime = System.currentTimeMillis();
        channel.closeFuture().addListener(future -> cancelServerListWait());
    }

    public static LoginClient of(Channel channel) {
        return channel.attr(KEY).get();
    }

    public void execute(Runnable task) {
        if (tasks.execute(task)) {
            return;
        }
        if (tasks.isClosed()) {
            closeNow();
        } else {
            Connections.close(channel, CloseReason.FLOOD);
        }
    }

    /** Any thread. */
    public void dropped(DropReason reason) {
        Connections.metrics(channel).dropped(reason);
    }

    /** Any thread: the connection is closed or has its last frame queued. */
    public boolean isClosing() {
        final OutboundQueue queue = OutboundQueue.of(channel);
        return !channel.isActive() || ((queue != null) && queue.isClosing());
    }

    public void sendPacket(LoginServerPacket packet) {
        final ByteBuf frame = packet.serialize(this, channel.alloc());
        if (frame != null) {
            OutboundQueue.of(channel).send(frame);
        }
    }

    public void close(LoginServerPacket packet) {
        final ByteBuf frame = packet.serialize(this, channel.alloc());
        if (frame == null) {
            closeNow();
            return;
        }
        OutboundQueue.of(channel).sendAndClose(frame);
    }

    public void close(LoginFailReason reason) {
        close(new LoginFail(reason));
    }

    public void close(PlayFailReason reason) {
        close(new PlayFail(reason));
    }

    public void close(AccountKickedReason reason) {
        close(new AccountKicked(reason));
    }

    public void closeForShutdown() {
        close(LoginFailReason.REASON_SERVER_MAINTENANCE);
    }

    public void closeNow() {
        channel.close();
    }

    public boolean isConnected() {
        return channel.isActive();
    }

    public void handshakeDone() {
        final ConnectionDeadlines deadlines = ConnectionDeadlines.of(channel);
        if (deadlines != null) {
            deadlines.handshakeReached();
        }
    }

    public void authDone() {
        final ConnectionDeadlines deadlines = ConnectionDeadlines.of(channel);
        if (deadlines != null) {
            deadlines.authReached();
        }
        // The client sends nothing on the licence and server list screens; the session lifetime bounds them.
        final IdlePolicy idle = IdlePolicy.of(channel);
        if (idle != null) {
            idle.extendReadTimeout(LoginController.LOGIN_TIMEOUT);
        }
    }

    public byte[] getBlowfishKey() {
        return keys.blowfishKey();
    }

    public byte[] getScrambledModulus() {
        return keys.rsa().getScrambledModulus();
    }

    public RSAPrivateKey getRSAPrivateKey() {
        return (RSAPrivateKey) keys.rsa().getPrivateKey();
    }

    public boolean hasJoinedGS() {
        return joinedGS;
    }

    public void setCharsOnServ(int serverId, int chars) {
        charsOnServers.put(serverId, chars);
    }

    public Map<Integer, Integer> getCharsOnServ() {
        return charsOnServers;
    }

    public void serCharsWaitingDelOnServ(int serverId, long[] charsToDel) {
        charsToDelete.put(serverId, charsToDel);
    }

    public Map<Integer, long[]> getCharsWaitingDelOnServ() {
        return charsToDelete;
    }

    /** Called before the requests go out, so no answer can arrive ahead of its server. */
    public void awaitCharacters(Collection<Integer> serverIds) {
        charactersDeadline = channel.eventLoop().ticker().nanoTime() + CHARACTERS_WAIT.toNanos();
        charactersPending.addAll(serverIds);
    }

    public void charactersArrived(int serverId) {
        if (charactersPending.remove(serverId) && charactersPending.isEmpty()) {
            onEventLoop(this::serverListWaitOver);
        }
    }

    /**
     * Waits for the game servers' character counts, up to {@link #CHARACTERS_WAIT}, before sending the list: the High
     * Five client never asks again, so an early list would show no characters for good.
     */
    public void sendServerList() {
        onEventLoop(this::scheduleServerList);
    }

    private void onEventLoop(Runnable task) {
        try {
            channel.eventLoop().execute(task);
        } catch (RejectedExecutionException e) {
            // The loop is shutting down and closes this connection with it; there is no list left to send.
        }
    }

    private void scheduleServerList() {
        if (!channel.isActive() || (serverListWait != null)) {
            return;
        }
        final long wait = charactersDeadline - channel.eventLoop().ticker().nanoTime();
        if (charactersPending.isEmpty() || (wait <= 0)) {
            buildServerList();
            return;
        }
        serverListWait = channel.eventLoop().schedule(this::serverListWaitOver, wait, TimeUnit.NANOSECONDS);
    }

    private void serverListWaitOver() {
        if (serverListWait == null) {
            return;
        }
        cancelServerListWait();
        if (channel.isActive()) {
            buildServerList();
        }
    }

    private void cancelServerListWait() {
        if (serverListWait != null) {
            serverListWait.cancel(false);
            serverListWait = null;
        }
    }

    private void buildServerList() {
        execute(() -> sendPacket(new ServerList(this)));
    }

    boolean isServerListWaiting() {
        return serverListWait != null;
    }

    @Override
    public String toString() {
        return account == null
                ? "LoginClient [IP: " + ip + "]"
                : "LoginClient [Account: " + account + " - IP: " + ip + "]";
    }
}
