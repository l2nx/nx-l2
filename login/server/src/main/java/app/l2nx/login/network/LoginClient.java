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
import app.l2nx.login.controller.ScrambledKeyPair;
import app.l2nx.login.controller.SessionKey;
import app.l2nx.login.enums.AccountKickedReason;
import app.l2nx.login.enums.LoginFailReason;
import app.l2nx.login.enums.PlayFailReason;
import app.l2nx.login.network.serverpackets.AccountKicked;
import app.l2nx.login.network.serverpackets.Init;
import app.l2nx.login.network.serverpackets.LoginFail;
import app.l2nx.login.network.serverpackets.LoginServerPacket;
import app.l2nx.login.network.serverpackets.PlayFail;
import app.l2nx.network.Client;
import app.l2nx.network.Connection;
import app.l2nx.network.buffer.ReadBuffer;
import app.l2nx.network.buffer.WriteBuffer;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a client connected to the LoginServer.<br>
 * Holds session state, cryptographic context, account metadata and server lists.
 * <ul>
 * <li>RSA/Blowfish handshake and per-client encryption.</li>
 * <li>Session id generation and access level tracking.</li>
 * <li>Lifecycle hooks for connection/disconnection.</li>
 * </ul>
 * @author KenM, BazookaRpm
 */
public class LoginClient extends Client<Connection<LoginClient>> {
    private static final String DEFAULT_IP = "N/A";
    private static final int DISCONNECT_GRACE_MS = 1000;

    private final LoginEncryption encryption;
    private final ScrambledKeyPair scrambledPair;

    @Getter
    private final byte[] blowfishKey;

    @Getter
    private String ip = DEFAULT_IP;

    @Getter
    private final int sessionId;

    @Getter
    private final long connectionStartTime;

    @Getter
    @Setter
    private String account;

    @Getter
    @Setter
    private UUID accountId;

    @Getter
    @Setter
    private int accessLevel;

    @Getter
    @Setter
    private int lastServer;

    @Getter
    @Setter
    private SessionKey sessionKey;

    @Setter
    private boolean joinedGS;

    @Getter
    @Setter
    private ConnectionState connectionState = ConnectionState.CONNECTED;

    @Getter
    private Map<Integer, Integer> charsOnServ;

    @Getter
    private Map<Integer, long[]> charsWaitingDelOnServ;

    public LoginClient(Connection<LoginClient> connection) {
        super(connection);

        scrambledPair = LoginController.getInstance().getScrambledRSAKeyPair();
        blowfishKey = LoginController.getInstance().getBlowfishKey();
        ip = connection.getRemoteAddress();
        sessionId = LoginController.SECURE_RANDOM.nextInt();
        connectionStartTime = System.currentTimeMillis();

        encryption = new LoginEncryption();
        encryption.setKey(blowfishKey);

        if (LoginController.getInstance().isBannedAddress(ip)) {
            close(LoginFailReason.REASON_NOT_AUTHED);
        }
    }

    @Override
    public boolean encrypt(WriteBuffer data, int offset, int size) {
        return encryption.encrypt(data, offset, size);
    }

    @Override
    public boolean decrypt(ReadBuffer data, int offset, int size) {
        final boolean decrypted = encryption.decrypt(data, offset, size);
        if (!decrypted) {
            close();
        }

        return decrypted;
    }

    @Override
    public void onConnected() {
        sendPacket(new Init(this));
    }

    @Override
    public void onDisconnection() {
        if (!joinedGS) {
            LoginController.getInstance().removeAuthedLoginClient(accountId);
            try {
                Thread.sleep(DISCONNECT_GRACE_MS);
            } catch (InterruptedException e) {
            }
        }
    }

    public byte[] getScrambledModulus() {
        return scrambledPair.getScrambledModulus();
    }

    public RSAPrivateKey getRSAPrivateKey() {
        return (RSAPrivateKey) scrambledPair.getPrivateKey();
    }

    public boolean hasJoinedGS() {
        return joinedGS;
    }

    public void setCharsOnServ(int servId, int chars) {
        if (charsOnServ == null) {
            charsOnServ = new HashMap<>();
        }
        charsOnServ.put(servId, chars);
    }

    public void serCharsWaitingDelOnServ(int servId, long[] charsToDel) {
        if (charsWaitingDelOnServ == null) {
            charsWaitingDelOnServ = new HashMap<>();
        }
        charsWaitingDelOnServ.put(servId, charsToDel);
    }

    public void sendPacket(LoginServerPacket packet) {
        writePacket(packet);
    }

    public void close(LoginFailReason reason) {
        sendPacket(new LoginFail(reason));
    }

    public void close(PlayFailReason reason) {
        close(new PlayFail(reason));
    }

    public void close(AccountKickedReason reason) {
        close(new AccountKicked(reason));
    }

    @Override
    public String toString() {
        final String ip = getHostAddress();
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        if (account != null) {
            sb.append("Account: ");
            sb.append(account);
        }
        if (account != null) {
            sb.append(" - ");
        }
        sb.append("IP: ");
        sb.append(ip);
        sb.append(']');
        return sb.toString();
    }
}
