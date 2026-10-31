/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.message.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.MessageParam;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record MessageShown(MessageKey key, List<MessageParam> params) implements ViewEvent {
    public MessageShown {
        params = List.copyOf(params);
    }

    public static MessageShown of(MessageKey key, MessageParam... params) {
        return new MessageShown(key, List.of(params));
    }
}
