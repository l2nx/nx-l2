/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.config;

import java.time.Duration;
import java.util.List;

/**
 * @param threadPoolSize below 1 means four threads per processor
 * @param bufferPools pools added on top of the header, segment and minimum pools
 */
public record NetworkSettings(Duration shutdownWaitTime, int threadPoolSize, int threadPriority, boolean dropPackets, int dropPacketThreshold, int bufferSegmentSize, boolean autoExpandPoolCapacity, float initBufferPoolFactor, List<BufferPool> bufferPools)
{
	public record BufferPool(int size, int bufferSize)
	{
	}
	
	public NetworkSettings
	{
		bufferPools = List.copyOf(bufferPools);
	}
	
	public static NetworkSettings defaults()
	{
		return new NetworkSettings(Duration.ofSeconds(5), 2, Thread.NORM_PRIORITY, false, 250, 64, true, 0, List.of());
	}
}
