/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.repository.account.AccountVariableRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AccountVariablesManager {
    private static volatile AccountVariablesManager instance;

    private final AccountVariableRepository variableRepository;

    public AccountVariablesManager(AccountVariableRepository variableRepository) {
        this.variableRepository = variableRepository;
        instance = this;
    }

    public Map<String, String> findVariables(UUID accountId, String namePrefix) {
        return variableRepository.findByNamePrefix(accountId, namePrefix);
    }

    public void saveVariables(UUID accountId, Collection<String> deletedNames, Map<String, String> values) {
        variableRepository.saveChanges(accountId, deletedNames, values);
    }

    /**
     * Fails with a duplicate key error when the variable exists.
     */
    public void insertVariable(UUID accountId, String name, String content) {
        variableRepository.insert(accountId, name, content);
    }

    /**
     * @return whether the variable existed
     */
    public boolean updateVariable(UUID accountId, String name, String content) {
        return variableRepository.update(accountId, name, content);
    }

    public void saveVariable(UUID accountId, String name, String content) {
        variableRepository.upsert(accountId, name, content);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static AccountVariablesManager getInstance() {
        final AccountVariablesManager current = instance;
        if (current == null) {
            throw new IllegalStateException("AccountVariablesManager is used before the Spring context created it.");
        }
        return current;
    }
}
