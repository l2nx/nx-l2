/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.wellformed.login;

import app.l2nx.architecture.fixture.wellformed.login.repository.AccountRepository;

public class Accounts {
    private final AccountRepository repository = new AccountRepository();
}
