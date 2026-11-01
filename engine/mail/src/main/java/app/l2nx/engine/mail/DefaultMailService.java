/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.persistence.value.ItemMovement;
import app.l2nx.engine.core.persistence.value.ValueOutcomeHandler;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.mail.repository.CharacterDeletedTransaction;
import app.l2nx.engine.mail.repository.MailRepository;
import app.l2nx.engine.mail.repository.MailWriter;
import app.l2nx.engine.mail.repository.ReturnTransaction;
import app.l2nx.engine.mail.repository.SendTransaction;
import app.l2nx.engine.mail.repository.SystemSendTransaction;
import app.l2nx.engine.mail.view.MailArrived;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class DefaultMailService implements MailService {
    private final MailParticipants participants;
    private final MailItemStore store;
    private final BackgroundQueries queries;
    private final MailRepository mails;
    private final ValueTransactionSubmitter submitter;
    private final MailWriter writer;
    private final MailSettings settings;

    @Override
    public void sendSystem(SystemMail mail) {
        store.checkCreatable(mail.items());
        submitter.submit(new SystemSendTransaction(writer, mail), participants.loop(), new ValueOutcomeHandler<>() {
            @Override
            public void committed(SendTransaction.Sent result) {
                participants
                        .online(result.receiverId())
                        .ifPresent(receiver -> receiver.port().emit(MailArrived.FRESH));
            }

            @Override
            public void replayed(List<ItemMovement> movements) {
                log.debug("System mail {} was sent before", mail.idempotencyKey());
            }

            @Override
            public void refused(Enum<?> reason, String param) {
                log.warn("System mail {} to {} not sent: {}", mail.idempotencyKey(), mail.receiverId(), reason);
            }

            @Override
            public void failed() {
                refused(MailRefusal.FAILED, null);
            }
        });
    }

    @Override
    public void hasAttached(UUID characterId, IntSet itemTemplateIds, Executor resultLoop, Consumer<Boolean> result) {
        final List<Integer> templates = List.copyOf(itemTemplateIds);
        queries.run(() -> mails.hasAttached(characterId, templates), resultLoop, result);
    }

    @Override
    public void onCharacterDeleted(UUID characterId, UUID idempotencyKey, Executor resultLoop, Consumer<Boolean> done) {
        final CharacterDeletedTransaction deleted = CharacterDeletedTransaction.builder()
                .writer(writer)
                .idempotencyKey(idempotencyKey)
                .characterId(characterId)
                .build();
        submitter.submit(deleted, resultLoop, new ValueOutcomeHandler<>() {
            @Override
            public void committed(List<ReturnTransaction.Returned> returned) {
                // The senders are notified on the world loop, whichever loop the caller waits on.
                participants
                        .loop()
                        .execute(() -> returned.forEach(mail -> participants
                                .online(mail.senderId())
                                .ifPresent(sender -> sender.port().emit(MailArrived.FRESH))));
                done.accept(true);
            }

            @Override
            public void replayed(List<ItemMovement> movements) {
                done.accept(true);
            }

            @Override
            public void refused(Enum<?> reason, String param) {
                log.error("Mail of deleted character {} not settled: {}", characterId, reason);
                done.accept(false);
            }

            @Override
            public void failed() {
                refused(MailRefusal.FAILED, null);
            }
        });
    }

    @Override
    public void onWorldEntered(MailActor actor) {
        if (!settings.enabled()) {
            return;
        }
        queries.run(() -> mails.countUnread(actor.characterId()), participants.loop(), unread -> {
            if (unread > 0) {
                actor.port().emit(MailArrived.WAITING);
            }
        });
    }
}
