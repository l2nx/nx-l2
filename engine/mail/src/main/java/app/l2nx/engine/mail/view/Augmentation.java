/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import lombok.Builder;

@Builder(toBuilder = true)
public record Augmentation(int option1, int option2) {}
