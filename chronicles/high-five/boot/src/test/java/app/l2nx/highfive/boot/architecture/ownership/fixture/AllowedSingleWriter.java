/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.ownership.fixture;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class AllowedSingleWriter {
    private AllowedSingleWriter() {}

    // Comparable<Checked> makes javac add a public synthetic bridge compareTo(Object) without the check.
    @SingleWriter
    public static final class Checked implements Comparable<Checked> {
        public static final int LIMIT = 10;

        private final Map<Integer, Object> map = new HashMap<>();
        private final TimeUnit unit = TimeUnit.SECONDS;
        private ScheduledFuture<?> timer;

        public Checked() {}

        public Object get(int id) {
            WorldPartition.checkOwner();
            return map.get(id);
        }

        public static int limit() {
            WorldPartition.checkOwner();
            return LIMIT;
        }

        @Override
        public int compareTo(Checked other) {
            WorldPartition.checkOwner();
            return Integer.compare(map.size(), other.map.size());
        }

        @Override
        public String toString() {
            return "Checked";
        }
    }

    @SingleWriter
    public abstract static class CheckedAbstract {
        public abstract int level();
    }

    @SingleWriter
    public static final class AnnotatedSubclass extends CheckedAbstract {
        @Override
        public int level() {
            WorldPartition.checkOwner();
            return 1;
        }
    }

    public static final class Plain {
        public volatile int value;
        private final Map<Integer, Object> map = new ConcurrentHashMap<>();

        public void set(int next) {
            value = next;
        }
    }
}
