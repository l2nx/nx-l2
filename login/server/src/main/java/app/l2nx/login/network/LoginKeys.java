/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.network;

import app.l2nx.login.controller.ScrambledKeyPair;
import lombok.Builder;

@Builder(toBuilder = true)
public record LoginKeys(ScrambledKeyPair rsa, byte[] blowfishKey) {}
