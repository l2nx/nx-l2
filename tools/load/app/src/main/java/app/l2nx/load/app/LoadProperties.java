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
        String protocol,
        @Positive int bots,
        @NotNull @DurationMin(nanos = 0) Duration ramp,
        @NotNull @DurationMin(nanos = 0) Duration warmUp,
        @NotNull @DurationMin(nanos = 0) Duration steady,
        @NotNull @DurationMin(nanos = 0) Duration rampDown,
        long seed,
        @Positive Integer eventLoopThreads,
        @NotNull @DurationMin(nanos = 0, inclusive = false) Duration intentTimeout,
        URI prometheusUrl,
        @NotNull @DurationMin(nanos = 0, inclusive = false) Duration scrapeTimeout,
        @NotNull Path outputDir,
        String serverCommit,
        String cpuAffinity,
        @NotNull @Valid Accounts accounts,
        @NotNull @Valid LoginStorm loginStorm,
        @NotNull @Valid InMemory inMemory,
        @NotNull @Valid P267 p267,
        @NotNull @Valid Database database,
        @NotNull @Valid @DefaultValue Gate gate) {

    public LoadProperties {
        scenario = StringUtils.trimToNull(scenario);
        protocol = StringUtils.trimToNull(protocol);
        serverCommit = StringUtils.trimToNull(serverCommit);
        cpuAffinity = StringUtils.trimToNull(cpuAffinity);
    }

    /** Spaces may belong to a password: only a blank one means none, any other is kept as given. */
    private static String normalizedPassword(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    /**
     * The seeded accounts: load bot {@code i} logs in as {@code <prefix><i>} and plays the character in slot 0.
     *
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
     * The database the servers under load run on and its template, for the {@code db-*} commands.
     *
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

        @AssertTrue(message = "l2nx.load.database.schemas must not repeat a schema")
        public boolean isSchemasDistinct() {
            return schemas == null
                    || schemas.stream()
                                    .filter(Objects::nonNull)
                                    .map(Schema::name)
                                    .distinct()
                                    .count()
                            == schemas.size();
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
