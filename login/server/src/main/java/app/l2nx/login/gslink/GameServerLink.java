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
package app.l2nx.login.gslink;

import app.l2nx.gslink.GsLinkStreamWriter;
import app.l2nx.gslink.v1.GsLinkProto;
import app.l2nx.gslink.v1.GsLinkProto.GameServerMessage;
import app.l2nx.gslink.v1.GsLinkProto.LoginServerMessage;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.controller.PasswordHash;
import app.l2nx.login.controller.SessionKey;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.network.LoginClient;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.TaskScheduler;

/**
 * gRPC delivers the messages of one stream one at a time, so the handlers of a link never overlap; the deadline and
 * other threads only send, through the writer.
 */
@Slf4j
public class GameServerLink implements StreamObserver<GameServerMessage> {
    private static final int MAX_HEX_ID_BYTES = 64;
    private static final int MAX_ADDRESSES = 32;
    private static final String MSG_INVALID_PASSWORD_DATA = "Invalid password data! Try again.";
    private static final String MSG_PASSWORD_CHANGED = "You have successfully changed your password!";
    private static final String MSG_PASSWORD_CHANGE_FAILED = "The password change was unsuccessful!";
    private static final String MSG_CURRENT_PASSWORD_MISMATCH =
            "The typed current password doesn't match with your current one.";

    private enum State {
        AWAITING_REGISTRATION,
        REGISTERED,
        ENDED
    }

    private final Set<UUID> accountsOnGameServer = ConcurrentHashMap.newKeySet();
    private final AtomicReference<State> state = new AtomicReference<>(State.AWAITING_REGISTRATION);
    private final GsLinkStreamWriter<LoginServerMessage> writer;
    private final SocketAddress remoteAddress;
    private final GameServerTable gameServerTable;
    private final LoginController loginController;
    private final boolean acceptNew;
    private final Consumer<GameServerLink> ended;

    @Getter
    private volatile GameServerInfo gameServerInfo;

    private volatile ScheduledFuture<?> registrationDeadline;

    /** @param remoteAddress the game server's address, {@code null} when the transport reports none */
    GameServerLink(
            StreamObserver<LoginServerMessage> responses,
            SocketAddress remoteAddress,
            GameServerTable gameServerTable,
            LoginController loginController,
            boolean acceptNew,
            Consumer<GameServerLink> ended) {
        writer = new GsLinkStreamWriter<>(responses);
        this.remoteAddress = remoteAddress;
        this.gameServerTable = gameServerTable;
        this.loginController = loginController;
        this.acceptNew = acceptNew;
        this.ended = ended;
    }

    /** Ends the stream when no Register arrives within {@code timeout}. */
    void awaitRegistration(TaskScheduler scheduler, Duration timeout) {
        registrationDeadline = scheduler.schedule(
                () -> registrationTimedOut(timeout), Instant.now().plus(timeout));
    }

    @Override
    public void onNext(GameServerMessage message) {
        try {
            switch (state.get()) {
                case AWAITING_REGISTRATION -> onFirstMessage(message);
                case REGISTERED -> onMessage(message);
                case ENDED -> {
                    // Already in flight when the link ended.
                }
            }
        } catch (IllegalArgumentException e) {
            close(Status.INVALID_ARGUMENT.withDescription(e.getMessage()));
        }
    }

    @Override
    public void onError(Throwable error) {
        writer.abandon();
        if (end()) {
            // Status.toString() would add the cause's stack trace.
            final Status status = Status.fromThrowable(error);
            log.info("GS-link of {} lost: {} {}.", describe(), status.getCode(), status.getDescription());
        }
    }

    @Override
    public void onCompleted() {
        // The game server ends its side after its last status; the login server ends its own.
        if (end()) {
            writer.complete();
        }
    }

    /** Ends the stream with {@code status}; the game server opens a new one after its reconnect delay. */
    void close(Status status) {
        if (end()) {
            writer.fail(status);
        }
    }

    public boolean hasAccountOnGameServer(UUID accountId) {
        return accountsOnGameServer.contains(accountId);
    }

    public int getPlayerCount() {
        return accountsOnGameServer.size();
    }

    public void kickPlayer(UUID accountId) {
        writer.send(LoginServerMessage.newBuilder()
                .setKickAccount(GsLinkProto.KickAccount.newBuilder().setAccountId(accountId.toString()))
                .build());
    }

    public void requestCharacters(UUID accountId) {
        writer.send(LoginServerMessage.newBuilder()
                .setReportCharacters(GsLinkProto.ReportCharacters.newBuilder().setAccountId(accountId.toString()))
                .build());
    }

