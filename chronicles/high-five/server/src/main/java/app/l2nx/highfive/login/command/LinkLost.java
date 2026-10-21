/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
package app.l2nx.highfive.login.command;

import app.l2nx.engine.core.session.command.Command;

/** The link to the login server ended while this server runs: no request sent on it will be answered. */
public record LinkLost() implements Command {}
