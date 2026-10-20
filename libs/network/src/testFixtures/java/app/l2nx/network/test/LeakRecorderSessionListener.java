/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import io.netty.util.ResourceLeakDetectorFactory;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Buffer classes take their leak detector from the factory once, when they load; the session opens before any test
 * class loads them.
 */
public final class LeakRecorderSessionListener implements LauncherSessionListener {
    @Override
    public void launcherSessionOpened(LauncherSession session) {
        ResourceLeakDetectorFactory.setResourceLeakDetectorFactory(new LeakRecorder());
    }
}
