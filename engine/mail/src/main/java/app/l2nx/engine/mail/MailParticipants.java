/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import app.l2nx.engine.core.session.command.ContextResolver;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/** The chronicle side over characters; everything but {@link #loop()} is called on the world loop only. */
public interface MailParticipants {
    ContextResolver<MailActor> context();

    /** The world partition, where query results and transaction outcomes are delivered. */
    Executor loop();

    ParticipantState state(int characterHandle);

    Optional<MailActor> online(UUID characterId);

    /**
     * A character whose rows are being read for its selection is neither online nor offline: nothing writes its rows
     * by delta until its load ends.
     */
    boolean loading(UUID characterId);

    /** Refusal by in-world state the database lacks (a message refusal mode); an offline receiver refuses nothing here. */
    boolean refusesMailFrom(String receiverName, UUID senderId);

    /** Holds the character's world entry, logout and save until the matching {@link #endTransaction}, online or not. */
    void beginTransaction(UUID characterId);

    void endTransaction(UUID characterId);
}
