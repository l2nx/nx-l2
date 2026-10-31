/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.account;

import app.l2nx.logging.RateLimitedLog;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.session.AddressBans;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The GS-link secret authenticates the game server, so it may change any account; only requests acting for the
 * player itself need the account on that server.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountService implements InitializingBean {
    private static final String MSG_INVALID_PASSWORD_DATA = "Invalid password data! Try again.";
    private static final String MSG_PASSWORD_CHANGED = "You have successfully changed your password!";
    private static final String MSG_PASSWORD_CHANGE_FAILED = "The password change was unsuccessful!";
    private static final String MSG_CURRENT_PASSWORD_MISMATCH =
            "The typed current password doesn't match with your current one.";
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private final AccountRepository repository;
    private final AddressBans addressBans;
    private final PasswordEncoder encoder;
    private final LoginProperties properties;

    /**
     * Matched against on an unknown login or an unreadable stored hash. Encoded once at bean initialisation, so the
     * first such login spends one hash like any other, not two.
     */
    private String unknownAccountHash;

    /** A new password must reach the server through every variant it serves, or a client could not log in with it. */
    private Set<LoginVariant> servedVariants;

    @Override
    public void afterPropertiesSet() {
        unknownAccountHash = encoder.encode("unknown-account");
        servedVariants = EnumSet.noneOf(LoginVariant.class);
        properties.client().ports().forEach(port -> servedVariants.add(port.variant()));
    }

    /**
     * Creates an unknown account when {@code auto-create-accounts} is on. A stored hash weaker than the encoder's
     * current parameters is replaced by a fresh one on a successful login.
     * @return the account, {@code null} when it does not exist (and is not created) or the password is wrong
     */
    public AccountInfo authenticate(String address, String login, String password) {
        try {
            final Optional<AccountInfo> stored = repository.findAccountInfo(login, Instant.now());
            if (stored.isEmpty()) {
                return properties.autoCreateAccounts() ? create(address, login, password) : unknown(address, password);
            }
            final AccountInfo info = stored.get();
            if (!matches(password, info)) {
                addressBans.loginFailed(address);
                return null;
            }
            addressBans.loginSucceeded(address);
            if (encoder.upgradeEncoding(info.passwordHash())) {
                upgrade(info, password);
            }
            return info;
        } catch (DataAccessException e) {
            log.warn("Could not authenticate account '{}'.", login, e);
            return null;
        }
    }

    /** A stored hash the encoder cannot read is a wrong password that costs the same hash as {@link #unknown}. */
    private boolean matches(String password, AccountInfo info) {
        try {
            return encoder.matches(password, info.passwordHash());
        } catch (IllegalArgumentException e) {
            log.warn("Could not check the password of account '{}': {}", info.login(), e.getMessage());
            encoder.matches(password, unknownAccountHash);
            return false;
        }
    }

    private AccountInfo create(String address, String login, String password) {
        final String passwordHash = encoder.encode(password);
        final UUID id = repository.create(login, passwordHash, Instant.now(), address);
        log.info("Auto created account '{}'.", login);
        addressBans.loginSucceeded(address);
        return AccountInfo.builder()
                .id(id)
                .login(login)
                .passwordHash(passwordHash)
                .accessLevel(0)
                .lastServer(0)
                .build();
    }

    /**
     * Spends one hash on an unknown login so the answer time does not tell which logins exist.
     */
    private AccountInfo unknown(String address, String password) {
        encoder.matches(password, unknownAccountHash);
        addressBans.loginFailed(address);
        return null;
    }

    private void upgrade(AccountInfo info, String password) {
        try {
            // A lost race means the password changed meanwhile; the next login upgrades the new hash.
            if (repository.replacePassword(info.id(), info.passwordHash(), encoder.encode(password))) {
                log.info("Upgraded the password hash of account '{}'.", info.login());
            }
        } catch (DataAccessException e) {
            log.warn("Could not upgrade the password hash of account '{}'.", info.login(), e);
        }
    }

    /** Applies to any account, online or not: a level of -1 is the permanent ban. */
    public void changeAccessLevel(UUID accountId, int level, int serverId) {
        try {
            if (repository.updateAccessLevel(accountId, level)) {
                log.info("Game server {} set the access level of account {} to {}.", serverId, accountId, level);
            } else {
                log.warn("Game server {} set the access level of unknown account {}.", serverId, accountId);
            }
        } catch (DataAccessException e) {
            log.warn("Could not set the access level of account {} from game server {}.", accountId, serverId, e);
        }
    }

    /**
     * Applies to any account, online or not, as {@link #changeAccessLevel} does.
     * @param ip the address to ban for the same length, {@code null} for none
     * @param onServer whether the account is on the requesting game server, for the log
     */
    public void tempBan(UUID accountId, Duration duration, String ip, boolean onServer, int serverId) {
        if (!duration.isPositive()) {
            log.warn(
                    "Refused a temporary ban of account {} from game server {}: length {}.",
                    accountId,
                    serverId,
                    duration);
            return;
        }
        banAccount(accountId, duration, onServer, serverId);
        if (ip != null) {
            banAddress(accountId, duration, ip, serverId);
        }
    }

    private void banAccount(UUID accountId, Duration duration, boolean onServer, int serverId) {
        try {
            if (repository.updateBannedUntil(accountId, Instant.now().plus(duration))) {
                log.info(
                        "Game server {} banned account {} for {} (online there: {}).",
                        serverId,
                        accountId,
                        duration,
                        onServer ? "yes" : "no");
            } else {
                log.warn(
                        "Skipped the account part of a temporary ban from game server {}: account {} does not exist.",
                        serverId,
                        accountId);
            }
        } catch (DataAccessException e) {
            log.warn("Could not store the temporary ban of account {} from game server {}.", accountId, serverId, e);
        }
    }

    /** Applies whether or not the account ban did: the game server saw the address itself. */
    private void banAddress(UUID accountId, Duration duration, String ip, int serverId) {
        if (addressBans.add(ip, duration.toMillis())) {
            log.info("Game server {} banned address {} of account {} for {}.", serverId, ip, accountId, duration);
        } else {
            log.warn(
                    "Ignored the address ban of account {} from game server {}: '{}' is not an IP address.",
                    accountId,
                    serverId,
                    ip);
        }
    }

    /**
     * Only for an account on the requesting game server: the client reports its own route.
     * @param clientAddress {@code null} when the client reported none
     * @param hops nearest first, a blank hop as {@code null}; hops past the fourth are not stored
     */
    public void reportRoute(UUID accountId, String clientAddress, List<String> hops, boolean onServer, int serverId) {
        if (!onServer) {
            if (RATE.tryAcquire("route")) {
                log.warn("Ignored the route of account {}: not on game server {}.", accountId, serverId);
            }
            return;
        }
        try {
            repository.updateTracert(accountId, clientAddress, hop(hops, 0), hop(hops, 1), hop(hops, 2), hop(hops, 3));
        } catch (DataAccessException e) {
            log.warn("Could not store the route of account {} from game server {}.", accountId, serverId, e);
        }
    }

    private static String hop(List<String> hops, int index) {
        return (index < hops.size()) ? hops.get(index) : null;
    }

    /**
     * Only for an account on the requesting game server: the player changes its own password there.
     * @return the answer text for the player, always one
     */
    public String changePassword(
            UUID accountId,
            String characterName,
            String oldPassword,
            String newPassword,
            boolean onServer,
            int serverId) {
        if (!onServer) {
            log.warn(
                    "Refused a password change of account {} requested by player {}: not on game server {}.",
                    accountId,
                    characterName,
                    serverId);
            return MSG_PASSWORD_CHANGE_FAILED;
        }
        if (oldPassword.isEmpty() || newPassword.isEmpty()) {
            if (RATE.tryAcquire("empty-password")) {
                log.warn(
                        "Refused a password change of account {} requested by player {} on game server {}: an empty"
                                + " password.",
                        accountId,
                        characterName,
                        serverId);
            }
            return MSG_INVALID_PASSWORD_DATA;
        }
        if (!carriedByEveryVariant(newPassword)) {
            log.warn(
                    "Refused a password change of account {} requested by player {} on game server {}: the client"
                            + " cannot send the new password.",
                    accountId,
                    characterName,
                    serverId);
            return MSG_INVALID_PASSWORD_DATA;
        }
        try {
            final String current = repository.findPassword(accountId).orElse(null);
            if (current == null) {
                log.warn(
                        "Refused a password change of unknown account {} requested by player {} on game server {}.",
                        accountId,
                        characterName,
                        serverId);
                return MSG_PASSWORD_CHANGE_FAILED;
            }
            if (!encoder.matches(oldPassword, current)) {
                if (RATE.tryAcquire("current-password-mismatch")) {
                    log.warn(
                            "Refused a password change of account {} requested by player {} on game server {}: the"
                                    + " current password does not match.",
                            accountId,
                            characterName,
                            serverId);
                }
                return MSG_CURRENT_PASSWORD_MISMATCH;
            }
            if (!repository.replacePassword(accountId, current, encoder.encode(newPassword))) {
                log.warn(
                        "Password change of account {} requested by player {} on game server {} lost a race with"
                                + " another change.",
                        accountId,
                        characterName,
                        serverId);
                return MSG_PASSWORD_CHANGE_FAILED;
            }
            log.info(
                    "Changed the password of account {} requested by player {} on game server {}.",
                    accountId,
                    characterName,
                    serverId);
            return MSG_PASSWORD_CHANGED;
        } catch (DataAccessException | IllegalArgumentException e) {
            log.warn(
                    "Error while changing the password of account {} requested by player {} on game server {}.",
                    accountId,
                    characterName,
                    serverId,
                    e);
            return MSG_PASSWORD_CHANGE_FAILED;
        }
    }

    private boolean carriedByEveryVariant(String password) {
        return servedVariants.stream().allMatch(variant -> variant.carriesPassword(password));
    }
}
