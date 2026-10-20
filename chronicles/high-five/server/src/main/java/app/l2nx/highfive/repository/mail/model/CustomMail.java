/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.mail.model;

import lombok.Builder;

/**
 * A mail queued by an outside writer for delivery.
 *
 * @param items {@code "templateId count [enchant];..."}, {@code null} for none
 */
@Builder(toBuilder = true)
public record CustomMail(long id, int receiverId, String subject, String content, String items) {}
