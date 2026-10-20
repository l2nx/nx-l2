/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.repository.character.CharacterRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class ReportCharactersHandlerTest {
    private final FakeLoginPort port = new FakeLoginPort();
    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final ReportCharactersHandler handler = new ReportCharactersHandler(port, characters);

    @Test
    void accept_shouldReportTheCountAndTheDeleteTimes() {
        final UUID accountId = UUID.randomUUID();
        final Instant deletion = Instant.parse("2026-11-01T10:00:00Z");
        when(characters.countByAccount(accountId)).thenReturn(3);
        when(characters.findDeleteTimes(accountId)).thenReturn(List.of(deletion));

        handler.accept(new ReportCharacters(accountId));

        assertThat(port.getCalls()).containsExactly(Call.of("reportCharacters", accountId, 3, List.of(deletion)));
    }

    @Test
    void accept_shouldReportNothing_whenTheDatabaseFails() {
        when(characters.countByAccount(any())).thenThrow(new DataAccessResourceFailureException("down"));

        handler.accept(new ReportCharacters(UUID.randomUUID()));

        assertThat(port.getCalls()).isEmpty();
    }
}
