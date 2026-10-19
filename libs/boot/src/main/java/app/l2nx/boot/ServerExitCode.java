/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.boot;

import org.springframework.boot.ExitCodeGenerator;

/**
 * A supervisor restarts the process on any code but {@link #SHUTDOWN}.
 */
public enum ServerExitCode implements ExitCodeGenerator {
    SHUTDOWN(0),
    RESTART(2);

    private final int code;

    ServerExitCode(int code) {
        this.code = code;
    }

    @Override
    public int getExitCode() {
        return code;
    }
}
