/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
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
package app.l2nx.login.session;

import app.l2nx.login.config.LoginProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AddressBans {
    private final LoginProperties properties;

    /** Expiry in epoch milliseconds by {@link #banKey} form; {@link Long#MAX_VALUE} for a permanent ban. */
    final Map<String, Long> bans = new ConcurrentHashMap<>();

    /** The failed-login counter's clock; a test replaces it before the first failure. */
    Ticker ticker = Ticker.systemTicker();

    /** Failures by address; an entry lapses once {@code failed-logins-window} passes without another failure. */
    private final Cache<String, Integer> failedLogins = Caffeine.newBuilder()
            .expireAfter(Expiry.writing(this::failedLoginsWindow))
            .ticker(() -> ticker.read())
            .build();

    /**
     * Keeps the later of the old and new expiry, so a shorter ban never cuts a longer one.
     *
     * @param address an IP literal; a host name is refused, not resolved
     * @param durationMillis zero or less bans permanently
     * @return {@code false} when the address is not an IP literal; the caller logs it, it knows where it came from
     */
    public boolean add(String address, long durationMillis) {
        final String key = banKey(address);
        if (key == null) {
            return false;
        }
        final long now = System.currentTimeMillis();
        final long expiry = ((durationMillis <= 0) || (durationMillis >= Long.MAX_VALUE - now))
                ? Long.MAX_VALUE
                : now + durationMillis;
        bans.merge(key, expiry, Math::max);
        return true;
    }

    public boolean remove(String address) {
        final String key = banKey(address);
        return (key != null) && (bans.remove(key) != null);
    }

    /** The {@link InetAddress#getHostAddress()} form that {@link #isBanned} looks up, e.g. IPv6 uncompressed. */
    private static String banKey(String address) {
        if (address == null) {
            return null;
        }
        try {
            return InetAddress.ofLiteral(address).getHostAddress();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isBanned(InetAddress address) {
        final String host = address.getHostAddress();
        final List<String> keys = new ArrayList<>(4);
        keys.add(host);
        // Zeroed-octet subnet bans exist only for IPv4; IPv6 addresses are banned exactly.
        if (address instanceof Inet4Address) {
            final String[] parts = host.split("\\.");
            keys.add(parts[0] + "." + parts[1] + "." + parts[2] + ".0");
            keys.add(parts[0] + "." + parts[1] + ".0.0");
            keys.add(parts[0] + ".0.0.0");
        }
        for (String key : keys) {
            final Long expiry = bans.get(key);
            if (expiry == null) {
                continue;
            }
            if (expiry < System.currentTimeMillis()) {
                bans.remove(key, expiry);
                log.info("Removed expired ip address ban {}.", key);
                continue;
            }
            return true;
        }
        return false;
    }

    public void loginFailed(String address) {
        if (failedLogins.asMap().merge(address, 1, Integer::sum)
                < properties.ban().failedLogins()) {
            return;
        }
        // Fresh attempts once the ban ends; an address that cannot be banned warns again only after as many failures.
        failedLogins.invalidate(address);
        if (add(address, properties.ban().duration().toMillis())) {
            log.warn("Added banned address {}! Too many login attempts.", address);
        } else {
            log.warn("Could not ban address {} after too many login attempts: it is not an IP address.", address);
        }
    }

    /** Read per write rather than at construction, where the field initializers run before the properties are set. */
    private Duration failedLoginsWindow(String address, Integer failures) {
        return properties.ban().failedLoginsWindow();
    }

    public void loginSucceeded(String address) {
        failedLogins.invalidate(address);
    }

    public int size() {
        return bans.size();
    }
}
