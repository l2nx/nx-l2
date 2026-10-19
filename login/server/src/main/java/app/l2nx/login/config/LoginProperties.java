/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.config;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import app.l2nx.network.config.NetworkSettings;

/**
 * @param client the listener for game clients
 * @param datapackRoot the directory {@code data/} is resolved against
 */
@Validated
@ConfigurationProperties("l2nx.login")
public record LoginProperties(@NotNull @Valid Client client, @NotNull @Valid GameServer gameServer, @NotNull @Valid Ban ban, @NotNull @Valid FloodProtection floodProtection, @NotNull @Valid ScheduledRestart scheduledRestart, boolean showLicence, boolean autoCreateAccounts, @NotNull File datapackRoot, @NotNull NetworkSettings network)
{
	public record Client(@NotBlank String host, @Min(1) @Max(65535) int port)
	{
	}
	
	/**
	 * @param host {@code *} listens on all interfaces
	 * @param acceptNew whether a game server may take a free id that is not registered yet
	 */
	public record GameServer(@NotBlank String host, @Min(1) @Max(65535) int port, boolean acceptNew)
	{
	}
	
	/**
	 * @param failedLogins failed logins from one address before the address is banned
	 * @param duration zero bans the address permanently
	 */
	public record Ban(@Positive int failedLogins, @NotNull @DurationMin(nanos = 0) @DurationUnit(ChronoUnit.SECONDS) Duration duration)
	{
	}
	
	/**
	 * Limits the connection rate of game servers per remote address.
	 */
	public record FloodProtection(boolean enabled, @Positive int fastConnectionLimit, @NotNull Duration normalConnectionTime, @NotNull Duration fastConnectionTime, @Positive int maxConnectionsPerIp)
	{
	}
	
	public record ScheduledRestart(boolean enabled, @NotNull @DurationMin(nanos = 0, inclusive = false) @DurationUnit(ChronoUnit.HOURS) Duration delay)
	{
	}
}
