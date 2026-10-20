/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.forum.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param ownerName read from the owning character; not written
 * @param content {@code null} for a post without text
 */
@Builder(toBuilder = true)
public record PostRow(long id, int topicId, int ownerId, String ownerName, Instant createdAt, String content) {}
