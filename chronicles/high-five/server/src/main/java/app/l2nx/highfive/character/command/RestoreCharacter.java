/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import app.l2nx.engine.core.session.command.Command;

public record RestoreCharacter(int slot) implements Command {}
