/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

import lombok.Builder;

@Builder(toBuilder = true)
public record UseSkill(int skillId, int targetObjectId) implements Intent {
    @Override
    public IntentKind kind() {
        return IntentKind.USE_SKILL;
    }
}
