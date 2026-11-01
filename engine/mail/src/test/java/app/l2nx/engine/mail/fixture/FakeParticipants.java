/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.fixture;

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailParticipants;
import app.l2nx.engine.mail.ParticipantState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Characters in a world whose loop runs work at once on the caller. */
public final class FakeParticipants implements MailParticipants {
    public static final ParticipantState IN_TOWN =
            ParticipantState.builder().peaceZone(true).tradeAllowed(true).build();

    private final Map<UUID, MailActor> online = new HashMap<>();
    private final Map<Integer, ParticipantState> states = new HashMap<>();
    private final Map<UUID, Integer> inFlight = new ConcurrentHashMap<>();
    private final Set<List<Object>> refusals = new HashSet<>();
    private Executor loop = Runnable::run;

    public MailActor enter(UUID characterId, int handle, String name) {
        final MailActor actor = MailActor.builder()
                .characterHandle(handle)
                .characterId(characterId)
                .name(name)
                .port(new RecordingPort())
                .build();
        online.put(characterId, actor);
        states.put(handle, IN_TOWN);
        return actor;
    }

    public void leave(UUID characterId) {
        online.remove(characterId);
    }

    public void state(int handle, ParticipantState state) {
        states.put(handle, state);
    }

    public void refuseMail(String receiverName, UUID senderId) {
        refusals.add(List.of(receiverName, senderId));
    }

    public void loop(Executor loop) {
        this.loop = loop;
    }

    public int inFlight(UUID characterId) {
        return inFlight.getOrDefault(characterId, 0);
    }

    public static RecordingPort port(MailActor actor) {
        return (RecordingPort) actor.port();
    }

    @Override
    public ContextResolver<MailActor> context() {
        return new ContextResolver<>() {
            @Override
            public String name() {
                return "mail-test";
            }

            @Override
            public MailActor resolve(Session session) {
                return null;
            }
        };
    }

    @Override
    public Executor loop() {
        return loop;
    }

    @Override
    public ParticipantState state(int characterHandle) {
        return states.getOrDefault(characterHandle, IN_TOWN);
    }

    @Override
    public Optional<MailActor> online(UUID characterId) {
        return Optional.ofNullable(online.get(characterId));
    }

    @Override
    public boolean refusesMailFrom(String receiverName, UUID senderId) {
        return refusals.contains(List.of(receiverName, senderId));
    }

    @Override
    public void beginTransaction(UUID characterId) {
        inFlight.merge(characterId, 1, Integer::sum);
    }

    @Override
    public void endTransaction(UUID characterId) {
        inFlight.compute(characterId, (id, count) -> {
            if (count == null) {
                throw new IllegalStateException("No transaction in flight for " + id);
            }
            return count == 1 ? null : count - 1;
        });
    }
}
