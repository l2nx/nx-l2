/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.Augmentation;
import app.l2nx.engine.mail.view.Elements;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailAttachmentRepositoryTest {
    private final MailWorld world = new MailWorld(MailSettings.defaults());
    private final UUID sender = world.character("Sender");
    private final UUID receiver = world.character("Receiver");

    private UUID mail() {
        return world.insert(world.mail(sender, receiver)
                .attachments(AttachmentState.ATTACHED)
                .build());
    }

    private static AttachmentSnapshot plain(int slot, int itemTemplateId, long count) {
        return AttachmentSnapshot.builder()
                .slot(slot)
                .itemTemplateId(itemTemplateId)
                .count(count)
                .enchantOptions(List.of())
                .build();
    }

    @Nested
    class InsertAll {
        @Test
        void insertAll_shouldStoreTheSnapshotsAsWritten_whenTheyHaveEveryHighFiveField() {
            final UUID mailId = mail();
            final AttachmentSnapshot weapon = AttachmentSnapshot.builder()
                    .slot(0)
                    .itemTemplateId(6579)
                    .count(1)
                    .enchantLevel(16)
                    .customType1(1)
                    .customType2(2)
                    .manaLeft(55)
                    .itemExpiresAt(Instant.parse("2026-12-01T00:00:00Z"))
                    .augmentation(new Augmentation(16_000, 16_500))
                    .elements(Elements.builder()
                            .attackElement(2)
                            .attackElementPower(150)
                            .fire(0)
                            .water(0)
                            .wind(0)
                            .earth(0)
                            .holy(0)
                            .dark(0)
                            .build())
                    .enchantOptions(List.of(101, 102, 103))
                    .build();
            final AttachmentSnapshot armor = AttachmentSnapshot.builder()
                    .slot(1)
                    .itemTemplateId(6380)
                    .count(1)
                    .elements(Elements.builder()
                            .fire(30)
                            .water(0)
                            .wind(60)
                            .earth(0)
                            .holy(0)
                            .dark(90)
                            .build())
                    .enchantOptions(List.of(7))
                    .build();
            final AttachmentSnapshot adena = plain(2, 57, 1_000_000);

            world.attachments.insertAll(mailId, List.of(weapon, armor, adena));

            assertThat(world.attachments.findByMail(mailId)).containsExactly(weapon, armor, adena);
        }
    }

    @Nested
    class FindByMail {
        @Test
        void findByMail_shouldReturnTheMailsOwnSnapshotsInSlotOrder_whenOthersHaveSome() {
            final UUID mailId = mail();
            world.attachments.insertAll(mailId, List.of(plain(1, 17, 20), plain(0, 1000, 1)));
            world.attachments.insertAll(mail(), List.of(plain(0, 57, 5)));

            assertThat(world.attachments.findByMail(mailId))
                    .extracting(AttachmentSnapshot::slot, AttachmentSnapshot::itemTemplateId)
                    .containsExactly(tuple(0, 1000), tuple(1, 17));
        }

        @Test
        void findByMail_shouldReturnNothing_whenTheMailHasNoSnapshots() {
            assertThat(world.attachments.findByMail(mail())).isEmpty();
        }
    }

    @Nested
    class FindByMails {
        @Test
        void findByMails_shouldGroupTheSnapshotsByMail_andLeaveOutMailsWithoutAny() {
            final UUID first = mail();
            final UUID second = mail();
            final UUID empty = mail();
            world.attachments.insertAll(first, List.of(plain(1, 17, 20), plain(0, 1000, 1)));
            world.attachments.insertAll(second, List.of(plain(0, 57, 5)));

            final Map<UUID, List<AttachmentSnapshot>> actual =
                    world.attachments.findByMails(List.of(first, second, empty));

            assertThat(actual)
                    .containsOnly(
                            Map.entry(first, List.of(plain(0, 1000, 1), plain(1, 17, 20))),
                            Map.entry(second, List.of(plain(0, 57, 5))));
        }

        @Test
        void findByMails_shouldReturnNothing_whenNoMailIsAsked() {
            assertThat(world.attachments.findByMails(List.of())).isEmpty();
        }

        @Test
        void findByMails_shouldReturnNothing_whenNoMailExists() {
            assertThat(world.attachments.findByMails(List.of(EntityIds.next()))).isEmpty();
        }
    }

    @Nested
    class Copy {
        @Test
        void copy_shouldGiveTheOtherMailTheSameSnapshots_whenAMailIsReturned() {
            final UUID original = world.insert(world.mail(sender, receiver)
                    .attachments(AttachmentState.RETURNED)
                    .build());
            world.attach(original, 1000, 1);
            world.attach(original, 17, 20);
            final UUID returned = world.insert(world.mail(receiver, sender)
                    .attachments(AttachmentState.ATTACHED)
                    .returnedFromId(original)
                    .build());

            world.attachments.copy(original, returned);

            assertThat(world.attachments.findByMail(returned))
                    .hasSize(2)
                    .isEqualTo(world.attachments.findByMail(original));
        }
    }
}
