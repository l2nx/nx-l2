/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.gslink;

import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.gslink.GsLinkProperties;
import app.l2nx.gslink.GsLinkStreamWriter;
import app.l2nx.gslink.v1.GsLinkGrpc;
import app.l2nx.gslink.v1.GsLinkProto;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.ListingAttribute;
import app.l2nx.highfive.login.LoginPort;
import app.l2nx.highfive.login.command.AuthenticationResult;
import app.l2nx.highfive.login.command.KickAccount;
import app.l2nx.highfive.login.command.LinkLost;
import app.l2nx.highfive.login.command.PasswordChangeResult;
import app.l2nx.highfive.login.command.RegistrationResult;
import app.l2nx.highfive.login.command.ReportCharacters;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.session.SessionKey;
import app.l2nx.logging.RateLimitedLog;
import app.l2nx.network.config.NetworkProperties;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

@Slf4j
@Component
public class LoginServerLink implements LoginPort, DisposableBean {
    public static final String CHRONICLE = "high-five";

    private static final int HEX_ID_SIZE = 16;
    private static final SecureRandom KEY_RANDOM = new SecureRandom();

    private final ManagedChannel channel;
    private final GsLinkGrpc.GsLinkStub stub;
    private final BackOff reconnectDelay;
    private final Duration shutdownTimeout;
    private final TaskScheduler scheduler;
    private final ObjectProvider<CommandDispatcher> commands;
    private final RateLimitedLog rate = RateLimitedLog.everyTenSeconds();

    // Published only once Register is queued on it, so every other message follows Register.
    private volatile Stream current;

    // Guarded by this.
    private BackOffExecution backOff;
    private boolean stopping;
    private ScheduledFuture<?> pendingOpen;
    private int requestId;

    // Set by start() before the first stream opens, read-only afterwards.
    private boolean acceptAlternate;
    private byte[] hexId;
    private int gamePort;
    private int maxPlayers;
    private List<String> subnets;
    private List<String> hosts;

    // The handlers answer through this link, so Spring creates the link first and resolves the dispatcher on first use.
    public LoginServerLink(
            ManagedChannel gsLinkChannel,
            GsLinkProperties properties,
            NetworkProperties network,
            TaskScheduler taskScheduler,
            ObjectProvider<CommandDispatcher> commands) {
        channel = gsLinkChannel;
        stub = GsLinkGrpc.newStub(gsLinkChannel);
        reconnectDelay = properties.reconnectDelay().backOff();
        shutdownTimeout = network.shutdownTimeout();
        scheduler = taskScheduler;
        this.commands = commands;
    }

    /** @param gamePort the port the game server bound, which a configured port 0 leaves to the system */
    public void start(int gamePort) {
        this.gamePort = gamePort;
        load();
        open();
    }

    /**
     * Sends the final down status, ends the stream, waits for the login server to end its side, then shuts the channel
     * down; the whole sequence shares one {@code l2nx.network.shutdown-timeout}.
     */
    public void shutdown() {
        final Stream stream;
        synchronized (this) {
            // Shutdown ends the link first; the context close calls this again.
            if (stopping) {
                return;
            }
            stopping = true;
            stream = current;
            current = null;
            if (pendingOpen != null) {
                pendingOpen.cancel(false);
            }
        }
        final Instant deadline = scheduler.getClock().instant().plus(shutdownTimeout);
        final boolean ended = (stream == null) || endStream(stream, deadline);
        if (ended) {
            channel.shutdown();
            awaitTermination(deadline);
        } else {
            log.warn("The login server did not end the GS-link within {}.", shutdownTimeout);
            channel.shutdownNow();
        }
    }

    @Override
    public void destroy() {
        shutdown();
    }

    @Override
    public boolean authenticate(UUID requestId, String login, SessionKey key) {
        return send(GameServerMessage.newBuilder()
                .setAuthenticate(GsLinkProto.Authenticate.newBuilder()
                        .setRequestId(requestId.toString())
                        .setLogin(login)
                        .setKey(GsLinkProto.SessionKey.newBuilder()
                                .setLoginOk1(key.loginOk1())
                                .setLoginOk2(key.loginOk2())
                                .setPlayOk1(key.playOk1())
                                .setPlayOk2(key.playOk2())))
                .build());
    }

