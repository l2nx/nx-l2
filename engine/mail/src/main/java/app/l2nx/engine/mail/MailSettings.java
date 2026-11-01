/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.mail.view.AttachmentSnapshot;
import java.time.Duration;
import java.util.Objects;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The postbox rules; the defaults are the retail values.
 *
 * @param sendInterval minimum time between two sends of a character; zero sends without a pause
 */
@Builder(toBuilder = true)
@ConfigurationProperties("l2nx.mail")
public record MailSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue Attachments attachments,
        @DefaultValue Fee fee,
        @DefaultValue("8") int maxAttachments,
        @DefaultValue("240") int mailboxLimit,
        @DefaultValue("360h") Duration expiry,
        @DefaultValue("12h") Duration codExpiry,
        @DefaultValue("10s") Duration sendInterval,
        @DefaultValue("1m") Duration expiryPoll) {
    private static final String PREFIX = "l2nx.mail.";

    public MailSettings {
        Objects.requireNonNull(attachments, PREFIX + "attachments");
        Objects.requireNonNull(fee, PREFIX + "fee");
        // A snapshot has no slot past the last one, and the clients show no more.
        if ((maxAttachments < 1) || (maxAttachments > AttachmentSnapshot.MAX_SLOTS)) {
            throw new IllegalArgumentException(
                    PREFIX + "max-attachments must be 1 to " + AttachmentSnapshot.MAX_SLOTS + ": " + maxAttachments);
        }
        if (mailboxLimit < 1) {
            throw new IllegalArgumentException(PREFIX + "mailbox-limit must be positive: " + mailboxLimit);
        }
        requirePositive("expiry", expiry);
        requirePositive("cod-expiry", codExpiry);
        requirePositive("expiry-poll", expiryPoll);
        Objects.requireNonNull(sendInterval, PREFIX + "send-interval");
        if (sendInterval.isNegative()) {
            throw new IllegalArgumentException(PREFIX + "send-interval must not be negative: " + sendInterval);
        }
    }

    public static MailSettings defaults() {
        return new Binder().bindOrCreate("l2nx.mail", MailSettings.class);
    }

    public long fee(int attachmentCount) {
        return fee.base() + (fee.perAttachment() * attachmentCount);
    }

    private static void requirePositive(String key, Duration value) {
        Objects.requireNonNull(value, PREFIX + key);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(PREFIX + key + " must be positive: " + value);
        }
    }

    /**
     * @param peaceZoneOnly attachment actions (send, receive, return, cancel) only in a peace zone
     */
    @Builder(toBuilder = true)
    public record Attachments(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("true") boolean peaceZoneOnly,
            @DefaultValue("false") boolean denyInCombat,
            @DefaultValue("false") boolean denyWhenDead) {}

    @Builder(toBuilder = true)
    public record Fee(
            @DefaultValue("100") long base,
            @DefaultValue("1000") long perAttachment) {
        public Fee {
            if ((base < 0) || (perAttachment < 0)) {
                throw new IllegalArgumentException(
                        PREFIX + "fee must not be negative: base " + base + ", per attachment " + perAttachment);
            }
        }
    }
}
