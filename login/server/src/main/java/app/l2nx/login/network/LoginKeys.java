/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.network;

import app.l2nx.login.session.model.ScrambledKeyPair;
import java.util.Arrays;
import java.util.Objects;
import java.util.random.RandomGenerator;
import lombok.Builder;

@Builder(toBuilder = true)
public record LoginKeys(ScrambledKeyPair rsa, byte[] blowfishKey) {
    private static final int BLOWFISH_KEY_SIZE = 16;

    public LoginKeys {
        blowfishKey = Objects.requireNonNull(blowfishKey, "blowfishKey").clone();
    }

    /**
     * The client reads the {@code Init} key as a zero-terminated string; a zero byte would shorten it, fail the
     * checksum of every frame it sends and hang the login.
     */
    public static byte[] newBlowfishKey(RandomGenerator random) {
        final byte[] key = new byte[BLOWFISH_KEY_SIZE];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) random.nextInt(1, 256);
        }
        return key;
    }

    @Override
    public byte[] blowfishKey() {
        return blowfishKey.clone();
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof LoginKeys that)
                && Objects.equals(rsa, that.rsa)
                && Arrays.equals(blowfishKey, that.blowfishKey);
    }

    @Override
    public int hashCode() {
        return (31 * Objects.hashCode(rsa)) + Arrays.hashCode(blowfishKey);
    }

    /** Never prints the keys. */
    @Override
    public String toString() {
        return "LoginKeys";
    }
}
