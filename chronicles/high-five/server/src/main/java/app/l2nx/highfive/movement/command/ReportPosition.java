/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.command;

import app.l2nx.engine.core.session.command.Command;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReportPosition(int x, int y, int z, int heading) implements Command {}
