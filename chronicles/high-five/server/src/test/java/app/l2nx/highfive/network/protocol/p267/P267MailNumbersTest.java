/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.mail.view.MailBox;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class P267MailNumbersTest {
    private static final UUID A = UUID.fromString("018f0000-0000-7000-8000-00000000000a");
    private static final UUID B = UUID.fromString("018f0000-0000-7000-8000-00000000000b");
    private static final UUID C = UUID.fromString("018f0000-0000-7000-8000-00000000000c");

    private final EmbeddedChannel channel = new EmbeddedChannel();

    @AfterEach
    void close() {
        channel.finishAndReleaseAll();
    }

    @Nested
    class Of {
        @Test
        void of_shouldKeepOneTablePerChannel() {
            assertThat(P267MailNumbers.of(channel)).isSameAs(P267MailNumbers.of(channel));
        }

        @Test
        void of_shouldStartEmpty_whenTheChannelIsAnother() {
            P267MailNumbers.of(channel).show(MailBox.RECEIVED, List.of(A));
            final EmbeddedChannel other = new EmbeddedChannel();
            try {
                assertThat(P267MailNumbers.of(other).mail(1)).isNull();
            } finally {
                other.finishAndReleaseAll();
            }
        }
    }

    @Nested
    class Show {
        @Test
        void show_shouldNumberTheMailsFromOneInListOrder() {
            final P267MailNumbers numbers = P267MailNumbers.of(channel);

            assertThat(numbers.show(MailBox.RECEIVED, List.of(A, B))).containsExactly(1, 2);
            assertThat(numbers.mail(1)).isEqualTo(A);
            assertThat(numbers.mail(2)).isEqualTo(B);
        }

        @Test
        void show_shouldKeepTheNumberOfAMail_whenTheBoxIsShownAgain() {
            final P267MailNumbers numbers = P267MailNumbers.of(channel);
            numbers.show(MailBox.RECEIVED, List.of(A, B));

            assertThat(numbers.show(MailBox.RECEIVED, List.of(C, B))).containsExactly(3, 2);
        }

        @Test
        void show_shouldForgetTheMailsOfTheBoxThatLeftIt() {
            final P267MailNumbers numbers = P267MailNumbers.of(channel);
            numbers.show(MailBox.RECEIVED, List.of(A, B));

            numbers.show(MailBox.RECEIVED, List.of(B));

            assertThat(numbers.mail(1)).isNull();
            assertThat(numbers.known(A)).isEqualTo(P267MailNumbers.NO_NUMBER);
            assertThat(numbers.mail(2)).isEqualTo(B);
        }

        @Test
        void show_shouldLeaveTheOtherBoxAlone() {
            final P267MailNumbers numbers = P267MailNumbers.of(channel);
            numbers.show(MailBox.SENT, List.of(A));

            numbers.show(MailBox.RECEIVED, List.of(B));
            numbers.show(MailBox.RECEIVED, List.of());

            assertThat(numbers.mail(1)).isEqualTo(A);
            assertThat(numbers.mail(2)).isNull();
        }
    }

    @Nested
    class Number {
        @Test
        void number_shouldReuseTheShownNumber() {
            final P267MailNumbers numbers = P267MailNumbers.of(channel);
            numbers.show(MailBox.SENT, List.of(A, B));

            assertThat(numbers.number(MailBox.SENT, B)).isEqualTo(2);
        }

        @Test
        void number_shouldGiveTheNextNumber_whenTheMailWasNotShown() {
            final P267MailNumbers numbers = P267MailNumbers.of(channel);
            numbers.show(MailBox.SENT, List.of(A));

            assertThat(numbers.number(MailBox.RECEIVED, C)).isEqualTo(2);
            assertThat(numbers.mail(2)).isEqualTo(C);
        }

        @Test
        void number_shouldNeverRepeatAForgottenNumber() {
            final P267MailNumbers numbers = P267MailNumbers.of(channel);
            numbers.show(MailBox.RECEIVED, List.of(A));
            numbers.show(MailBox.RECEIVED, List.of());

            assertThat(numbers.number(MailBox.RECEIVED, A)).isEqualTo(2);
        }
    }

    @Nested
    class Known {
        @Test
        void known_shouldNotGiveANumber() {
            final P267MailNumbers numbers = P267MailNumbers.of(channel);

            assertThat(numbers.known(A)).isEqualTo(P267MailNumbers.NO_NUMBER);
            assertThat(numbers.mail(1)).isNull();
        }
    }
}
