/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.c621.clientpackets;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.c621.crypt.Credentials;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Objects;
import lombok.Builder;

/**
 * The {@link Credentials} as RSA blocks: one, or two from clients that split login and password. Bytes past the
 * blocks are ignored.
 *
 * @param block2 {@code null} for the one-block method
 */
@Builder(toBuilder = true)
public record RequestAuthLogin(byte[] block1, byte[] block2) {
    public static final int BLOCK = 128;

    public RequestAuthLogin {
        block1 = copyBlock(Objects.requireNonNull(block1, "block1"));
        block2 = block2 == null ? null : copyBlock(block2);
    }

    public static RequestAuthLogin decode(ByteBuf body) {
        if (body.readableBytes() >= 2 * BLOCK) {
            return RequestAuthLogin.builder()
                    .block1(readBlock(body))
                    .block2(readBlock(body))
                    .build();
        }
        if (body.readableBytes() >= BLOCK) {
            return RequestAuthLogin.builder().block1(readBlock(body)).build();
        }
        throw new PacketDecodeException(
                "RequestAuthLogin needs " + BLOCK + " bytes, " + body.readableBytes() + " in the frame");
    }

    public void encode(ByteBuf out) {
        out.writeByte(LoginClientOpcode.REQUEST_AUTH_LOGIN.opcode());
        out.writeBytes(block1);
        if (block2 != null) {
            out.writeBytes(block2);
        }
    }

    @Override
    public byte[] block1() {
        return block1.clone();
    }

    @Override
    public byte[] block2() {
        return block2 == null ? null : block2.clone();
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof RequestAuthLogin that)
                && Arrays.equals(block1, that.block1)
                && Arrays.equals(block2, that.block2);
    }

    @Override
    public int hashCode() {
        return (31 * Arrays.hashCode(block1)) + Arrays.hashCode(block2);
    }

    /** Never prints the credential blocks. */
    @Override
    public String toString() {
        return "RequestAuthLogin[blocks=" + (block2 == null ? 1 : 2) + "]";
    }

    private static byte[] copyBlock(byte[] block) {
        if (block.length != BLOCK) {
            throw new IllegalArgumentException("An RSA block has " + BLOCK + " bytes, not " + block.length);
        }
        return block.clone();
    }

    private static byte[] readBlock(ByteBuf body) {
        final byte[] block = new byte[BLOCK];
        body.readBytes(block);
        return block;
    }
}
