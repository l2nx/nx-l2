/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.network;

import app.l2nx.login.session.model.ScrambledKeyPair;
import java.util.Arrays;
import java.util.Objects;
import lombok.Builder;

@Builder(toBuilder = true)
public record LoginKeys(ScrambledKeyPair rsa, byte[] blowfishKey) {
    public LoginKeys {
        blowfishKey = Objects.requireNonNull(blowfishKey, "blowfishKey").clone();
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
