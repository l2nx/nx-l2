/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.config.TestPasswordEncoders;
import app.l2nx.login.session.AddressBans;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountServiceTest {
    private static final UUID ACCOUNT = UUID.fromString("0192f0a0-0000-7000-8000-000000000001");
    private static final int SERVER = 1;
    private static final Duration THIRTY_MINUTES = Duration.ofMinutes(30);
    private static final PasswordEncoder ENCODER = TestPasswordEncoders.argon2();

    private final AccountRepository repository = mock(AccountRepository.class);
    private final AddressBans addressBans = mock(AddressBans.class);
    private final PasswordEncoder encoder = spy(ENCODER);
    private final AccountService service = service(true);

    private AccountService service(boolean autoCreateAccounts) {
        final LoginProperties properties = TestLoginProperties.create(new File(".")).toBuilder()
                .autoCreateAccounts(autoCreateAccounts)
                .build();
        final AccountService created = new AccountService(repository, addressBans, encoder, properties);
        created.afterPropertiesSet();
        return created;
    }

    @Nested
    class Authenticate {
        private static final String ADDRESS = "203.0.113.7";
        private static final String LOGIN = "hero";

        @Test
        void authenticate_shouldReturnTheAccount_whenThePasswordIsRight() {
            final AccountInfo stored = account(ENCODER.encode("secret"));
            when(repository.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(stored));

            final AccountInfo actual = service.authenticate(ADDRESS, LOGIN, "secret");

            assertThat(actual).isEqualTo(stored);
            verify(addressBans).loginSucceeded(ADDRESS);
            verify(repository, never()).replacePassword(any(), anyString(), anyString());
        }

        @Test
        void authenticate_shouldReturnNull_andCountAFailure_whenThePasswordIsWrong() {
            when(repository.findAccountInfo(eq(LOGIN), any()))
                    .thenReturn(Optional.of(account(ENCODER.encode("secret"))));

            final AccountInfo actual = service.authenticate(ADDRESS, LOGIN, "wrong");

            assertThat(actual).isNull();
            verify(addressBans).loginFailed(ADDRESS);
        }

        @Test
        void authenticate_shouldCreateTheAccountWithAMatchingHash_whenUnknownAndAutoCreateIsOn() {
            when(repository.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.empty());
            when(repository.create(eq(LOGIN), anyString(), any(), eq(ADDRESS))).thenReturn(ACCOUNT);

            final AccountInfo actual = service.authenticate(ADDRESS, LOGIN, "secret");

            final ArgumentCaptor<String> hash = ArgumentCaptor.captor();
            verify(repository).create(eq(LOGIN), hash.capture(), any(), eq(ADDRESS));
            assertThat(ENCODER.matches("secret", hash.getValue())).isTrue();
            assertThat(actual)
                    .isEqualTo(AccountInfo.builder()
                            .id(ACCOUNT)
                            .login(LOGIN)
                            .passwordHash(hash.getValue())
                            .accessLevel(0)
                            .lastServer(0)
                            .build());
            verify(repository, times(1)).findAccountInfo(eq(LOGIN), any());
            verify(addressBans).loginSucceeded(ADDRESS);
        }

        @Test
        void authenticate_shouldReturnNull_whenCreatingTheAccountFails() {
            when(repository.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.empty());
            when(repository.create(eq(LOGIN), anyString(), any(), eq(ADDRESS)))
                    .thenThrow(new DuplicateKeyException("accounts_login_key"));

            assertThat(service.authenticate(ADDRESS, LOGIN, "secret")).isNull();
        }

        @Test
        void authenticate_shouldStillSpendAHash_whenUnknownAndAutoCreateIsOff() {
            when(repository.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.empty());

            final AccountInfo actual = service(false).authenticate(ADDRESS, LOGIN, "secret");

            assertThat(actual).isNull();
            verify(encoder, times(1)).matches(eq("secret"), anyString());
            verify(addressBans).loginFailed(ADDRESS);
            verify(repository, never()).create(any(), any(), any(), any());
        }

        @Test
        void authenticate_shouldEncodeNoHash_whenTheLoginIsUnknown() {
            clearInvocations(encoder);
            final AccountService initialised = service(false);
            verify(encoder).encode("unknown-account");
            clearInvocations(encoder);
            when(repository.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.empty());

            initialised.authenticate(ADDRESS, LOGIN, "secret");

            verify(encoder, never()).encode(anyString());
            verify(encoder, times(1)).matches(eq("secret"), anyString());
        }

        @Test
        void authenticate_shouldReplaceAWeakerHash_whenThePasswordIsRight() {
            final String weak = TestPasswordEncoders.weakArgon2Hash("secret");
            when(repository.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(account(weak)));

            final AccountInfo actual = service.authenticate(ADDRESS, LOGIN, "secret");

            assertThat(actual).isNotNull();
            final ArgumentCaptor<String> hash = ArgumentCaptor.captor();
            verify(repository).replacePassword(eq(ACCOUNT), eq(weak), hash.capture());
            assertThat(ENCODER.matches("secret", hash.getValue())).isTrue();
            assertThat(ENCODER.upgradeEncoding(hash.getValue())).isFalse();
        }

        @Test
        void authenticate_shouldLogIn_whenTheUpgradeLosesARace() {
            final String weak = TestPasswordEncoders.weakArgon2Hash("secret");
            when(repository.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(account(weak)));
            when(repository.replacePassword(eq(ACCOUNT), eq(weak), anyString())).thenReturn(false);

            assertThat(service.authenticate(ADDRESS, LOGIN, "secret")).isNotNull();
        }

        @Test
        void authenticate_shouldLogIn_whenStoringTheUpgradeFails() {
            final String weak = TestPasswordEncoders.weakArgon2Hash("secret");
            when(repository.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(account(weak)));
            when(repository.replacePassword(eq(ACCOUNT), eq(weak), anyString()))
                    .thenThrow(new QueryTimeoutException("statement timeout"));

            assertThat(service.authenticate(ADDRESS, LOGIN, "secret")).isNotNull();
        }

        @Test
        void authenticate_shouldCountAFailure_andSpendAHash_whenTheStoredHashHasAnUnknownEncoding() {
            final String sha1 = "{sha1}5en6G6MezRroT3XKqkdPOmY/BfQ=";
            when(repository.findAccountInfo(eq(LOGIN), any())).thenReturn(Optional.of(account(sha1)));

            final AccountInfo actual = service.authenticate(ADDRESS, LOGIN, "secret");

            assertThat(actual).isNull();
            verify(encoder).matches("secret", sha1);
            verify(encoder).matches(eq("secret"), argThat(hash -> hash.startsWith("{argon2}")));
            verify(addressBans).loginFailed(ADDRESS);
            verify(addressBans, never()).loginSucceeded(anyString());
        }

        @Test
        void authenticate_shouldReturnNull_whenTheLookupFails() {
            when(repository.findAccountInfo(eq(LOGIN), any()))
                    .thenThrow(new QueryTimeoutException("statement timeout"));

            assertThat(service.authenticate(ADDRESS, LOGIN, "secret")).isNull();
        }

        private static AccountInfo account(String passwordHash) {
            return AccountInfo.builder()
                    .id(ACCOUNT)
                    .login(LOGIN)
                    .passwordHash(passwordHash)
                    .accessLevel(0)
                    .lastServer(SERVER)
                    .build();
        }
    }

    @Nested
    class ChangeAccessLevel {
        @Test
        void changeAccessLevel_shouldStoreTheLevel() {
            when(repository.updateAccessLevel(ACCOUNT, -1)).thenReturn(true);

            service.changeAccessLevel(ACCOUNT, -1, SERVER);

            verify(repository).updateAccessLevel(ACCOUNT, -1);
        }

        @Test
        void changeAccessLevel_shouldChangeNothingElse_whenTheAccountIsUnknown() {
            service.changeAccessLevel(ACCOUNT, -1, SERVER);

            verify(repository).updateAccessLevel(ACCOUNT, -1);
            verifyNoInteractions(addressBans);
        }

        @Test
        void changeAccessLevel_shouldNotThrow_whenTheStoreFails() {
            when(repository.updateAccessLevel(ACCOUNT, -1)).thenThrow(new QueryTimeoutException("statement timeout"));

            service.changeAccessLevel(ACCOUNT, -1, SERVER);

            verify(repository).updateAccessLevel(ACCOUNT, -1);
        }
    }

    @Nested
    class TempBan {
        @ParameterizedTest(name = "{0} minutes")
        @ValueSource(longs = {0, -5})
        void tempBan_shouldBeRefused_whenTheLengthIsNotPositive(long minutes) {
            service.tempBan(ACCOUNT, Duration.ofMinutes(minutes), "10.0.0.7", true, SERVER);

            verifyNoInteractions(repository, addressBans);
        }

        @ParameterizedTest(name = "online there: {0}")
        @ValueSource(booleans = {true, false})
        void tempBan_shouldBanTheAccount_whetherItIsOnTheServerOrNot(boolean onServer) {
            when(repository.updateBannedUntil(eq(ACCOUNT), any())).thenReturn(true);
            final Instant before = Instant.now();

            service.tempBan(ACCOUNT, THIRTY_MINUTES, null, onServer, SERVER);

            final ArgumentCaptor<Instant> until = ArgumentCaptor.captor();
            verify(repository).updateBannedUntil(eq(ACCOUNT), until.capture());
            assertThat(until.getValue()).isCloseTo(before.plus(THIRTY_MINUTES), within(5, ChronoUnit.SECONDS));
            verifyNoInteractions(addressBans);
        }

        @Test
        void tempBan_shouldBanTheAddressForTheSameLength() {
            when(repository.updateBannedUntil(eq(ACCOUNT), any())).thenReturn(true);
            when(addressBans.add("10.0.0.7", THIRTY_MINUTES.toMillis())).thenReturn(true);

            service.tempBan(ACCOUNT, THIRTY_MINUTES, "10.0.0.7", false, SERVER);

            verify(addressBans).add("10.0.0.7", THIRTY_MINUTES.toMillis());
        }

        @Test
        void tempBan_shouldStillBanTheAddress_whenTheAccountIsUnknown() {
            when(repository.updateBannedUntil(eq(ACCOUNT), any())).thenReturn(false);

            service.tempBan(ACCOUNT, THIRTY_MINUTES, "10.0.0.7", false, SERVER);

            verify(addressBans).add("10.0.0.7", THIRTY_MINUTES.toMillis());
        }

        @Test
        void tempBan_shouldStillBanTheAccount_whenTheAddressIsNotAnIpAddress() {
            when(repository.updateBannedUntil(eq(ACCOUNT), any())).thenReturn(true);
            when(addressBans.add("localhost", THIRTY_MINUTES.toMillis())).thenReturn(false);

            service.tempBan(ACCOUNT, THIRTY_MINUTES, "localhost", true, SERVER);

            verify(repository).updateBannedUntil(eq(ACCOUNT), any());
            verify(addressBans).add("localhost", THIRTY_MINUTES.toMillis());
        }

        @Test
        void tempBan_shouldStillBanTheAddress_whenStoringTheAccountBanFails() {
            when(repository.updateBannedUntil(eq(ACCOUNT), any()))
                    .thenThrow(new QueryTimeoutException("statement timeout"));

            service.tempBan(ACCOUNT, THIRTY_MINUTES, "10.0.0.7", true, SERVER);

            verify(addressBans).add("10.0.0.7", THIRTY_MINUTES.toMillis());
        }
    }

    @Nested
    class ReportRoute {
        @Test
        void reportRoute_shouldStoreTheAddressAndTheFirstFourHops_whenTheAccountIsOnTheServer() {
            service.reportRoute(
                    ACCOUNT,
                    "10.0.0.1",
                    Arrays.asList("10.0.0.2", null, "10.0.0.4", "10.0.0.5", "10.0.0.6"),
                    true,
                    SERVER);

            verify(repository).updateTracert(ACCOUNT, "10.0.0.1", "10.0.0.2", null, "10.0.0.4", "10.0.0.5");
        }

        @Test
        void reportRoute_shouldLeaveMissingHopsEmpty() {
            service.reportRoute(ACCOUNT, null, List.of("10.0.0.2"), true, SERVER);

            verify(repository).updateTracert(ACCOUNT, null, "10.0.0.2", null, null, null);
        }

        @Test
        void reportRoute_shouldBeRefused_whenTheAccountIsNotOnTheServer() {
            service.reportRoute(ACCOUNT, "10.0.0.1", List.of("10.0.0.2"), false, SERVER);

            verifyNoInteractions(repository);
        }
    }

    @Nested
    class ChangePassword {
        @Test
        void changePassword_shouldStoreANewHash_whenTheCurrentPasswordMatches() {
            final String current = ENCODER.encode("old-pass");
            when(repository.findPassword(ACCOUNT)).thenReturn(Optional.of(current));
            when(repository.replacePassword(eq(ACCOUNT), eq(current), anyString()))
                    .thenReturn(true);

            final String actual = changePassword("old-pass", "new-pass", true);

            assertThat(actual).isEqualTo("You have successfully changed your password!");
            final ArgumentCaptor<String> hash = ArgumentCaptor.captor();
            verify(repository).replacePassword(eq(ACCOUNT), eq(current), hash.capture());
            assertThat(ENCODER.matches("new-pass", hash.getValue())).isTrue();
        }

        @Test
        void changePassword_shouldAnswerMismatch_andChangeNothing_whenTheCurrentPasswordDiffers() {
            when(repository.findPassword(ACCOUNT)).thenReturn(Optional.of(ENCODER.encode("other-pass")));

            final String actual = changePassword("old-pass", "new-pass", true);

            assertThat(actual).isEqualTo("The typed current password doesn't match with your current one.");
            verify(repository, never()).replacePassword(any(), anyString(), anyString());
        }

        @Test
        void changePassword_shouldAnswerFailed_whenAnotherChangeWonTheRace() {
            when(repository.findPassword(ACCOUNT)).thenReturn(Optional.of(ENCODER.encode("old-pass")));

            final String actual = changePassword("old-pass", "new-pass", true);

            assertThat(actual).isEqualTo("The password change was unsuccessful!");
        }

        @Test
        void changePassword_shouldAnswerFailed_whenTheAccountIsUnknown() {
            when(repository.findPassword(ACCOUNT)).thenReturn(Optional.empty());

            final String actual = changePassword("old-pass", "new-pass", true);

            assertThat(actual).isEqualTo("The password change was unsuccessful!");
            verify(repository, never()).replacePassword(any(), anyString(), anyString());
        }

        @ParameterizedTest(name = "old \"{0}\", new \"{1}\"")
        @CsvSource({"'', new-pass", "old-pass, ''"})
        void changePassword_shouldAnswerInvalidData_whenAPasswordIsEmpty(String oldPassword, String newPassword) {
            final String actual = changePassword(oldPassword, newPassword, true);

            assertThat(actual).isEqualTo("Invalid password data! Try again.");
            verifyNoInteractions(repository);
        }

        @ParameterizedTest(name = "new \"{0}\"")
        @ValueSource(strings = {"seventeen_bytes_p", "пароль_пароль", " new-pass", "new-pass ", "   "})
        void changePassword_shouldAnswerInvalidData_andChangeNothing_whenTheClientCannotSendTheNewPassword(
                String newPassword) {
            final String actual = changePassword("old-pass", newPassword, true);

            assertThat(actual).isEqualTo("Invalid password data! Try again.");
            verifyNoInteractions(repository);
        }

        @Test
        void changePassword_shouldAnswerFailed_whenTheStoreFails() {
            when(repository.findPassword(ACCOUNT)).thenThrow(new QueryTimeoutException("statement timeout"));

            final String actual = changePassword("old-pass", "new-pass", true);

            assertThat(actual).isEqualTo("The password change was unsuccessful!");
        }

        @Test
        void changePassword_shouldAnswerFailed_andChangeNothing_whenTheAccountIsNotOnTheServer() {
            final String actual = changePassword("old-pass", "new-pass", false);

            assertThat(actual).isEqualTo("The password change was unsuccessful!");
            verifyNoInteractions(repository);
        }

        private String changePassword(String oldPassword, String newPassword, boolean onServer) {
            return service.changePassword(ACCOUNT, "Hero", oldPassword, newPassword, onServer, SERVER);
        }
    }
}
