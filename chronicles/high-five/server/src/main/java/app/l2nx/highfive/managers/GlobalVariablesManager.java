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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.mechanics.variables.AbstractVariables;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.world.GlobalVariableRepository;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@SingleWriter
public class GlobalVariablesManager extends AbstractVariables {
    // Plain, not volatile: the context creates the bean before the world loop runs its first pass, and handing work
    // to the loop through its inbox publishes the reference.
    private static GlobalVariablesManager instance;

    public static final String DAILY_TASK_RESET = "DAILY_TASK_RESET";

    private final GlobalVariableRepository variableRepository;

    public GlobalVariablesManager(GlobalVariableRepository variableRepository) {
        this.variableRepository = variableRepository;
        instance = this;
    }

    public boolean restoreMe() {
        WorldPartition.checkOwner();
        try {
            variableRepository.findAll().forEach((name, value) -> set(name, value, false));
        } catch (DataAccessException e) {
            log.warn("Couldn't restore global variables.", e);
            return false;
        }

        log.info("Loaded {} variables.", allValues().size());
        return true;
    }

    /**
     * Saves at once instead of scheduling: the callers include shutdown, after which a scheduled save never runs.
     */
    @Override
    public boolean storeMe() {
        WorldPartition.checkOwner();
        return saveNow();
    }

    // Replaces every row, whatever the tracked changes are.
    @Override
    protected void persist(Set<String> deleted, Map<String, String> changed) {
        variableRepository.replaceAll(allValues());
    }

    @Override
    protected String variablesKey() {
        return "global";
    }

    public boolean deleteMe() {
        WorldPartition.checkOwner();
        try {
            variableRepository.deleteAll();
        } catch (DataAccessException e) {
            log.warn("Couldn't delete global variables to database.", e);
            return false;
        }

        return true;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static GlobalVariablesManager getInstance() {
        WorldPartition.checkOwner();
        final GlobalVariablesManager current = instance;
        if (current == null) {
            throw new IllegalStateException("GlobalVariablesManager is used before the Spring context created it.");
        }
        return current;
    }
}