    @Override
    public void accountsEntered(List<UUID> accountIds) {
        send(GameServerMessage.newBuilder()
                .setAccountsEntered(GsLinkProto.AccountsEntered.newBuilder()
                        .addAllAccountIds(
                                accountIds.stream().map(UUID::toString).toList()))
                .build());
    }

    @Override
    public void accountLeft(UUID accountId) {
        send(GameServerMessage.newBuilder()
                .setAccountLeft(GsLinkProto.AccountLeft.newBuilder().setAccountId(accountId.toString()))
                .build());
    }

    @Override
    public void changeAccessLevel(UUID accountId, int level) {
        send(GameServerMessage.newBuilder()
                .setChangeAccessLevel(GsLinkProto.ChangeAccessLevel.newBuilder()
                        .setAccountId(accountId.toString())
                        .setLevel(level))
                .build());
    }

    @Override
    public void reportRoute(UUID accountId, List<String> route) {
        final GsLinkProto.ReportRoute.Builder report =
                GsLinkProto.ReportRoute.newBuilder().setAccountId(accountId.toString());
        if (!route.isEmpty()) {
            report.setClientAddress(route.getFirst()).addAllHops(route.subList(1, route.size()));
        }
        send(GameServerMessage.newBuilder().setReportRoute(report).build());
    }

    @Override
    public void requestTempBan(UUID accountId, String ip, Duration duration) {
        final GsLinkProto.RequestTempBan.Builder ban = GsLinkProto.RequestTempBan.newBuilder()
                .setAccountId(accountId.toString())
                .setDuration(com.google.protobuf.Duration.newBuilder()
                        .setSeconds(duration.getSeconds())
                        .setNanos(duration.getNano()));
        if (ip != null) {
            ban.setIp(ip);
        }
        send(GameServerMessage.newBuilder().setRequestTempBan(ban).build());
    }

    @Override
    public boolean changePassword(
            UUID requestId, UUID accountId, String characterName, String oldPassword, String newPassword) {
        return send(GameServerMessage.newBuilder()
                .setChangePassword(GsLinkProto.ChangePassword.newBuilder()
                        .setRequestId(requestId.toString())
                        .setAccountId(accountId.toString())
                        .setCharacterName(characterName)
                        .setOldPassword(oldPassword)
                        .setNewPassword(newPassword))
                .build());
    }

    @Override
    public void reportCharacters(UUID accountId, int count, List<Instant> deleteTimes) {
        send(GameServerMessage.newBuilder()
                .setCharacterReport(GsLinkProto.CharacterReport.newBuilder()
                        .setAccountId(accountId.toString())
                        .setCount(count)
                        .addAllDeleteTimes(deleteTimes.stream()
                                .map(LoginServerLink::timestamp)
                                .toList()))
                .build());
    }

    @Override
    public void publishListing(Map<ListingAttribute, Integer> attributes) {
        final GsLinkProto.PublishListing.Builder listing = GsLinkProto.PublishListing.newBuilder();
        attributes.forEach((attribute, value) -> {
            switch (attribute) {
                case STATUS -> {
                    final GsLinkProto.ListingStatus status = GsLinkProto.ListingStatus.forNumber(value);
                    if (status == null) {
                        throw new IllegalArgumentException("Unknown listing status code " + value);
                    }
                    listing.setStatus(status);
                }
                case TYPE -> listing.setType(value);
                case SQUARE_BRACKET -> listing.setSquareBracket(value != 0);
                case MAX_PLAYERS -> listing.setMaxPlayers(value);
                case AGE -> listing.setAge(value);
            }
        });
        send(GameServerMessage.newBuilder().setPublishListing(listing).build());
    }

    private boolean send(GameServerMessage message) {
        final Stream stream = current;
        if (stream == null) {
            return false;
        }
        stream.writer.send(message);
        return true;
    }

