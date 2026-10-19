/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

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

		@ParameterizedTest(name = "{0} after {1} -> {2}")
		@CsvSource(delimiter = '|', textBlock = """
			FREQ=WEEKLY;BYDAY=FR;BYHOUR=20;BYMINUTE=0                 | 2026-01-12T11:17:00Z | 2026-01-16T20:00:00Z
			FREQ=WEEKLY;BYDAY=FR;BYHOUR=20;BYMINUTE=0                 | 2026-01-14T03:41:00Z | 2026-01-16T20:00:00Z
			FREQ=MONTHLY;BYMONTHDAY=20;BYHOUR=20;BYMINUTE=0           | 2026-01-03T11:17:00Z | 2026-01-20T20:00:00Z
			FREQ=MONTHLY;BYMONTHDAY=20;BYHOUR=20;BYMINUTE=0           | 2026-01-14T03:41:00Z | 2026-01-20T20:00:00Z
			FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=5;BYHOUR=20;BYMINUTE=0   | 2025-12-31T03:41:00Z | 2026-03-05T20:00:00Z
			FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=5;BYHOUR=20;BYMINUTE=0   | 2026-02-14T11:17:00Z | 2026-03-05T20:00:00Z
			FREQ=YEARLY;BYWEEKNO=10;BYDAY=MO;BYHOUR=20;BYMINUTE=0     | 2025-12-31T03:41:00Z | 2026-03-02T20:00:00Z
			FREQ=YEARLY;BYWEEKNO=10;BYDAY=MO;BYHOUR=20;BYMINUTE=0     | 2026-02-14T11:17:00Z | 2026-03-02T20:00:00Z
			FREQ=YEARLY;BYWEEKNO=10;BYMONTHDAY=5;BYHOUR=20;BYMINUTE=0 | 2025-12-31T03:41:00Z | 2026-03-05T20:00:00Z
			FREQ=YEARLY;BYWEEKNO=10;BYMONTHDAY=5;BYHOUR=20;BYMINUTE=0 | 2026-02-14T11:17:00Z | 2026-03-05T20:00:00Z
			FREQ=YEARLY;BYYEARDAY=100;BYHOUR=20;BYMINUTE=0            | 2025-12-31T03:41:00Z | 2026-04-10T20:00:00Z
			FREQ=YEARLY;BYYEARDAY=100;BYHOUR=20;BYMINUTE=0            | 2026-02-14T11:17:00Z | 2026-04-10T20:00:00Z
			""")
		void nextAfter_shouldNotDependOnEvaluationMoment_whenRuleFixesTheDay(String rrule, String now, String expected)
		{
			final RecurrenceSchedule schedule = new RecurrenceSchedule(rrule);

			assertThat(schedule.nextAfter(Instant.parse(now), ZoneOffset.UTC)).contains(Instant.parse(expected));
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
			"FREQ=WEEKLY;BYHOUR=20;BYMINUTE=0",
			"FREQ=WEEKLY;BYMONTH=3;BYHOUR=20;BYMINUTE=0",
			"FREQ=MONTHLY;BYHOUR=20;BYMINUTE=0",
			"FREQ=MONTHLY;BYMONTH=3;BYHOUR=20;BYMINUTE=0",
			"FREQ=MONTHLY;BYYEARDAY=100;BYHOUR=20;BYMINUTE=0",
			"FREQ=MONTHLY;BYWEEKNO=10;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYMONTH=3;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYMONTHDAY=1;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYMONTHDAY=13;BYDAY=FR;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYWEEKNO=10;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYWEEKNO=10;BYMONTH=3;BYHOUR=20;BYMINUTE=0",
		})
		void create_shouldThrow_whenRuleIsInvalid(String rrule)
		{
			assertThatIllegalArgumentException().isThrownBy(() -> new RecurrenceSchedule(rrule)).withMessageContaining(rrule);
		}

		@ParameterizedTest(name = "{0}")
		@ValueSource(strings = {
			"FREQ=DAILY;BYHOUR=20;BYMINUTE=0",
			"FREQ=DAILY;BYHOUR=0,4,8,12,16,20;BYMINUTE=0",
			"FREQ=WEEKLY;BYDAY=FR,SA,SU;BYHOUR=21;BYMINUTE=0",
			"FREQ=WEEKLY;BYDAY=SA,SU;BYHOUR=18;BYMINUTE=30",
			"FREQ=HOURLY;BYMINUTE=15",
			"FREQ=MINUTELY",
			"FREQ=MONTHLY;BYMONTHDAY=-1;BYHOUR=20;BYMINUTE=0",
			"FREQ=MONTHLY;BYDAY=-1FR;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYYEARDAY=100;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYDAY=20MO;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYMONTH=3;BYDAY=MO;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=5;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYWEEKNO=10;BYDAY=MO;BYHOUR=20;BYMINUTE=0",
			"FREQ=YEARLY;BYWEEKNO=10;BYMONTHDAY=5;BYHOUR=20;BYMINUTE=0",
		})
		void create_shouldAccept_whenRuleFixesEveryPart(String rrule)
		{
			assertThatNoException().isThrownBy(() -> new RecurrenceSchedule(rrule));
		}
	}
}
