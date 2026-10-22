/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import app.l2nx.load.protocol.ClientProtocol;
import java.util.function.Supplier;
import lombok.Builder;

/**
 * A client protocol selected by id and built only when a command selects it: building may resolve a host, which
 * commands using another protocol must not wait for.
 */
@Builder(toBuilder = true)
public record ClientProtocolFactory(String id, Supplier<ClientProtocol> create) {}
