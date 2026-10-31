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
import app.l2nx.login.account.AccountService;
import app.l2nx.login.gameserver.GameServerRegistry;
import app.l2nx.login.gameserver.GameServerTable;
import app.l2nx.login.gameserver.model.GameServerInfo;
import app.l2nx.login.gameserver.model.ListingStatus;
import app.l2nx.login.gameserver.model.Registration;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.session.LoginController;
import app.l2nx.login.session.model.SessionKey;
import app.l2nx.logging.RateLimitedLog;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.scheduling.TaskScheduler;

/**
 * gRPC delivers the messages of one stream one at a time, so the handlers of a link never overlap; the deadline and
 * other threads only send, through the writer.
 */
@Slf4j
public class GameServerLink implements StreamObserver<GameServerMessage> {
    private static final int MAX_HEX_ID_BYTES = 64;
    private static final int MAX_ADDRESSES = 32;
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private enum State {
        AWAITING_REGISTRATION,
        REGISTERED,
        ENDED
    }

    private final Set<UUID> accountsOnGameServer = ConcurrentHashMap.newKeySet();
    private final AtomicReference<State> state = new AtomicReference<>(State.AWAITING_REGISTRATION);
    private final GsLinkStreamWriter<LoginServerMessage> writer;
    private final GameServerRegistry registry;
    private final GameServerTable gameServerTable;
    private final LoginController loginController;
    private final AccountService accountService;
    private final boolean checkLoginPair;
    private final Consumer<GameServerLink> ended;

    @Getter
    private volatile GameServerInfo gameServerInfo;

    private volatile ScheduledFuture<?> registrationDeadline;

