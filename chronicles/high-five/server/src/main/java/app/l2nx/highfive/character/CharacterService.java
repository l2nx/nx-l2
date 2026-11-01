/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;

import app.l2nx.highfive.character.repository.CharacterSlots;
import app.l2nx.highfive.character.view.CharacterListShown;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CharacterService {
    public static final int MAX_NAME_LENGTH = 16;

    private static volatile CharacterService instance;

    private final CharacterSlots slots;

    CharacterService(CharacterSlots slots) {
        this.slots = slots;
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
        final List<CharacterSummary> characters = load(client.getAccountId());
        show(client, characters, mostRecent(characters));
    }

    public void showCharacterList(GameClient client, int activeSlot) {
        show(client, load(client.getAccountId()), activeSlot);
    }

    public void refreshSlots(GameClient client) {
        client.setCharSelection(load(client.getAccountId()));
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

    private List<CharacterSummary> load(UUID accountId) {
        final List<CharacterSummary> characters = new ArrayList<>();
        try {
            for (CharacterRow row : slots.findByAccount(accountId)) {
                final long deleteAt = epochMillisOrZero(row.deleteAt());
                if ((deleteAt > 0) && (ClockBridge.wallMillis() >= deleteAt)) {
                    deleteExpired(row);
                    continue;
                }
                final CharacterSummary character = slots.summarize(row);
                characters.add(character);
                disconnectOfflineSession(character.objectId());
            }
        } catch (DataAccessException e) {
            log.warn("Could not restore char info: {}", e.getMessage(), e);
        }
        return characters;
    }

    private static void deleteExpired(CharacterRow row) {
        final Clan clan = ClanTable.getInstance().getClan(row.clanId());
        if (clan != null) {
            clan.removeClanMember(row.objectId(), 0);
        }
        GameClient.deleteCharByObjId(row.objectId());
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
