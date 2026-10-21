/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class LeakRecorder extends ResourceLeakDetectorFactory {
    private static final ConcurrentLinkedQueue<String> LEAKS = new ConcurrentLinkedQueue<>();
    // A leaked tracker is never closed, so after a leak this stays above zero for the rest of the JVM.
    private static final AtomicLong OPEN_TRACKERS = new AtomicLong();

    /**
     * @return tracked resources not released yet; zero means nothing can leak until the next tracked allocation
     */
    public static long openTrackers() {
        return OPEN_TRACKERS.get();
    }

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
        public ResourceLeakTracker<T> track(T obj) {
            return counted(super.track(obj));
        }

        @Override
        public ResourceLeakTracker<T> trackForcibly(T obj) {
            return counted(super.trackForcibly(obj));
        }

        private static <T> ResourceLeakTracker<T> counted(ResourceLeakTracker<T> tracker) {
            if (tracker == null) {
                return null;
            }
            OPEN_TRACKERS.incrementAndGet();
            return new CountedTracker<>(tracker);
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

    private record CountedTracker<T>(ResourceLeakTracker<T> delegate) implements ResourceLeakTracker<T> {
        @Override
        public void record() {
            delegate.record();
        }

        @Override
        public void record(Object hint) {
            delegate.record(hint);
        }

        @Override
        public boolean close(T trackedObject) {
            final boolean closed = delegate.close(trackedObject);
            if (closed) {
                OPEN_TRACKERS.decrementAndGet();
            }
            return closed;
        }
    }
}
