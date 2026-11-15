/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.variables.AccountVariables;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * One shared {@code AccountVariables} per account with a loaded character. A load pins its account while it reads its
 * snapshot off the loop, so a release saving meanwhile is not shadowed by the stale snapshot. Loop-confined.
 */
public final class LoadedGameAccounts {
    static final Duration SAVE_RETRY = Duration.ofMinutes(1);

    private final Map<UUID, GameAccount> accounts = new HashMap<>();

    /**
     * @param snapshot the variables the player's load read; dropped when the account already has a live instance
     */
    public AccountVariables attach(Player player, UUID accountId, Map<String, String> snapshot) {
        final GameAccount account = accounts.computeIfAbsent(accountId, id -> new GameAccount());
        if (account.variables == null) {
            account.variables = new AccountVariables(accountId, snapshot);
        }
        account.holders.add(player);
        return account.variables;
    }

    /**
     * Saves the variables; the last holder of an unpinned account evicts them once saved. Until then they stay and the
     * save is retried, so the next load does not read the older row.
     */
    public void detach(Player player, UUID accountId) {
        final GameAccount account = accounts.get(accountId);
        if ((account == null) || !account.holders.remove(player)) {
            return;
        }
        account.variables.saveNow();
        dropIfUnused(accountId, account);
    }

    public void pin(UUID accountId) {
        accounts.computeIfAbsent(accountId, id -> new GameAccount()).pins++;
    }

    public void unpin(UUID accountId) {
        final GameAccount account = accounts.get(accountId);
        if ((account == null) || (account.pins == 0)) {
            throw new IllegalStateException("Account " + accountId + " is not pinned");
        }
        account.pins--;
        dropIfUnused(accountId, account);
    }

    public boolean isLoaded(UUID accountId) {
        return accounts.containsKey(accountId);
    }

    private void dropIfUnused(UUID accountId, GameAccount account) {
        if (!account.holders.isEmpty() || (account.pins > 0)) {
            return;
        }
        if ((account.variables != null) && account.variables.hasChanges()) {
            scheduleRetry(accountId, account);
            return;
        }
        accounts.remove(accountId);
    }

    private void scheduleRetry(UUID accountId, GameAccount account) {
        if (account.retryScheduled) {
            return;
        }
        account.retryScheduled = true;
        WorldPartition.loop()
                .schedule(
                        () -> {
                            account.retryScheduled = false;
                            if (accounts.get(accountId) != account) {
                                return;
                            }
                            account.variables.saveNow();
                            dropIfUnused(accountId, account);
                        },
                        SAVE_RETRY.toMillis(),
                        TimeUnit.MILLISECONDS);
    }

    private static final class GameAccount {
        private final Set<Player> holders = Collections.newSetFromMap(new IdentityHashMap<>());
        private AccountVariables variables;
        private int pins;
        private boolean retryScheduled;
    }
}
