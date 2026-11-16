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
        @NotNull @Valid Password password,
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
            @NotEmpty List<@Valid ClientListener> ports,
            @Positive @DefaultValue("32") int maxConcurrentTasks) {
        // Port 0 is a fresh free port for each listener, so only the fixed ones can collide.
        @AssertTrue(message = "l2nx.login.client.ports must not repeat a port")
        public boolean isPortsDistinct() {
            if (ports == null) {
                return true;
            }
            final List<Integer> fixed = ports.stream()
                    .map(ClientListener::port)
                    .filter(port -> port != 0)
                    .toList();
            return fixed.stream().distinct().count() == fixed.size();
        }
    }

    /** @param port {@code 0} binds a free port the system picks; the listener logs the one it bound */
    @Builder(toBuilder = true)
    public record ClientListener(
            @Min(0) @Max(65535) int port, @NotNull LoginVariant variant) {}

    @Builder(toBuilder = true)
    public record GameServer(boolean acceptNew) {}

    /**
     * @param failedLoginsWindow an address's failed-login count is forgotten once this long passes without another
     *     failure
     * @param duration zero bans the address permanently
     */
    @Builder(toBuilder = true)
    public record Ban(
            @Positive int failedLogins,

            @NotNull @DurationMin(nanos = 0, inclusive = false) @DurationUnit(ChronoUnit.SECONDS)
            Duration failedLoginsWindow,

            @NotNull @DurationMin(nanos = 0) @DurationUnit(ChronoUnit.SECONDS)
            Duration duration) {}

    @Builder(toBuilder = true)
    public record Password(@Positive int maxConcurrentHashes) {}

    @Builder(toBuilder = true)
    public record ScheduledRestart(
            boolean enabled,

            @NotNull @DurationMin(nanos = 0, inclusive = false) @DurationUnit(ChronoUnit.HOURS)
            Duration delay) {}
}
