/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.ai.system;

import app.l2nx.engine.core.loop.Durations;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Cadences of the AI systems; the defaults are the Mobius task periods. */
@ConfigurationProperties("l2nx.ai")
public record AiSystemProperties(@DefaultValue("1s") Duration thinkCadence) {
    public AiSystemProperties {
        Durations.requireWholeMillis("l2nx.ai.think-cadence", thinkCadence);
    }
}
