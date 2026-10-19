/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.l2nx.commons.util.ConfigReader;
import app.l2nx.network.NetworkSettings;

public class NetworkConfig
{
	private static final String NETWORK_CONFIG_FILE = "./config/Network.ini";
	private static final Pattern BUFFER_POOL_PROPERTY = Pattern.compile("(BufferPool\\.\\w+?\\.)Size", Pattern.CASE_INSENSITIVE);
	
	public static NetworkSettings SETTINGS;
	
	public static void load()
	{
		final ConfigReader config = new ConfigReader(NETWORK_CONFIG_FILE);
		final NetworkSettings defaults = NetworkSettings.defaults();
		final List<NetworkSettings.BufferPool> bufferPools = new ArrayList<>();
		for (String property : config.getStringPropertyNames())
		{
			final Matcher matcher = BUFFER_POOL_PROPERTY.matcher(property);
			if (matcher.matches())
			{
				bufferPools.add(new NetworkSettings.BufferPool(config.getInt(property, 10), config.getInt(matcher.group(1) + "BufferSize", 1024)));
			}
		}
		
		SETTINGS = new NetworkSettings(Duration.ofSeconds(config.getInt("ShutdownWaitTime", (int) defaults.shutdownWaitTime().toSeconds())), config.getInt("ThreadPoolSize", defaults.threadPoolSize()), config.getInt("ThreadPriority", defaults.threadPriority()), config.getBoolean("DropPackets", defaults.dropPackets()), config.getInt("DropPacketThreshold", defaults.dropPacketThreshold()), config.getInt("BufferSegmentSize", defaults.bufferSegmentSize()), config.getBoolean("BufferPool.AutoExpandCapacity", defaults.autoExpandPoolCapacity()), config.getFloat("BufferPool.InitFactor", defaults.initBufferPoolFactor()), bufferPools);
	}
}
