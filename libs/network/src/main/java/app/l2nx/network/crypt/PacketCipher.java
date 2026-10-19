/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

public interface PacketCipher
{
	/**
	 * @param size the region length, a multiple of the cipher's block size
	 * @throws IllegalArgumentException if {@code size} is not a multiple of the block size
	 */
	void encrypt(byte[] data, int offset, int size);

	/**
	 * @param size the region length, a multiple of the cipher's block size
	 * @throws IllegalArgumentException if {@code size} is not a multiple of the block size
	 */
	void decrypt(byte[] data, int offset, int size);
}
