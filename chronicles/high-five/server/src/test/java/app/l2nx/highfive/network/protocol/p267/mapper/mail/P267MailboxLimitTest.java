/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper.mail;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.mail.MailSettings;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class P267MailboxLimitTest {
    @Nested
    class Construct {
        @Test
        void construct_shouldStart_whenAFullBoxFitsTheListFrame() {
            final MailSettings settings = MailSettings.defaults().toBuilder()
                    .mailboxLimit(P267MailWire.maxListMails())
                    .build();

            assertThatCode(() -> new P267MailboxLimit(settings)).doesNotThrowAnyException();
        }

        @Test
        void construct_shouldRefuseToStart_whenAFullBoxOutgrowsTheListFrame() {
            final MailSettings settings = MailSettings.defaults().toBuilder()
                    .mailboxLimit(P267MailWire.maxListMails() + 1)
                    .build();

            assertThatThrownBy(() -> new P267MailboxLimit(settings))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("l2nx.mail.mailbox-limit");
        }
    }
}
