/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.mail.fixture.MailWorld;

final class MailHandlers {
    private MailHandlers() {}

    static SendMailHandler send(MailWorld world) {
        return new SendMailHandler(
                world.settings, world.participants, world.items, world.submitter, world.writer, world.clock);
    }

    static ReceiveAttachmentsHandler receive(MailWorld world) {
        return receive(world, world.submitter);
    }

    static ReceiveAttachmentsHandler receive(MailWorld world, ValueTransactionSubmitter submitter) {
        return new ReceiveAttachmentsHandler(
                world.settings,
                world.participants,
                world.items,
                world.queries,
                world.mails,
                world.attachments,
                world.store,
                submitter,
                world.writer);
    }

    static CancelMailHandler cancel(MailWorld world) {
        return cancel(world, world.submitter);
    }

    static CancelMailHandler cancel(MailWorld world, ValueTransactionSubmitter submitter) {
        return new CancelMailHandler(
                world.settings,
                world.participants,
                world.items,
                world.queries,
                world.mails,
                world.attachments,
                world.store,
                submitter,
                world.writer);
    }

    static ReturnMailHandler returnMail(MailWorld world) {
        return new ReturnMailHandler(
                world.settings, world.participants, world.queries, world.mails, world.submitter, world.writer);
    }

    static ListReceivedMailHandler listReceived(MailWorld world) {
        return new ListReceivedMailHandler(world.settings, world.participants, world.queries, world.mails);
    }

    static ListSentMailHandler listSent(MailWorld world) {
        return new ListSentMailHandler(world.settings, world.participants, world.queries, world.mails);
    }

    static OpenReceivedMailHandler openReceived(MailWorld world) {
        return new OpenReceivedMailHandler(
                world.settings, world.participants, world.queries, world.mails, world.attachments, world.clock);
    }

    static OpenSentMailHandler openSent(MailWorld world) {
        return new OpenSentMailHandler(
                world.settings, world.participants, world.queries, world.mails, world.attachments);
    }

    static DeleteReceivedMailHandler deleteReceived(MailWorld world) {
        return new DeleteReceivedMailHandler(
                world.settings, world.participants, world.queries, world.mails, world.clock);
    }

    static DeleteSentMailHandler deleteSent(MailWorld world) {
        return new DeleteSentMailHandler(world.settings, world.participants, world.queries, world.mails, world.clock);
    }

    static ListMailableItemsHandler listMailable(MailWorld world) {
        return new ListMailableItemsHandler(world.settings, world.participants, world.items);
    }
}
