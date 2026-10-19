/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.config;

import java.io.File;
import java.time.Duration;

import app.l2nx.network.config.NetworkSettings;

public final class TestLoginProperties
{
	private static final String LOOPBACK = "127.0.0.1";
	
	private TestLoginProperties()
	{
	}
	
	public static LoginProperties create(int clientPort, int gameServerPort, LoginProperties.ScheduledRestart scheduledRestart, File datapackRoot)
	{
		return new LoginProperties(new LoginProperties.Client(LOOPBACK, clientPort), new LoginProperties.GameServer(LOOPBACK, gameServerPort, true), new LoginProperties.Ban(5, Duration.ofMinutes(15)), new LoginProperties.FloodProtection(true, 15, Duration.ofMillis(700), Duration.ofMillis(350), 50), scheduledRestart, true, true, datapackRoot, NetworkSettings.defaults());
	}
	
	public static LoginProperties create(int clientPort, int gameServerPort, LoginProperties.ScheduledRestart scheduledRestart)
	{
		return create(clientPort, gameServerPort, scheduledRestart, new File("."));
	}
	
	public static LoginProperties create(File datapackRoot)
	{
		return create(2106, 9014, noRestart(), datapackRoot);
	}
	
	public static LoginProperties.ScheduledRestart noRestart()
	{
		return new LoginProperties.ScheduledRestart(false, Duration.ofHours(24));
	}
}
