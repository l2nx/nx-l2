/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.chat.command;

import app.l2nx.engine.core.session.command.Command;
import app.l2nx.highfive.chat.ChatType;
import lombok.Builder;

/**
 * @param type {@code null} when the client named a channel its protocol does not have
 * @param target the whisper's receiver, {@code null} for any other channel
 */
@Builder(toBuilder = true)
public record SendChat(String text, ChatType type, String target) implements Command {}
