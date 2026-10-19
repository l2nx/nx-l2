/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class StatSetTest
{
	private static StatSet statSetOf(String key, Object value)
	{
		final Map<String, Object> map = new HashMap<>();
		map.put(key, value);
		return new StatSet(map);
	}

	@Nested
	class GetDuration
	{
		@ParameterizedTest(name = "{0} -> {1}s")
		@CsvSource(
		{
			"30secs, 30",
			"1sec, 1",
			"5MINS, 300",
			"2hours, 7200",
			"1day, 86400",
			"2weeks, 1209600",
			"1month, 2592000",
			"1year, 31536000"
		})
		void getDuration_shouldParseValueAndUnit(String raw, long expectedSeconds)
		{
			final Duration actual = statSetOf("time", raw).getDuration("time");

			assertThat(actual).isEqualTo(Duration.ofSeconds(expectedSeconds));
		}

		@ParameterizedTest(name = "\"{0}\"")
		@ValueSource(strings =
		{
			"",
			"secs",
			"30",
			"30fortnights",
			"-5mins"
		})
		void getDuration_shouldThrow_whenFormatIsInvalid(String raw)
		{
			final StatSet set = statSetOf("time", raw);

			assertThatIllegalArgumentException().isThrownBy(() -> set.getDuration("time"));
		}

		@Test
		void getDuration_shouldReturnDefault_whenKeyIsAbsent()
		{
			final Duration expected = Duration.ofMinutes(3);

			final Duration actual = new StatSet().getDuration("time", expected);

			assertThat(actual).isSameAs(expected);
		}
	}

	@Nested
	class GetList
	{
		@Test
		void getList_shouldConvertStrings_whenAllParse()
		{
			final StatSet set = statSetOf("values", new ArrayList<>(List.of("1", "-2", "30")));

			final List<Integer> actual = set.getList("values", Integer.class);

			assertThat(actual).containsExactly(1, -2, 30);
		}

		@Test
		void getList_shouldConvertToDouble()
		{
			final StatSet set = statSetOf("values", new ArrayList<>(List.of("1.5", "2")));

			final List<Double> actual = set.getList("values", Double.class);

			assertThat(actual).containsExactly(1.5, 2.0);
		}

		@Test
		void getList_shouldReturnNull_whenAnyValueDoesNotParse()
		{
			final StatSet set = statSetOf("values", new ArrayList<>(List.of("1", "x")));

			final List<Integer> actual = set.getList("values", Integer.class);

			assertThat(actual).isNull();
		}
	}

	@Nested
	class GetEnumList
	{
		@Test
		void getEnumList_shouldConvertNames_whenAllAreConstants()
		{
			final StatSet set = statSetOf("units", new ArrayList<>(List.of("SECONDS", "DAYS")));

			final List<TimeUnit> actual = set.getEnumList("units", TimeUnit.class);

			assertThat(actual).containsExactly(TimeUnit.SECONDS, TimeUnit.DAYS);
		}
	}
}