    private synchronized void open() {
        if (stopping) {
            return;
        }
        final Stream stream = new Stream();
        stub.connect(stream);
        stream.writer.send(
                GameServerMessage.newBuilder().setRegister(registration()).build());
        current = stream;
        if (stream.ended.isDone()) {
            // The call ended before it was published, so its own end could not reopen it.
            reopen(stream);
        }
    }

    private synchronized boolean reopen(Stream ended) {
        if (stopping || (current != ended)) {
            return false;
        }
        current = null;
        if (backOff == null) {
            backOff = reconnectDelay.start();
        }
        final long delay = backOff.nextBackOff();
        log.info("Opening a new GS-link to the login server in {} ms.", delay);
        try {
            pendingOpen = scheduler.schedule(
                    this::open, scheduler.getClock().instant().plusMillis(delay));
        } catch (TaskRejectedException e) {
            // Boot's scheduler stops on the context close, before Shutdown ends the link.
            log.info("The GS-link is not reopened: the application is stopping.");
        }
        return true;
    }

    private void lost() {
        try {
            commands.getObject().submit(new LinkLost());
        } catch (RuntimeException e) {
            log.warn("Failing the requests left on the ended GS-link failed.", e);
        }
    }

    private void registered(GsLinkProto.RegistrationResult result) {
        synchronized (this) {
            backOff = null;
            // With an alternative id accepted, asking for the configured id again would take yet another id.
            requestId = result.getServerId();
        }
        commands.getObject()
                .submit(RegistrationResult.builder()
                        .serverId(result.getServerId())
                        .serverName(result.hasServerName() ? result.getServerName() : null)
                        .hexId(HexFormat.of().formatHex(hexId))
                        .build());
    }

