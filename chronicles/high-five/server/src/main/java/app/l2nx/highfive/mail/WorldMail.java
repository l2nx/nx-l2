/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mail;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.mail.MailService;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.SystemMail;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import com.github.f4b6a3.uuid.UuidCreator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Postbox for the legacy world code: every answer is delivered on the world loop. */
@Component
public class WorldMail {
    private static final String CHARACTER_DELETED = "CHARACTER_DELETED";

    private static volatile WorldMail instance;

    private final MailService mailService;
    private final MailSettings settings;
    private final WorldMailParticipants participants;
    private final IdentityRegistry registry;

    WorldMail(
            MailService mailService,
            MailSettings settings,
            WorldMailParticipants participants,
            IdentityRegistry registry) {
        this.mailService = mailService;
        this.settings = settings;
        this.participants = participants;
        this.registry = registry;
        instance = this;
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public void sendSystem(SystemMail mail) {
        mailService.sendSystem(mail);
    }

    public void onWorldEntered(Player player) {
        mailService.onWorldEntered(participants.actor(player));
    }

    /** The answer is dropped when the character left the world meanwhile. */
    public void hasAttached(Player player, int[] itemTemplateIds, Consumer<Boolean> result) {
        final int handle = player.getObjectId();
        final IntOpenHashSet templates = new IntOpenHashSet(itemTemplateIds);
        mailService.hasAttached(registry.idOf(handle), templates, participants.loop(), attached -> {
            if (World.getPlayer(handle) == player) {
                result.accept(attached);
            }
        });
    }

    /**
     * Attempts for one character share an idempotency key: a retry settles once, a repeat after the commit answers
     * {@code true} again.
     *
     * @param done {@code true} once the mail is settled and the character's row may go
     */
    public void onCharacterDeleted(UUID characterId, Consumer<Boolean> done) {
        mailService.onCharacterDeleted(characterId, deletionKey(characterId), participants.loop(), done);
    }

    static UUID deletionKey(UUID characterId) {
        return UuidCreator.getNameBasedSha1(characterId, CHARACTER_DELETED);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static WorldMail getInstance() {
        final WorldMail current = instance;
        if (current == null) {
            throw new IllegalStateException("WorldMail is used before the Spring context created it.");
        }
        return current;
    }
}
