/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the {@code run} and {@code gate} commands.
 *
 * @param scenario id of the load scenario {@code run} executes; {@code null} is allowed for {@code gate}, which reads
 *     the scenario from the reports
 * @param repeats runs of the scenario one {@code run} executes, each with its own report; the gate needs three
 * @param eventLoopThreads {@code null} takes every core the process may use
 * @param prometheusUrl the server's {@code /actuator/prometheus}; {@code null} reads no server meters
 * @param scrapeTimeout limit of the connect and of every request to {@code prometheusUrl}
 * @param serverCommit commit of the server under load, {@code null} when not given
 * @param cpuAffinity CPU affinity of the processes as the start script set it, {@code null} when not given
 */
@Builder(toBuilder = true)
@Validated
@ConfigurationProperties("l2nx.load")
public record LoadProperties(
        String scenario,
        @NotBlank String protocol,
        @Positive int bots,
        @NotNull @DurationMin(nanos = 0) Duration ramp,
        @NotNull @DurationMin(nanos = 0) Duration warmUp,
        @NotNull @DurationMin(nanos = 0, inclusive = false) Duration steady,
        @NotNull @DurationMin(nanos = 0) Duration rampDown,
        long seed,
        @Positive int repeats,
        @Positive Integer eventLoopThreads,
        @NotNull @DurationMin(nanos = 0, inclusive = false) Duration intentTimeout,
        URI prometheusUrl,
        @NotNull @DurationMin(nanos = 0, inclusive = false) Duration scrapeTimeout,
        @NotNull Path outputDir,
        String serverCommit,
        String cpuAffinity,
        @NotNull @Valid Accounts accounts,
        @NotNull @Valid InMemory inMemory,
        @NotNull @Valid @DefaultValue Gate gate) {

    public LoadProperties {
        scenario = StringUtils.trimToNull(scenario);
        serverCommit = StringUtils.trimToNull(serverCommit);
        cpuAffinity = StringUtils.trimToNull(cpuAffinity);
    }

    /**
     * The seeded accounts: load bot {@code i} logs in as {@code <prefix><i>} and plays the character in slot 0.
     *
     * @param password the password every seeded account shares
     */
    @Builder(toBuilder = true)
    public record Accounts(
            @NotBlank String prefix, @NotEmpty String password) {}

    /**
     * The {@code in-memory} protocol, a server stand-in without a network.
     *
     * @param stepDelay time of every lifecycle step from the connect to {@code IN_GAME} and of the logout
     * @param ackLatency time from an intent of a load scenario to its acknowledgement
     */
    @Builder(toBuilder = true)
    public record InMemory(
            @NotNull @DurationMin(nanos = 0) Duration stepDelay,
            @NotNull @DurationMin(nanos = 0) Duration ackLatency) {}

    /**
     * Overrides of the gate thresholds; {@code null} keeps the threshold's built-in default.
     *
     * @param runs valid runs the gate needs
     * @param passBudget limit of the engine pass p99 of every loop
     * @param maxOverBudgetShare limit of the passes over budget over all passes of a loop, a fraction
     * @param maxClientP99Ratio limit of the client p99 of an intent kind over its baseline p99
     * @param maxTimeoutAndErrorShare limit of the timed-out intents and of the stopped load bots, each a fraction
     */
    @Builder(toBuilder = true)
    public record Gate(
            @Positive Integer runs,
            @DurationMin(nanos = 0, inclusive = false) Duration passBudget,
            @DecimalMin("0") @DecimalMax("1") Double maxOverBudgetShare,
            @DecimalMin(value = "0", inclusive = false) Double maxClientP99Ratio,
            @DecimalMin("0") @DecimalMax("1") Double maxTimeoutAndErrorShare) {}
}
