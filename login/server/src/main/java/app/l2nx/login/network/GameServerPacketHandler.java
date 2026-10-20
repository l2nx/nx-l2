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

import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.network.gameserverpackets.receive.BlowFishKey;
import app.l2nx.login.network.gameserverpackets.receive.ChangeAccessLevel;
import app.l2nx.login.network.gameserverpackets.receive.ChangePassword;
import app.l2nx.login.network.gameserverpackets.receive.GameServerAuth;
import app.l2nx.login.network.gameserverpackets.receive.PlayerAuthRequest;
import app.l2nx.login.network.gameserverpackets.receive.PlayerInGame;
import app.l2nx.login.network.gameserverpackets.receive.PlayerLogout;
import app.l2nx.login.network.gameserverpackets.receive.PlayerTracert;
import app.l2nx.login.network.gameserverpackets.receive.ReplyCharacters;
import app.l2nx.login.network.gameserverpackets.receive.RequestTempBan;
import app.l2nx.login.network.gameserverpackets.receive.ServerStatus;
import app.l2nx.login.network.gameserverpackets.send.LoginServerFail;
import app.l2nx.network.packet.SimpleReadablePacket;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles routing of packets received from game server connections.
 * @author BazookaRpm
 */
@Slf4j
public class GameServerPacketHandler {

    public enum GameServerState {
        CONNECTED,
        BF_CONNECTED,
        AUTHED
    }

    private GameServerPacketHandler() {}

    public static SimpleReadablePacket handle(byte[] data, GameServerLink server) {
        if ((data == null) || (data.length == 0)) {
            log.warn("Received empty packet from {}", server);
            server.forceClose(LoginServerFail.NOT_AUTHED);
            return null;
        }

        final int opcode = data[0] & 0xFF;
        final GameServerState state = server.getLoginConnectionState();
        switch (state) {
            case CONNECTED: {
                switch (opcode) {
                    case 0x00: {
                        return new BlowFishKey(data, server);
                    }
                    default: {
                        logInvalidOpcode(opcode, state, server);
                        return null;
                    }
                }
            }
            case BF_CONNECTED: {
                switch (opcode) {
                    case 0x01: {
                        return new GameServerAuth(data, server);
                    }
                    default: {
                        logInvalidOpcode(opcode, state, server);
                        return null;
                    }
                }
            }
            case AUTHED: {
                switch (opcode) {
                    case 0x02: {
                        return new PlayerInGame(data, server);
                    }
                    case 0x03: {
                        return new PlayerLogout(data, server);
                    }
                    case 0x04: {
                        return new ChangeAccessLevel(data, server);
                    }
                    case 0x05: {
                        return new PlayerAuthRequest(data, server);
                    }
                    case 0x06: {
                        return new ServerStatus(data, server);
                    }
                    case 0x07: {
                        return new PlayerTracert(data, server);
                    }
                    case 0x08: {
                        return new ReplyCharacters(data, server);
                    }
                    case 0x09: // RequestSendMail (not implemented)
                    {
                        return null;
                    }
                    case 0x0A: {
                        return new RequestTempBan(data, server);
                    }
                    case 0x0B: {
                        new ChangePassword(data, server);
                        return null;
                    }
                    default: {
                        logInvalidOpcode(opcode, state, server);
                        return null;
                    }
                }
            }
            default: {
                log.warn("Unknown state {} from {}", state, server);
                server.forceClose(LoginServerFail.NOT_AUTHED);
                return null;
            }
        }
    }

    private static void logInvalidOpcode(int opcode, GameServerState state, GameServerLink server) {
        log.warn(
                "Unknown opcode ({}) in state {} from {}, closing connection.",
                Integer.toHexString(opcode).toUpperCase(),
                state,
                server);
        server.forceClose(LoginServerFail.NOT_AUTHED);
    }
}
