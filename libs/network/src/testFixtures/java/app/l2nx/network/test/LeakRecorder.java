/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class LeakRecorder extends ResourceLeakDetectorFactory {
    private static final ConcurrentLinkedQueue<String> LEAKS = new ConcurrentLinkedQueue<>();

    public static List<String> drain() {
        final List<String> leaks = new ArrayList<>();
        String leak;
        while ((leak = LEAKS.poll()) != null) {
            leaks.add(leak);
        }
        return leaks;
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T> ResourceLeakDetector<T> newResourceLeakDetector(
            Class<T> resource, int samplingInterval, long maxActive) {
        return new RecordingDetector<>(resource, samplingInterval);
    }

    private static final class RecordingDetector<T> extends ResourceLeakDetector<T> {
        RecordingDetector(Class<T> resource, int samplingInterval) {
            super(resource, samplingInterval);
        }

        @Override
        protected void reportTracedLeak(String resourceType, String records) {
            LEAKS.add(resourceType + records);
            super.reportTracedLeak(resourceType, records);
        }

        @Override
        protected void reportUntracedLeak(String resourceType) {
            LEAKS.add(resourceType);
            super.reportUntracedLeak(resourceType);
        }
    }
}