    GameServerLink(
            StreamObserver<LoginServerMessage> responses,
            GameServerRegistry registry,
            GameServerTable gameServerTable,
            LoginController loginController,
            AccountService accountService,
            boolean checkLoginPair,
            Consumer<GameServerLink> ended) {
        writer = new GsLinkStreamWriter<>(responses);
        this.registry = registry;
        this.gameServerTable = gameServerTable;
        this.loginController = loginController;
        this.accountService = accountService;
        this.checkLoginPair = checkLoginPair;
        this.ended = ended;
    }

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
                case ENDED -> {}
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
        if (end()) {
            writer.complete();
        }
    }

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
        switch (registry.register(
                registration.getServerId(),
                registration.getHexId().toByteArray(),
                registration.getAcceptAlternativeId())) {
            case Registration.Accepted(GameServerInfo info) -> attach(info, registration, chronicle);
            case Registration.Refused(Registration.Reason reason, String detail) -> refuse(refusal(reason), detail);
        }
    }

    private void attach(GameServerInfo info, GsLinkProto.Register registration, String chronicle) {
        final String previousChronicle;
        synchronized (info) {
            if (info.getGameServerLink() != null) {
                refuseDuplicate(info);
                return;
            }
            if (!state.compareAndSet(State.AWAITING_REGISTRATION, State.REGISTERED)) {
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
            registry.storeChronicle(info.getId(), chronicle);
        }
        final GsLinkProto.RegistrationResult.Builder result =
                GsLinkProto.RegistrationResult.newBuilder().setServerId(info.getId());
        final String name = gameServerTable.getServerNameById(info.getId());
        if (name != null) {
            result.setServerName(name);
        }
        writer.send(
                LoginServerMessage.newBuilder().setRegistrationResult(result).build());
        log.info("Game server [{}] {} registered, chronicle {}.", info.getId(), name, chronicle);
    }

    /**
     * Refusing, not evicting: two processes with one hex id would otherwise evict each other forever. A dead holder
     * is ended by keepalive, so the next attempt registers.
     */
    private void refuseDuplicate(GameServerInfo info) {
        log.warn(
                "Refused the registration of server [{}] {}: a live GS-link holds its hex id. Is a second game server"
                        + " running with a copy of its hexid?",
                info.getId(),
                gameServerTable.getServerNameById(info.getId()));
        close(Status.ALREADY_EXISTS.withDescription("Registration refused: server id " + info.getId()
                + " is held by a live GS-link with the same hex id."));
    }

    private void refuse(Status status, String reason) {
        log.info("Refused a game server registration: {}.", reason);
        close(status.withDescription("Registration refused: " + reason + "."));
    }

    private static Status refusal(Registration.Reason reason) {
        return switch (reason) {
            case NOT_ACCEPTED, HEX_ID_MISMATCH -> Status.PERMISSION_DENIED;
            case ID_TAKEN -> Status.ALREADY_EXISTS;
            case NO_FREE_ID -> Status.RESOURCE_EXHAUSTED;
        };
    }

    private void registrationTimedOut(Duration timeout) {
        if (state.compareAndSet(State.AWAITING_REGISTRATION, State.ENDED)) {
            log.info("Closed a GS-link that sent no Register within {}.", timeout);
            writer.fail(Status.DEADLINE_EXCEEDED.withDescription("No Register within " + timeout + "."));
            ended.accept(this);
        }
    }

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
            log.info(
                    "Server [{}] {} is now marked as disconnected.",
                    info.getId(),
                    gameServerTable.getServerNameById(info.getId()));
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
        final LoginClient client = loginController.getAuthedClientByLogin(request.getLogin());
        final GsLinkProto.AuthenticationResult.Builder result = GsLinkProto.AuthenticationResult.newBuilder()
                .setLogin(request.getLogin())
                .setRequestId(request.getRequestId());
        if (isRedeemable(client, request)) {
            result.setAccepted(true).setAccountId(client.getAccountId().toString());
        }
        writer.send(
                LoginServerMessage.newBuilder().setAuthenticationResult(result).build());
    }

    /**
     * A key enters only the server its PlayOk named, and only once: removal from the authed clients is the
     * redemption, so a replay or another server finds nothing.
     */
    private boolean isRedeemable(LoginClient client, GsLinkProto.Authenticate request) {
        if (client == null) {
            return false;
        }
        final Integer selected = client.getJoinedServerId();
        if (selected == null) {
            return false;
        }
        if (selected != getServerId()) {
            if (RATE.tryAcquire("authenticate-server")) {
                log.warn(
                        "Refused the login of {} on {}: its PlayOk was for server {}.",
                        request.getLogin(),
                        describe(),
                        selected);
            }
            return false;
        }
        return client.getSessionKey().matches(sessionKey(request.getKey()), checkLoginPair)
                && loginController.removeAuthedLoginClientIfCurrent(client);
    }

    private static SessionKey sessionKey(GsLinkProto.SessionKey key) {
        return SessionKey.builder()
                .loginOk1(key.getLoginOk1())
                .loginOk2(key.getLoginOk2())
                .playOk1(key.getPlayOk1())
                .playOk2(key.getPlayOk2())
                .build();
    }

    private void changeAccessLevel(GsLinkProto.ChangeAccessLevel change) {
        accountService.changeAccessLevel(UUID.fromString(change.getAccountId()), change.getLevel(), getServerId());
    }

    private void reportRoute(GsLinkProto.ReportRoute route) {
        final UUID accountId = UUID.fromString(route.getAccountId());
        accountService.reportRoute(
                accountId,
                route.hasClientAddress() ? StringUtils.trimToNull(route.getClientAddress()) : null,
                route.getHopsList().stream().map(StringUtils::trimToNull).toList(),
                hasAccountOnGameServer(accountId),
                getServerId());
    }

    private void requestTempBan(GsLinkProto.RequestTempBan request) {
        final UUID accountId = UUID.fromString(request.getAccountId());
        accountService.tempBan(
                accountId,
                Duration.ofSeconds(
                        request.getDuration().getSeconds(),
                        request.getDuration().getNanos()),
                request.hasIp() ? StringUtils.trimToNull(request.getIp()) : null,
                hasAccountOnGameServer(accountId),
                getServerId());
    }

    private void changePassword(GsLinkProto.ChangePassword request) {
        final UUID accountId = UUID.fromString(request.getAccountId());
        final String message = accountService.changePassword(
                accountId,
                request.getCharacterName(),
                request.getOldPassword(),
                request.getNewPassword(),
                hasAccountOnGameServer(accountId),
                getServerId());
        passwordChangeResult(request.getRequestId(), request.getCharacterName(), message);
    }

    private void passwordChangeResult(String requestId, String characterName, String message) {
        writer.send(LoginServerMessage.newBuilder()
                .setPasswordChangeResult(GsLinkProto.PasswordChangeResult.newBuilder()
                        .setRequestId(requestId)
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

    /** One listing is one update: an unknown status refuses all of it, and the stream stays open. */
    private void publishListing(GsLinkProto.PublishListing listing) {
        final ListingStatus status = listing.hasStatus() ? listingStatus(listing.getStatus()) : null;
        if (listing.hasStatus() && (status == null)) {
            if (RATE.tryAcquire("listing-status")) {
                log.warn("Refused a listing of {}: unknown status {}.", describe(), listing.getStatusValue());
            }
            return;
        }
        final GameServerInfo info = gameServerInfo;
        if (status != null) {
            info.setStatus(status);
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

    /** @return {@code null} for a value this release does not know */
    private static ListingStatus listingStatus(GsLinkProto.ListingStatus status) {
        return switch (status) {
            case LISTING_STATUS_AUTO -> ListingStatus.AUTO;
            case LISTING_STATUS_GOOD -> ListingStatus.GOOD;
            case LISTING_STATUS_NORMAL -> ListingStatus.NORMAL;
            case LISTING_STATUS_FULL -> ListingStatus.FULL;
            case LISTING_STATUS_DOWN -> ListingStatus.DOWN;
            case LISTING_STATUS_GM_ONLY -> ListingStatus.GM_ONLY;
            case UNRECOGNIZED -> null;
        };
    }

    private void setGameHosts(GameServerInfo info, List<GsLinkProto.GameServerAddress> addresses) {
        info.clearServerAddresses();
        for (GsLinkProto.GameServerAddress address : addresses) {
            try {
                info.addServerAddress(address.getSubnet(), address.getAddress());
            } catch (UnknownHostException | IllegalArgumentException e) {
                log.warn("Skipped the game server address of subnet \"{}\" - {}", address.getSubnet(), e.getMessage());
            }
        }
        log.info(
                "Updated Gameserver [{}] {} IP's: {}",
                info.getId(),
                gameServerTable.getServerNameById(info.getId()),
                String.join(", ", info.getServerAddresses()));
    }

    private String describe() {
        final GameServerInfo info = gameServerInfo;
        return (info != null)
                ? "server [" + info.getId() + "] " + gameServerTable.getServerNameById(info.getId())
                : "an unregistered game server";
    }
}
