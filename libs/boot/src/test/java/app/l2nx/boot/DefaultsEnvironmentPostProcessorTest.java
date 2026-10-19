/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DefaultsEnvironmentPostProcessorTest
{
	@Test
	void applicationKeysOverrideTheDefaults()
	{
		final StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of("server.address", "0.0.0.0")));

		new DefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
		assertThat(environment.getProperty("spring.datasource.hikari.pool-name")).isEqualTo("l2nx-db");
	}
}
