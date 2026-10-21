/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.model;

import lombok.Builder;

/** @param index position in the run, from 0; fixes the ramp offset and the random seed */
@Builder(toBuilder = true)
public record LoadBotIdentity(int index, String account, String password, int characterSlot) {
    public LoadBotIdentity {
        if (index < 0) {
            throw new IllegalArgumentException("Load bot index must not be negative: " + index);
        }
        if (account == null || account.isBlank()) {
            throw new IllegalArgumentException("Load bot " + index + " has no account");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Load bot " + index + " has no password");
        }
        if (characterSlot < 0) {
            throw new IllegalArgumentException("Character slot must not be negative: " + characterSlot);
        }
    }
}
