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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLogin;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLogout;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;
import app.l2nx.highfive.repository.account.AccountPremiumRepository;
import app.l2nx.highfive.repository.character.CharacterPremiumRepository;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.threads.ThreadPool;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Premium status of an account or, with {@code AccountWidePremium=false}, of a single character. Every method names
 * the holder by a character: with an account-wide premium, the character's account holds it.
 *
 * @author Mobius
 */
@Component
@Slf4j
public class PremiumManager {
    private static volatile PremiumManager instance;

    private final AccountPremiumRepository accountPremiumRepository;
    private final CharacterPremiumRepository characterPremiumRepository;
    private final CharacterRepository characterRepository;

    // By the object id of the online character.
    private final Map<Integer, ScheduledFuture<?>> expireTasks = new ConcurrentHashMap<>();

    private final ListenersContainer listenerContainer = Containers.Players();

    private final Consumer<OnPlayerLogin> playerLoginEvent = event -> {
        final Player player = event.player();
        try {
            final long now = ClockBridge.wallMillis();
            final long premiumExpiration = getPremiumExpiration(player.getObjectId());
            player.setPremiumStatus(premiumExpiration > now);
            if (player.hasPremiumStatus()) {
                startExpireTask(player, premiumExpiration - now);
            } else if (premiumExpiration > 0) {
                deletePremium(player.getObjectId());
            }
        } catch (DataAccessException e) {
            log.warn("Could not load the premium status of {}: {}", player.getName(), e.getMessage(), e);
        }
    };

    private final Consumer<OnPlayerLogout> playerLogoutEvent = event -> stopExpireTask(event.player());

    public PremiumManager(
            AccountPremiumRepository accountPremiumRepository,
            CharacterPremiumRepository characterPremiumRepository,
            CharacterRepository characterRepository) {
        this.accountPremiumRepository = accountPremiumRepository;
        this.characterPremiumRepository = characterPremiumRepository;
        this.characterRepository = characterRepository;
        instance = this;
    }

    public void load() {
        listenerContainer.addListener(
                new ConsumerEventListener(listenerContainer, EventType.ON_PLAYER_LOGIN, playerLoginEvent, this));
        listenerContainer.addListener(
                new ConsumerEventListener(listenerContainer, EventType.ON_PLAYER_LOGOUT, playerLogoutEvent, this));
    }

    private void startExpireTask(Player player, long delay) {
        expireTasks.put(player.getObjectId(), ThreadPool.schedule(() -> player.setPremiumStatus(false), delay));
    }

    private void stopExpireTask(Player player) {
        final ScheduledFuture<?> task = expireTasks.remove(player.getObjectId());
        if (task != null) {
            task.cancel(false);
        }
    }

    /**
     * @return when the premium of the character's holder ends, in epoch milliseconds; {@code 0} for none
     */
    public long getPremiumExpiration(int objectId) {
        final Optional<Instant> endsAt = PremiumSystemConfig.ACCOUNT_WIDE_PREMIUM
                ? findAccountId(objectId).flatMap(accountPremiumRepository::findEndsAt)
                : characterPremiumRepository.findEndsAt(objectId);
        return endsAt.map(Instant::toEpochMilli).orElse(0L);
    }

    public void addPremiumTime(int objectId, int timeValue, TimeUnit timeUnit) {
        final long now = ClockBridge.wallMillis();
        final long newPremiumExpiration = Math.max(now, getPremiumExpiration(objectId)) + timeUnit.toMillis(timeValue);
        final Instant endsAt = Instant.ofEpochMilli(newPremiumExpiration);
        if (PremiumSystemConfig.ACCOUNT_WIDE_PREMIUM) {
            accountPremiumRepository.save(requireAccountId(objectId), endsAt);
        } else {
            characterPremiumRepository.save(objectId, endsAt);
        }

        for (Player player : onlineHolders(objectId)) {
            stopExpireTask(player);
            startExpireTask(player, newPremiumExpiration - now);
            player.setPremiumStatus(true);
        }
    }

    public void removePremiumStatus(int objectId) {
        for (Player player : onlineHolders(objectId)) {
            player.setPremiumStatus(false);
            stopExpireTask(player);
        }

        deletePremium(objectId);
    }

    private void deletePremium(int objectId) {
        if (PremiumSystemConfig.ACCOUNT_WIDE_PREMIUM) {
            findAccountId(objectId).ifPresent(accountPremiumRepository::delete);
        } else {
            characterPremiumRepository.delete(objectId);
        }
    }

    private List<Player> onlineHolders(int objectId) {
        if (!PremiumSystemConfig.ACCOUNT_WIDE_PREMIUM) {
            final Player player = World.getPlayer(objectId);
            return player == null ? List.of() : List.of(player);
        }

        return findAccountId(objectId)
                .map(accountId -> World.getPlayers().stream()
                        .filter(player -> accountId.equals(player.getAccountId()))
                        .toList())
                .orElse(List.of());
    }

    private Optional<UUID> findAccountId(int objectId) {
        final Player player = World.getPlayer(objectId);
        return player != null ? Optional.of(player.getAccountId()) : characterRepository.findAccountId(objectId);
    }

    private UUID requireAccountId(int objectId) {
        return findAccountId(objectId)
                .orElseThrow(() -> new IllegalArgumentException("No character with object id " + objectId + "."));
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static PremiumManager getInstance() {
        final PremiumManager current = instance;
        if (current == null) {
            throw new IllegalStateException("PremiumManager is used before the Spring context created it.");
        }
        return current;
    }
}
