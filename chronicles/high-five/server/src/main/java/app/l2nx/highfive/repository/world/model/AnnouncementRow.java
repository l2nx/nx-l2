/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world.model;

import app.l2nx.highfive.mechanics.announce.AnnouncementType;
import lombok.Builder;

/**
 * Initial delay, interval and repeat apply to the {@code AUTO_*} types only and read {@code 0}, {@code 0}, {@code -1}
 * for the others.
 *
 * @param repeat broadcasts left, {@code -1} for forever
 */
@Builder(toBuilder = true)
public record AnnouncementRow(
        int id,
        AnnouncementType type,
        String content,
        String author,
        long initialDelayMs,
        long intervalMs,
        int repeat) {}
