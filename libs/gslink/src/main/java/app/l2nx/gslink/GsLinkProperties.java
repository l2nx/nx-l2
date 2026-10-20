/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
 * @param secret shared by the login and the game servers (at least 32 characters, 256 bits), sent as {@code authorization: Bearer <secret>}
 */
@Validated
@Builder(toBuilder = true)
@ConfigurationProperties("l2nx.gslink")
public record GsLinkProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotNull @Valid @DefaultValue ReconnectDelay reconnectDelay) {

    // Metadata values are printable ASCII. A @Pattern would print the rejected secret in the startup failure.
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
