/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.config;

import app.l2nx.login.protocol.LoginVariant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.Builder;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@Builder(toBuilder = true)
@Validated
@ConfigurationProperties("l2nx.login")
public record LoginProperties(
        @NotNull @Valid Client client,
        @NotNull @Valid GameServer gameServer,
        @NotNull @Valid Ban ban,
        @NotNull @Valid FloodProtection floodProtection,
        @NotNull @Valid ScheduledRestart scheduledRestart,
        boolean showLicence,
        boolean autoCreateAccounts,
        @NotNull File datapackRoot) {
    /**
     * @param maxConcurrentTasks blocking packet tasks (RSA, database) running at once across all connections
     */
    @Builder(toBuilder = true)
    public record Client(
            @NotBlank String host,
            @NotEmpty List<@Valid ClientPort> ports,
            @Positive @DefaultValue("32") int maxConcurrentTasks) {
        @AssertTrue(message = "l2nx.login.client.ports must not repeat a port")
        public boolean isPortsDistinct() {
            return (ports == null)
                    || (ports.stream().map(ClientPort::port).distinct().count() == ports.size());
        }
    }

    @Builder(toBuilder = true)
    public record ClientPort(
            @Min(1) @Max(65535) int port, @NotNull LoginVariant variant) {}

    /**
     * @param host {@code *} listens on all interfaces
     * @param acceptNew whether a game server may take a free id that is not registered yet
     */
    @Builder(toBuilder = true)
    public record GameServer(
            @NotBlank String host, @Min(1) @Max(65535) int port, boolean acceptNew) {}

    /**
     * @param duration zero bans the address permanently
     */
    @Builder(toBuilder = true)
    public record Ban(
            @Positive int failedLogins,

            @NotNull @DurationMin(nanos = 0) @DurationUnit(ChronoUnit.SECONDS)
            Duration duration) {}

    @Builder(toBuilder = true)
    public record FloodProtection(
            boolean enabled,
            @Positive int fastConnectionLimit,
            @NotNull Duration normalConnectionTime,
            @NotNull Duration fastConnectionTime,
            @Positive int maxConnectionsPerIp) {}

    @Builder(toBuilder = true)
    public record ScheduledRestart(
            boolean enabled,

            @NotNull @DurationMin(nanos = 0, inclusive = false) @DurationUnit(ChronoUnit.HOURS)
            Duration delay) {}
}
