/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.boot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import app.l2nx.login.config.LoginProperties;

/**
 * Binds {@link LoginProperties} from the shipped {@code application.yaml}.
 */
class LoginPropertiesTest
{
	private final ApplicationContextRunner runner = new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(PropertiesConfiguration.class);

	@Nested
	class Bind
	{
		@Test
		void bind_shouldStart_withShippedDefaults()
		{
			runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(LoginProperties.class));
		}

		@ParameterizedTest(name = "{0}")
		@ValueSource(strings =
		{
			"l2nx.login.client.host=",
			"l2nx.login.client.port=0",
			"l2nx.login.client.port=65536",
			"l2nx.login.game-server.port=0",
			"l2nx.login.ban.failed-logins=0",
			"l2nx.login.ban.duration=-1s",
			"l2nx.login.flood-protection.max-connections-per-ip=0",
			"l2nx.login.scheduled-restart.delay=0"
		})
		void bind_shouldFailStartup_whenValueIsInvalid(String property)
		{
			runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed().getFailure().rootCause().isInstanceOf(BindValidationException.class));
		}

		@ParameterizedTest(name = "{0}")
		@ValueSource(strings =
		{
			"l2nx.login.network.shutdown-wait-time=-1s",
			"l2nx.login.network.thread-priority=0",
			"l2nx.login.network.thread-priority=11",
			"l2nx.login.network.drop-packet-threshold=0",
			"l2nx.login.network.buffer-segment-size=0",
			"l2nx.login.network.init-buffer-pool-factor=-1",
			"l2nx.login.network.buffer-pools[0].size=0"
		})
		void bind_shouldFailStartup_whenNetworkValueIsInvalid(String property)
		{
			runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed().getFailure().rootCause().isInstanceOf(IllegalArgumentException.class));
		}
	}

	@EnableConfigurationProperties(LoginProperties.class)
	static class PropertiesConfiguration
	{
	}
}
