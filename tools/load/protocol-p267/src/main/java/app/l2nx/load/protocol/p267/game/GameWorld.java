/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267.game;

import app.l2nx.load.bot.intent.AdminCommand;
import app.l2nx.load.bot.intent.AdminTeleport;
import app.l2nx.load.bot.intent.Attack;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.MoveTo;
import app.l2nx.load.bot.intent.ResurrectAtRestartPoint;
import app.l2nx.load.bot.intent.ResurrectInPlace;
import app.l2nx.load.bot.intent.Say;
import app.l2nx.load.bot.intent.UseSkill;
import app.l2nx.load.bot.model.CharacterTeam;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.CastStarted;
import app.l2nx.load.bot.observation.Died;
import app.l2nx.load.bot.observation.ObjectGone;
import app.l2nx.load.bot.observation.ObjectMoved;
import app.l2nx.load.bot.observation.ObjectSeen;
import app.l2nx.load.bot.observation.Revived;
import app.l2nx.load.bot.observation.StatusChanged;
import app.l2nx.load.protocol.SessionListener;
import app.l2nx.protocol.game.p267.Bodies;
import app.l2nx.protocol.game.p267.ChatChannel;
import app.l2nx.protocol.game.p267.clientpackets.Action;
import app.l2nx.protocol.game.p267.clientpackets.Appearing;
import app.l2nx.protocol.game.p267.clientpackets.RequestMagicSkillUse;
import app.l2nx.protocol.game.p267.clientpackets.RequestRestartPoint;
import app.l2nx.protocol.game.p267.clientpackets.Say2;
import app.l2nx.protocol.game.p267.clientpackets.SendBypassBuildCmd;
import app.l2nx.protocol.game.p267.clientpackets.ValidatePosition;
import app.l2nx.protocol.game.p267.serverpackets.DeleteObject;
import app.l2nx.protocol.game.p267.serverpackets.Die;
import app.l2nx.protocol.game.p267.serverpackets.GameServerOpcode;
import app.l2nx.protocol.game.p267.serverpackets.MagicSkillUse;
import app.l2nx.protocol.game.p267.serverpackets.MoveToLocation;
import app.l2nx.protocol.game.p267.serverpackets.MoveToPawn;
import app.l2nx.protocol.game.p267.serverpackets.MyTargetSelected;
import app.l2nx.protocol.game.p267.serverpackets.Revive;
import app.l2nx.protocol.game.p267.serverpackets.StatusUpdate;
import app.l2nx.protocol.game.p267.serverpackets.StopMove;
import app.l2nx.protocol.game.p267.serverpackets.TeleportToLocation;
import app.l2nx.protocol.game.p267.serverpackets.ValidateLocation;
import app.l2nx.protocol.game.p267.serverpackets.head.CharInfoHead;
import app.l2nx.protocol.game.p267.serverpackets.head.NpcInfoHead;
import app.l2nx.protocol.game.p267.serverpackets.head.UserInfoHead;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Only the opcodes of {@link #frame} are decoded; broadcasts about other objects are dropped by the object id at their
 * head before any record is built.
 *
 * <p>{@code ActionFailed} is ignored: the server sends it after every click and every refusal, so it cannot be tied to
 * an intent. A refused request expires as a timeout.
 */
final class GameWorld {
    /** An {@code Action} inside one flood protector tick of the last is dropped without an answer; a tick is 100 ms. */
    static final Duration CLICK_GAP = Duration.ofMillis(150);

    static final Duration VALIDATE_INTERVAL = Duration.ofSeconds(1);

    private static final int NO_TARGET = 0;
    // Destinations the server never echoed stay until this many newer ones pushed them out.
    private static final int MAX_MOVES = 8;

    private final int selfObjectId;
    private final SessionListener listener;
    private final Consumer<Consumer<ByteBuf>> out;
    private final EventLoop loop;
    private final LongSupplier clock;
    private final OwnPosition own;
    private final ArrayDeque<Position> moves = new ArrayDeque<>();
    private Position teleport;
    private IntentKind resurrection = IntentKind.RESURRECT_AT_RESTART_POINT;
    private int hp;
    private int mp;
    private int selectedTarget;
    private int clickingTarget;
    private ScheduledFuture<?> secondClick;
    private ScheduledFuture<?> validation;

    GameWorld(
            int selfObjectId,
            Position start,
            int hp,
            int mp,
            SessionListener listener,
            Consumer<Consumer<ByteBuf>> out,
            EventLoop loop,
            LongSupplier clock) {
        this.selfObjectId = selfObjectId;
        this.hp = hp;
        this.mp = mp;
        this.listener = listener;
        this.out = out;
        this.loop = loop;
        this.clock = clock;
        own = new OwnPosition(start);
    }

    void startValidating() {
        final long interval = VALIDATE_INTERVAL.toNanos();
        validation = loop.scheduleAtFixedRate(this::validatePosition, interval, interval, TimeUnit.NANOSECONDS);
    }

    void stop() {
        if (validation != null) {
            validation.cancel(false);
            validation = null;
        }
        cancelClick();
    }

    /** @return {@code false} when the intent is none of the world's */
    boolean send(Intent intent) {
        switch (intent) {
            case MoveTo move -> moveTo(move.destination());
            case Say say ->
                out.accept(Say2.builder()
                        .text(say.text())
                        .channelCode(ChatChannel.GENERAL.code())
                        .build()::encode);
            case Attack attack -> attack(attack.targetObjectId());
            case UseSkill skill ->
                out.accept(
                        RequestMagicSkillUse.builder().skillId(skill.skillId()).build()::encode);
            case ResurrectAtRestartPoint _ ->
                resurrect(IntentKind.RESURRECT_AT_RESTART_POINT, RequestRestartPoint.TO_RESTART_POINT);
            case ResurrectInPlace _ -> resurrect(IntentKind.RESURRECT_IN_PLACE, RequestRestartPoint.IN_PLACE);
            case AdminCommand admin -> {
                out.accept(new SendBypassBuildCmd(admin.command())::encode);
                listener.acknowledged(IntentKind.ADMIN_COMMAND);
            }
            case AdminTeleport admin -> {
                final Position destination = admin.destination();
                teleport = destination;
                out.accept(new SendBypassBuildCmd(
                        "move_to " + destination.x() + " " + destination.y() + " " + destination.z())::encode);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    void frame(GameServerOpcode opcode, ByteBuf body) {
        switch (opcode) {
            case USER_INFO -> {
                final UserInfoHead head = UserInfoHead.read(body);
                own.sync(position(head.x(), head.y(), head.z()), clock.getAsLong());
            }
            case CHAR_INFO -> seen(CharInfoHead.read(body));
            case NPC_INFO -> seen(NpcInfoHead.read(body));
            case DELETE_OBJECT -> gone(DeleteObject.read(body).objectId());
            case MOVE_TO_LOCATION -> moved(MoveToLocation.read(body));
            case MOVE_TO_PAWN -> approaching(MoveToPawn.read(body));
            case STOP_MOVE -> stopped(StopMove.read(body));
            case VALIDATE_LOCATION -> corrected(ValidateLocation.read(body));
            case TELEPORT_TO_LOCATION -> teleported(TeleportToLocation.read(body));
            case ATTACK -> {
                if (concernsSelf(body)) {
                    attacked(app.l2nx.protocol.game.p267.serverpackets.Attack.read(body));
                }
            }
            case MAGIC_SKILL_USE -> {
                if (concernsSelf(body)) {
                    cast(MagicSkillUse.read(body));
                }
            }
            case SAY2 -> {
                if (concernsSelf(body)) {
                    listener.acknowledged(IntentKind.SAY);
                }
            }
            case DIE -> died(Die.read(body).objectId());
            case REVIVE -> revived(Revive.read(body).objectId());
            case STATUS_UPDATE -> {
                if (concernsSelf(body)) {
                    status(StatusUpdate.read(body));
                }
            }
            case MY_TARGET_SELECTED ->
                targetSelected(MyTargetSelected.read(body).objectId());
            default -> {}
        }
    }

    private void moveTo(Position destination) {
        if (moves.size() == MAX_MOVES) {
            moves.removeFirst();
        }
        moves.addLast(destination);
        final Position origin = own.current(clock.getAsLong());
        out.accept(app.l2nx.protocol.game.p267.clientpackets.MoveToLocation.builder()
                .targetX(destination.x())
                .targetY(destination.y())
                .targetZ(destination.z())
                .originX(origin.x())
                .originY(origin.y())
                .originZ(origin.z())
                .movementMode(app.l2nx.protocol.game.p267.clientpackets.MoveToLocation.MOUSE)
                .build()::encode);
    }

    /** A first click selects, a second click on the selected target attacks: {@code NpcClick}, {@code PlayerClick}. */
    private void attack(int target) {
        cancelClick();
        click(target);
        if (selectedTarget != target) {
            clickingTarget = target;
        }
    }

    private void click(int target) {
        final Position origin = own.current(clock.getAsLong());
        out.accept(Action.builder()
                .objectId(target)
                .originX(origin.x())
                .originY(origin.y())
                .originZ(origin.z())
                .actionId(Action.LEFT_CLICK)
                .build()::encode);
    }

    private void targetSelected(int target) {
        selectedTarget = target;
        if (target == clickingTarget) {
            clickingTarget = NO_TARGET;
            secondClick = loop.schedule(
                    () -> {
                        secondClick = null;
                        click(target);
                    },
                    CLICK_GAP.toNanos(),
                    TimeUnit.NANOSECONDS);
        }
    }

    private void cancelClick() {
        clickingTarget = NO_TARGET;
        if (secondClick != null) {
            secondClick.cancel(false);
            secondClick = null;
        }
    }

    private void clearTarget() {
        selectedTarget = NO_TARGET;
        cancelClick();
    }

    private void seen(CharInfoHead head) {
        listener.observed(ObjectSeen.builder()
                .objectId(head.objectId())
                .kind(ObjectSeen.Kind.CHARACTER)
                .position(position(head.x(), head.y(), head.z()))
                .team(CharacterTeam.ofName(head.name()))
                .dead(head.dead())
                .build());
    }

    private void seen(NpcInfoHead head) {
        listener.observed(ObjectSeen.builder()
                .objectId(head.objectId())
                .kind(head.attackable() ? ObjectSeen.Kind.MONSTER : ObjectSeen.Kind.NPC)
                .position(position(head.x(), head.y(), head.z()))
                .team(CharacterTeam.NONE)
                .dead(head.dead())
                .build());
    }

    /** An object out of view stops being the server's target, and a monster comes back under the same object id. */
    private void gone(int objectId) {
        if (objectId == selectedTarget) {
            clearTarget();
        }
        listener.observed(new ObjectGone(objectId));
    }

    /** How the server walks a character up to its target: no intent waits for it, only the position follows. */
    private void approaching(MoveToPawn packet) {
        final Position target = position(packet.targetX(), packet.targetY(), packet.targetZ());
        if (packet.objectId() == selfObjectId) {
            own.moveTo(position(packet.x(), packet.y(), packet.z()), target, clock.getAsLong());
            objectMoved(selfObjectId, target);
        } else {
            objectMoved(packet.objectId(), target);
        }
    }

    private void moved(MoveToLocation packet) {
        final Position target = position(packet.targetX(), packet.targetY(), packet.targetZ());
        if (packet.objectId() == selfObjectId) {
            own.moveTo(position(packet.originX(), packet.originY(), packet.originZ()), target, clock.getAsLong());
            objectMoved(selfObjectId, target);
            acknowledgeMove(target);
        } else {
            objectMoved(packet.objectId(), target);
        }
    }

    /**
     * The server corrects z by the geodata and, on a route around an obstacle, echoes its first node, so a destination is
     * matched by x and y, and an echo that matches none takes the oldest destination waiting.
     */
    private void acknowledgeMove(Position echoed) {
        for (Iterator<Position> waiting = moves.iterator(); waiting.hasNext(); ) {
            final Position destination = waiting.next();
            if (destination.x() == echoed.x() && destination.y() == echoed.y()) {
                waiting.remove();
                listener.acknowledged(IntentKind.MOVE_TO, destination);
                return;
            }
        }
        if (moves.pollFirst() != null) {
            listener.acknowledged(IntentKind.MOVE_TO);
        }
    }

    private void stopped(StopMove packet) {
        final Position position = position(packet.x(), packet.y(), packet.z());
        if (packet.objectId() == selfObjectId) {
            own.stop(position);
        }
        objectMoved(packet.objectId(), position);
    }

    private void corrected(ValidateLocation packet) {
        final Position position = position(packet.x(), packet.y(), packet.z());
        if (packet.objectId() == selfObjectId) {
            own.sync(position, clock.getAsLong());
        }
        objectMoved(packet.objectId(), position);
    }

    private void teleported(TeleportToLocation packet) {
        final Position position = position(packet.x(), packet.y(), packet.z());
        if (packet.objectId() != selfObjectId) {
            objectMoved(packet.objectId(), position);
            return;
        }
        // The server keeps the character teleporting, and a dead one dead, until the client appears.
        out.accept(Appearing.INSTANCE::encode);
        own.stop(position);
        clearTarget();
        objectMoved(selfObjectId, position);
        final Position awaited = teleport;
        if (awaited != null && awaited.x() == position.x() && awaited.y() == position.y()) {
            teleport = null;
            listener.acknowledged(IntentKind.ADMIN_TELEPORT, awaited);
        }
    }

    private void attacked(app.l2nx.protocol.game.p267.serverpackets.Attack packet) {
        final Position position = position(packet.x(), packet.y(), packet.z());
        own.stop(position);
        objectMoved(selfObjectId, position);
        listener.acknowledged(IntentKind.ATTACK, packet.hit().targetObjectId());
    }

    private void cast(MagicSkillUse packet) {
        own.stop(position(packet.x(), packet.y(), packet.z()));
        listener.observed(new CastStarted(Duration.ofMillis(packet.hitTime())));
        listener.acknowledged(IntentKind.USE_SKILL, packet.skillId());
    }

    private void resurrect(IntentKind kind, int point) {
        resurrection = kind;
        out.accept(new RequestRestartPoint(point)::encode);
    }

    private void died(int objectId) {
        if (objectId == selfObjectId) {
            clearTarget();
            moves.clear();
        }
        listener.observed(new Died(objectId));
    }

    private void revived(int objectId) {
        listener.observed(new Revived(objectId));
        if (objectId == selfObjectId) {
            listener.acknowledged(resurrection);
        }
    }

    private void status(StatusUpdate packet) {
        boolean changed = false;
        for (StatusUpdate.Attribute attribute : packet.attributes()) {
            if (attribute.id() == StatusUpdate.CUR_HP) {
                hp = attribute.value();
                changed = true;
            } else if (attribute.id() == StatusUpdate.CUR_MP) {
                mp = attribute.value();
                changed = true;
            }
        }
        if (changed) {
            listener.observed(StatusChanged.builder().hp(hp).mp(mp).build());
        }
    }

    private void validatePosition() {
        final Position position = own.current(clock.getAsLong());
        out.accept(ValidatePosition.builder()
                .x(position.x())
                .y(position.y())
                .z(position.z())
                .build()::encode);
    }

    private void objectMoved(int objectId, Position position) {
        listener.observed(
                ObjectMoved.builder().objectId(objectId).position(position).build());
    }

    private boolean concernsSelf(ByteBuf body) {
        return Bodies.parse("object id", body, in -> in.getIntLE(in.readerIndex())) == selfObjectId;
    }

    private static Position position(int x, int y, int z) {
        return Position.builder().x(x).y(y).z(z).build();
    }
}
