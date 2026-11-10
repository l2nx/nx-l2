/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.highfive.character.repository.CharacterSlots;
import app.l2nx.highfive.character.view.CharacterListShown;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.character.view.ServerClosed;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.partition.OfflineRows;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.model.CharacterSlotRow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CharacterService {
    public static final int MAX_NAME_LENGTH = 16;

    private static volatile CharacterService instance;

    private final CharacterSlots slots;
    private final BackgroundQueries queries;
    private final IdentityRegistry registry;

    CharacterService(CharacterSlots slots, BackgroundQueries queries, IdentityRegistry registry) {
        this.slots = slots;
        this.queries = queries;
        this.registry = registry;
        instance = this;
    }

    // TODO: drop the static accessor once callers take the bean through their constructor.
    public static CharacterService getInstance() {
        final CharacterService bound = instance;
        if (bound == null) {
            throw new IllegalStateException("CharacterService is not bound: the Spring context has not created it");
        }
        return bound;
    }

    /** Shows the list with the most recently used character preselected. */
    public void showCharacterList(GameClient client) {
        list(client, characters -> show(client, characters, mostRecent(characters)));
    }

    public void showCharacterList(GameClient client, int activeSlot) {
        list(client, characters -> show(client, characters, activeSlot));
    }

    public void refreshSlots(GameClient client) {
        list(client, client::setCharSelection);
    }

    private void list(GameClient client, Consumer<List<CharacterSummary>> onListed) {
        final UUID accountId = client.getAccountId();
        client.session().hold();
        queries.run(
                () -> slots.findByAccount(accountId),
                WorldPartition::offer,
                stored -> {
                    try {
                        onListed.accept(build(stored));
                    } catch (RuntimeException e) {
                        failed(client, accountId, e);
                    } finally {
                        client.session().release();
                    }
                },
                failure -> {
                    failed(client, accountId, failure);
                    client.session().release();
                });
    }

    private static void failed(GameClient client, UUID accountId, RuntimeException failure) {
        log.error("The character list of account {} could not be built", accountId, failure);
        client.port().emitAndClose(ServerClosed.INSTANCE);
    }

    private static void show(GameClient client, List<CharacterSummary> characters, int activeSlot) {
        client.setCharSelection(characters);
        client.port()
                .emit(CharacterListShown.builder()
                        .login(client.getAccountName())
                        .playOk1(client.getSessionId().playOk1())
                        .characters(characters)
                        .activeSlot(activeSlot)
                        .build());
    }

    private List<CharacterSummary> build(List<StoredCharacterSlot> stored) {
        final List<CharacterSummary> characters = new ArrayList<>();
        for (StoredCharacterSlot slot : stored) {
            final CharacterSlotRow row = slot.character();
            final long deleteAt = epochMillisOrZero(row.deleteAt());
            if ((deleteAt > 0) && (ClockBridge.wallMillis() >= deleteAt)) {
                final int objectId = registry.handleOf(row.id());
                // A loaded character, such as an offline trader, saves its row over the deletion: it stays due.
                if (!OfflineRows.isLoaded(objectId)) {
                    deleteExpired(objectId, row.clanId());
                    continue;
                }
            }
            final CharacterSummary character = slots.summarize(slot);
            characters.add(character);
            disconnectOfflineSession(character.objectId());
        }
        return characters;
    }

    private void deleteExpired(int objectId, UUID clanId) {
        final Clan clan = (clanId == null) ? null : ClanTable.getInstance().getClan(registry.handleOf(clanId));
        if (clan != null) {
            clan.removeClanMember(objectId, 0);
        }
        GameClient.deleteCharByObjId(objectId);
    }

    private static void disconnectOfflineSession(int objectId) {
        if (OfflineTradeConfig.OFFLINE_DISCONNECT_SAME_ACCOUNT) {
            final Player player = World.getPlayer(objectId);
            if ((player != null) && player.isInStoreMode()) {
                Disconnection.of(player).storeAndDelete();
                return;
            }
        }
        if (OfflinePlayConfig.OFFLINE_PLAY_DISCONNECT_SAME_ACCOUNT) {
            final Player player = World.getPlayer(objectId);
            if ((player != null) && player.isOfflinePlay()) {
                Disconnection.of(player).storeAndDelete();
            }
        }
    }

    private static int mostRecent(List<CharacterSummary> characters) {
        int active = CharacterListShown.NO_SLOT;
        long lastAccess = 0;
        for (int slot = 0; slot < characters.size(); slot++) {
            if (lastAccess < characters.get(slot).lastAccess()) {
                lastAccess = characters.get(slot).lastAccess();
                active = slot;
            }
        }
        return active;
    }
}
