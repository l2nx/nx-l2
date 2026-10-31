/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import app.l2nx.engine.core.session.command.Command;
import app.l2nx.highfive.session.SessionKey;
import lombok.Builder;

@Builder(toBuilder = true)
public record Authenticate(String login, SessionKey key) implements Command {}
