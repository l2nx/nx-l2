/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling makes Spring Boot auto-configure the TaskScheduler the login server schedules its tasks on.
@SpringBootApplication(scanBasePackages = "app.l2nx.login")
@ConfigurationPropertiesScan("app.l2nx.login")
@EnableScheduling
public class LoginApplication
{
	public static void main(String[] args)
	{
		SpringApplication.run(LoginApplication.class, args);
	}
}
