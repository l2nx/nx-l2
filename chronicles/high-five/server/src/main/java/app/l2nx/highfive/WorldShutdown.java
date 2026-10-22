/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive;

import org.springframework.stereotype.Component;

@Component
public class WorldShutdown {
    public void run() {
        Shutdown.getInstance().startShutdownActions();
    }
}
