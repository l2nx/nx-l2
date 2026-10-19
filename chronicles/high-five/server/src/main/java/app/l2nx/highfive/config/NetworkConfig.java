/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.config;

import app.l2nx.network.config.NetworkSettings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkConfig {
    private static final String NETWORK_CONFIG_FILE = "./config/Network.ini";
    private static final Pattern BUFFER_POOL_PROPERTY =
            Pattern.compile("(BufferPool\\.\\w+?\\.)Size", Pattern.CASE_INSENSITIVE);

    public static NetworkSettings SETTINGS;

    public static void load() {
        final ConfigReader config = new ConfigReader(NETWORK_CONFIG_FILE);
        final NetworkSettings defaults = NetworkSettings.defaults();
        final List<NetworkSettings.BufferPool> bufferPools = new ArrayList<>();
        for (String property : config.getStringPropertyNames()) {
            final Matcher matcher = BUFFER_POOL_PROPERTY.matcher(property);
            if (matcher.matches()) {
                bufferPools.add(NetworkSettings.BufferPool.builder()
                        .size(config.getInt(property, 10))
                        .bufferSize(config.getInt(matcher.group(1) + "BufferSize", 1024))
                        .build());
            }
        }

        SETTINGS = NetworkSettings.builder()
                .shutdownWaitTime(Duration.ofSeconds(config.getInt(
                        "ShutdownWaitTime", (int) defaults.shutdownWaitTime().toSeconds())))
                .threadPoolSize(config.getInt("ThreadPoolSize", defaults.threadPoolSize()))
                .threadPriority(config.getInt("ThreadPriority", defaults.threadPriority()))
                .dropPackets(config.getBoolean("DropPackets", defaults.dropPackets()))
                .dropPacketThreshold(config.getInt("DropPacketThreshold", defaults.dropPacketThreshold()))
                .bufferSegmentSize(config.getInt("BufferSegmentSize", defaults.bufferSegmentSize()))
                .autoExpandPoolCapacity(
                        config.getBoolean("BufferPool.AutoExpandCapacity", defaults.autoExpandPoolCapacity()))
                .initBufferPoolFactor(config.getFloat("BufferPool.InitFactor", defaults.initBufferPoolFactor()))
                .bufferPools(bufferPools)
                .build();
    }
}
