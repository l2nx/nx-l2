/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.transport;

import app.l2nx.network.config.Transport;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.SocketChannel;
import lombok.Builder;

@Builder(toBuilder = true)
public record ResolvedTransport(
        Transport transport,
        IoHandlerFactory ioHandlerFactory,
        Class<? extends ServerChannel> serverChannelClass,
        Class<? extends SocketChannel> socketChannelClass) {}