    public boolean isAuthed() {
        final GameServerInfo info = gameServerInfo;
        return (info != null) && info.isAuthed();
    }

    public int getServerId() {
        final GameServerInfo info = gameServerInfo;
        return (info != null) ? info.getId() : -1;
    }

    private void onFirstMessage(GameServerMessage message) {
        if (message.getMessageCase() != GameServerMessage.MessageCase.REGISTER) {
            close(Status.FAILED_PRECONDITION.withDescription(
                    "The first GS-link message must be Register, not " + message.getMessageCase() + "."));
            return;
        }
        register(message.getRegister());
    }

    private void onMessage(GameServerMessage message) {
        switch (message.getMessageCase()) {
            case AUTHENTICATE -> authenticate(message.getAuthenticate());
            case ACCOUNTS_ENTERED ->
                message.getAccountsEntered().getAccountIdsList().stream()
                        .map(UUID::fromString)
                        .forEach(this::addAccountOnGameServer);
            case ACCOUNT_LEFT ->
                removeAccountOnGameServer(
                        UUID.fromString(message.getAccountLeft().getAccountId()));
            case CHANGE_ACCESS_LEVEL -> changeAccessLevel(message.getChangeAccessLevel());
            case REPORT_ROUTE -> reportRoute(message.getReportRoute());
            case REQUEST_TEMP_BAN -> requestTempBan(message.getRequestTempBan());
            case CHANGE_PASSWORD -> changePassword(message.getChangePassword());
            case CHARACTER_REPORT -> characterReport(message.getCharacterReport());
            case PUBLISH_LISTING -> publishListing(message.getPublishListing());
            case REGISTER, MESSAGE_NOT_SET ->
                close(Status.FAILED_PRECONDITION.withDescription(
                        "Unexpected " + message.getMessageCase() + " on a registered GS-link."));
        }
    }

    private void register(GsLinkProto.Register registration) {
        final String chronicle = StringUtils.trimToNull(registration.getChronicle());
        if (chronicle == null) {
            refuse(Status.INVALID_ARGUMENT, "Register carries no chronicle");
            return;
        }
        if (registration.getHexId().isEmpty() || (registration.getHexId().size() > MAX_HEX_ID_BYTES)) {
            refuse(Status.INVALID_ARGUMENT, "the hex id must have 1 to " + MAX_HEX_ID_BYTES + " bytes");
            return;
        }
        if (registration.getAddressesCount() > MAX_ADDRESSES) {
            refuse(Status.INVALID_ARGUMENT, "more than " + MAX_ADDRESSES + " game server addresses");
            return;
        }
        final byte[] hexId = registration.getHexId().toByteArray();
        final int desiredId = registration.getServerId();
        final GameServerInfo registered = gameServerTable.getRegisteredGameServerById(desiredId);
        final GameServerInfo info;
        if ((registered != null) && Arrays.equals(registered.getHexId(), hexId)) {
            info = registered;
        } else if (registered == null) {
            if (!acceptNew) {
                refuse(
                        Status.PERMISSION_DENIED,
                        "server id " + desiredId + " is not registered and new game servers are not accepted");
                return;
            }
            info = new GameServerInfo(desiredId, hexId);
            if (!gameServerTable.register(desiredId, info)) {
                refuse(Status.ALREADY_EXISTS, "server id " + desiredId + " was taken during the registration");
                return;
            }
            gameServerTable.registerServerOnDB(info);
        } else {
            if (!acceptNew || !registration.getAcceptAlternativeId()) {
                refuse(
                        Status.PERMISSION_DENIED,
                        "the hex id does not match server id " + desiredId + " and no other id may be given");
                return;
            }
            info = new GameServerInfo(desiredId, hexId);
            if (!gameServerTable.registerWithFirstAvailableId(info)) {
                refuse(Status.RESOURCE_EXHAUSTED, "no free server id is left");
                return;
            }
            gameServerTable.registerServerOnDB(info);
        }
        attach(info, registration, chronicle);
    }

