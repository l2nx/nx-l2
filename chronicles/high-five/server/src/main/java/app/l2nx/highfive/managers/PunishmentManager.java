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

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.holders.PunishmentHolder;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.IllegalActionPunishmentType;
import app.l2nx.highfive.entity.actor.tasks.player.IllegalPlayerActionTask;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentTask;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.world.PunishmentRepository;
import app.l2nx.highfive.repository.world.model.Punishment;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Punishment keys are typed by affect: the account id ({@link UUID}) for {@link PunishmentAffect#ACCOUNT}, the
 * character object id ({@link Integer}) for {@link PunishmentAffect#CHARACTER}, the address or hardware id
 * ({@link String}) for {@link PunishmentAffect#IP} and {@link PunishmentAffect#HWID}.
 * @author UnAfraid, Skache
 */
@Slf4j
@Component
public class PunishmentManager {
    private static volatile PunishmentManager instance;

    private final Map<PunishmentAffect, PunishmentHolder> tasks = new ConcurrentHashMap<>();

    private final PunishmentRepository punishmentRepository;
    private final CharacterRepository characterRepository;

    public PunishmentManager(PunishmentRepository punishmentRepository, CharacterRepository characterRepository) {
        this.punishmentRepository = punishmentRepository;
        this.characterRepository = characterRepository;
        instance = this;
    }

    public void load() {
        // Initiate task holders.
        for (PunishmentAffect affect : PunishmentAffect.values()) {
            tasks.put(affect, new PunishmentHolder());
        }

        int initiated = 0;
        int expired = 0;

        // Load punishments.
        try {
            for (Punishment punishment : punishmentRepository.findAll()) {
                final long expirationTime = epochMillisOrZero(punishment.expiration());
                if ((expirationTime > 0) && (ClockBridge.wallMillis() > expirationTime)) // expired task.
                {
                    expired++;
                } else {
                    initiated++;
                    tasks.get(punishment.affect())
                            .addPunishment(new PunishmentTask(
                                    punishment.id(),
                                    keyOf(punishment),
                                    punishment.affect(),
                                    punishment.type(),
                                    expirationTime,
                                    punishment.reason(),
                                    punishment.punishedBy(),
                                    true));
                }
            }
        } catch (DataAccessException e) {
            log.warn("Error while loading punishments: {}", e.getMessage());
        }

        log.info("Loaded {} active and {} expired punishments.", initiated, expired);
    }

    private static Object keyOf(Punishment punishment) {
        return switch (punishment.affect()) {
            case ACCOUNT -> punishment.accountId();
            case CHARACTER -> punishment.characterId();
            case IP -> punishment.ip();
            case HWID -> punishment.hwid();
        };
    }

    /**
     * @throws IllegalArgumentException when the key cannot be one of the affect, an account name for an account
     *     punishment among them
     */
    public static Object normalizeKey(Object key, PunishmentAffect affect) {
        return switch (affect) {
            case ACCOUNT -> key instanceof UUID accountId ? accountId : UUID.fromString(String.valueOf(key));
            case CHARACTER -> key instanceof Integer objectId ? objectId : Integer.valueOf(String.valueOf(key));
            case IP, HWID -> String.valueOf(key);
        };
    }

    public long insert(PunishmentTask task) {
        final Object key = task.getKey();
        final PunishmentAffect affect = task.getAffect();
        return punishmentRepository.insert(Punishment.builder()
                .affect(affect)
                .accountId(affect == PunishmentAffect.ACCOUNT ? (UUID) key : null)
                .characterId(affect == PunishmentAffect.CHARACTER ? (Integer) key : null)
                .ip(affect == PunishmentAffect.IP ? (String) key : null)
                .hwid(affect == PunishmentAffect.HWID ? (String) key : null)
                .type(task.getType())
                .expiration(instantOrNull(task.getExpirationTime()))
                .reason(task.getReason())
                .punishedBy(task.getPunishedBy())
                .build());
    }

    /**
     * @param expiration epoch milliseconds, the time the punishment ended
     */
    public void updateExpiration(long id, long expiration) {
        punishmentRepository.updateExpiration(id, instantOrNull(expiration));
    }

    public Optional<UUID> findAccountId(int objectId) {
        return characterRepository.findAccountId(objectId);
    }

    public void startPunishment(PunishmentTask task) {
        final PunishmentAffect affect = task.getAffect();
        if (task.getType() == PunishmentType.BAN) {
            switch (affect) {
                case ACCOUNT: {
                    LoginService.getInstance().changeAccessLevel((UUID) task.getKey(), -1);
                    break;
                }
                case CHARACTER: {
                    changeCharAccessLevel((Integer) task.getKey(), -1);
                    break;
                }
            }
        }

        tasks.get(affect).addPunishment(task);
    }

    public void stopPunishment(Object key, PunishmentAffect affect, PunishmentType type) {
        final PunishmentTask task = getPunishment(key, affect, type);
        if (task == null) {
            return;
        }

        if (type == PunishmentType.BAN) {
            switch (affect) {
                case ACCOUNT: {
                    LoginService.getInstance().changeAccessLevel((UUID) task.getKey(), 0);
                    break;
                }
                case CHARACTER: {
                    changeCharAccessLevel((Integer) task.getKey(), 0);
                    break;
                }
            }
        }

        tasks.get(affect).stopPunishment(task);
    }

    private void changeCharAccessLevel(int objectId, int lvl) {
        try {
            characterRepository.updateAccessLevel(objectId, lvl);
        } catch (DataAccessException e) {
            log.warn("Error updating access level for character: {}. {}", objectId, e.getMessage());
        }
    }

    public boolean hasPunishment(Object key, PunishmentAffect affect, PunishmentType type) {
        return getPunishment(key, affect, type) != null;
    }

    public long getPunishmentExpiration(Object key, PunishmentAffect affect, PunishmentType type) {
        final PunishmentTask p = getPunishment(key, affect, type);
        return p != null ? p.getExpirationTime() : 0;
    }

    public PunishmentTask getPunishment(Object key, PunishmentAffect affect, PunishmentType type) {
        return tasks.get(affect).getPunishment(String.valueOf(normalizeKey(key, affect)), type);
    }

    public static void handleIllegalPlayerAction(Player actor, String message, IllegalActionPunishmentType punishment) {
        WorldPartition.loop()
                .schedule(new IllegalPlayerActionTask(actor, message, punishment), 5000, TimeUnit.MILLISECONDS);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static PunishmentManager getInstance() {
        final PunishmentManager current = instance;
        if (current == null) {
            throw new IllegalStateException("PunishmentManager is used before the Spring context created it.");
        }
        return current;
    }
}
