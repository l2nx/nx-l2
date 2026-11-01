/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import lombok.Builder;

@Builder(toBuilder = true)
public record NewItem(int itemTemplateId, long count, int enchantLevel) {
    public NewItem {
        if (count < 1) {
            throw new IllegalArgumentException("A new item needs a positive count: " + count);
        }
    }
}
