/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RecurrenceScheduleTest
{
	@Nested
	class NextAfter
	{
		/**
		 * Each RRULE replaces a cron string shipped before the switch: {@code 0 20 * * *} (custom events),
		 * {@code 0 17 * * *} (Underground Coliseum default) and {@code 0 21 * * 5,6,7} (Underground Coliseum config).
		 * The expected times are the cron implementation's results, kept as literals.
		 */
		@ParameterizedTest(name = "{0} in {1} after {2} -> {3}")
		@CsvSource(delimiter = '|', textBlock = """
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | Europe/Berlin    | 2026-10-19T12:00:00Z     | 2026-10-19T18:00:00Z
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | Europe/Berlin    | 2026-10-19T17:59:59.999Z | 2026-10-19T18:00:00Z
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | Europe/Berlin    | 2026-10-19T18:00:00Z     | 2026-10-20T18:00:00Z
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | Europe/Berlin    | 2026-10-19T18:00:30Z     | 2026-10-20T18:00:00Z
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | Europe/Berlin    | 2026-10-24T19:30:00Z     | 2026-10-25T19:00:00Z
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | Europe/Berlin    | 2026-03-28T20:00:00Z     | 2026-03-29T18:00:00Z
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | Europe/Berlin    | 2026-12-31T22:59:30Z     | 2027-01-01T19:00:00Z
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | America/New_York | 2026-10-19T12:00:00Z     | 2026-10-20T00:00:00Z
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | America/New_York | 2026-03-29T00:30:00Z     | 2026-03-30T00:00:00Z
			FREQ=DAILY;BYHOUR=20;BYMINUTE=0                  | UTC              | 2026-10-25T10:00:00Z     | 2026-10-25T20:00:00Z
			FREQ=DAILY;BYHOUR=17;BYMINUTE=0                  | Europe/Berlin    | 2026-10-19T12:00:00Z     | 2026-10-19T15:00:00Z
			FREQ=DAILY;BYHOUR=17;BYMINUTE=0                  | Europe/Berlin    | 2026-10-24T19:30:00Z     | 2026-10-25T16:00:00Z
			FREQ=DAILY;BYHOUR=17;BYMINUTE=0                  | Europe/Berlin    | 2026-03-29T00:30:00Z     | 2026-03-29T15:00:00Z
			FREQ=DAILY;BYHOUR=17;BYMINUTE=0                  | America/New_York | 2026-12-31T22:59:30Z     | 2027-01-01T22:00:00Z
			FREQ=DAILY;BYHOUR=17;BYMINUTE=0                  | UTC              | 2026-10-19T18:00:00Z     | 2026-10-20T17:00:00Z
			FREQ=WEEKLY;BYDAY=FR,SA,SU;BYHOUR=21;BYMINUTE=0 | Europe/Berlin    | 2026-10-19T12:00:00Z     | 2026-10-23T19:00:00Z
			FREQ=WEEKLY;BYDAY=FR,SA,SU;BYHOUR=21;BYMINUTE=0 | Europe/Berlin    | 2026-10-23T18:00:00Z     | 2026-10-23T19:00:00Z
			FREQ=WEEKLY;BYDAY=FR,SA,SU;BYHOUR=21;BYMINUTE=0 | Europe/Berlin    | 2026-12-31T22:59:30Z     | 2027-01-01T20:00:00Z
			FREQ=WEEKLY;BYDAY=FR,SA,SU;BYHOUR=21;BYMINUTE=0 | America/New_York | 2026-10-24T19:30:00Z     | 2026-10-25T01:00:00Z
			FREQ=WEEKLY;BYDAY=FR,SA,SU;BYHOUR=21;BYMINUTE=0 | America/New_York | 2026-03-28T20:00:00Z     | 2026-03-29T01:00:00Z
			FREQ=WEEKLY;BYDAY=FR,SA,SU;BYHOUR=21;BYMINUTE=0 | UTC              | 2026-03-28T20:00:00Z     | 2026-03-28T21:00:00Z
			""")
		void nextAfter_shouldMatchReplacedCronString(String rrule, String zone, String now, String expected)
		{
			final RecurrenceSchedule schedule = new RecurrenceSchedule(rrule);

			assertThat(schedule.nextAfter(Instant.parse(now), ZoneId.of(zone))).contains(Instant.parse(expected));
		}

		/**
		 * The cron implementation accepted day of week 7 and never matched it, so {@code 0 21 * * 5,6,7} skipped Sunday
		 * against its documented "Friday, Saturday, and Sunday"; the RRULE keeps Sunday.
		 */
		@ParameterizedTest(name = "{0} in {1} -> {2}")
		@CsvSource(delimiter = '|', textBlock = """
			2026-10-25T10:00:00Z | Europe/Berlin    | 2026-10-25T20:00:00Z
			2026-03-29T00:30:00Z | UTC              | 2026-03-29T21:00:00Z
			2026-10-25T10:00:00Z | America/New_York | 2026-10-26T01:00:00Z
			""")
		void nextAfter_shouldFireOnSunday_whenSundayIsInByDay(String now, String zone, String expected)
		{
			final RecurrenceSchedule schedule = new RecurrenceSchedule("FREQ=WEEKLY;BYDAY=FR,SA,SU;BYHOUR=21;BYMINUTE=0");

			assertThat(schedule.nextAfter(Instant.parse(now), ZoneId.of(zone))).contains(Instant.parse(expected));
		}

		@Test
		void nextAfter_shouldBeEmpty_whenRuleHasEnded()
		{
			final RecurrenceSchedule schedule = new RecurrenceSchedule("FREQ=DAILY;BYHOUR=20;BYMINUTE=0;UNTIL=20260101T000000Z");

			assertThat(schedule.nextAfter(Instant.parse("2026-10-19T12:00:00Z"), ZoneId.of("UTC"))).isEmpty();
		}
	}

	@Nested
	class Create
	{
		@ParameterizedTest(name = "{0}")
		@ValueSource(strings = {
			"0 20 * * *",
			"FREQ=DAILY;BYHOUR=25;BYMINUTE=0",
			"BYHOUR=20;BYMINUTE=0",
			"FREQ=DAILY;BYHOUR=20;BYMINUTE=0;COUNT=3",
			"FREQ=HOURLY;INTERVAL=4;BYMINUTE=0",
			"FREQ=HOURLY",
			"FREQ=DAILY;BYMINUTE=0",
			"FREQ=WEEKLY;BYDAY=SA;BYMINUTE=0",
		})
		void create_shouldThrow_whenRuleIsInvalid(String rrule)
		{
			assertThatIllegalArgumentException().isThrownBy(() -> new RecurrenceSchedule(rrule)).withMessageContaining(rrule);
		}
	}
}
