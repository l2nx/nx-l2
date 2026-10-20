/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.forum.model;

import java.util.UUID;
import lombok.Builder;

/**
 * A community board forum. Type and permission are the {@code Forum} constants.
 *
 * @param parentId {@code 0} for a root forum
 * @param name {@code null} for an owned (clan, mail, memo) forum
 * @param ownerId the clan id of a clan forum or the character object id of a mail forum, else {@code 0}
 * @param accountId the owning account of a memo forum, else {@code null}
 */
@Builder(toBuilder = true)
public record ForumRow(int id, int parentId, String name, int type, int permission, int ownerId, UUID accountId) {}
