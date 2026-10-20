/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.broken.network;

import app.l2nx.architecture.fixture.broken.login.system.AccountRepository;

public class AccountAdapter {
    private final AccountRepository repository = new AccountRepository();
}
