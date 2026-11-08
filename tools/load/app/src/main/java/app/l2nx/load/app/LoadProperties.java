/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import app.l2nx.load.app.database.DatabaseCommands;
import app.l2nx.load.bot.model.LoadBotState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param scenario id of the load scenario {@code run} executes; {@code null} is allowed for {@code gate}, which reads
 *     the scenario from the reports
 * @param protocol id of the client protocol {@code run} and {@code seed} connect with; {@code null} is allowed for the
 *     commands that connect no load bot
 * @param eventLoopThreads {@code null} takes every core the process may use
 * @param lifecycleTimeout a load bot that stays this long in a state before its final state stops on an error; not
 *     below {@code intentTimeout}
 * @param maxGeneratorLagP99 a run with more generator lag is invalid
 * @param serverCommit commit of the server under load, {@code null} when not given
 * @param cpuAffinity CPU affinity of the processes as the start script set it, {@code null} when not given
 */
@Builder(toBuilder = true)
@Validated
@ConfigurationProperties("l2nx.load")
public record LoadProperties(
        String scenario,
        String protocol,
        @Positive int bots,
        @NotNull @DurationMin(nanos = 0) Duration ramp,
        @NotNull @DurationMin(nanos = 0) Duration warmUp,
        @NotNull @DurationMin(nanos = 0) Duration steady,
        @NotNull @DurationMin(nanos = 0) Duration rampDown,
        long seed,
        @Positive Integer eventLoopThreads,
        @NotNull @DurationMin(nanos = 0, inclusive = false) Duration intentTimeout,
        @NotNull @DurationMin(nanos = 0, inclusive = false) Duration lifecycleTimeout,
        @NotNull @DurationMin(nanos = 0, inclusive = false) Duration maxGeneratorLagP99,
        @NotNull Path outputDir,
        String serverCommit,
        String cpuAffinity,
        @NotNull @Valid Accounts accounts,
        @NotNull @Valid LoginStorm loginStorm,
        @NotNull @Valid InMemory inMemory,
        @NotNull @Valid P267 p267,
        @NotNull @Valid Prometheus prometheus,
        @NotNull @Valid Database database,
        @NotNull @Valid @DefaultValue Gate gate,
        @NotNull @Valid @DefaultValue Levels levels) {

    public LoadProperties {
        scenario = StringUtils.trimToNull(scenario);
        protocol = StringUtils.trimToNull(protocol);
        serverCommit = StringUtils.trimToNull(serverCommit);
        cpuAffinity = StringUtils.trimToNull(cpuAffinity);
    }

    private static boolean distinct(List<String> values) {
        final List<String> present = values.stream().filter(Objects::nonNull).toList();
        return present.stream().distinct().count() == present.size();
    }

    /** Spaces may belong to a password: only a blank one means none, any other is kept as given. */
    private static String normalizedPassword(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    /**
     * @param prefix lowercase ASCII, so an account name takes one byte per character on the wire
     * @param password the password every seeded account shares, as given
     */
    @Builder(toBuilder = true)
    public record Accounts(
            @NotNull @Pattern(regexp = "[a-z][a-z0-9]*") String prefix,
            @NotNull String password) {
        public Accounts {
            prefix = StringUtils.trimToNull(prefix);
            password = normalizedPassword(password);
        }
    }

    public record LoginStorm(@NotNull LoadBotState finalState) {
        @AssertTrue(message = "l2nx.load.login-storm.final-state must lie after LOGIN_CONNECT and up to IN_GAME")
        public boolean isFinalStateValid() {
            return finalState == null || finalState.canBeFinal();
        }
    }

    /**
     * @param stepDelay time of every lifecycle step from the connect to {@code IN_GAME} and of the logout
     * @param ackLatency time from an intent of a load scenario to its acknowledgement
     */
    @Builder(toBuilder = true)
    public record InMemory(
            @NotNull @DurationMin(nanos = 0) Duration stepDelay,
            @NotNull @DurationMin(nanos = 0) Duration ackLatency) {}

    /**
     * @param serverId one byte on the wire
     * @param connectTimeout limit of every connect, to the login server and to the game server
     */
    @Builder(toBuilder = true)
    public record P267(
            @NotBlank String loginHost,
            @Min(1) @Max(65535) int loginPort,
            @Min(1) @Max(255) int serverId,

            @NotNull @DurationMin(nanos = 0, inclusive = false)
            Duration connectTimeout) {}

    /**
     * Read by every {@code run} except on a protocol without a server ({@code ClientProtocol.hasServer}).
     *
     * @param targets the scrape jobs of {@code docker/prometheus/prometheus.yml}; each must be up before a run starts
     * @param maxClockSkew limit of the difference between the harness's clock and Prometheus's, checked before a run
     *     starts: the window is taken on the one and evaluated on the other
     */
    @Builder(toBuilder = true)
    public record Prometheus(
            @NotNull URI url,
            @NotEmpty List<@NotNull @Valid Target> targets,
            @NotNull @DurationMin(nanos = 0) Duration maxClockSkew) {
        // A null target or job is reported by its own constraint.
        @AssertTrue(message = "l2nx.load.prometheus.targets must not repeat a job")
        public boolean isJobsDistinct() {
            return targets == null
                    || distinct(targets.stream()
                            .filter(Objects::nonNull)
                            .map(Target::job)
                            .toList());
        }
    }

    /**
     * @param job the job name in the Prometheus config; a PromQL label value, so a plain identifier
     * @param metricsUrl the server's actuator endpoint as the harness reaches it, for the raw window snapshots
     */
    @Builder(toBuilder = true)
    public record Target(
            @NotNull @Pattern(regexp = "[a-z][a-z0-9_]*") String job,
            @NotNull URI metricsUrl) {
        public Target {
            job = StringUtils.trimToNull(job);
        }
    }

    /**
     * @param adminUrl JDBC URL of a database other than {@code name} and {@code template} to run the commands from, such
     *     as the maintenance database {@code postgres}
     * @param user a role allowed to create databases; {@code null} takes the URL's or the driver's default
     * @param password {@code null} takes the URL's or none
     * @param schemas created in a new database, one per server, each owned by the server's role
     */
    @Builder(toBuilder = true)
    public record Database(
            @NotBlank String adminUrl,
            String user,
            String password,

            @NotNull @Pattern(regexp = DatabaseCommands.IDENTIFIER)
            String name,

            @NotNull @Pattern(regexp = DatabaseCommands.TEMPLATE_IDENTIFIER)
            String template,

            @NotEmpty List<@NotNull @Valid Schema> schemas) {
        public Database {
            adminUrl = StringUtils.trimToNull(adminUrl);
            user = StringUtils.trimToNull(user);
            password = normalizedPassword(password);
            name = StringUtils.trimToNull(name);
            template = StringUtils.trimToNull(template);
        }

        @AssertTrue(
                message = "l2nx.load.database.name must differ from l2nx.load.database.template and from the"
                        + " template's staging and retired copies that db-snapshot makes")
        public boolean isNameDistinct() {
            // A template that does not match its pattern is reported by the pattern.
            return name == null
                    || template == null
                    || !template.matches(DatabaseCommands.TEMPLATE_IDENTIFIER)
                    || !DatabaseCommands.TemplateNames.of(template).contains(name);
        }

        // A null schema or name is reported by its own constraint.
        @AssertTrue(message = "l2nx.load.database.schemas must not repeat a schema")
        public boolean isSchemasDistinct() {
            return schemas == null
                    || distinct(schemas.stream()
                            .filter(Objects::nonNull)
                            .map(Schema::name)
                            .toList());
        }
    }

    /** @param owner the server's role; it exists already, as roles belong to the whole cluster */
    @Builder(toBuilder = true)
    public record Schema(
            @NotNull @Pattern(regexp = DatabaseCommands.IDENTIFIER)
            String name,

            @NotNull @Pattern(regexp = DatabaseCommands.IDENTIFIER)
            String owner) {
        public Schema {
            name = StringUtils.trimToNull(name);
            owner = StringUtils.trimToNull(owner);
        }
    }

    /**
     * Overrides of the gate thresholds; {@code null} keeps the threshold's built-in default.
     *
     * @param runs valid runs the gate needs
     * @param passBudget limit of the engine pass p99 of every loop
     * @param maxOverBudgetShare limit of the passes over budget over all passes of a loop, a fraction
     * @param maxInputP99Ratio limit of the input time p99 of an input class over its baseline p99
     * @param inputP99Floor added to the baseline p99 before the comparison
     * @param maxStepP99Ratio limit of the client p99 of a lifecycle step over its baseline p99
     * @param stepP99Floor added to the baseline p99 of a lifecycle step before the comparison
     * @param maxTimeoutAndErrorShare limit of the timed-out intents, of the rejected intents and of the stopped load
     *     bots, each a fraction
     * @param maxNotAtFinalStateShare limit of the load bots that did not reach the final state, a fraction
     */
    @Builder(toBuilder = true)
    public record Gate(
            @Positive Integer runs,
            @DurationMin(nanos = 0, inclusive = false) Duration passBudget,
            @DecimalMin("0") @DecimalMax("1") Double maxOverBudgetShare,
            @DecimalMin(value = "0", inclusive = false) Double maxInputP99Ratio,
            @DurationMin(nanos = 0) Duration inputP99Floor,
            @DecimalMin(value = "0", inclusive = false) Double maxStepP99Ratio,
            @DurationMin(nanos = 0) Duration stepP99Floor,
            @DecimalMin("0") @DecimalMax("1") Double maxTimeoutAndErrorShare,
            @DecimalMin("0") @DecimalMax("1") Double maxNotAtFinalStateShare) {}

    /**
     * Levels of a capacity run; without {@code increment} the run injects {@code bots} once.
     *
     * @param count levels, the first ({@code bots}) included
     * @param duration of each level; its server window is the duration minus {@code warm-up}
     */
    @Builder(toBuilder = true)
    public record Levels(
            @Positive Integer increment,
            @Positive Integer count,
            @DurationMin(nanos = 0, inclusive = false) Duration duration,

            @NotNull @DurationMin(nanos = 0) @DefaultValue("0s")
            Duration ramp) {
        @AssertTrue(message = "l2nx.load.levels.increment, .count and .duration go together")
        public boolean isComplete() {
            return (increment == null) == (count == null) && (increment == null) == (duration == null);
        }
    }
}