    private boolean endStream(Stream stream, Instant deadline) {
        stream.writer.send(GameServerMessage.newBuilder()
                .setPublishListing(GsLinkProto.PublishListing.newBuilder()
                        .setStatus(GsLinkProto.ListingStatus.LISTING_STATUS_DOWN))
                .build());
        stream.writer.complete();
        try {
            stream.ended.get(remaining(deadline).toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException | ExecutionException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void awaitTermination(Instant deadline) {
        try {
            if (!channel.awaitTermination(remaining(deadline).toMillis(), TimeUnit.MILLISECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Duration remaining(Instant deadline) {
        final Duration left = Duration.between(scheduler.getClock().instant(), deadline);
        return left.isNegative() ? Duration.ZERO : left;
    }

    private void load() {
        acceptAlternate = ServerConfig.ACCEPT_ALTERNATE_ID;
        subnets = ServerConfig.GAME_SERVER_SUBNETS;
        hosts = ServerConfig.GAME_SERVER_HOSTS;
        maxPlayers = ServerConfig.MAXIMUM_ONLINE_USERS;
        if (ServerConfig.HEX_ID == null) {
            requestId = ServerConfig.REQUEST_ID;
            hexId = generateHexBytes(HEX_ID_SIZE);
        } else {
            requestId = ServerConfig.SERVER_ID;
            hexId = ServerConfig.HEX_ID;
        }
    }

    private GsLinkProto.Register registration() {
        final GsLinkProto.Register.Builder register = GsLinkProto.Register.newBuilder()
                .setServerId(requestId)
                .setAcceptAlternativeId(acceptAlternate)
                .setHexId(ByteString.copyFrom(hexId))
                .setChronicle(CHRONICLE)
                .setPort(gamePort)
                .setMaxPlayers(maxPlayers);
        for (int i = 0; i < Math.min(subnets.size(), hosts.size()); i++) {
            register.addAddresses(GsLinkProto.GameServerAddress.newBuilder()
                    .setSubnet(subnets.get(i))
                    .setAddress(hosts.get(i)));
        }
        return register.build();
    }

    private static Timestamp timestamp(Instant time) {
        return Timestamp.newBuilder()
                .setSeconds(time.getEpochSecond())
                .setNanos(time.getNano())
                .build();
    }

    private static byte[] generateHexBytes(int size) {
        final byte[] array = new byte[size];
        KEY_RANDOM.nextBytes(array);
        return array;
    }

    private final class Stream implements ClientResponseObserver<GameServerMessage, LoginServerMessage> {
        private final CompletableFuture<Void> ended = new CompletableFuture<>();
        private volatile GsLinkStreamWriter<GameServerMessage> writer;

        @Override
        public void beforeStart(ClientCallStreamObserver<GameServerMessage> requests) {
            writer = new GsLinkStreamWriter<>(requests);
        }

        @Override
        public void onNext(LoginServerMessage message) {
            try {
                dispatch(message);
            } catch (RuntimeException e) {
                // Escaping onNext, the exception would make gRPC cancel the call and drop every session on the link.
                if (rate.tryAcquire(message.getMessageCase().name())) {
                    log.warn(
                            "Dropped the login server's {} for account {}.",
                            message.getMessageCase(),
                            accountOf(message),
                            e);
                }
            }
        }

        private void dispatch(LoginServerMessage message) {
            switch (message.getMessageCase()) {
                case REGISTRATION_RESULT -> registered(message.getRegistrationResult());
                case AUTHENTICATION_RESULT -> {
                    final GsLinkProto.AuthenticationResult result = message.getAuthenticationResult();
                    commands.getObject()
                            .submit(AuthenticationResult.builder()
                                    .requestId(UUID.fromString(result.getRequestId()))
                                    .accountId(result.hasAccountId() ? UUID.fromString(result.getAccountId()) : null)
                                    .accepted(result.getAccepted())
                                    .build());
                }
                case KICK_ACCOUNT -> {
                    final KickAccount kick = new KickAccount(
                            UUID.fromString(message.getKickAccount().getAccountId()));
                    // A kick without a session saves and removes a detached player, which only the world loop may do.
                    try {
                        WorldPartition.offer(() -> commands.getObject().submit(kick));
                    } catch (RejectedExecutionException e) {
                        log.warn("Dropped the kick of account {}: the world loop has stopped.", kick.accountId());
                    }
                }
                case REPORT_CHARACTERS ->
                    commands.getObject()
                            .submit(new ReportCharacters(UUID.fromString(
                                    message.getReportCharacters().getAccountId())));
                case PASSWORD_CHANGE_RESULT -> {
                    final GsLinkProto.PasswordChangeResult result = message.getPasswordChangeResult();
                    commands.getObject()
                            .submit(PasswordChangeResult.builder()
                                    .requestId(UUID.fromString(result.getRequestId()))
                                    .message(result.getMessage())
                                    .build());
                }
                case MESSAGE_NOT_SET -> log.warn("Ignored an empty message from the login server.");
            }
        }

        private static String accountOf(LoginServerMessage message) {
            return switch (message.getMessageCase()) {
                case AUTHENTICATION_RESULT ->
                    message.getAuthenticationResult().hasAccountId()
                            ? message.getAuthenticationResult().getAccountId()
                            : null;
                case KICK_ACCOUNT -> message.getKickAccount().getAccountId();
                case REPORT_CHARACTERS -> message.getReportCharacters().getAccountId();
                case REGISTRATION_RESULT, PASSWORD_CHANGE_RESULT, MESSAGE_NOT_SET -> null;
            };
        }

        @Override
        public void onError(Throwable error) {
            writer.abandon();
            ended.complete(null);
            // Status.toString() carries the cause's stack trace: one per reopen attempt while the login server is down.
            final Status status = Status.fromThrowable(error);
            final Throwable cause = status.getCause();
            log.warn(
                    "GS-link to the login server ended: {} {}{}.",
                    status.getCode(),
                    status.getDescription(),
                    (cause != null) ? " (" + cause.getMessage() + ")" : "");
            if (reopen(this)) {
                lost();
            }
        }

        @Override
        public void onCompleted() {
            ended.complete(null);
            if (reopen(this)) {
                lost();
            }
        }
    }
}
