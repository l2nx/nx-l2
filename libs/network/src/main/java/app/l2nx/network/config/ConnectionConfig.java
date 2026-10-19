/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.network.config;

import java.net.SocketAddress;

import app.l2nx.network.pool.BufferPool;
import app.l2nx.network.pool.ResourcePool;

/**
 * Configures and initializes connection parameters for the network layer.<br>
 * This class handles configuration settings, buffer pools and network properties.
 * @author Mobius
 */
public class ConnectionConfig
{
	public static final int HEADER_SIZE = 2;
	
	private static final int MINIMUM_POOL_GROUPS = 3;
	
	public ResourcePool resourcePool;
	public SocketAddress address;
	
	public float initBufferPoolFactor;
	public long shutdownWaitTime;
	public int threadPoolSize;
	public boolean useNagle;
	public boolean dropPackets;
	public int dropPacketThreshold;
	public int threadPriority;
	public boolean autoExpandPoolCapacity;
	
	/**
	 * Initializes the connection configuration with the specified socket address.
	 * @param socketAddress the address to which this configuration applies
	 * @param settings the network settings of the server
	 */
	public ConnectionConfig(SocketAddress socketAddress, NetworkSettings settings)
	{
		address = socketAddress;
		
		// Initialize Resource Pool and default buffer settings.
		resourcePool = new ResourcePool();
		resourcePool.addBufferPool(HEADER_SIZE, new BufferPool(100, HEADER_SIZE));
		
		shutdownWaitTime = settings.shutdownWaitTime().toMillis();
		
		// Configure thread pool based on processor count.
		final int processors = Runtime.getRuntime().availableProcessors();
		threadPoolSize = settings.threadPoolSize() < 1 ? processors * 4 : settings.threadPoolSize();
		
		// Other network and buffer configurations.
		threadPriority = settings.threadPriority();
		autoExpandPoolCapacity = settings.autoExpandPoolCapacity();
		initBufferPoolFactor = settings.initBufferPoolFactor();
		dropPackets = settings.dropPackets();
		dropPacketThreshold = settings.dropPacketThreshold();
		resourcePool.setBufferSegmentSize(settings.bufferSegmentSize());
		
		// Set up custom buffer pools from settings.
		for (NetworkSettings.BufferPool pool : settings.bufferPools())
		{
			resourcePool.addBufferPool(pool.bufferSize(), new BufferPool(pool.size(), pool.bufferSize()));
		}
		
		// Add additional buffer pool for segment size.
		resourcePool.addBufferPool(resourcePool.getSegmentSize(), new BufferPool(100, resourcePool.getSegmentSize()));
		
		// Ensure minimum pool groups.
		final int missingPools = MINIMUM_POOL_GROUPS - resourcePool.bufferPoolSize();
		for (int i = 0; i < missingPools; i++)
		{
			final int bufferSize = 256 << i;
			resourcePool.addBufferPool(bufferSize, new BufferPool(10, bufferSize));
		}
		
		// Initialize resource pool buffers.
		resourcePool.initializeBuffers(autoExpandPoolCapacity, initBufferPoolFactor);
	}
}
