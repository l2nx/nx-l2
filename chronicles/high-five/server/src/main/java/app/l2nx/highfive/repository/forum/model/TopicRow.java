/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.forum.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param ownerName read from the owning character; not written
 */
@Builder(toBuilder = true)
public record TopicRow(int id, int forumId, String title, Instant createdAt, int ownerId, String ownerName, int type) {}
