/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** What scripts, the platform and the chronicle ask of the postbox outside the client's commands. */
public interface MailService {
    /**
     * Creates the items in the mail inside the send transaction; a repeated key sends nothing again.
     *
     * @throws IllegalArgumentException at once, before anything is sent, for an item the chronicle cannot create
     */
    void sendSystem(SystemMail mail);

    void hasAttached(UUID characterId, IntSet itemTemplateIds, Executor resultLoop, Consumer<Boolean> result);

    /**
     * Returns received player mail to living senders, destroys attachments nobody else can get (settled
     * {@code DESTROYED}) and drops cash on delivery of sent mail, in one transaction. Remove the character's row only
     * once {@code done} got {@code true}.
     */
    void onCharacterDeleted(UUID characterId, UUID idempotencyKey, Executor resultLoop, Consumer<Boolean> done);

    /** Called on the world loop. */
    void onWorldEntered(MailActor actor);
}
