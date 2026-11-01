/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record Recipient(UUID id, String name, boolean gm) {}
