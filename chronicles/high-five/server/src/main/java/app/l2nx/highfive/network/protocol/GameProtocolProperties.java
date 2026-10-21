/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowLegacyWithMultipleAdapters lets a test profile register a second adapter while legacy routes remain;
 *     production keeps the default {@code false}
 */
@ConfigurationProperties("l2nx.game.protocol")
public record GameProtocolProperties(boolean allowLegacyWithMultipleAdapters) {}
