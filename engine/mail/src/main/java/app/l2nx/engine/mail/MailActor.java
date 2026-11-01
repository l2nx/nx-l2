/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.core.session.outbound.ClientPort;
import java.util.UUID;
import lombok.Builder;

/** The game context of a mail command: the character in the world who acts, or an online character to notify. */
@Builder(toBuilder = true)
public record MailActor(int characterHandle, UUID characterId, String name, ClientPort port) {}
