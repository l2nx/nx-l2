/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.GameConnection;
import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Session-local numbers the client gets for mails instead of ids; showing a box renumbers only that box. Decoders read
 * on the IO thread, mappers update on the emitting thread, so each change publishes a new immutable table.
 */
public final class P267MailNumbers {
    public static final int NO_NUMBER = 0;

    private static final AttributeKey<P267MailNumbers> KEY = AttributeKey.valueOf("l2nx.p267.mailNumbers");

    private final AtomicReference<Table> table = new AtomicReference<>(Table.EMPTY);

    public static P267MailNumbers of(GameConnection connection) {
        return of(connection.channel());
    }

    public static P267MailNumbers of(GameClient viewer) {
        return of(viewer.connection().channel());
    }

    static P267MailNumbers of(Channel channel) {
        final Attribute<P267MailNumbers> attribute = channel.attr(KEY);
        final P267MailNumbers current = attribute.get();
        if (current != null) {
            return current;
        }
        final P267MailNumbers created = new P267MailNumbers();
        final P267MailNumbers raced = attribute.setIfAbsent(created);
        return (raced == null) ? created : raced;
    }

    /** Mails of the box missing from {@code mails} lose their numbers; returns numbers in {@code mails} order. */
    public int[] show(MailBox box, List<UUID> mails) {
        final Table updated = table.updateAndGet(current -> current.show(box, mails));
        final int[] numbers = new int[mails.size()];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = updated.byMail.get(mails.get(i)).number();
        }
        return numbers;
    }

    /** The number of a mail of the box, given now if it has none. */
    public int number(MailBox box, UUID mail) {
        return table.updateAndGet(current -> current.add(box, mail))
                .byMail
                .get(mail)
                .number();
    }

    /**
     * @return {@link #NO_NUMBER} for a mail the client was never shown
     */
    public int known(UUID mail) {
        final Entry entry = table.get().byMail.get(mail);
        return (entry == null) ? NO_NUMBER : entry.number();
    }

    /**
     * @return {@code null} for a number this session did not give
     */
    public UUID mail(int number) {
        return table.get().byNumber.get(number);
    }

    private record Entry(int number, MailBox box) {}

    private record Table(Map<UUID, Entry> byMail, Map<Integer, UUID> byNumber, int last) {
        static final Table EMPTY = new Table(Map.of(), Map.of(), NO_NUMBER);

        Table show(MailBox box, List<UUID> mails) {
            final Set<UUID> listed = new HashSet<>(mails);
            final Map<UUID, Entry> kept = new HashMap<>();
            final Map<Integer, UUID> numbers = new HashMap<>();
            byMail.forEach((mail, entry) -> {
                if ((entry.box() != box) || listed.contains(mail)) {
                    kept.put(mail, entry);
                    numbers.put(entry.number(), mail);
                }
            });
            int next = last;
            for (UUID mail : mails) {
                if (!kept.containsKey(mail)) {
                    next = free(next, numbers);
                    kept.put(mail, new Entry(next, box));
                    numbers.put(next, mail);
                }
            }
            return new Table(Map.copyOf(kept), Map.copyOf(numbers), next);
        }

        Table add(MailBox box, UUID mail) {
            if (byMail.containsKey(mail)) {
                return this;
            }
            final Map<UUID, Entry> kept = new HashMap<>(byMail);
            final Map<Integer, UUID> numbers = new HashMap<>(byNumber);
            final int next = free(last, numbers);
            kept.put(mail, new Entry(next, box));
            numbers.put(next, mail);
            return new Table(Map.copyOf(kept), Map.copyOf(numbers), next);
        }

        // Wraps past the int range, skipping numbers still held, so a long session never runs out.
        private static int free(int after, Map<Integer, UUID> taken) {
            int candidate = after;
            do {
                candidate = (candidate == Integer.MAX_VALUE) ? (NO_NUMBER + 1) : (candidate + 1);
            } while (taken.containsKey(candidate));
            return candidate;
        }
    }
}
