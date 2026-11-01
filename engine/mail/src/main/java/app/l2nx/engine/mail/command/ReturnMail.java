/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import app.l2nx.engine.core.session.command.Command;
import java.util.UUID;

public record ReturnMail(UUID mailId) implements Command {}
