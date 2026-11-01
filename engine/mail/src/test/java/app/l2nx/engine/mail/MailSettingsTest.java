/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class MailSettingsTest {
    @Nested
    class Constructor {
        @ParameterizedTest(name = "{0} {index}")
        @MethodSource("valuesThePostboxCannotRunWith")
        void constructor_shouldReject_whenAValueCannotWork(
                String key, UnaryOperator<MailSettings.MailSettingsBuilder> change) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            change.apply(MailSettings.defaults().toBuilder()).build())
                    .withMessageContaining(key);
        }

        static Stream<Arguments> valuesThePostboxCannotRunWith() {
            return Stream.of(
                    rejected("l2nx.mail.max-attachments", b -> b.maxAttachments(0)),
                    rejected("l2nx.mail.max-attachments", b -> b.maxAttachments(9)),
                    rejected("l2nx.mail.mailbox-limit", b -> b.mailboxLimit(0)),
                    rejected("l2nx.mail.expiry", b -> b.expiry(Duration.ZERO)),
                    rejected("l2nx.mail.cod-expiry", b -> b.codExpiry(Duration.ofHours(-1))),
                    rejected("l2nx.mail.expiry-poll", b -> b.expiryPoll(Duration.ZERO)),
                    rejected("l2nx.mail.send-interval", b -> b.sendInterval(Duration.ofSeconds(-1))),
                    rejected("l2nx.mail.fee", b -> b.fee(new MailSettings.Fee(-1, 1000))));
        }

        private static Arguments rejected(String key, UnaryOperator<MailSettings.MailSettingsBuilder> change) {
            return Arguments.of(key, change);
        }

        @Test
        void defaults_shouldBeTheRetailValues_whenNothingIsSet() {
            final MailSettings settings = MailSettings.defaults();

            assertThat(settings.enabled()).isTrue();
            assertThat(settings.attachments()).isEqualTo(new MailSettings.Attachments(true, true, false, false));
            assertThat(settings.fee()).isEqualTo(new MailSettings.Fee(100, 1000));
            assertThat(settings.maxAttachments()).isEqualTo(8);
            assertThat(settings.mailboxLimit()).isEqualTo(240);
            assertThat(settings.expiry()).isEqualTo(Duration.ofHours(360));
            assertThat(settings.codExpiry()).isEqualTo(Duration.ofHours(12));
            assertThat(settings.sendInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(settings.expiryPoll()).isEqualTo(Duration.ofMinutes(1));
        }
    }

    @Nested
    class Fee {
        @ParameterizedTest(name = "{0} attachments cost {1}")
        @CsvSource({"0, 100", "1, 1100", "8, 8100"})
        void fee_shouldAddThePerAttachmentPart_whenItemsAreAttached(int attachments, long expected) {
            assertThat(MailSettings.defaults().fee(attachments)).isEqualTo(expected);
        }
    }
}
