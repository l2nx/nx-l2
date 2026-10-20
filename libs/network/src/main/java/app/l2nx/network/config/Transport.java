/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.config;

public enum Transport {
    AUTO,
    EPOLL,
    NIO,
    /** Opt-in: the default Docker seccomp profile blocks the io_uring system calls. */
    IO_URING
}
