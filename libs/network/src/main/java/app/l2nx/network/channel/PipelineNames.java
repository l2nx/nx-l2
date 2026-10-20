/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

public final class PipelineNames {
    public static final String FRAME_DECODER = "frame-decoder";
    public static final String CIPHER_DECODER = "cipher-decoder";
    public static final String FRAME_ENCODER = "frame-encoder";
    public static final String IDLE = "idle";
    public static final String IDLE_POLICY = "idle-policy";
    public static final String DEADLINES = "deadlines";
    public static final String OUTBOUND = "outbound";
    public static final String HANDSHAKE = "handshake";
    public static final String PACKET = "packet";
    public static final String SESSION = "session";
    public static final String TAIL = "tail";

    private PipelineNames() {}
}
