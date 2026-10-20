/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Records every port call in order; a call that waits for an answer reports whether the link is up. */
public final class FakeLoginPort implements LoginPort {
    @Getter
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    @Setter
    private volatile boolean linkUp = true;

    @Override
    public boolean authenticate(String login, SessionKey key) {
        calls.add(Call.of("authenticate", login, key));
        return linkUp;
    }

    @Override
    public void accountsEntered(List<UUID> accountIds) {
        calls.add(Call.of("accountsEntered", accountIds));
    }

    @Override
    public void accountLeft(UUID accountId) {
        calls.add(Call.of("accountLeft", accountId));
    }

    @Override
    public void changeAccessLevel(UUID accountId, int level) {
        calls.add(Call.of("changeAccessLevel", accountId, level));
    }

    @Override
    public void reportRoute(UUID accountId, List<String> route) {
        calls.add(Call.of("reportRoute", accountId, route));
    }

    @Override
    public void requestTempBan(UUID accountId, String ip, Duration duration) {
        calls.add(Call.of("requestTempBan", accountId, ip, duration));
    }

    @Override
    public boolean changePassword(UUID accountId, String characterName, String oldPassword, String newPassword) {
        calls.add(Call.of("changePassword", accountId, characterName, oldPassword, newPassword));
        return linkUp;
    }

    @Override
    public void reportCharacters(UUID accountId, int count, List<Instant> deleteTimes) {
        calls.add(Call.of("reportCharacters", accountId, count, deleteTimes));
    }

    @Override
    public void publishListing(Map<ListingAttribute, Integer> attributes) {
        calls.add(Call.of("publishListing", attributes));
    }

    @Builder(toBuilder = true)
    public record Call(String method, List<Object> arguments) {
        public static Call of(String method, Object... arguments) {
            // Arrays.asList keeps null arguments, List.of would reject them.
            return Call.builder()
                    .method(method)
                    .arguments(Arrays.asList(arguments))
                    .build();
        }
    }
}
