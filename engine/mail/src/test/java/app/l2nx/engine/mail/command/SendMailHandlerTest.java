/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.ValueOutcome;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.mail.ItemCount;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.ParticipantState;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.FakeParticipants;
import app.l2nx.engine.mail.fixture.FakeTransactions;
import app.l2nx.engine.mail.fixture.ManualScheduler;
import app.l2nx.engine.mail.repository.MailWriter;
import app.l2nx.engine.mail.repository.SendTransaction;
import app.l2nx.engine.mail.view.MailArrived;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailSent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SendMailHandlerTest {
    private static final int SWORD = 1000;

    private final ManualGameClock clock = new ManualGameClock(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private final FakeParticipants participants = new FakeParticipants();
    private final FakeItems items = new FakeItems();
    private final FakeTransactions transactions = new FakeTransactions();
    private final ManualScheduler scheduler = new ManualScheduler();
    private final MailActor sender = participants.enter(EntityIds.next(), 1, "Sender");
    private final MailActor receiver = participants.enter(EntityIds.next(), 2, "Receiver");

    private SendMailHandler handler(MailSettings settings) {
        return new SendMailHandler(
                settings,
                participants,
                items,
                new ValueTransactionSubmitter(transactions, scheduler),
                new MailWriter(settings, null, null, null),
                clock);
    }

    private FakeItems.Item give(int itemTemplateId, long count) {
        final FakeItems.Item item = new FakeItems.Item(
                100 + items.items(1).size(), EntityIds.next(), sender.characterId(), itemTemplateId, count);
        items.add(sender.characterHandle(), item);
        return item;
    }

    private SendMail mail(List<ItemCount> attached, Long codAdena) {
        return SendMail.builder()
                .receiverName("Receiver")
                .subject("Hello")
                .body("Body")
                .items(attached)
                .codAdena(codAdena)
                .build();
    }

    private static ValueOutcome<?> committedTo(MailActor receiver) {
        return new ValueOutcome.Committed<>(
                SendTransaction.Sent.builder()
                        .mailId(EntityIds.next())
                        .receiverId(receiver.characterId())
                        .build(),
                EntityIds.next());
    }

    @Nested
    class Rules {
        @ParameterizedTest(name = "{0}")
        @MethodSource("refusedByRules")
        void handle_shouldRefuse_whenTheSettingsOrTheStateForbid(
                String name,
                UnaryOperator<MailSettings.MailSettingsBuilder> settings,
                ParticipantState state,
                MailRefusal expected) {
            give(FakeItems.ADENA, 10_000);
            final FakeItems.Item sword = give(SWORD, 1);
            participants.state(sender.characterHandle(), state);

            handler(settings.apply(MailSettings.defaults().toBuilder()).build())
                    .handle(sender, mail(List.of(new ItemCount(sword.handle(), 1)), null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailRefused.of(MailAction.SEND, expected));
            assertThat(transactions.submitted()).isEmpty();
            assertThat(items.pending()).isFalse();
        }

        static Stream<Arguments> refusedByRules() {
            final ParticipantState town = FakeParticipants.IN_TOWN;
            final MailSettings.Attachments rules = MailSettings.defaults().attachments();
            return Stream.of(
                    Arguments.of("postbox off", settings(b -> b.enabled(false)), town, MailRefusal.DISABLED),
                    Arguments.of(
                            "access level without trade",
                            settings(b -> b),
                            town.toBuilder().tradeAllowed(false).build(),
                            MailRefusal.ACCESS_DENIED),
                    Arguments.of(
                            "attachments off",
                            settings(b -> b.attachments(
                                    rules.toBuilder().enabled(false).build())),
                            town,
                            MailRefusal.ATTACHMENTS_DISABLED),
                    Arguments.of(
                            "outside a peace zone",
                            settings(b -> b),
                            town.toBuilder().peaceZone(false).build(),
                            MailRefusal.NOT_IN_PEACE_ZONE),
                    Arguments.of(
                            "in combat when denied",
                            settings(b -> b.attachments(
                                    rules.toBuilder().denyInCombat(true).build())),
                            town.toBuilder().inCombat(true).build(),
                            MailRefusal.IN_COMBAT),
                    Arguments.of(
                            "dead when denied",
                            settings(b -> b.attachments(
                                    rules.toBuilder().denyWhenDead(true).build())),
                            town.toBuilder().dead(true).build(),
                            MailRefusal.DEAD),
                    Arguments.of(
                            "busy",
                            settings(b -> b),
                            town.toBuilder().busy(true).build(),
                            MailRefusal.BUSY),
                    Arguments.of(
                            "jailed with transactions blocked",
                            settings(b -> b),
                            town.toBuilder().transactionsBlocked(true).build(),
                            MailRefusal.JAILED),
                    Arguments.of(
                            "jailed with mail blocked",
                            settings(b -> b),
                            town.toBuilder().mailBlocked(true).build(),
                            MailRefusal.JAILED));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("allowedByRules")
        void handle_shouldSubmit_whenARuleDoesNotApply(
                String name,
                UnaryOperator<MailSettings.MailSettingsBuilder> settings,
                ParticipantState state,
                boolean attach) {
            give(FakeItems.ADENA, 10_000);
            final FakeItems.Item sword = give(SWORD, 1);
            participants.state(sender.characterHandle(), state);
            transactions.answer(committedTo(receiver));

            handler(settings.apply(MailSettings.defaults().toBuilder()).build())
                    .handle(sender, mail(attach ? List.of(new ItemCount(sword.handle(), 1)) : List.of(), null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailSent.INSTANCE, MailDone.of(MailNotice.SENT));
        }

        static Stream<Arguments> allowedByRules() {
            final ParticipantState town = FakeParticipants.IN_TOWN;
            final MailSettings.Attachments rules = MailSettings.defaults().attachments();
            return Stream.of(
                    Arguments.of(
                            "outside a peace zone without attachments",
                            settings(b -> b),
                            town.toBuilder().peaceZone(false).build(),
                            false),
                    Arguments.of(
                            "attachments off without attachments",
                            settings(b -> b.attachments(
                                    rules.toBuilder().enabled(false).build())),
                            town,
                            false),
                    Arguments.of(
                            "outside a peace zone when allowed",
                            settings(b -> b.attachments(
                                    rules.toBuilder().peaceZoneOnly(false).build())),
                            town.toBuilder().peaceZone(false).build(),
                            true),
                    Arguments.of(
                            "in combat by default",
                            settings(b -> b),
                            town.toBuilder().inCombat(true).build(),
                            true),
                    Arguments.of(
                            "dead by default",
                            settings(b -> b),
                            town.toBuilder().dead(true).build(),
                            true),
                    Arguments.of(
                            "jailed with transactions blocked without attachments",
                            settings(b -> b),
                            town.toBuilder().transactionsBlocked(true).build(),
                            false));
        }

        private static UnaryOperator<MailSettings.MailSettingsBuilder> settings(
                UnaryOperator<MailSettings.MailSettingsBuilder> change) {
            return change;
        }
    }

    @Nested
    class Content {
        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidTexts")
        void handle_shouldRefuseInvalidText_whenATextIsOutOfBounds(String name, SendMail mail) {
            give(FakeItems.ADENA, 10_000);

            handler(MailSettings.defaults()).handle(sender, mail);

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailRefused.of(MailAction.SEND, MailRefusal.INVALID_TEXT));
        }

        static Stream<Arguments> invalidTexts() {
            final SendMail valid = SendMail.builder()
                    .receiverName("Receiver")
                    .subject("Hello")
                    .items(List.of())
                    .build();
            return Stream.of(
                    Arguments.of(
                            "blank receiver",
                            valid.toBuilder().receiverName(" ").build()),
                    Arguments.of(
                            "receiver of 17",
                            valid.toBuilder().receiverName("a".repeat(17)).build()),
                    Arguments.of("no subject", valid.toBuilder().subject(null).build()),
                    Arguments.of(
                            "blank subject", valid.toBuilder().subject("  ").build()),
                    Arguments.of(
                            "subject of 129",
                            valid.toBuilder().subject("a".repeat(129)).build()),
                    Arguments.of(
                            "body of 513",
                            valid.toBuilder().body("a".repeat(513)).build()));
        }

        @Test
        void handle_shouldAccept_whenTextsAreAtTheirLimits() {
            give(FakeItems.ADENA, 10_000);
            transactions.answer(committedTo(receiver));

            handler(MailSettings.defaults())
                    .handle(
                            sender,
                            SendMail.builder()
                                    .receiverName("a".repeat(16))
                                    .subject("a".repeat(128))
                                    .body("a".repeat(512))
                                    .items(List.of())
                                    .build());

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailSent.INSTANCE, MailDone.of(MailNotice.SENT));
        }

        @Test
        void handle_shouldRefuseTooManyAttachments_whenMoreThanTheSettingAreAttached() {
            give(FakeItems.ADENA, 100_000);
            final List<ItemCount> three = IntStream.range(0, 3)
                    .mapToObj(i -> new ItemCount(give(SWORD, 1).handle(), 1))
                    .toList();

            handler(MailSettings.defaults().toBuilder().maxAttachments(2).build())
                    .handle(sender, mail(three, null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailRefused.of(MailAction.SEND, MailRefusal.TOO_MANY_ATTACHMENTS));
        }

        @Test
        void handle_shouldRefuseTooManyAttachments_whenNineAreAttached() {
            give(FakeItems.ADENA, 100_000);
            final List<ItemCount> nine = IntStream.range(0, 9)
                    .mapToObj(i -> new ItemCount(give(SWORD, 1).handle(), 1))
                    .toList();

            handler(MailSettings.defaults()).handle(sender, mail(nine, null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailRefused.of(MailAction.SEND, MailRefusal.TOO_MANY_ATTACHMENTS));
        }

        @Test
        void handle_shouldRefuseCod_whenNothingIsAttached() {
            give(FakeItems.ADENA, 10_000);

            handler(MailSettings.defaults()).handle(sender, mail(List.of(), 500L));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailRefused.of(MailAction.SEND, MailRefusal.COD_WITHOUT_ATTACHMENTS));
        }

        @ParameterizedTest(name = "cod {0}")
        @MethodSource("codsOutOfRange")
        void handle_shouldRefuseTheAmount_whenTheCodIsOutOfRange(long codAdena) {
            final FakeItems.Item sword = give(SWORD, 1);
            give(FakeItems.ADENA, 10_000);

            handler(MailSettings.defaults()).handle(sender, mail(List.of(new ItemCount(sword.handle(), 1)), codAdena));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailRefused.of(MailAction.SEND, MailRefusal.COD_AMOUNT_INVALID));
            assertThat(transactions.submitted()).isEmpty();
        }

        static Stream<Long> codsOutOfRange() {
            return Stream.of(0L, -1L, FakeItems.ADENA_LIMIT + 1);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unmailable")
        void handle_shouldRefuseItemNotMailable_whenAnAttachmentIsNotMailable(String name, List<ItemCount> attached) {
            give(FakeItems.ADENA, 10_000);
            give(SWORD, 1);

            handler(MailSettings.defaults()).handle(sender, mail(attached, null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailRefused.of(MailAction.SEND, MailRefusal.ITEM_NOT_MAILABLE));
        }

        static Stream<Arguments> unmailable() {
            // give() hands out 100 to the adena and 101 to the sword.
            return Stream.of(
                    Arguments.of("unknown handle", List.of(new ItemCount(999, 1))),
                    Arguments.of("handle twice", List.of(new ItemCount(101, 1), new ItemCount(101, 1))),
                    Arguments.of("zero count", List.of(new ItemCount(101, 0))),
                    Arguments.of("more than owned", List.of(new ItemCount(101, 2))));
        }
    }

    @Nested
    class Receiver {
        @Test
        void handle_shouldRefuseBeforeReserving_whenTheReceiverInTheWorldRefusesTheSender() {
            give(FakeItems.ADENA, 10_000);
            final FakeItems.Item sword = give(SWORD, 1);
            participants.refuseMail("Receiver", sender.characterId());

            handler(MailSettings.defaults()).handle(sender, mail(List.of(new ItemCount(sword.handle(), 1)), null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(new MailRefused(MailAction.SEND, MailRefusal.RECEIVER_BLOCKED, "Receiver"));
            assertThat(transactions.submitted()).isEmpty();
            assertThat(items.pending()).isFalse();
        }
    }

    @Nested
    class Reservation {
        @Test
        void handle_shouldReserveTheFee_whenItemsAreAttached() {
            give(FakeItems.ADENA, 10_000);
            final FakeItems.Item sword = give(SWORD, 1);
            final FakeItems.Item shield = give(SWORD + 1, 1);
            transactions.answer(committedTo(receiver));

            handler(MailSettings.defaults())
                    .handle(
                            sender,
                            mail(List.of(new ItemCount(sword.handle(), 1), new ItemCount(shield.handle(), 1)), null));

            assertThat(items.appliedOutgoing())
                    .singleElement()
                    .satisfies(
                            reservation -> assertThat(reservation.adenaAmount()).isEqualTo(100 + (2 * 1000)));
        }

        @Test
        void handle_shouldRefuseAndKeepTheInterval_whenTheFeeIsNotAffordable() {
            give(FakeItems.ADENA, 99);
            final SendMailHandler handler = handler(MailSettings.defaults());

            handler.handle(sender, mail(List.of(), null));
            handler.handle(sender, mail(List.of(), null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(
                            MailRefused.of(MailAction.SEND, MailRefusal.NOT_ENOUGH_ADENA),
                            MailRefused.of(MailAction.SEND, MailRefusal.NOT_ENOUGH_ADENA));
        }
    }

    @Nested
    class SendInterval {
        @Test
        void handle_shouldRefuse_whenTheLastSendIsWithinTheInterval() {
            give(FakeItems.ADENA, 10_000);
            transactions.answer(committedTo(receiver));
            final SendMailHandler handler = handler(MailSettings.defaults());

            handler.handle(sender, mail(List.of(), null));
            clock.advance(Duration.ofSeconds(9));
            handler.handle(sender, mail(List.of(), null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(
                            MailSent.INSTANCE,
                            MailDone.of(MailNotice.SENT),
                            MailRefused.of(MailAction.SEND, MailRefusal.SEND_INTERVAL));
        }

        @Test
        void handle_shouldSend_whenTheIntervalPassed() {
            give(FakeItems.ADENA, 10_000);
            transactions.answer(committedTo(receiver));
            transactions.answer(committedTo(receiver));
            final SendMailHandler handler = handler(MailSettings.defaults());

            handler.handle(sender, mail(List.of(), null));
            clock.advance(Duration.ofSeconds(10));
            handler.handle(sender, mail(List.of(), null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(
                            MailSent.INSTANCE,
                            MailDone.of(MailNotice.SENT),
                            MailSent.INSTANCE,
                            MailDone.of(MailNotice.SENT));
        }

        @Test
        void handle_shouldNotLimit_whenTheIntervalIsZero() {
            give(FakeItems.ADENA, 10_000);
            transactions.answer(committedTo(receiver));
            transactions.answer(committedTo(receiver));
            final SendMailHandler handler = handler(MailSettings.defaults().toBuilder()
                    .sendInterval(Duration.ZERO)
                    .build());

            handler.handle(sender, mail(List.of(), null));
            handler.handle(sender, mail(List.of(), null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(
                            MailSent.INSTANCE,
                            MailDone.of(MailNotice.SENT),
                            MailSent.INSTANCE,
                            MailDone.of(MailNotice.SENT));
        }

        @Test
        void handle_shouldGiveTheIntervalBack_whenTheTransactionIsRefused() {
            give(FakeItems.ADENA, 10_000);
            transactions.answer(new ValueOutcome.Refused<>(MailRefusal.RECEIVER_NOT_FOUND, null));
            transactions.answer(committedTo(receiver));
            final SendMailHandler handler = handler(MailSettings.defaults());

            handler.handle(sender, mail(List.of(), null));
            handler.handle(sender, mail(List.of(), null));

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(
                            MailRefused.of(MailAction.SEND, MailRefusal.RECEIVER_NOT_FOUND),
                            MailSent.INSTANCE,
                            MailDone.of(MailNotice.SENT));
        }
    }

    @Nested
    class Outcome {
        @Test
        void handle_shouldApplyAndNotifyBoth_whenCommitted() {
            give(FakeItems.ADENA, 10_000);
            final FakeItems.Item sword = give(SWORD, 1);
            transactions.answer(committedTo(receiver));

            handler(MailSettings.defaults()).handle(sender, mail(List.of(new ItemCount(sword.handle(), 1)), 500L));

            assertThat(items.appliedOutgoing()).hasSize(1);
            assertThat(items.pending()).isFalse();
            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailSent.INSTANCE, MailDone.of(MailNotice.SENT));
            assertThat(FakeParticipants.port(receiver).events()).containsExactly(MailArrived.FRESH);
            assertThat(participants.inFlight(sender.characterId())).isZero();
        }

        @Test
        void handle_shouldNotNotify_whenTheReceiverIsOffline() {
            give(FakeItems.ADENA, 10_000);
            transactions.answer(committedTo(receiver));
            participants.leave(receiver.characterId());

            handler(MailSettings.defaults()).handle(sender, mail(List.of(), null));

            assertThat(FakeParticipants.port(receiver).events()).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("notCommitted")
        void handle_shouldReleaseAndRefuse_whenNothingCommitted(
                String name, ValueOutcome<?> outcome, ValueOutcome<Void> lookup, MailRefused expected) {
            give(FakeItems.ADENA, 10_000);
            transactions.answer(outcome);
            if (lookup != null) {
                transactions.answerLookup(lookup);
            }

            handler(MailSettings.defaults()).handle(sender, mail(List.of(), null));

            assertThat(items.released()).hasSize(1);
            assertThat(items.pending()).isFalse();
            assertThat(FakeParticipants.port(sender).events()).containsExactly(expected);
            assertThat(participants.inFlight(sender.characterId())).isZero();
        }

        static Stream<Arguments> notCommitted() {
            final RuntimeException lost = new RuntimeException("connection lost");
            return Stream.of(
                    Arguments.of(
                            "refused with a parameter",
                            new ValueOutcome.Refused<>(MailRefusal.RECEIVER_BLOCKED, "Receiver"),
                            null,
                            new MailRefused(MailAction.SEND, MailRefusal.RECEIVER_BLOCKED, "Receiver")),
                    Arguments.of(
                            "failed",
                            new ValueOutcome.Failed<>(lost),
                            null,
                            MailRefused.of(MailAction.SEND, MailRefusal.FAILED)),
                    Arguments.of(
                            "unknown, then not committed",
                            new ValueOutcome.Unknown<>(lost),
                            new ValueOutcome.Failed<Void>(lost),
                            MailRefused.of(MailAction.SEND, MailRefusal.FAILED)));
        }

        @Test
        void handle_shouldApply_whenAnUnknownOutcomeTurnsOutCommitted() {
            give(FakeItems.ADENA, 10_000);
            transactions.answer(new ValueOutcome.Unknown<>(new RuntimeException("connection lost")));
            transactions.answerLookup(new ValueOutcome.Replayed<>(EntityIds.next(), UUID.randomUUID(), List.of()));

            handler(MailSettings.defaults()).handle(sender, mail(List.of(), null));

            assertThat(items.appliedOutgoing()).hasSize(1);
            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailSent.INSTANCE, MailDone.of(MailNotice.SENT));
            assertThat(participants.inFlight(sender.characterId())).isZero();
        }

        @Test
        void handle_shouldKeepTheReservationAndLookUpAgain_whenTheOutcomeStaysUnknown() {
            give(FakeItems.ADENA, 10_000);
            final RuntimeException lost = new RuntimeException("connection lost");
            transactions.answer(new ValueOutcome.Unknown<>(lost));
            for (int i = 0; i < 5; i++) {
                transactions.answerLookup(new ValueOutcome.Unknown<>(lost));
            }
            transactions.answerLookup(new ValueOutcome.Replayed<>(EntityIds.next(), null, List.of()));
            final SendMailHandler handler = handler(MailSettings.defaults());

            handler.handle(sender, mail(List.of(), null));
            for (int i = 0; i < 4; i++) {
                scheduler.runDue();
            }

            assertThat(items.pending()).isTrue();
            assertThat(FakeParticipants.port(sender).events()).isEmpty();
            assertThat(participants.inFlight(sender.characterId())).isOne();
            assertThat(scheduler.delays())
                    .containsExactly(
                            Duration.ofSeconds(1),
                            Duration.ofSeconds(5),
                            Duration.ofSeconds(30),
                            Duration.ofSeconds(60),
                            Duration.ofSeconds(60));

            scheduler.runDue();

            assertThat(items.appliedOutgoing()).hasSize(1);
            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailSent.INSTANCE, MailDone.of(MailNotice.SENT));
            assertThat(participants.inFlight(sender.characterId())).isZero();
        }
    }
}