    private void attach(GameServerInfo info, GsLinkProto.Register registration, String chronicle) {
        final String previousChronicle;
        synchronized (info) {
            if (info.getGameServerLink() != null) {
                refuseDuplicate(info);
                return;
            }
            if (!state.compareAndSet(State.AWAITING_REGISTRATION, State.REGISTERED)) {
                // The registration deadline ended the link meanwhile.
                return;
            }
            previousChronicle = info.getChronicle();
            gameServerInfo = info;
            info.setGameServerLink(this);
            info.setPort(registration.getPort());
            info.setMaxPlayers(registration.getMaxPlayers());
            info.setChronicle(chronicle);
            setGameHosts(info, registration.getAddressesList());
            info.setAuthed(true);
        }
        cancelRegistrationDeadline();
        if (!chronicle.equals(previousChronicle)) {
            gameServerTable.storeChronicle(info.getId(), chronicle);
        }
        final GsLinkProto.RegistrationResult.Builder result =
                GsLinkProto.RegistrationResult.newBuilder().setServerId(info.getId());
        if (info.getName() != null) {
            result.setServerName(info.getName());
        }
        writer.send(
                LoginServerMessage.newBuilder().setRegistrationResult(result).build());
        log.info("Game server [{}] {} registered, chronicle {}.", info.getId(), info.getName(), chronicle);
    }

    /**
     * Two processes started with one hex id would otherwise evict each other forever. A dead stream holding the id is
     * ended by keepalive or by its end, and the game server's next attempt registers.
     */
    private void refuseDuplicate(GameServerInfo info) {
        log.warn(
                "Refused the registration of server [{}] {} from {}: a live GS-link holds its hex id. Is a second game"
                        + " server running with a copy of its hexid?",
                info.getId(),
                info.getName(),
                remoteAddress);
        close(Status.ALREADY_EXISTS.withDescription("Registration refused: server id " + info.getId()
                + " is held by a live GS-link with the same hex id."));
    }

    private void refuse(Status status, String reason) {
        log.info("Refused a game server registration: {}.", reason);
        close(status.withDescription("Registration refused: " + reason + "."));
    }

    private void registrationTimedOut(Duration timeout) {
        if (state.compareAndSet(State.AWAITING_REGISTRATION, State.ENDED)) {
            log.info("Closed a GS-link that sent no Register within {}.", timeout);
            writer.fail(Status.DEADLINE_EXCEEDED.withDescription("No Register within " + timeout + "."));
            ended.accept(this);
        }
    }

    // True for the one caller that ends the link.
    private boolean end() {
        if (state.getAndSet(State.ENDED) == State.ENDED) {
            return false;
        }
        cancelRegistrationDeadline();
        final GameServerInfo info = gameServerInfo;
        if (info != null) {
            synchronized (info) {
                info.setDown();
            }
            log.info("Server [{}] {} is now marked as disconnected.", info.getId(), info.getName());
        }
        ended.accept(this);
        return true;
    }

    private void cancelRegistrationDeadline() {
        final ScheduledFuture<?> deadline = registrationDeadline;
        if (deadline != null) {
            deadline.cancel(false);
        }
    }

    private void addAccountOnGameServer(UUID accountId) {
        accountsOnGameServer.add(accountId);
        loginController.removeAuthedLoginClient(accountId);
    }

    private void removeAccountOnGameServer(UUID accountId) {
        accountsOnGameServer.remove(accountId);
        loginController.removeAuthedLoginClient(accountId);
    }

    private void authenticate(GsLinkProto.Authenticate request) {
        final GsLinkProto.SessionKey key = request.getKey();
        final SessionKey sessionKey =
                new SessionKey(key.getLoginOk1(), key.getLoginOk2(), key.getPlayOk1(), key.getPlayOk2());
        final LoginClient client = loginController.getAuthedClientByLogin(request.getLogin());
        final GsLinkProto.AuthenticationResult.Builder result =
                GsLinkProto.AuthenticationResult.newBuilder().setLogin(request.getLogin());
        if ((client != null) && sessionKey.equals(client.getSessionKey())) {
            loginController.removeAuthedLoginClientIfCurrent(client);
            result.setAccepted(true).setAccountId(client.getAccountId().toString());
        }
        writer.send(
                LoginServerMessage.newBuilder().setAuthenticationResult(result).build());
    }

    private void changeAccessLevel(GsLinkProto.ChangeAccessLevel change) {
        final UUID accountId = UUID.fromString(change.getAccountId());
        loginController.setAccountAccessLevel(accountId, change.getLevel());
        log.info("Changed account {} access level to {}", accountId, change.getLevel());
    }

    private void reportRoute(GsLinkProto.ReportRoute route) {
        final UUID accountId = UUID.fromString(route.getAccountId());
        if (hasAccountOnGameServer(accountId)) {
            loginController.setAccountLastTracert(
                    accountId,
                    route.hasClientAddress() ? StringUtils.trimToNull(route.getClientAddress()) : null,
                    hop(route, 0),
                    hop(route, 1),
                    hop(route, 2),
                    hop(route, 3));
        }
    }

    private static String hop(GsLinkProto.ReportRoute route, int index) {
        return (index < route.getHopsCount()) ? StringUtils.trimToNull(route.getHops(index)) : null;
    }

