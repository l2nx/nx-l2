/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol;

import app.l2nx.engine.core.session.command.Command;
import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable table of an opcode, and behind the extended prefix a 2-byte little-endian sub-opcode, to its route.
 */
public final class OpcodeTable {
    public static final Object UNKNOWN = new Marker("UNKNOWN");
    /** A known opcode the server deliberately does not handle; counted, unlike {@link #CONSUMED}. */
    public static final Object IGNORED = new Marker("IGNORED");
    /** Fully handled by the decoder on the IO thread; nothing reaches the session. */
    public static final Object CONSUMED = new Marker("CONSUMED");

    public enum RouteKind {
        COMMAND,
        /** Decodes a legacy packet class that runs its own code from the session queue. */
        LEGACY,
        IGNORE,
        /** Handled by the connection on the IO thread, such as the client's answer to the server's ping; never queued. */
        CONNECTION
    }

    /** {@code command} is set for a {@link RouteKind#COMMAND} route only. */
    @lombok.Builder(toBuilder = true)
    public record Route(boolean preAuth, int opcode, int exOpcode, RouteKind kind, Class<? extends Command> command) {
        public static final int NO_EX_OPCODE = -1;
    }

    public record Entry(RouteKind kind, InboundDecoder decoder, boolean preAuth) {}

    private final int exPrefix;
    private final Entry[] main;
    private final Entry[] ex;
    private final int legacyCount;
    private final List<Route> routes;

    private OpcodeTable(Builder builder) {
        exPrefix = builder.exPrefix;
        main = freeze(builder.main, 256);
        ex = freeze(builder.ex, builder.maxEx + 1);
        legacyCount = builder.legacyMain.cardinality()
                + builder.legacyEx.values().stream().mapToInt(Integer::intValue).sum();
        routes = List.copyOf(builder.routes);
    }

    public static Builder builder(int exPrefix) {
        return new Builder(exPrefix);
    }

    /**
     * Reads the opcode, and behind the extended prefix the sub-opcode, leaving the frame at the packet body.
     *
     * @return {@code null} for an unknown opcode
     */
    public Entry lookup(ByteBuf frame) {
        final int opcode = frame.readUnsignedByte();
        if (opcode == exPrefix) {
            if (frame.readableBytes() < 2) {
                throw new PacketDecodeException("Extended packet without sub-opcode");
            }
            return entry(ex, frame.readUnsignedShortLE());
        }
        return entry(main, opcode);
    }

    public int legacyCount() {
        return legacyCount;
    }

    public List<Route> routes() {
        return routes;
    }

    private static Entry entry(Entry[] table, int opcode) {
        return (opcode < table.length) ? table[opcode] : null;
    }

    private static Entry[] freeze(Map<Integer, Entry> routes, int size) {
        final Entry[] table = new Entry[size];
        routes.forEach((opcode, entry) -> table[opcode] = entry);
        return table;
    }

    private record Marker(String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public static final class Builder {
        private final int exPrefix;
        private final Map<Integer, Entry> main = new HashMap<>();
        private final Map<Integer, Entry> ex = new HashMap<>();
        private final BitSet legacyMain = new BitSet(256);
        // Packets behind each legacy extended opcode: more than one when a third id dispatches (bookmarks).
        private final Map<Integer, Integer> legacyEx = new HashMap<>();
        private final List<Route> routes = new ArrayList<>();
        private int maxEx;

        private Builder(int exPrefix) {
            this.exPrefix = exPrefix;
        }

        public Builder command(int opcode, Class<? extends Command> type, InboundDecoder decoder) {
            return addMain(opcode, RouteKind.COMMAND, type, decoder, false);
        }

        /** A command read before authentication: the login, nothing that reaches game state. */
        public Builder preAuthCommand(int opcode, Class<? extends Command> type, InboundDecoder decoder) {
            return addMain(opcode, RouteKind.COMMAND, type, decoder, true);
        }

        public Builder route(int opcode, RouteKind kind, InboundDecoder decoder) {
            return addMain(opcode, notCommand(kind), null, decoder, false);
        }

        public Builder preAuthRoute(int opcode, RouteKind kind, InboundDecoder decoder) {
            return addMain(opcode, notCommand(kind), null, decoder, true);
        }

        public Builder exCommand(int exOpcode, Class<? extends Command> type, InboundDecoder decoder) {
            return addEx(exOpcode, RouteKind.COMMAND, type, decoder, 0);
        }

        public Builder exRoute(int exOpcode, RouteKind kind, InboundDecoder decoder) {
            return addEx(exOpcode, notCommand(kind), null, decoder, kind == RouteKind.LEGACY ? 1 : 0);
        }

        /** A legacy route that picks one of {@code legacyPackets} packets by a further id; the gate counts each. */
        public Builder exLegacy(int exOpcode, InboundDecoder decoder, int legacyPackets) {
            return addEx(exOpcode, RouteKind.LEGACY, null, decoder, legacyPackets);
        }

        public OpcodeTable build() {
            return new OpcodeTable(this);
        }

        private Builder addMain(
                int opcode, RouteKind kind, Class<? extends Command> type, InboundDecoder decoder, boolean preAuth) {
            if ((opcode < 0) || (opcode > 0xFF) || (opcode == exPrefix)) {
                throw new IllegalArgumentException("Opcode 0x" + Integer.toHexString(opcode) + " cannot be routed");
            }
            put(main, opcode, new Entry(kind, decoder, preAuth), "0x" + Integer.toHexString(opcode));
            if (kind == RouteKind.LEGACY) {
                legacyMain.set(opcode);
            }
            record(opcode, Route.NO_EX_OPCODE, kind, type, preAuth);
            return this;
        }

        private Builder addEx(
                int exOpcode,
                RouteKind kind,
                Class<? extends Command> type,
                InboundDecoder decoder,
                int legacyPackets) {
            if ((exOpcode < 0) || (exOpcode > 0xFFFF)) {
                throw new IllegalArgumentException(
                        "Extended opcode 0x" + Integer.toHexString(exOpcode) + " is out of range");
            }
            put(ex, exOpcode, new Entry(kind, decoder, false), "ex 0x" + Integer.toHexString(exOpcode));
            maxEx = Math.max(maxEx, exOpcode);
            if (legacyPackets > 0) {
                legacyEx.put(exOpcode, legacyPackets);
            }
            record(exPrefix, exOpcode, kind, type, false);
            return this;
        }

        private void record(int opcode, int exOpcode, RouteKind kind, Class<? extends Command> type, boolean preAuth) {
            routes.add(Route.builder()
                    .preAuth(preAuth)
                    .opcode(opcode)
                    .exOpcode(exOpcode)
                    .kind(kind)
                    .command(type)
                    .build());
        }

        private static RouteKind notCommand(RouteKind kind) {
            if (kind == RouteKind.COMMAND) {
                throw new IllegalArgumentException("A command route names its command type: use command or exCommand");
            }
            return kind;
        }

        private static void put(Map<Integer, Entry> routes, int opcode, Entry entry, String name) {
            if (routes.putIfAbsent(opcode, entry) != null) {
                throw new IllegalStateException("Two routes for " + name);
            }
        }
    }
}
