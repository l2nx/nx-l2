/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record CharacterTeleportBookmark(int bookmarkId, int x, int y, int z, int icon, String tag, String name) {}
