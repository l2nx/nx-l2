/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.TimeZone;

import org.dmfs.rfc5545.recur.Freq;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recur.RecurrenceRule.Part;
import org.dmfs.rfc5545.recur.RecurrenceRule.RfcMode;
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator;

/**
 * A recurring start time configured as an RFC 5545 RRULE ({@code FREQ=DAILY;BYHOUR=20;BYMINUTE=0}).<br>
 * The rule has no DTSTART of its own and is evaluated from the current minute, so everything that would depend on
 * DTSTART is rejected: COUNT, INTERVAL, and a left-out BYMINUTE / BYHOUR that would take the minute or hour of the
 * evaluation.
 */
public final class RecurrenceSchedule
{
	private final String rrule;
	private final RecurrenceRule rule;

	/**
	 * @param rrule the RRULE value, without the {@code RRULE:} prefix
	 * @throws IllegalArgumentException if the rule is not valid RFC 5545
	 */
	public RecurrenceSchedule(String rrule)
	{
		this.rrule = rrule;
		try
		{
			rule = new RecurrenceRule(rrule, RfcMode.RFC5545_STRICT);
		}
		catch (InvalidRecurrenceRuleException e)
		{
			throw new IllegalArgumentException("Invalid RRULE: " + rrule, e);
		}

		// Without a fixed DTSTART these would count from whenever the next start is computed, not from a stable origin.
		if ((rule.getCount() != null) || (rule.getInterval() > 1))
		{
			throw new IllegalArgumentException("COUNT and INTERVAL are not supported, list the times with BYHOUR / BYDAY instead: " + rrule);
		}

		final boolean minuteMissing = (rule.getFreq().compareTo(Freq.HOURLY) <= 0) && (rule.getByPart(Part.BYMINUTE) == null);
		final boolean hourMissing = (rule.getFreq().compareTo(Freq.DAILY) <= 0) && (rule.getByPart(Part.BYHOUR) == null);
		if (minuteMissing || hourMissing)
		{
			throw new IllegalArgumentException("The rule must set BYMINUTE when FREQ is HOURLY or longer, and BYHOUR when it is DAILY or longer: " + rrule);
		}
	}

	/**
	 * @return the first instance strictly after now in the server time zone, empty when the rule has no further instances
	 */
	public Optional<Instant> next()
	{
		return nextAfter(Instant.now(), ZoneId.systemDefault());
	}

	/**
	 * @param now the moment to search after
	 * @param zone the time zone the rule's local times are in
	 * @return the first instance strictly after {@code now}, empty when the rule has no further instances
	 */
	public Optional<Instant> nextAfter(Instant now, ZoneId zone)
	{
		// The DTSTART minute itself is an instance when it matches the rule, and it is never after now.
		final RecurrenceRuleIterator iterator = rule.iterator(now.truncatedTo(ChronoUnit.MINUTES).toEpochMilli(), TimeZone.getTimeZone(zone));
		final long nowMillis = now.toEpochMilli();
		while (iterator.hasNext())
		{
			final long instance = iterator.nextMillis();
			if (instance > nowMillis)
			{
				return Optional.of(Instant.ofEpochMilli(instance));
			}
		}

		return Optional.empty();
	}

	@Override
	public String toString()
	{
		return rrule;
	}
}
