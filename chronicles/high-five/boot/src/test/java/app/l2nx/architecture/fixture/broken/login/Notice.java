/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.broken.login;

import app.l2nx.architecture.fixture.broken.network.protocol.highfive.serverpackets.Pong;
import java.util.function.Supplier;

public class Notice {
    Pong send() {
        return new Pong();
    }

    Supplier<Pong> later() {
        return new Supplier<>() {
            @Override
            public Pong get() {
                return new Pong();
            }
        };
    }

    static final class Retry {
        Pong resend() {
            return new Pong();
        }
    }
}
