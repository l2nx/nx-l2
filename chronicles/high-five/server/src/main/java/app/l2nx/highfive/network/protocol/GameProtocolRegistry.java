/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps the protocol version a client opens with to its adapter. A legacy route serves the game the way one client
 * protocol expects it, so a second protocol cannot be offered until every legacy route is gone.
 */
@Slf4j
@Component
public final class GameProtocolRegistry {
    private static final int MAX_VERSION = 0xFFFF;

    private final List<GameProtocolAdapter> adapters;

    public GameProtocolRegistry(List<GameProtocolAdapter> adapters, GameProtocolProperties properties) {
        if (adapters.isEmpty()) {
            throw new IllegalStateException("No game protocol adapter is registered");
        }
        if ((adapters.size() > 1) && !properties.allowLegacyWithMultipleAdapters()) {
            final int legacy = adapters.stream()
                    .mapToInt(GameProtocolAdapter::legacyInboundCount)
                    .sum();
            if (legacy > 0) {
                throw new IllegalStateException("The server refuses a second protocol adapter while the adapters have "
                        + legacy + " legacy inbound routes: "
                        + adapters.stream().map(GameProtocolAdapter::id).toList());
            }
        }
        for (int version = 0; version <= MAX_VERSION; version++) {
            final int v = version;
            final List<String> owners = adapters.stream()
                    .filter(a -> a.accepts(v))
                    .map(GameProtocolAdapter::id)
                    .toList();
            if (owners.size() > 1) {
                throw new IllegalStateException("Protocol version " + v + " is accepted by " + owners);
            }
        }
        this.adapters = List.copyOf(adapters);
        log.info(
                "Game protocol adapters: {}",
                this.adapters.stream().map(GameProtocolAdapter::id).toList());
    }

    public Optional<GameProtocolAdapter> find(int version) {
        return adapters.stream().filter(adapter -> adapter.accepts(version)).findFirst();
    }

    public List<GameProtocolAdapter> adapters() {
        return adapters;
    }
}
