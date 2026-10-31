/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.command;

import app.l2nx.engine.core.session.command.CoalescableCommand;
import app.l2nx.highfive.movement.MoveInput;
import lombok.Builder;

@Builder(toBuilder = true)
public record MoveTo(int targetX, int targetY, int targetZ, int originX, int originY, int originZ, MoveInput input)
        implements CoalescableCommand {}
