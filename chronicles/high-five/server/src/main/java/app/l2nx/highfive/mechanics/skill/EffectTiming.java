/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.skill;

final class EffectTiming {
    private int abnormalTime;

    private long startedAt;
    private long tickOrigin;

    EffectTiming(int abnormalTime, long now) {
        this.abnormalTime = abnormalTime;
        this.startedAt = now;
        this.tickOrigin = now;
    }

    int abnormalTime() {
        return abnormalTime;
    }

    void setAbnormalTime(int abnormalTime) {
        this.abnormalTime = abnormalTime;
    }

    int remainingSeconds(long now) {
        return abnormalTime - (int) ((now - startedAt) / 1000);
    }

    long remainingMillis(long now) {
        return (abnormalTime * 1000L) - (now - startedAt);
    }

    boolean isExpired(long now) {
        return (abnormalTime > 0) && (remainingMillis(now) <= 0);
    }

    long firstTickDelay(long now, long periodMillis) {
        return periodMillis - Math.floorMod(now - tickOrigin, periodMillis);
    }

    StoredEffect toStored(long now) {
        final long tickPhase = now - tickOrigin;
        if (abnormalTime <= 0) {
            return StoredEffect.builder().tickPhaseMillis(tickPhase).build();
        }

        final long remaining = remainingMillis(now);
        return remaining > 0
                ? StoredEffect.builder()
                        .remainingMillis(remaining)
                        .tickPhaseMillis(tickPhase)
                        .build()
                : null;
    }

    void restore(StoredEffect stored, long now) {
        if (stored.remainingMillis() != null) {
            abnormalTime = (int) Math.ceilDiv(stored.remainingMillis(), 1000L);
            startedAt = now - ((abnormalTime * 1000L) - stored.remainingMillis());
        }

        tickOrigin = now - stored.tickPhaseMillis();
    }
}
