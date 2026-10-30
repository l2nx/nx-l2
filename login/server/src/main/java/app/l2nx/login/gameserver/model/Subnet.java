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
package app.l2nx.login.gameserver.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * @author Mobius
 */
public class Subnet {
    private final byte[] address;
    private final byte[] subnetMask;
    private final boolean v4;

    public Subnet(String input) throws UnknownHostException {
        final String[] parts = input.split("/");
        final InetAddress inetAddress = InetAddress.getByName(parts[0]);
        address = inetAddress.getAddress();
        v4 = address.length == 4;

        // Determine prefix length, with defaults for IPv4 (32) and IPv6 (128).
        int prefixLength = v4 ? 32 : 128;
        if (parts.length > 1) {
            prefixLength = Integer.parseInt(parts[1]);
        }

        // Validate prefix length.
        if ((prefixLength < 0) || (prefixLength > (address.length * 8))) {
            throw new IllegalArgumentException("Invalid prefix length: " + prefixLength);
        }

        // Create mask based on prefix length.
        final int fullBytes = prefixLength / 8;
        final int remainingBits = prefixLength % 8;
        subnetMask = new byte[address.length];
        Arrays.fill(subnetMask, 0, fullBytes, (byte) 0xFF);
        if (remainingBits > 0) {
            subnetMask[fullBytes] = (byte) (0xFF << (8 - remainingBits));
        }

        // Apply mask to address.
        for (int i = 0; i < address.length; i++) {
            address[i] &= subnetMask[i];
        }
    }

    public boolean isInSubnet(byte[] address) {
        // IPv4 and IPv6 address length mismatch.
        if (address.length != this.address.length) {
            if (!v4 || (address.length != 16)) {
                return false;
            }

            // Check for IPv4-in-IPv6 address format (::ffff:192.168.x.x).
            // @formatter:off
            final byte[] ipv4In6Prefix = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF};
            // @formatter:on
            for (int i = 0; i < ipv4In6Prefix.length; i++) {
                if (address[i] != ipv4In6Prefix[i]) {
                    return false;
                }
            }

            // Extract IPv4 portion.
            address = Arrays.copyOfRange(address, 12, 16);
        }

        // Apply mask to check subnet match.
        for (int i = 0; i < address.length; i++) {
            if ((address[i] & subnetMask[i]) != this.address[i]) {
                return false;
            }
        }

        return true;
    }

    public byte[] getAddress() {
        return address.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Subnet) {
            return isInSubnet(((Subnet) obj).address);
        }

        if (obj instanceof InetAddress) {
            return isInSubnet(((InetAddress) obj).getAddress());
        }

        return false;
    }

    @Override
    public String toString() {
        int prefixLength = 0;
        for (byte b : subnetMask) {
            prefixLength += Integer.bitCount(b & 0xFF);
        }

        try {
            return InetAddress.getByAddress(address).getHostAddress() + "/" + prefixLength;
        } catch (UnknownHostException e) {
            return "Invalid address";
        }
    }
}