    private void requestTempBan(GsLinkProto.RequestTempBan request) {
        final UUID accountId = UUID.fromString(request.getAccountId());
        final Duration duration = Duration.ofSeconds(
                request.getDuration().getSeconds(), request.getDuration().getNanos());
        if (!duration.isPositive()) {
            log.warn("Ignored a temporary ban of account {} with a non-positive length of {}.", accountId, duration);
            return;
        }
        if (!hasAccountOnGameServer(accountId)) {
            return;
        }
        loginController.setAccountTempBan(accountId, duration);
        final String ip = request.hasIp() ? StringUtils.trimToNull(request.getIp()) : null;
        if (ip != null) {
            loginController.addBanForAddress(ip, duration.toMillis());
        }
    }

    private void changePassword(GsLinkProto.ChangePassword request) {
        final UUID accountId = UUID.fromString(request.getAccountId());
        final String characterName = request.getCharacterName();
        if (!hasAccountOnGameServer(accountId)) {
            log.trace("Ignored a password change of account {} requested by player {}.", accountId, characterName);
            return;
        }
        if (request.getOldPassword().isEmpty() || request.getNewPassword().isEmpty()) {
            passwordChangeResult(characterName, MSG_INVALID_PASSWORD_DATA);
            return;
        }
        try {
            final String currentHash = PasswordHash.of(request.getOldPassword());
            final String newHash = PasswordHash.of(request.getNewPassword());
            if (loginController.changePassword(accountId, currentHash, newHash)) {
                passwordChangeResult(characterName, MSG_PASSWORD_CHANGED);
            } else if (currentHash.equals(loginController.getPasswordHash(accountId))) {
                log.warn(
                        "Password change failed for account {} requested by player {} on GS {} (0 rows updated).",
                        accountId,
                        characterName,
                        getServerId());
                passwordChangeResult(characterName, MSG_PASSWORD_CHANGE_FAILED);
            } else {
                passwordChangeResult(characterName, MSG_CURRENT_PASSWORD_MISMATCH);
            }
        } catch (DataAccessException e) {
            log.warn(
                    "Error while changing the password of account {} requested by player {} on GS {}.",
                    accountId,
                    characterName,
                    getServerId(),
                    e);
        }
    }

    private void passwordChangeResult(String characterName, String message) {
        writer.send(LoginServerMessage.newBuilder()
                .setPasswordChangeResult(GsLinkProto.PasswordChangeResult.newBuilder()
                        .setCharacterName(characterName)
                        .setMessage(message))
                .build());
    }

    private void characterReport(GsLinkProto.CharacterReport report) {
        final long[] deleteTimes = report.getDeleteTimesList().stream()
                .mapToLong(GameServerLink::epochMillis)
                .toArray();
        loginController.setCharactersOnServer(
                UUID.fromString(report.getAccountId()), report.getCount(), deleteTimes, getServerId());
    }

    private static long epochMillis(Timestamp time) {
        return Instant.ofEpochSecond(time.getSeconds(), time.getNanos()).toEpochMilli();
    }

    private void publishListing(GsLinkProto.PublishListing listing) {
        final GameServerInfo info = gameServerInfo;
        if (listing.hasStatus()) {
            info.setStatus(listing.getStatusValue());
        }
        if (listing.hasType()) {
            info.setServerType(listing.getType());
        }
        if (listing.hasSquareBracket()) {
            info.setShowingBrackets(listing.getSquareBracket());
        }
        if (listing.hasMaxPlayers()) {
            info.setMaxPlayers(listing.getMaxPlayers());
        }
        if (listing.hasAge()) {
            info.setAgeLimit(listing.getAge());
        }
    }

    private static void setGameHosts(GameServerInfo info, List<GsLinkProto.GameServerAddress> addresses) {
        info.clearServerAddresses();
        for (GsLinkProto.GameServerAddress address : addresses) {
            try {
                info.addServerAddress(address.getSubnet(), address.getAddress());
            } catch (UnknownHostException | IllegalArgumentException e) {
                // Skipped, not refused: clients of the other subnets can still reach the server.
                log.warn("Skipped the game server address of subnet \"{}\" - {}", address.getSubnet(), e.getMessage());
            }
        }
        log.info(
                "Updated Gameserver [{}] {} IP's: {}",
                info.getId(),
                info.getName(),
                String.join(", ", info.getServerAddresses()));
    }

    private String describe() {
        final GameServerInfo info = gameServerInfo;
        return (info != null) ? "server [" + info.getId() + "] " + info.getName() : "an unregistered game server";
    }
}
