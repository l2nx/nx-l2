/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.highfive.entity.actor.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Characters loaded into memory, spawned or not. Between selection and world entry the memory already owns the items,
 * so a value transaction must treat such a character as online. Loop-confined.
 */
public final class LoadedCharacters {
    private final Map<UUID, Player> players = new HashMap<>();

    public void register(UUID characterId, Player player) {
        players.put(characterId, player);
    }

    public void release(UUID characterId, Player player) {
        players.remove(characterId, player);
    }

    public Optional<Player> find(UUID characterId) {
        return Optional.ofNullable(players.get(characterId));
    }
}
