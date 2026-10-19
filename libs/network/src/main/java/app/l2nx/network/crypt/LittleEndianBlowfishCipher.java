/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Blowfish in ECB mode over 32-bit words stored little-endian.<br>
 * Standard Blowfish (JCE {@code Blowfish/ECB/NoPadding}) reads each half of a block big-endian, so every 4-byte word is byte-swapped before and after the JCE pass.<br>
 * Thread-safe: each direction owns one {@link Cipher} and serializes on it, so one instance may be shared across connections.
 */
public final class LittleEndianBlowfishCipher implements PacketCipher
{
	private static final String ALGORITHM = "Blowfish";
	private static final String TRANSFORMATION = "Blowfish/ECB/NoPadding";
	private static final int BLOCK_SIZE = 8;
	private static final int WORD_SIZE = 4;
	private static final VarHandle WORD = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

	private final Cipher encryptCipher;
	private final Cipher decryptCipher;

	/**
	 * @param key the Blowfish key, 1 to 56 bytes (the JCE limit; Blowfish itself would take up to 72)
	 * @throws IllegalArgumentException if the key length is outside that range
	 */
	public LittleEndianBlowfishCipher(byte[] key)
	{
		final SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
		encryptCipher = newCipher(Cipher.ENCRYPT_MODE, keySpec);
		decryptCipher = newCipher(Cipher.DECRYPT_MODE, keySpec);
	}

	@Override
	public void encrypt(byte[] data, int offset, int size)
	{
		process(encryptCipher, data, offset, size);
	}

	@Override
	public void decrypt(byte[] data, int offset, int size)
	{
		process(decryptCipher, data, offset, size);
	}

	private static Cipher newCipher(int mode, SecretKeySpec keySpec)
	{
		final Cipher cipher;
		try
		{
			cipher = Cipher.getInstance(TRANSFORMATION);
		}
		catch (GeneralSecurityException e)
		{
			throw new IllegalStateException(TRANSFORMATION + " is not available", e);
		}

		try
		{
			cipher.init(mode, keySpec);
		}
		catch (InvalidKeyException e)
		{
			throw new IllegalArgumentException("Invalid Blowfish key of " + keySpec.getEncoded().length + " bytes", e);
		}

		return cipher;
	}

	private static void process(Cipher cipher, byte[] data, int offset, int size)
	{
		Objects.checkFromIndexSize(offset, size, data.length);
		if ((size % BLOCK_SIZE) != 0)
		{
			throw new IllegalArgumentException("Size " + size + " is not a multiple of the Blowfish block size " + BLOCK_SIZE);
		}

		swapWords(data, offset, size);
		synchronized (cipher)
		{
			try
			{
				cipher.doFinal(data, offset, size, data, offset);
			}
			catch (GeneralSecurityException e)
			{
				// Unreachable: the region is block-aligned, NoPadding never fails on padding, and the output is the input region.
				throw new IllegalStateException(e);
			}
		}
		swapWords(data, offset, size);
	}

	private static void swapWords(byte[] data, int offset, int size)
	{
		final int end = offset + size;
		for (int i = offset; i < end; i += WORD_SIZE)
		{
			WORD.set(data, i, Integer.reverseBytes((int) WORD.get(data, i)));
		}
	}
}
