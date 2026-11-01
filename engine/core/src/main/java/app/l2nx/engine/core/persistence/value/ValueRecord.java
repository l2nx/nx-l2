/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record ValueRecord(
        UUID transactionId, UUID idempotencyKey, ValueTransactionType type, UUID subjectId, Instant committedAt) {}
