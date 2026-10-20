/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import lombok.Builder;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * @param workerThreads 0 takes Netty's default (twice the available processors)
 */
@Validated
@Builder(toBuilder = true)
@ConfigurationProperties("l2nx.network")
public record NetworkProperties(
        @NotNull @DefaultValue("auto") Transport transport,
        @PositiveOrZero @DefaultValue("0") int workerThreads,
        @NotNull @DefaultValue("16KB") DataSize maxFrame,

        @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("120s")
        Duration readTimeout,

        @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("30s")
        Duration pingInterval,

        @Positive @DefaultValue("3") int pingMisses,

        @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("10s")
        Duration handshakeTimeout,

        @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("60s")
        Duration authTimeout,

        @Positive @DefaultValue("256") int inboundQueueLimit,
        @Positive @DefaultValue("16") int packetsPerDrain,
        @Positive @DefaultValue("200") int packetsPerSecond,
        @NotNull @Valid @DefaultValue DecodeErrorLimit decodeErrorLimit,
        @NotNull @DefaultValue("32KB") DataSize writeBufferLow,
        @NotNull @DefaultValue("128KB") DataSize writeBufferHigh,
        @NotNull @DefaultValue("1MB") DataSize outboundLimit,

        @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("10s")
        Duration slowClientTimeout,

        @Positive @DefaultValue("10") int connectionsPerIp,
        @Positive @DefaultValue("5") int acceptPerIpPerSecond,
        @Positive @DefaultValue("10000") int maxConnections,
        @NotNull @Valid @DefaultValue GsLink gslink,
        @NotNull @DurationMin(nanos = 0) @DefaultValue("2s") Duration shutdownQuietPeriod,

        @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("15s")
        Duration shutdownTimeout) {

    @Builder(toBuilder = true)
    public record DecodeErrorLimit(
            @Positive @DefaultValue("10") int count,

            @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("60s")
            Duration window) {}

    /**
     * @param secret shared by the login and the game servers; validated where the GS-link starts
     */
    @Builder(toBuilder = true)
    public record GsLink(
            String secret,
            @DefaultValue("false") boolean tls,

            @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("15s")
            Duration pingInterval,

            @NotNull @Valid @DefaultValue ReconnectDelay reconnectDelay) {}

    @Builder(toBuilder = true)
    public record ReconnectDelay(
            @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("1s")
            Duration initial,

            @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("30s")
            Duration max) {}

    @AssertTrue(message = "max-frame must be positive and at most 65535 bytes")
    public boolean isMaxFrameInRange() {
        return (maxFrame != null) && (maxFrame.toBytes() > 0) && (maxFrame.toBytes() <= 0xFFFF);
    }

    @AssertTrue(message = "write-buffer-low must be at least 0 and at most write-buffer-high, which must fit an int")
    public boolean isWatermarkValid() {
        return (writeBufferLow == null)
                || (writeBufferHigh == null)
                || ((writeBufferLow.toBytes() >= 0)
                        && (writeBufferLow.toBytes() <= writeBufferHigh.toBytes())
                        && (writeBufferHigh.toBytes() <= Integer.MAX_VALUE));
    }

    @AssertTrue(message = "shutdown-quiet-period must not exceed shutdown-timeout")
    public boolean isShutdownQuietPeriodWithinTimeout() {
        return (shutdownQuietPeriod == null)
                || (shutdownTimeout == null)
                || (shutdownQuietPeriod.compareTo(shutdownTimeout) <= 0);
    }

    public static NetworkProperties defaults() {
        return new Binder(new MapConfigurationPropertySource()).bindOrCreate("l2nx.network", NetworkProperties.class);
    }
}
