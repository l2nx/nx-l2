/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item.model;

import lombok.Builder;

/**
 * @param element an {@code Elementals} code, FIRE (0) to DARK (5)
 */
@Builder(toBuilder = true)
public record ItemElemental(byte element, int value) {}
