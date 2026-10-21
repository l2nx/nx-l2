/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.injection;

import java.time.Duration;
import lombok.Builder;

/**
 * How load bots enter a run: {@code bots} start evenly over {@code ramp}, the warm-up is not reported, the steady
 * window is measured, then the load bots leave over {@code rampDown}.
 */
@Builder(toBuilder = true)
public record InjectionProfile(int bots, Duration ramp, Duration warmUp, Duration steady, Duration rampDown) {}
