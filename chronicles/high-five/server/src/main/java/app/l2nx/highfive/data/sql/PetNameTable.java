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

import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.repository.pet.PetRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PetNameTable {
    private static volatile PetNameTable instance;

    private final PetRepository petRepository;

    public PetNameTable(PetRepository petRepository) {
        this.petRepository = petRepository;
        instance = this;
    }

    public boolean doesPetNameExist(String name, int petNpcId) {
        final int petItemId = PetDataTable.getInstance().getPetItemsByNpc(petNpcId);
        try {
            return petRepository.nameExists(name, petItemId);
        } catch (DataAccessException e) {
            log.warn("Could not check existing pet name: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean isValidPetName(String name) {
        // Only allow alphanumeric names.
        if (!StringUtils.isAlphanumeric(name)) {
            return false;
        }

        return ServerConfig.PET_NAME_TEMPLATE_PATTERN.matcher(name).matches();
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static PetNameTable getInstance() {
        final PetNameTable current = instance;
        if (current == null) {
            throw new IllegalStateException("PetNameTable is used before the Spring context created it.");
        }
        return current;
    }
}
