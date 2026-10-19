/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Checksum stored in the last 32-bit word of a region: the XOR of all preceding little-endian words.
 */
public final class XorChecksum
{
	private static final int WORD_SIZE = 4;
	private static final VarHandle WORD = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

	private XorChecksum()
	{
	}

	/**
	 * Computes the checksum of the region and writes it into the region's last word.
	 * @param data the buffer
	 * @param offset the start of the region
	 * @param size the region length including the checksum word, a positive multiple of 4
	 * @throws IllegalArgumentException if {@code size} is not a positive multiple of 4
	 */
	public static void append(byte[] data, int offset, int size)
	{
		Objects.checkFromIndexSize(offset, size, data.length);
		if (((size % WORD_SIZE) != 0) || (size < WORD_SIZE))
		{
			throw new IllegalArgumentException("Size " + size + " is not a positive multiple of " + WORD_SIZE);
		}

		final int checksumOffset = (offset + size) - WORD_SIZE;
		WORD.set(data, checksumOffset, xorWords(data, offset, checksumOffset));
	}

	/**
	 * Checks the checksum in the region's last word.
	 * @param data the buffer
	 * @param offset the start of the region
	 * @param size the region length including the checksum word
	 * @return {@code true} if the region holds more than the checksum word, its size is a multiple of 4 and the checksum matches
	 */
	public static boolean verify(byte[] data, int offset, int size)
	{
		Objects.checkFromIndexSize(offset, size, data.length);
		if (((size % WORD_SIZE) != 0) || (size <= WORD_SIZE))
		{
			return false;
		}

		final int checksumOffset = (offset + size) - WORD_SIZE;
		return xorWords(data, offset, checksumOffset) == (int) WORD.get(data, checksumOffset);
	}

	private static int xorWords(byte[] data, int from, int to)
	{
		int checksum = 0;
		for (int i = from; i < to; i += WORD_SIZE)
		{
			checksum ^= (int) WORD.get(data, i);
		}
		return checksum;
	}
}
