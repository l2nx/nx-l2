/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import lombok.Builder;

/**
 * @param attackElement {@code null} when the item has no attack attribute; {@code attackElementPower} is then 0
 */
@Builder(toBuilder = true)
public record Elements(
        Integer attackElement, int attackElementPower, int fire, int water, int wind, int earth, int holy, int dark) {}
