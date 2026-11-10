/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.managers.IdManager;
import java.util.Optional;
import java.util.UUID;

/**
 * A character being read, or built and not yet in the world, is neither online nor offline: the writer's decision
 * waits until it entered the world or left memory.
 */
public final class OfflineRows {
    private OfflineRows() {}

    public static void whenSettled(int characterHandle, Runnable decision) {
        final Optional<UUID> characterId = IdManager.getInstance().findId(characterHandle);
        if (characterId.isEmpty()) {
            decision.run();
            return;
        }
        WorldPartition.loadedCharacters().whenSettled(characterId.get(), decision);
    }

    public static void whenSettled(String characterName, Runnable decision) {
        final int handle = CharInfoTable.getInstance().getIdByName(characterName);
        if (handle < 0) {
            decision.run();
            return;
        }
        whenSettled(handle, decision);
    }

    public static boolean isLoaded(int characterHandle) {
        if (World.getPlayer(characterHandle) != null) {
            return true;
        }
        return IdManager.getInstance()
                .findId(characterHandle)
                .map(id -> WorldPartition.loadedCharacters().isBusy(id)
                        || WorldPartition.loadedCharacters().find(id).isPresent())
                .orElse(false);
    }

    public static boolean isBusy(int characterHandle) {
        return IdManager.getInstance()
                .findId(characterHandle)
                .map(WorldPartition.loadedCharacters()::isBusy)
                .orElse(false);
    }
}
