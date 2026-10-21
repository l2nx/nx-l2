/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.time.fixture;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.random.RandomGenerator;

public final class AllowedTimeAndRandomness {
    List<Object> read(Clock clock, RandomGenerator random, List<?> list) {
        Collections.shuffle(list, random);
        return List.of(
                Instant.now(clock),
                LocalDateTime.now(clock),
                new Date(clock.millis()),
                new SecureRandom(),
                random.nextInt(10));
    }
}
