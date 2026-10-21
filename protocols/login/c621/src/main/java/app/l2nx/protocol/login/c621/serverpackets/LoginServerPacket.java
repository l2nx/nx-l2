/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.serverpackets;

import io.netty.buffer.ByteBuf;

public sealed interface LoginServerPacket
        permits AccountKicked, GGAuth, Init, LoginFail, LoginOk, PlayFail, PlayOk, ServerList {
    void write(ByteBuf out);
}
