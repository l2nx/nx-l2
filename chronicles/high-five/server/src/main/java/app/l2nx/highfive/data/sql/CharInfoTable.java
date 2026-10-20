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
package app.l2nx.highfive.data.sql;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.model.CharacterAccess;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CharInfoTable {
    private static volatile CharInfoTable instance;

    private final Map<Integer, String> names = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> accessLevels = new ConcurrentHashMap<>();

    private final CharacterRepository characterRepository;

    public CharInfoTable(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
        instance = this;
    }

    public void load() {
        try {
            for (CharacterAccess character : characterRepository.findAllAccess()) {
                cache(character);
            }
        } catch (DataAccessException e) {
            log.warn("Couldn't retrieve all char id/name/access: {}", e.getMessage(), e);
        }

        log.info("Loaded {} char names.", names.size());
    }

    public void addName(Player player) {
        if (player != null) {
            addName(player.getObjectId(), player.getName());
            accessLevels.put(player.getObjectId(), player.getAccessLevel().getLevel());
        }
    }

    private void addName(int objectId, String name) {
        if ((name != null) && !name.equals(names.get(objectId))) {
            names.put(objectId, name);
        }
    }

    public void removeName(int objId) {
        names.remove(objId);
        accessLevels.remove(objId);
    }

    public int getIdByName(String name) {
        if ((name == null) || name.isEmpty()) {
            return -1;
        }

        for (Entry<Integer, String> entry : names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }

        try {
            return characterRepository.findAccessByName(name).map(this::cache).orElse(-1);
        } catch (DataAccessException e) {
            log.warn("Could not check existing char name: {}", e.getMessage(), e);
            return -1;
        }
    }

    public String getNameById(int id) {
        if (id <= 0) {
            return null;
        }

        final String name = names.get(id);
        if (name != null) {
            return name;
        }

        try {
            final Optional<CharacterAccess> character = characterRepository.findAccessById(id);
            character.ifPresent(this::cache);
            return character.map(CharacterAccess::name).orElse(null);
        } catch (DataAccessException e) {
            log.warn("Could not check existing char id: {}", e.getMessage(), e);
            return null;
        }
    }

    public int getAccessLevelById(int objectId) {
        return getNameById(objectId) != null ? accessLevels.get(objectId) : 0;
    }

    public synchronized boolean doesCharNameExist(String name) {
        try {
            return characterRepository.existsByName(name);
        } catch (DataAccessException e) {
            log.warn("Could not check existing charname: {}", e.getMessage(), e);
            return false;
        }
    }

    public int getAccountCharacterCount(UUID accountId) {
        try {
            return characterRepository.countByAccount(accountId);
        } catch (DataAccessException e) {
            log.warn("Couldn't retrieve account for id: {}", e.getMessage(), e);
            return 0;
        }
    }

    private int cache(CharacterAccess character) {
        names.put(character.objectId(), character.name());
        accessLevels.put(character.objectId(), character.accessLevel());
        return character.objectId();
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CharInfoTable getInstance() {
        final CharInfoTable current = instance;
        if (current == null) {
            throw new IllegalStateException("CharInfoTable is used before the Spring context created it.");
        }
        return current;
    }
}
