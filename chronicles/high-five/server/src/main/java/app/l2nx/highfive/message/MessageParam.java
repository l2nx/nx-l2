/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.message;

import lombok.Builder;

/** Typed message parameters: the adapter decides how its client renders each kind. */
public sealed interface MessageParam {
    record Text(String value) implements MessageParam {}

    record Numeric(long value) implements MessageParam {}

    record PlayerName(String name) implements MessageParam {}

    record NpcTemplate(int templateId) implements MessageParam {}

    record ItemTemplate(int templateId) implements MessageParam {}

    @Builder(toBuilder = true)
    record Skill(int id, int level) implements MessageParam {}
}
