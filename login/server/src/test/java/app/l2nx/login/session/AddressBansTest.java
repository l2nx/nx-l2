/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.session;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.config.TestLoginProperties;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

class AddressBansTest {
    private static final String ADDRESS = "203.0.113.7";

    private final LoginProperties properties = TestLoginProperties.create(new File("."));
    private final AddressBans bans = new AddressBans(properties);

    @Nested
    class IsBanned {
        @Test
        void isBanned_shouldMatchAnExactIpv6Ban_andNotThrow() throws Exception {
            bans.add("2001:db8:0:0:0:0:0:1", 0);

            assertThat(bans.isBanned(InetAddress.getByName("2001:db8::1"))).isTrue();
            assertThat(bans.isBanned(InetAddress.getByName("2001:db8::2"))).isFalse();
        }

        @ParameterizedTest(name = "ban {0}, client {1}")
        @CsvSource({"2001:db8::1, 2001:db8:0:0:0:0:0:1", "2001:db8:0:0:0:0:0:1, 2001:db8::1", "2001:DB8::1, 2001:db8::1"
        })
        void isBanned_shouldMatch_whateverIpv6FormTheBanUses(String ban, String client) {
            bans.add(ban, 0);

            assertThat(bans.isBanned(InetAddress.ofLiteral(client))).isTrue();
        }

        @Test
        void isBanned_shouldMatchAZeroedIpv4Subnet() throws Exception {
            bans.add("10.1.0.0", 0);

            assertThat(bans.isBanned(InetAddress.getByName("10.1.2.3"))).isTrue();
            assertThat(bans.isBanned(InetAddress.getByName("10.2.2.3"))).isFalse();
        }

        @Test
        void isBanned_shouldDropAnExpiredSubnetBan_andCheckTheWiderOnes() throws Exception {
            bans.bans.put("10.1.2.0", System.currentTimeMillis() - 1);
            bans.add("10.0.0.0", 0);

            assertThat(bans.isBanned(InetAddress.getByName("10.1.2.3"))).isTrue();
            assertThat(bans.bans).doesNotContainKey("10.1.2.0");
        }
    }

    @Nested
    class Add {
        @Test
        void add_shouldRefuseAHostName_withoutResolvingIt() {
            assertThat(bans.add("localhost", 0)).isFalse();
            assertThat(bans.size()).isZero();
        }

        @ParameterizedTest(name = "{0} ms, then {1} ms")
        @CsvSource({"60000, 3600000", "3600000, 60000", "0, 60000", "60000, 0"})
        void add_shouldKeepTheLaterExpiry_whenTheAddressIsAlreadyBanned(long first, long second) {
            final long before = System.currentTimeMillis();

            bans.add(ADDRESS, first);
            bans.add(ADDRESS, second);

            final long actual = bans.bans.get(ADDRESS);
            if ((first <= 0) || (second <= 0)) {
                assertThat(actual).isEqualTo(Long.MAX_VALUE);
            } else {
                assertThat(actual)
                        .isBetween(
                                before + Math.max(first, second), System.currentTimeMillis() + Math.max(first, second));
            }
        }

        @Test
        void add_shouldReplaceAnExpiredBan_thatIsNotSweptYet() throws Exception {
            bans.bans.put(ADDRESS, System.currentTimeMillis() - 1);

            assertThat(bans.add(ADDRESS, 60_000)).isTrue();

            assertThat(bans.isBanned(InetAddress.getByName(ADDRESS))).isTrue();
        }

        @Test
        void add_shouldBanPermanently_whenTheExpiryWouldOverflow() {
            bans.add(ADDRESS, Long.MAX_VALUE - 1);

            assertThat(bans.bans.get(ADDRESS)).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Nested
    class Remove {
        @Test
        void remove_shouldRemoveTheBan_givenAnotherIpv6Form() {
            bans.add("2001:db8::1", 0);

            assertThat(bans.remove("2001:db8:0:0:0:0:0:1")).isTrue();
            assertThat(bans.size()).isZero();
        }
    }

    @Nested
    class LoginFailed {
        @Test
        void loginFailed_shouldBanTheAddress_atTheConfiguredCount() throws Exception {
            failLogins(properties.ban().failedLogins() - 1);
            assertThat(bans.isBanned(InetAddress.getByName(ADDRESS))).isFalse();

            bans.loginFailed(ADDRESS);

            assertThat(bans.isBanned(InetAddress.getByName(ADDRESS))).isTrue();
        }

        @Test
        void loginFailed_shouldWarnOncePerThreshold_whenTheAddressCannotBeBanned() {
            final String local = "local:login-failed-test";
            final Logger logger = (Logger) LoggerFactory.getLogger(AddressBans.class);
            final ListAppender<ILoggingEvent> logs = new ListAppender<>();
            logs.start();
            logger.addAppender(logs);
            try {
                for (int i = 0; i < (2 * properties.ban().failedLogins()) - 1; i++) {
                    bans.loginFailed(local);
                }
            } finally {
                logger.detachAppender(logs);
            }

            assertThat(bans.size()).isZero();
            assertThat(logs.list)
                    .filteredOn(event -> event.getFormattedMessage().contains(local))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).startsWith("Could not ban address");
                    });
        }

        @Test
        void loginSucceeded_shouldResetTheCount() throws Exception {
            failLogins(properties.ban().failedLogins() - 1);

            bans.loginSucceeded(ADDRESS);
            failLogins(properties.ban().failedLogins() - 1);

            assertThat(bans.isBanned(InetAddress.getByName(ADDRESS))).isFalse();
        }

        @Test
        void loginFailed_shouldCountAFreshStreak_whenTheWindowPassesWithoutAFailure() throws Exception {
            final AtomicLong nanos = new AtomicLong();
            bans.ticker = nanos::get;
            failLogins(properties.ban().failedLogins() - 1);

            nanos.addAndGet(properties.ban().failedLoginsWindow().toNanos());
            failLogins(properties.ban().failedLogins() - 1);

            assertThat(bans.isBanned(InetAddress.getByName(ADDRESS))).isFalse();
        }

        @Test
        void loginFailed_shouldBanTheAddress_whenEachFailureFallsWithinTheWindowOfThePreviousOne() throws Exception {
            final AtomicLong nanos = new AtomicLong();
            bans.ticker = nanos::get;
            final long step = properties.ban().failedLoginsWindow().toNanos() - 1;

            for (int i = 0; i < properties.ban().failedLogins(); i++) {
                bans.loginFailed(ADDRESS);
                nanos.addAndGet(step);
            }

            assertThat(bans.isBanned(InetAddress.getByName(ADDRESS))).isTrue();
        }

        private void failLogins(int count) {
            for (int i = 0; i < count; i++) {
                bans.loginFailed(ADDRESS);
            }
        }
    }
}
