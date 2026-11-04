/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.config;

import app.l2nx.highfive.partition.WorldPartition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The ini configuration ignores profiles and the world load reads it, so the flag flips only once the server is
 * ready, on the loop that reads it.
 */
@Component
@Profile("load-seed")
class LoadSeedAdminRights {
    @EventListener(ApplicationReadyEvent.class)
    void grant() {
        WorldPartition.loop().execute(() -> GeneralConfig.EVERYBODY_HAS_ADMIN_RIGHTS = true);
    }
}
