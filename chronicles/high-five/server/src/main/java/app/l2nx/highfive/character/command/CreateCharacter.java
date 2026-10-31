/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import app.l2nx.engine.core.session.command.Command;
import lombok.Builder;

/** The looks are the client's values unchecked: the handler refuses what a High Five character cannot have. */
@Builder(toBuilder = true)
public record CreateCharacter(String name, boolean female, int classId, int hairStyle, int hairColor, int face)
        implements Command {}
