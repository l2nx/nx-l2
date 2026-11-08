/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267.game;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.bot.intent.AdminCommand;
import app.l2nx.load.bot.intent.AdminTeleport;
import app.l2nx.load.bot.intent.Attack;
import app.l2nx.load.bot.intent.CreateCharacter;
import app.l2nx.load.bot.intent.EnterWorld;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.Logout;
import app.l2nx.load.bot.intent.MoveTo;
import app.l2nx.load.bot.intent.ResurrectInPlace;
import app.l2nx.load.bot.intent.ReviveAtRestartPoint;
import app.l2nx.load.bot.intent.Say;
import app.l2nx.load.bot.intent.SelectCharacter;
import app.l2nx.load.bot.intent.UseSkill;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.Died;
import app.l2nx.load.bot.observation.Entered;
import app.l2nx.load.bot.observation.ObjectGone;
import app.l2nx.load.bot.observation.ObjectMoved;
import app.l2nx.load.bot.observation.ObjectSeen;
import app.l2nx.load.bot.observation.Observation;
import app.l2nx.load.bot.observation.Revived;
import app.l2nx.load.bot.observation.RunSpeedChanged;
import app.l2nx.load.bot.observation.StatusChanged;
import app.l2nx.load.protocol.p267.RecordingListener;
import app.l2nx.network.bootstrap.L2Pipeline;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.network.test.GoldenBytes;
import app.l2nx.protocol.game.p267.ChatChannel;
import app.l2nx.protocol.game.p267.crypt.GameFrameCipher;
import app.l2nx.protocol.game.p267.crypt.GameXorKeys;
import app.l2nx.protocol.game.p267.serverpackets.ActionFailed;
import app.l2nx.protocol.game.p267.serverpackets.CharCreateFail;
import app.l2nx.protocol.game.p267.serverpackets.CharCreateOk;
import app.l2nx.protocol.game.p267.serverpackets.CharSelected;
import app.l2nx.protocol.game.p267.serverpackets.CharSelectionInfo;
import app.l2nx.protocol.game.p267.serverpackets.CreatureSay;
import app.l2nx.protocol.game.p267.serverpackets.DeleteObject;
import app.l2nx.protocol.game.p267.serverpackets.Die;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.KeyPacket;
import app.l2nx.protocol.game.p267.serverpackets.LoginFail;
import app.l2nx.protocol.game.p267.serverpackets.MagicSkillUse;
import app.l2nx.protocol.game.p267.serverpackets.MoveToLocation;
import app.l2nx.protocol.game.p267.serverpackets.MoveToPawn;
import app.l2nx.protocol.game.p267.serverpackets.MyTargetSelected;
import app.l2nx.protocol.game.p267.serverpackets.RestartResponse;
import app.l2nx.protocol.game.p267.serverpackets.Revive;
import app.l2nx.protocol.game.p267.serverpackets.StatusUpdate;
import app.l2nx.protocol.game.p267.serverpackets.StopMove;
import app.l2nx.protocol.game.p267.serverpackets.TeleportToLocation;
import app.l2nx.protocol.game.p267.serverpackets.ValidateLocation;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharCreateFailReason;
import app.l2nx.protocol.game.p267.serverpackets.reason.LoginFailReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.MockTicker;
import io.netty.util.concurrent.Ticker;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GameExchangeTest {
    private static final byte[] KEY = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final int SELF = 0x10000001;
    private static final int OTHER = 0x10000002;
    private static final int MONSTER = 0x10000003;
    private static final Position START =
            Position.builder().x(1_000).y(2_000).z(-300).build();
    private static final GameTicket TICKET = GameTicket.builder()
            .account("load7")
            .gameServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 7777))
            .loginOk1(0x11)
            .loginOk2(0x22)
            .playOk1(0x33)
            .playOk2(0x44)
            .build();

    private final MockTicker ticker = Ticker.newMockTicker();
    private final RecordingListener listener = new RecordingListener();
    private final GameExchange exchange = new GameExchange(TICKET, listener, ticker::nanoTime);
    private final List<Long> frameTimes = Collections.synchronizedList(new ArrayList<>());
    private final GameFrameCipher serverCipher = new GameFrameCipher();
    private final EmbeddedChannel client = connect();
    private final EmbeddedChannel server = new EmbeddedChannel();

    GameExchangeTest() {
        L2Pipeline.installCodec(server.pipeline(), serverCipher, Frames.MAX_LENGTH);
    }

    @AfterEach
    void tearDown() {
        client.finishAndReleaseAll();
        server.finishAndReleaseAll();
    }

    @Nested
    class Handshake {
        @Test
        void channelActive_shouldSendTheProtocolVersionInClear() {
            assertThat(sentHex()).isEqualTo("0e" + d(267));
        }

        @Test
        void key_shouldSendAuthLoginUnderTheXorKey_withTheSessionKeyInWireOrder() {
            sentHex();

            serverKey();

            assertThat(sentHex()).isEqualTo("2b" + s("load7") + d(0x44) + d(0x33) + d(0x11) + d(0x22));
        }

        @Test
        void key_shouldFail_whenTheVersionIsRefused() {
            sentHex();

            deliverClear(
                    KeyPacket.builder().accepted(false).key(KEY).serverId(1).build());

            assertFailed(IllegalStateException.class, "refused protocol version");
        }

        @Test
        void loginFail_shouldFail_whenTheGameServerRefusesTheSession() {
            authenticate();

            deliver(new LoginFail(LoginFailReason.SYSTEM_ERROR_LOGIN_LATER));

            assertFailed(IllegalStateException.class, "refused the session");
        }

        @Test
        void characterList_shouldReportCharacterSelect() {
            authenticate();

            deliver(characterList(1));

            assertThat(listener.events()).containsExactly("state CHARACTER_SELECT");
        }
    }

    @Nested
    class Characters {
        @Test
        void selectCharacter_shouldSendTheSlot_thenReportEntering() {
            authenticate();
            deliver(characterList(2));

            exchange.send(new SelectCharacter(1));

            assertThat(sentHex()).isEqualTo("12" + d(1) + "00".repeat(14));
            deliver(selected());
            assertThat(listener.events()).endsWith("ack SELECT_CHARACTER", "state ENTERING");
        }

        @Test
        void selectCharacter_shouldFail_whenTheAccountHasNoCharacterInTheSlot() {
            authenticate();
            deliver(characterList(1));

            exchange.send(new SelectCharacter(1));

            assertFailed(IllegalStateException.class, "is the database seeded?");
        }

        @Test
        void createCharacter_shouldSendTheCharacter_thenTakeItsSlotWithoutANewList() {
            authenticate();
            deliver(characterList(0));

            exchange.send(
                    CreateCharacter.builder().name("load7").race(0).classId(10).build());

            assertThat(sentHex()).isEqualTo("0c" + s("load7") + d(0) + d(0) + d(10) + "00".repeat(9 * 4));
            deliver(CharCreateOk.INSTANCE);
            assertThat(listener.events()).endsWith("ack CREATE_CHARACTER");
            exchange.send(new SelectCharacter(0));
            assertThat(sentHex()).startsWith("12");
        }

        @Test
        void createCharacter_shouldRejectAndStop_whenTheServerRefuses() {
            authenticate();
            deliver(characterList(0));
            exchange.send(CreateCharacter.builder().name("load7").build());
            sentHex();

            deliver(new CharCreateFail(CharCreateFailReason.REASON_NAME_ALREADY_EXISTS));

            assertThat(listener.events()).endsWith("reject CREATE_CHARACTER", "state STOPPED");
            assertThat(client.isOpen()).isFalse();
        }
    }

    @Nested
    class Entering {
        @Test
        void enterWorld_shouldSendAnEmptyRoute() {
            authenticate();
            deliver(characterList(1));
            exchange.send(new SelectCharacter(0));
            sentHex();
            deliver(selected());

            exchange.send(new EnterWorld());

            assertThat(sentHex()).isEqualTo("11" + "00".repeat(104));
        }

        @Test
        void userInfo_shouldEnterTheWorldWithTheHeadAndTheStatusOfTheSelection() {
            inWorld();

            assertThat(listener.events())
                    .endsWith(
                            "ack ENTER_WORLD",
                            "observed "
                                    + Entered.builder()
                                            .selfObjectId(SELF)
                                            .position(START)
                                            .runSpeed(120.0)
                                            .build(),
                            "observed "
                                    + StatusChanged.builder().hp(500).mp(300).build(),
                            "state IN_GAME");
        }
    }

    @Nested
    class Intents {
        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.load.protocol.p267.game.GameExchangeTest#frames")
        void send_shouldWriteTheFrame(Intent intent, String expected) {
            inWorld();

            exchange.send(intent);

            assertThat(sentHex()).isEqualTo(expected);
        }

        @Test
        void adminCommand_shouldBeConfirmedOnSending() {
            inWorld();

            exchange.send(new AdminCommand("give_all_skills"));

            assertThat(listener.events()).last().isEqualTo("ack ADMIN_COMMAND");
        }

        @Test
        void moveTo_shouldTakeItsOriginFromDeadReckoning_whenTheCharacterIsRunning() {
            inWorld();
            deliver(move(SELF, position(2_000, 2_000, -300), START));
            ticker.advance(1, TimeUnit.SECONDS);

            exchange.send(new MoveTo(position(1_000, 1_000, -300)));

            assertThat(sentHex()).contains(d(1_120) + d(2_000) + d(-300) + d(1));
        }

        @Test
        void attack_shouldClickTwice_withTheSecondAfterTheTargetIsSelectedAndAGap() {
            inWorld();

            exchange.send(new Attack(OTHER));
            final String expected = click(OTHER);

            assertThat(sentHex()).isEqualTo(expected);
            assertThat(client.outboundMessages()).isEmpty();
            deliver(MyTargetSelected.builder().objectId(OTHER).build());
            assertThat(client.outboundMessages())
                    .as("not before the flood protector's tick")
                    .isEmpty();
            advance(GameWorld.CLICK_GAP.toMillis());
            assertThat(sentHex()).isEqualTo(expected);
        }

        @Test
        void attack_shouldClickOnce_whenTheTargetIsAlreadySelected() {
            inWorld();
            deliver(MyTargetSelected.builder().objectId(OTHER).build());

            exchange.send(new Attack(OTHER));

            assertThat(sentHex()).isEqualTo(click(OTHER));
            advance(GameWorld.CLICK_GAP.toMillis() * 2);
            assertThat(outboundOtherThanValidation()).isEmpty();
        }

        @Test
        void attack_shouldDropTheSecondClick_whenTheLoadBotDiesFirst() {
            inWorld();
            exchange.send(new Attack(OTHER));
            sentHex();
            deliver(MyTargetSelected.builder().objectId(OTHER).build());

            deliver(die(SELF));
            advance(GameWorld.CLICK_GAP.toMillis() * 2);

            assertThat(outboundOtherThanValidation()).isEmpty();
        }

        @Test
        void logout_shouldAcknowledgeAndClose_whenTheServerAccepts() {
            inWorld();
            exchange.send(new Logout());
            assertThat(sentHex()).isEqualTo("57");

            deliver(new RestartResponse(true));

            assertThat(listener.events()).endsWith("ack LOGOUT", "state CLOSING", "state STOPPED");
            assertThat(client.isOpen()).isFalse();
        }

        @Test
        void logout_shouldRejectAndStop_whenTheCharacterIsInCombat() {
            inWorld();
            exchange.send(new Logout());
            sentHex();

            deliver(new RestartResponse(false));

            assertThat(listener.events()).endsWith("reject LOGOUT", "state STOPPED");
        }

        @Test
        void send_shouldFail_whenTheIntentNeedsTheWorldBeforeTheEntry() {
            authenticate();

            exchange.send(new Say("hello"));

            assertFailed(IllegalStateException.class, "cannot send SAY");
        }
    }

    @Nested
    class Observations {
        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.load.protocol.p267.game.GameExchangeTest#observations")
        void frame_shouldBecomeTheObservation(String name, Consumer<GameExchangeTest> frame, Observation expected) {
            inWorld();

            frame.accept(GameExchangeTest.this);

            assertThat(listener.events()).last().isEqualTo("observed " + expected);
        }

        @Test
        void userInfo_shouldReportTheNewRunSpeed_whenItChanged() {
            inWorld();

            deliverRaw(out -> {
                out.writeByte(0x32);
                writeHead(out, START, SELF);
                writeName(out, "load7");
                writeSpeed(out, 120, 1.25);
            });

            assertThat(listener.events()).last().isEqualTo("observed " + new RunSpeedChanged(150.0));
        }

        @Test
        void userInfo_shouldReportNothing_whenTheRunSpeedStayed() {
            inWorld();

            deliverRaw(out -> {
                out.writeByte(0x32);
                writeHead(out, START, SELF);
                writeName(out, "load7");
                writeSpeed(out, 120, 1.0);
            });

            assertThat(listener.events()).last().isEqualTo("state IN_GAME");
        }

        @Test
        void statusUpdate_shouldKeepTheOtherValue_whenOnlyOneChanged() {
            inWorld();

            deliver(StatusUpdate.builder()
                    .objectId(SELF)
                    .attributes(List.of(StatusUpdate.Attribute.builder()
                            .id(StatusUpdate.CUR_MP)
                            .value(120)
                            .build()))
                    .build());

            assertThat(listener.events())
                    .last()
                    .isEqualTo("observed "
                            + StatusChanged.builder().hp(500).mp(120).build());
        }

        @Test
        void statusUpdate_shouldBeDropped_whenItIsOfAnotherObject() {
            inWorld();
            final int before = listener.events().size();

            deliver(StatusUpdate.builder()
                    .objectId(OTHER)
                    .attributes(List.of(StatusUpdate.Attribute.builder()
                            .id(StatusUpdate.CUR_HP)
                            .value(1)
                            .build()))
                    .build());

            assertThat(listener.events()).hasSize(before);
        }

        @Test
        void frame_shouldPassTheCipherAndBeSkipped_whenItsOpcodeIsNotNeeded() {
            inWorld();
            final int before = listener.events().size();

            deliverRaw(out -> out.writeByte(0xFE).writeShortLE(0x1234).writeLongLE(-1));
            deliver(new Revive(OTHER));

            assertThat(listener.events().subList(before, listener.events().size()))
                    .as("the rolling key stayed in step")
                    .containsExactly("observed " + new Revived(OTHER));
        }

        @Test
        void frame_shouldFail_whenANeededFrameDoesNotDecode() {
            inWorld();

            deliverRaw(out -> out.writeByte(0x47).writeIntLE(OTHER));

            assertFailed(PacketDecodeException.class, "StopMove");
        }
    }

    @Nested
    class Acknowledgements {
        @Test
        void moveEcho_shouldAcknowledgeByDestination_whenTheServerCorrectsZ() {
            inWorld();
            final Position destination = position(1_500, 2_500, -300);
            exchange.send(new MoveTo(destination));
            sentHex();

            deliver(move(SELF, position(1_500, 2_500, -280), START));

            assertThat(listener.events()).last().isEqualTo("ack MOVE_TO " + destination);
        }

        @Test
        void moveEcho_shouldFallBackToTheOldest_whenTheServerRoutesAroundAnObstacle() {
            inWorld();
            exchange.send(new MoveTo(position(1_500, 2_500, -300)));
            sentHex();

            deliver(move(SELF, position(1_200, 2_100, -300), START));

            assertThat(listener.events()).last().isEqualTo("ack MOVE_TO");
        }

        @Test
        void moveEcho_shouldAcknowledgeNothing_whenNoMoveWaits() {
            inWorld();

            deliver(move(SELF, position(1_200, 2_100, -300), START));

            assertThat(listener.events()).noneMatch(event -> event.startsWith("ack MOVE_TO"));
        }

        @Test
        void attackPacket_shouldAcknowledgeByTarget_whenTheLoadBotHits() {
            inWorld();

            deliver(attack(SELF, OTHER));

            assertThat(listener.events()).last().isEqualTo("ack ATTACK " + OTHER);
        }

        @Test
        void attackPacket_shouldBeDropped_whenAnotherObjectHits() {
            inWorld();
            final int before = listener.events().size();

            deliver(attack(OTHER, SELF));

            assertThat(listener.events()).hasSize(before);
        }

        @Test
        void magicSkillUse_shouldAcknowledgeBySkill_whenTheLoadBotCasts() {
            inWorld();

            deliver(cast(SELF, 1230));

            assertThat(listener.events()).last().isEqualTo("ack USE_SKILL 1230");
        }

        @Test
        void magicSkillUse_shouldBeDropped_whenAnotherObjectCasts() {
            inWorld();
            final int before = listener.events().size();

            deliver(cast(OTHER, 1230));

            assertThat(listener.events()).hasSize(before);
        }

        @Test
        void chatEcho_shouldAcknowledgeTheSay() {
            inWorld();

            deliver(CreatureSay.builder()
                    .senderId(SELF)
                    .channel(ChatChannel.GENERAL)
                    .senderName("load7")
                    .messageId(CreatureSay.NO_MESSAGE)
                    .text("Roaming around")
                    .build());

            assertThat(listener.events()).last().isEqualTo("ack SAY");
        }

        @Test
        void chat_shouldNotBeDecoded_whenAnotherObjectSpeaks() {
            inWorld();
            final int before = listener.events().size();

            // An NPC speaking by name id: a layout CreatureSay.read cannot parse.
            deliverRaw(out -> out.writeByte(0x4A).writeIntLE(OTHER).writeIntLE(99));

            assertThat(listener.events()).hasSize(before);
            assertThat(client.isOpen()).isTrue();
        }

        @Test
        void revive_shouldAcknowledgeTheRevival_afterTheObservation() {
            inWorld();

            deliver(new Revive(SELF));

            assertThat(listener.events()).endsWith("observed " + new Revived(SELF), "ack REVIVE_AT_RESTART_POINT");
        }

        @Test
        void revive_shouldAcknowledgeTheKindSent_whenTheResurrectionIsInPlace() {
            inWorld();
            exchange.send(new ResurrectInPlace());

            deliver(new Revive(SELF));

            assertThat(listener.events()).endsWith("observed " + new Revived(SELF), "ack RESURRECT_IN_PLACE");
        }

        @Test
        void actionFailed_shouldNeitherAcknowledgeNorReject() {
            inWorld();
            exchange.send(new UseSkill(1230));
            sentHex();
            final int before = listener.events().size();

            deliver(ActionFailed.INSTANCE);

            assertThat(listener.events())
                    .as("it follows every click, so it answers no intent")
                    .hasSize(before);
        }
    }

    @Nested
    class ClientDuties {
        @Test
        void teleport_shouldAppearAndReportTheNewPosition() {
            inWorld();
            final Position destination = position(5_000, 6_000, -700);

            deliver(teleport(SELF, destination));

            assertThat(sentHex()).isEqualTo("3a");
            assertThat(listener.events())
                    .last()
                    .isEqualTo("observed "
                            + ObjectMoved.builder()
                                    .objectId(SELF)
                                    .position(destination)
                                    .build());
        }

        @Test
        void teleport_shouldNotAppear_whenAnotherObjectTeleports() {
            inWorld();

            deliver(teleport(OTHER, position(5_000, 6_000, -700)));

            assertThat(client.outboundMessages()).isEmpty();
        }

        @Test
        void teleport_shouldConfirmTheAdminTeleport_whenTheServerRaisesZ() {
            inWorld();
            final Position destination = position(5_000, 6_000, -700);
            exchange.send(new AdminTeleport(destination));
            sentHex();

            deliver(teleport(SELF, position(5_000, 6_000, -695)));

            assertThat(listener.events()).last().isEqualTo("ack ADMIN_TELEPORT " + destination);
        }

        @Test
        void teleport_shouldForgetTheSelectedTarget() {
            inWorld();
            deliver(MyTargetSelected.builder().objectId(OTHER).build());
            deliver(teleport(SELF, START));
            sentHex();

            exchange.send(new Attack(OTHER));
            sentHex();
            deliver(MyTargetSelected.builder().objectId(OTHER).build());
            advance(GameWorld.CLICK_GAP.toMillis());

            assertThat(sentHex()).as("two clicks again").isEqualTo(click(OTHER));
        }

        @Test
        void validatePosition_shouldBeSentEverySecond_withTheReckonedPosition() {
            inWorld();
            deliver(move(SELF, position(2_000, 2_000, -300), START));

            advance(GameWorld.VALIDATE_INTERVAL.toMillis());

            assertThat(sentHex()).isEqualTo("59" + d(1_120) + d(2_000) + d(-300) + d(0) + d(0));
            advance(GameWorld.VALIDATE_INTERVAL.toMillis());
            assertThat(sentHex()).isEqualTo("59" + d(1_240) + d(2_000) + d(-300) + d(0) + d(0));
        }

        @Test
        void validatePosition_shouldStop_whenTheSessionCloses() {
            inWorld();

            exchange.close();
            advance(GameWorld.VALIDATE_INTERVAL.toMillis() * 3);

            assertThat(client.outboundMessages()).isEmpty();
        }
    }

    @Nested
    class Timing {
        @Test
        void frameHandled_shouldReportTheTimeFromTheReadToTheHandling() {
            inWorld();
            final ByteBuf frames = Unpooled.wrappedBuffer(encoded(new Revive(OTHER)), encoded(new Revive(OTHER)));
            listener.reacting(event -> ticker.advance(3, TimeUnit.MILLISECONDS));
            frameTimes.clear();

            client.writeInbound(frames);

            assertThat(frameTimes)
                    .as("the second frame of one read waited for the first")
                    .containsExactly(TimeUnit.MILLISECONDS.toNanos(3), TimeUnit.MILLISECONDS.toNanos(6));
        }
    }

    static Stream<Arguments> frames() {
        final String destination = d(1_500) + d(2_500) + d(-300);
        final String origin = d(1_000) + d(2_000) + d(-300);
        return Stream.of(
                Arguments.of(new MoveTo(position(1_500, 2_500, -300)), "0f" + destination + origin + d(1)),
                Arguments.of(new Say("hello"), "49" + s("hello") + d(0)),
                Arguments.of(new UseSkill(190), "39" + d(190) + d(0) + "00"),
                Arguments.of(new ReviveAtRestartPoint(), "7d" + d(0)),
                Arguments.of(new AdminCommand("set_level 75"), "74" + s("set_level 75")),
                Arguments.of(new AdminTeleport(position(5, 6, -7)), "74" + s("move_to 5 6 -7")));
    }

    static Stream<Arguments> observations() {
        final Position there = position(5_000, 6_000, -700);
        return Stream.of(
                Arguments.of(
                        "CharInfo of team 2",
                        frame(t -> t.deliverRaw(out -> {
                            out.writeByte(0x31);
                            writeHead(out, there, OTHER);
                            writeName(out, "load7");
                            writeCharacterTail(out);
                        })),
                        ObjectSeen.builder()
                                .objectId(OTHER)
                                .kind(ObjectSeen.Kind.CHARACTER)
                                .position(there)
                                .team(2)
                                .build()),
                Arguments.of(
                        "CharInfo of a player with no team",
                        frame(t -> t.deliverRaw(out -> {
                            out.writeByte(0x31);
                            writeHead(out, there, OTHER);
                            writeName(out, "Watcher");
                            writeCharacterTail(out);
                        })),
                        ObjectSeen.builder()
                                .objectId(OTHER)
                                .kind(ObjectSeen.Kind.CHARACTER)
                                .position(there)
                                .team(0)
                                .build()),
                Arguments.of(
                        "CharInfo of a corpse",
                        frame(t -> t.deliverRaw(out -> {
                            out.writeByte(0x31);
                            writeHead(out, there, OTHER);
                            writeName(out, "Watcher");
                            out.writeZero(284 + 2 + 19);
                            out.writeByte(1);
                        })),
                        ObjectSeen.builder()
                                .objectId(OTHER)
                                .kind(ObjectSeen.Kind.CHARACTER)
                                .position(there)
                                .team(0)
                                .dead(true)
                                .build()),
                Arguments.of(
                        "NpcInfo of a monster",
                        frame(t -> t.deliverRaw(out -> writeNpc(out, there, 1, 0))),
                        ObjectSeen.builder()
                                .objectId(MONSTER)
                                .kind(ObjectSeen.Kind.MONSTER)
                                .position(there)
                                .build()),
                Arguments.of(
                        "NpcInfo of a corpse",
                        frame(t -> t.deliverRaw(out -> writeNpc(out, there, 1, 1))),
                        ObjectSeen.builder()
                                .objectId(MONSTER)
                                .kind(ObjectSeen.Kind.MONSTER)
                                .position(there)
                                .dead(true)
                                .build()),
                Arguments.of(
                        "NpcInfo of a peaceful NPC",
                        frame(t -> t.deliverRaw(out -> writeNpc(out, there, 0, 0))),
                        ObjectSeen.builder()
                                .objectId(MONSTER)
                                .kind(ObjectSeen.Kind.NPC)
                                .position(there)
                                .build()),
                Arguments.of(
                        "MoveToPawn of another object",
                        frame(t -> t.deliver(MoveToPawn.builder()
                                .objectId(OTHER)
                                .targetObjectId(MONSTER)
                                .distance(40)
                                .targetX(there.x())
                                .targetY(there.y())
                                .targetZ(there.z())
                                .build())),
                        ObjectMoved.builder().objectId(OTHER).position(there).build()),
                Arguments.of("DeleteObject", frame(t -> t.deliver(new DeleteObject(OTHER))), new ObjectGone(OTHER)),
                Arguments.of(
                        "MoveToLocation of another object",
                        frame(t -> t.deliver(move(OTHER, there, START))),
                        ObjectMoved.builder().objectId(OTHER).position(there).build()),
                Arguments.of(
                        "StopMove of another object",
                        frame(t -> t.deliver(StopMove.builder()
                                .objectId(OTHER)
                                .x(5_000)
                                .y(6_000)
                                .z(-700)
                                .build())),
                        ObjectMoved.builder().objectId(OTHER).position(there).build()),
                Arguments.of(
                        "ValidateLocation of another object",
                        frame(t -> t.deliver(ValidateLocation.builder()
                                .objectId(OTHER)
                                .x(5_000)
                                .y(6_000)
                                .z(-700)
                                .build())),
                        ObjectMoved.builder().objectId(OTHER).position(there).build()),
                Arguments.of("Die of another object", frame(t -> t.deliver(die(OTHER))), new Died(OTHER)),
                Arguments.of("Die of the load bot", frame(t -> t.deliver(die(SELF))), new Died(SELF)),
                Arguments.of("Revive of another object", frame(t -> t.deliver(new Revive(OTHER))), new Revived(OTHER)),
                Arguments.of(
                        "StatusUpdate of the load bot",
                        frame(t -> t.deliver(StatusUpdate.builder()
                                .objectId(SELF)
                                .attributes(List.of(
                                        StatusUpdate.Attribute.builder()
                                                .id(StatusUpdate.CUR_HP)
                                                .value(40)
                                                .build(),
                                        StatusUpdate.Attribute.builder()
                                                .id(StatusUpdate.CUR_MP)
                                                .value(20)
                                                .build()))
                                .build())),
                        StatusChanged.builder().hp(40).mp(20).build()));
    }

    /** Names the parameter type: a lambda alone cannot be an argument of {@code Arguments.of}. */
    private static Consumer<GameExchangeTest> frame(Consumer<GameExchangeTest> frame) {
        return frame;
    }

    private EmbeddedChannel connect() {
        final EmbeddedChannel channel =
                EmbeddedChannel.builder().ticker(ticker).register(false).build();
        exchange.install(channel);
        listener.timing(frameTimes::add);
        try {
            channel.register();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return channel;
    }

    private void serverKey() {
        final byte[] key = GameXorKeys.fromSent(KEY);
        serverCipher.setKey(key);
        deliver(KeyPacket.builder().accepted(true).key(KEY).serverId(1).build());
    }

    private void authenticate() {
        sentHex();
        serverKey();
        sentHex();
    }

    private void inWorld() {
        authenticate();
        deliver(characterList(1));
        exchange.send(new SelectCharacter(0));
        sentHex();
        deliver(selected());
        exchange.send(new EnterWorld());
        sentHex();
        deliverRaw(out -> {
            out.writeByte(0x32);
            writeHead(out, START, SELF);
            writeName(out, "load7");
            writeSpeed(out, 120, 1.0);
        });
    }

    private static void writeHead(ByteBuf out, Position position, int objectId) {
        out.writeIntLE(position.x());
        out.writeIntLE(position.y());
        out.writeIntLE(position.z());
        out.writeIntLE(0);
        out.writeIntLE(objectId);
    }

    /** Zeros from the race to the run speed, then the speeds and the movement multiplier. */
    private static void writeSpeed(ByteBuf out, int runSpd, double moveMultiplier) {
        out.writeZero(456);
        out.writeIntLE(runSpd);
        out.writeZero(28);
        out.writeDoubleLE(moveMultiplier);
    }

    private static void writeName(ByteBuf out, String name) {
        for (int i = 0; i < name.length(); i++) {
            out.writeShortLE(name.charAt(i));
        }
        out.writeShortLE(0);
    }

    /** All zeros from the race on: an empty title and a live character. */
    private static void writeCharacterTail(ByteBuf out) {
        out.writeZero(284 + 2 + 19 + 1);
    }

    private static void writeNpc(ByteBuf out, Position position, int attackable, int dead) {
        out.writeByte(0x0C);
        out.writeIntLE(MONSTER);
        out.writeIntLE(1_020_000);
        out.writeIntLE(attackable);
        out.writeIntLE(position.x());
        out.writeIntLE(position.y());
        out.writeIntLE(position.z());
        out.writeZero(95);
        out.writeByte(dead);
    }

    private void deliver(GameServerPacket packet) {
        deliverRaw(packet::write);
    }

    private void deliverClear(KeyPacket packet) {
        deliverRaw(packet::write);
    }

    private void deliverRaw(Consumer<ByteBuf> payload) {
        client.writeInbound((Object) encoded(payload));
    }

    private ByteBuf encoded(GameServerPacket packet) {
        return encoded(packet::write);
    }

    private ByteBuf encoded(Consumer<ByteBuf> payload) {
        final ByteBuf frame = Frames.allocate(server.alloc(), 64);
        payload.accept(frame);
        server.writeOutbound(frame);
        return server.readOutbound();
    }

    private void advance(long millis) {
        ticker.advance(millis, TimeUnit.MILLISECONDS);
        client.runPendingTasks();
        client.runScheduledPendingTasks();
    }

    private List<String> outboundOtherThanValidation() {
        final List<String> frames = new ArrayList<>();
        while (!client.outboundMessages().isEmpty()) {
            final String frame = sentHex();
            if (!frame.startsWith("59")) {
                frames.add(frame);
            }
        }
        return frames;
    }

    private String sentHex() {
        final ByteBuf frame = client.readOutbound();
        assertThat(frame).as("a client frame").isNotNull();
        server.writeInbound(frame);
        final ByteBuf payload = server.readInbound();
        assertThat(payload).as("a frame the server cipher accepts").isNotNull();
        try {
            return HexFormat.of().formatHex(ByteBufUtil.getBytes(payload));
        } finally {
            payload.release();
        }
    }

    private void assertFailed(Class<? extends Throwable> type, String message) {
        assertThat(listener.events()).last().isEqualTo("failed");
        assertThat(listener.failures())
                .singleElement(InstanceOfAssertFactories.THROWABLE)
                .isInstanceOf(type)
                .hasMessageContaining(message);
        assertThat(client.isOpen()).isFalse();
    }

    private static String click(int target) {
        return "1f" + d(target) + d(START.x()) + d(START.y()) + d(START.z()) + "00";
    }

    private static CharSelectionInfo characterList(int characters) {
        final List<CharSelectionInfo.Slot> slots = new ArrayList<>();
        for (int i = 0; i < characters; i++) {
            slots.add(CharSelectionInfo.Slot.builder()
                    .name("load7")
                    .login("load7")
                    .paperdollItemIds(Collections.nCopies(CharSelectionInfo.PAPERDOLL_SLOTS, 0))
                    .build());
        }
        return CharSelectionInfo.builder().maxCharacters(7).slots(slots).build();
    }

    private static CharSelected selected() {
        return CharSelected.builder()
                .name("load7")
                .objectId(SELF)
                .currentHp(500.9)
                .currentMp(300.9)
                .build();
    }

    private static MoveToLocation move(int objectId, Position target, Position origin) {
        return MoveToLocation.builder()
                .objectId(objectId)
                .targetX(target.x())
                .targetY(target.y())
                .targetZ(target.z())
                .originX(origin.x())
                .originY(origin.y())
                .originZ(origin.z())
                .build();
    }

    private static TeleportToLocation teleport(int objectId, Position position) {
        return TeleportToLocation.builder()
                .objectId(objectId)
                .x(position.x())
                .y(position.y())
                .z(position.z())
                .build();
    }

    private static Die die(int objectId) {
        return Die.builder().objectId(objectId).build();
    }

    private static app.l2nx.protocol.game.p267.serverpackets.Attack attack(int attacker, int target) {
        return app.l2nx.protocol.game.p267.serverpackets.Attack.builder()
                .attackerObjectId(attacker)
                .hit(app.l2nx.protocol.game.p267.serverpackets.Attack.Hit.builder()
                        .targetObjectId(target)
                        .damage(10)
                        .build())
                .x(START.x())
                .y(START.y())
                .z(START.z())
                .extraHits(List.of())
                .build();
    }

    private static MagicSkillUse cast(int caster, int skillId) {
        return MagicSkillUse.builder()
                .objectId(caster)
                .targetObjectId(OTHER)
                .skillId(skillId)
                .x(START.x())
                .y(START.y())
                .z(START.z())
                .groundLocations(List.of())
                .build();
    }

    private static Position position(int x, int y, int z) {
        return Position.builder().x(x).y(y).z(z).build();
    }

    private static String d(int value) {
        return GoldenBytes.written(out -> out.writeIntLE(value));
    }

    private static String s(String text) {
        return GoldenBytes.written(out -> writeName(out, text));
    }
}
