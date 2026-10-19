/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

/**
 * Symmetric cipher applied in place to a region of a packet buffer.
 */
public interface PacketCipher
{
	/**
	 * Encrypts {@code size} bytes of {@code data} starting at {@code offset}, in place.
	 * @param data the buffer
	 * @param offset the start of the region
	 * @param size the region length, a multiple of the cipher's block size
	 * @throws IllegalArgumentException if {@code size} is not a multiple of the block size
	 */
	void encrypt(byte[] data, int offset, int size);

	/**
	 * Decrypts {@code size} bytes of {@code data} starting at {@code offset}, in place.
	 * @param data the buffer
	 * @param offset the start of the region
	 * @param size the region length, a multiple of the cipher's block size
	 * @throws IllegalArgumentException if {@code size} is not a multiple of the block size
	 */
	void decrypt(byte[] data, int offset, int size);
}
