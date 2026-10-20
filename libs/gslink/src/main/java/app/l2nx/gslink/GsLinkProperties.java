/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Builder;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.validation.annotation.Validated;

/**
 * GS-link settings both servers share; address, TLS and keepalive are Spring Boot's {@code spring.grpc.*}.
 *
 * @param secret shared by both servers, sent as {@code authorization: Bearer <secret>}
 */
@Validated
@Builder(toBuilder = true)
@ConfigurationProperties("l2nx.gslink")
public record GsLinkProperties(
        String secret, @NotNull @Valid @DefaultValue ReconnectDelay reconnectDelay) {
    private static final int MIN_SECRET_LENGTH = 32;

    // The secret is checked by methods, not by constraints on the component: a failed constraint on the component
    // prints the rejected value in the startup failure, a failed method prints only false.
    @AssertTrue(message = "secret must be set")
    public boolean isSecretSet() {
        return (secret != null) && !secret.isBlank();
    }

    @AssertTrue(message = "secret must have at least " + MIN_SECRET_LENGTH + " characters")
    public boolean isSecretLongEnough() {
        return (secret == null) || (secret.length() >= MIN_SECRET_LENGTH);
    }

    // Metadata values are printable ASCII.
    @AssertTrue(message = "secret must be printable ASCII without spaces")
    public boolean isSecretUsable() {
        return (secret == null) || secret.chars().allMatch(c -> (c > ' ') && (c < 0x7F));
    }

    @Override
    public String toString() {
        return "GsLinkProperties[secret=****, reconnectDelay=" + reconnectDelay + "]";
    }

    /** Delay before the game server opens a new stream after one ended: {@code initial}, doubling up to {@code max}. */
    @Builder(toBuilder = true)
    public record ReconnectDelay(
            @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("1s")
            Duration initial,

            @NotNull @DurationMin(nanos = 0, inclusive = false) @DefaultValue("30s")
            Duration max) {

        @AssertTrue(message = "max must not be shorter than initial")
        public boolean isMaxAtLeastInitial() {
            return (initial == null) || (max == null) || (max.compareTo(initial) >= 0);
        }

        public BackOff backOff() {
            final ExponentialBackOff backOff = new ExponentialBackOff(initial.toMillis(), 2);
            backOff.setMaxInterval(max.toMillis());
            return backOff;
        }
    }
}
