/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267.game;

import app.l2nx.load.bot.intent.CreateCharacter;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.Logout;
import app.l2nx.load.bot.intent.SelectCharacter;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.Entered;
import app.l2nx.load.bot.observation.StatusChanged;
import app.l2nx.load.protocol.SessionListener;
import app.l2nx.logging.RateLimitedLog;
import app.l2nx.network.bootstrap.L2Pipeline;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.clientpackets.AuthLogin;
import app.l2nx.protocol.game.p267.clientpackets.CharacterCreate;
import app.l2nx.protocol.game.p267.clientpackets.CharacterSelect;
import app.l2nx.protocol.game.p267.clientpackets.EnterWorld;
import app.l2nx.protocol.game.p267.clientpackets.ProtocolVersion;
import app.l2nx.protocol.game.p267.clientpackets.RequestRestart;
import app.l2nx.protocol.game.p267.crypt.GameFrameCipher;
import app.l2nx.protocol.game.p267.crypt.GameXorKeys;
import app.l2nx.protocol.game.p267.serverpackets.CharCreateFail;
import app.l2nx.protocol.game.p267.serverpackets.CharSelected;
import app.l2nx.protocol.game.p267.serverpackets.CharSelectionInfo;
import app.l2nx.protocol.game.p267.serverpackets.GameServerOpcode;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.KeyPacket;
import app.l2nx.protocol.game.p267.serverpackets.LoginFail;
import app.l2nx.protocol.game.p267.serverpackets.RestartResponse;
import app.l2nx.protocol.game.p267.serverpackets.head.UserInfoHead;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * The game connection of one load bot, up to {@code EnterWorld}, then {@link GameWorld}. A listener call may close the
 * session synchronously, so {@link #detached} is checked after every one.
 */
@Slf4j
public final class GameExchange extends ChannelInboundHandlerAdapter {
    /** The oldest of the High Five clients the protocol stands for. */
    static final int PROTOCOL_VERSION = 267;

    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();
    private static final byte[] NO_ROUTE = new byte[EnterWorld.HOPS * EnterWorld.OCTETS];
    private static final int PAYLOAD_HINT = 128;

    private final GameFrameCipher cipher = GameFrameCipher.client();
    private final GameTicket ticket;
    private final SessionListener listener;
    private final LongSupplier clock;
    private final ReadClock readClock;
    private Channel channel;
    private Phase phase = Phase.CONNECTING;
    private GameWorld world;
    private int characters;
    private CharSelected selected;
    private boolean detached;

    public GameExchange(GameTicket ticket, SessionListener listener, LongSupplier clock) {
        this.ticket = ticket;
        this.listener = listener;
        this.clock = clock;
        readClock = new ReadClock(clock);
    }

    public void install(Channel channel) {
        this.channel = channel;
        channel.pipeline().addLast(ReadClock.NAME, readClock);
        // Frames up to the protocol's limit: a skill or item list runs far past what a client ever sends.
        L2Pipeline.installCodec(channel.pipeline(), cipher, Frames.MAX_LENGTH, Connections.metrics(channel));
        channel.pipeline().addLast(PipelineNames.SESSION, this);
    }

    public void send(Intent intent) {
        if (detached) {
            return;
        }
        switch (intent) {
            case CreateCharacter character -> create(character);
            case SelectCharacter select -> select(select);
            case app.l2nx.load.bot.intent.EnterWorld _ -> enterWorld();
            case Logout _ -> logout();
            default -> {
                if (world == null || !world.send(intent)) {
                    fail(cannotSend(intent.kind()));
                }
            }
        }
    }

    /** Ignored once the session is closed: closing fails a pending connect. */
    public void connectFailed(Throwable cause) {
        fail(cause);
    }

    public void close() {
        detach();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!detached) {
            phase = Phase.AWAIT_KEY;
            write(new ProtocolVersion(PROTOCOL_VERSION)::encode);
        }
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!detached) {
            stopWorld();
            detached = true;
            phase = Phase.DONE;
            listener.stateChanged(LoadBotState.STOPPED);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.fireChannelRead(msg);
            return;
        }
        try {
            if (!detached) {
                read(frame);
                listener.frameHandled(readClock.nanosSinceRead());
            }
        } finally {
            frame.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        fail(cause);
    }

    private void read(ByteBuf frame) {
        final int start = frame.readerIndex();
        try {
            final GameServerOpcode opcode = GameServerOpcode.of(frame.readUnsignedByte());
            if (opcode != null) {
                handle(opcode, frame);
            }
        } catch (PacketDecodeException e) {
            log.warn(
                    "A game server frame from {} did not decode ({}): {}",
                    channel.remoteAddress(),
                    e.getMessage(),
                    ByteBufUtil.hexDump(frame, start, frame.writerIndex() - start));
            fail(e);
        }
    }

    private void handle(GameServerOpcode opcode, ByteBuf body) {
        switch (phase) {
            case AWAIT_KEY -> {
                if (opcode == GameServerOpcode.VERSION_CHECK) {
                    key(KeyPacket.read(body));
                }
            }
            case AWAIT_CHARACTERS -> {
                if (opcode == GameServerOpcode.CHARACTER_SELECTION_INFO) {
                    listed(CharSelectionInfo.read(body));
                } else if (opcode == GameServerOpcode.LOGIN_FAIL) {
                    fail(new IllegalStateException("The game server refused the session: " + LoginFail.read(body)));
                }
            }
            case AWAIT_CREATED -> {
                if (opcode == GameServerOpcode.CHARACTER_CREATE_SUCCESS) {
                    created();
                } else if (opcode == GameServerOpcode.CHARACTER_CREATE_FAIL) {
                    reject(IntentKind.CREATE_CHARACTER, CharCreateFail.read(body));
                }
            }
            case AWAIT_SELECTED -> {
                if (opcode == GameServerOpcode.CHARACTER_SELECTED) {
                    selected(CharSelected.read(body));
                }
            }
            case AWAIT_WORLD -> {
                if (opcode == GameServerOpcode.USER_INFO) {
                    entered(UserInfoHead.read(body));
                }
            }
            case IN_WORLD -> world.frame(opcode, body);
            case LOGGING_OUT -> {
                if (opcode == GameServerOpcode.RESTART_RESPONSE) {
                    loggedOut(RestartResponse.read(body));
                } else {
                    world.frame(opcode, body);
                }
            }
            default -> {}
        }
    }

    private void key(KeyPacket packet) {
        if (!packet.accepted()) {
            fail(new IllegalStateException("The game server refused protocol version " + PROTOCOL_VERSION));
            return;
        }
        cipher.setKey(GameXorKeys.fromSent(packet.key()));
        phase = Phase.AWAIT_CHARACTERS;
        write(AuthLogin.builder()
                .login(ticket.account())
                .playOk2(ticket.playOk2())
                .playOk1(ticket.playOk1())
                .loginOk1(ticket.loginOk1())
                .loginOk2(ticket.loginOk2())
                .build()::encode);
    }

    private void listed(CharSelectionInfo list) {
        characters = list.slots().size();
        phase = Phase.CHARACTERS;
        listener.stateChanged(LoadBotState.CHARACTER_SELECT);
    }

    private void create(CreateCharacter character) {
        if (phase != Phase.CHARACTERS) {
            fail(cannotSend(character.kind()));
            return;
        }
        phase = Phase.AWAIT_CREATED;
        write(CharacterCreate.builder()
                .name(character.name())
                .race(character.race())
                .classId(character.classId())
                .build()::encode);
    }

    /** The server answers with this packet alone: the client's list is not sent again. */
    private void created() {
        characters++;
        phase = Phase.CHARACTERS;
        listener.acknowledged(IntentKind.CREATE_CHARACTER);
    }

    private void select(SelectCharacter intent) {
        if (phase != Phase.CHARACTERS) {
            fail(cannotSend(intent.kind()));
            return;
        }
        // The server ignores a slot it does not have: the load bot would wait out the lifecycle timeout for nothing.
        if (intent.slot() >= characters) {
            fail(new IllegalStateException("The account " + ticket.account() + " has " + characters
                    + " characters, no slot " + intent.slot() + ": is the database seeded?"));
            return;
        }
        phase = Phase.AWAIT_SELECTED;
        write(new CharacterSelect(intent.slot())::encode);
    }

    private void selected(CharSelected packet) {
        selected = packet;
        phase = Phase.SELECTED;
        listener.acknowledged(IntentKind.SELECT_CHARACTER);
        if (!detached) {
            listener.stateChanged(LoadBotState.ENTERING);
        }
    }

    private void enterWorld() {
        if (phase != Phase.SELECTED) {
            fail(cannotSend(IntentKind.ENTER_WORLD));
            return;
        }
        phase = Phase.AWAIT_WORLD;
        write(new EnterWorld(NO_ROUTE)::encode);
    }

    /** {@code UserInfo} is the first frame of the entry, which the server runs whole before any later packet. */
    private void entered(UserInfoHead head) {
        final Position position =
                Position.builder().x(head.x()).y(head.y()).z(head.z()).build();
        final int hp = (int) selected.currentHp();
        final int mp = (int) selected.currentMp();
        world = new GameWorld(
                head.objectId(), position, hp, mp, head.runSpeed(), listener, this::write, channel.eventLoop(), clock);
        phase = Phase.IN_WORLD;
        listener.acknowledged(IntentKind.ENTER_WORLD);
        if (detached) {
            return;
        }
        listener.observed(Entered.builder()
                .selfObjectId(head.objectId())
                .position(position)
                .runSpeed(head.runSpeed())
                .build());
        listener.observed(StatusChanged.builder().hp(hp).mp(mp).build());
        world.startValidating();
        listener.stateChanged(LoadBotState.IN_GAME);
    }

    /** {@code RequestRestart} stores the character like a logout and leaves the session at the list, then it closes. */
    private void logout() {
        if (phase != Phase.IN_WORLD) {
            fail(cannotSend(IntentKind.LOGOUT));
            return;
        }
        phase = Phase.LOGGING_OUT;
        write(RequestRestart.INSTANCE::encode);
    }

    private void loggedOut(RestartResponse response) {
        if (!response.accepted()) {
            // In combat: the character stays in the world until the server drops it with the connection.
            reject(IntentKind.LOGOUT, response);
            return;
        }
        listener.acknowledged(IntentKind.LOGOUT);
        if (detached) {
            return;
        }
        listener.stateChanged(LoadBotState.CLOSING);
        if (!detached) {
            channel.close();
        }
    }

    private void reject(IntentKind kind, GameServerPacket refusal) {
        if (RATE.tryAcquire(refusal.toString())) {
            log.warn("The game server refused {} with {}", kind, refusal);
        }
        phase = Phase.DONE;
        listener.rejected(kind);
        if (!detached) {
            channel.close();
        }
    }

    private void fail(Throwable cause) {
        if (detached) {
            return;
        }
        detach();
        listener.failed(cause);
    }

    private void detach() {
        stopWorld();
        detached = true;
        phase = Phase.DONE;
        if (channel != null) {
            channel.close();
        }
    }

    private void stopWorld() {
        if (world != null) {
            world.stop();
        }
    }

    private IllegalStateException cannotSend(IntentKind kind) {
        return new IllegalStateException("Protocol p267 cannot send " + kind + " in game phase " + phase);
    }

    private void write(Consumer<ByteBuf> packet) {
        final ByteBuf frame = Frames.allocate(channel.alloc(), PAYLOAD_HINT);
        packet.accept(frame);
        channel.writeAndFlush(frame, channel.voidPromise());
    }

    private enum Phase {
        CONNECTING,
        AWAIT_KEY,
        AWAIT_CHARACTERS,
        CHARACTERS,
        AWAIT_CREATED,
        AWAIT_SELECTED,
        SELECTED,
        AWAIT_WORLD,
        IN_WORLD,
        LOGGING_OUT,
        DONE
    }
}
