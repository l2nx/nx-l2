/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.olympiad.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param action one of the {@code Hero.ACTION_*} constants
 * @param param the raid boss npc id or the castle id, by action; {@code 0} for a gained hero status
 */
@Builder(toBuilder = true)
public record HeroDiaryEntry(Instant recordedAt, int action, int param) {}
