/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record BufferScheme(int characterId, String name, List<Integer> skillIds) {}
