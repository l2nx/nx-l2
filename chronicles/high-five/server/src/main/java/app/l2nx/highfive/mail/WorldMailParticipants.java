/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mail;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailParticipants;
import app.l2nx.engine.mail.ParticipantState;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.BlockList;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.session.GameContexts;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WorldMailParticipants implements MailParticipants {
    private static final Executor WORLD_LOOP = WorldPartition::offer;

    private final IdentityRegistry registry;

    private final ContextResolver<MailActor> context = new ContextResolver<>() {
        @Override
        public String name() {
            return GameContexts.WORLD.name();
        }

        @Override
        public MailActor resolve(Session session) {
            final Player player = GameContexts.WORLD.resolve(session);
            return player == null ? null : actor(player);
        }
    };

    @Override
    public ContextResolver<MailActor> context() {
        return context;
    }

    @Override
    public Executor loop() {
        return WORLD_LOOP;
    }

    @Override
    public ParticipantState state(int characterHandle) {
        final Player player = World.getPlayer(characterHandle);
        if (player == null) {
            throw new IllegalStateException("No character " + characterHandle + " in the world");
        }
        final boolean jailed = player.isJailed();
        return ParticipantState.builder()
                .peaceZone(player.isInsideZone(ZoneId.PEACE))
                .inCombat(player.isInCombat()
                        || AttackStanceTaskManager.getInstance().hasAttackStanceTask(player))
                .dead(player.isAlikeDead())
                .busy((player.getActiveTradeList() != null)
                        || player.isInStoreMode()
                        || player.isEnchanting()
                        || player.isInventoryDisabled())
                .tradeAllowed(player.getAccessLevel().allowTransaction())
                // The legacy jail rules: chat cut off means no mail at all, transactions cut off no attachments.
                .mailBlocked(jailed && GeneralConfig.JAIL_DISABLE_CHAT)
                .transactionsBlocked(jailed && GeneralConfig.JAIL_DISABLE_TRANSACTION)
                .gm(player.isGM())
                .build();
    }

    /** A session-loaded character counts before world entry: its memory owns its items from the load on. */
    @Override
    public Optional<MailActor> online(UUID characterId) {
        final Optional<Player> loaded = WorldPartition.loadedCharacters().find(characterId);
        if (loaded.isPresent()) {
            return loaded.map(this::actor);
        }
        final OptionalInt handle = registry.findHandle(characterId);
        if (handle.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(World.getPlayer(handle.getAsInt())).map(this::actor);
    }

    /** The block-list check also covers the receiver's message refusal mode. */
    @Override
    public boolean refusesMailFrom(String receiverName, UUID senderId) {
        final Player receiver = World.getPlayer(receiverName);
        return (receiver != null) && BlockList.isBlocked(receiver, registry.handleOf(senderId));
    }

    @Override
    public void beginTransaction(UUID characterId) {
        WorldPartition.inFlightCharacters().begin(characterId);
    }

    @Override
    public void endTransaction(UUID characterId) {
        WorldPartition.inFlightCharacters().end(characterId);
    }

    MailActor actor(Player player) {
        return MailActor.builder()
                .characterHandle(player.getObjectId())
                .characterId(registry.idOf(player.getObjectId()))
                .name(player.getName())
                .port(player.getPort())
                .build();
    }
}
