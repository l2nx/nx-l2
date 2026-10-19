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
		public BufferPool
		{
			require(size > 0, "buffer pool size must be positive: " + size);
			require(bufferSize > 0, "buffer pool buffer size must be positive: " + bufferSize);
		}
	}
	
	/**
	 * @throws IllegalArgumentException on a value the transport cannot run with, so a misconfiguration fails at startup
	 */
	public NetworkSettings
	{
		require((shutdownWaitTime != null) && !shutdownWaitTime.isNegative(), "shutdown wait time must not be negative: " + shutdownWaitTime);
		require((threadPriority >= Thread.MIN_PRIORITY) && (threadPriority <= Thread.MAX_PRIORITY), "thread priority must be within " + Thread.MIN_PRIORITY + ".." + Thread.MAX_PRIORITY + ": " + threadPriority);
		require(dropPacketThreshold > 0, "drop packet threshold must be positive: " + dropPacketThreshold);
		require(bufferSegmentSize > 0, "buffer segment size must be positive: " + bufferSegmentSize);
		require(initBufferPoolFactor >= 0, "init buffer pool factor must not be negative: " + initBufferPoolFactor);
		bufferPools = List.copyOf(bufferPools);
	}
	
	public static NetworkSettings defaults()
	{
		return new NetworkSettings(Duration.ofSeconds(5), 2, Thread.NORM_PRIORITY, false, 250, 64, true, 0, List.of());
	}
	
	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new IllegalArgumentException(message);
		}
	}
}
