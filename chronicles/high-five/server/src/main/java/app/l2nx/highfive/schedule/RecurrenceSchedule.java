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
 * An RFC 5545 RRULE ({@code FREQ=DAILY;BYHOUR=20;BYMINUTE=0}) evaluated from the current minute with no DTSTART, so
 * COUNT, INTERVAL and any left-out minute, hour or day part, which would be taken from the evaluation moment, are rejected.
 */
public final class RecurrenceSchedule {
    private final String rrule;
    private final RecurrenceRule rule;

    /**
     * @param rrule the RRULE value, without the {@code RRULE:} prefix
     * @throws IllegalArgumentException if the rule is not valid RFC 5545
     */
    public RecurrenceSchedule(String rrule) {
        this.rrule = rrule;
        try {
            rule = new RecurrenceRule(rrule, RfcMode.RFC5545_STRICT);
        } catch (InvalidRecurrenceRuleException e) {
            throw new IllegalArgumentException("Invalid RRULE: " + rrule, e);
        }

        if ((rule.getCount() != null) || (rule.getInterval() > 1)) {
            throw new IllegalArgumentException(
                    "COUNT and INTERVAL are not supported, list the times with BYHOUR / BYDAY instead: " + rrule);
        }

        final boolean minuteMissing = (rule.getFreq().compareTo(Freq.HOURLY) <= 0) && !rule.hasPart(Part.BYMINUTE);
        final boolean hourMissing = (rule.getFreq().compareTo(Freq.DAILY) <= 0) && !rule.hasPart(Part.BYHOUR);
        if (minuteMissing || hourMissing) {
            throw new IllegalArgumentException(
                    "The rule must set BYMINUTE when FREQ is HOURLY or longer, and BYHOUR when it is DAILY or longer: "
                            + rrule);
        }

        if (!dayFixed(rule)) {
            throw new IllegalArgumentException(
                    "The rule must set BYDAY when FREQ is WEEKLY, BYMONTHDAY or BYDAY when it is MONTHLY, and BYYEARDAY, BYDAY or BYMONTHDAY with BYMONTH when it is YEARLY: "
                            + rrule);
        }
    }

    /**
     * Whether the rule fixes its day itself; lib-recur also takes the month from DTSTART for a YEARLY BYMONTHDAY without
     * BYMONTH or BYWEEKNO.
     */
    private static boolean dayFixed(RecurrenceRule rule) {
        return switch (rule.getFreq()) {
            case WEEKLY -> rule.hasPart(Part.BYDAY);
            case MONTHLY -> rule.hasPart(Part.BYMONTHDAY) || rule.hasPart(Part.BYDAY);
            case YEARLY ->
                rule.hasPart(Part.BYYEARDAY)
                        || (rule.hasPart(Part.BYMONTHDAY)
                                ? rule.hasPart(Part.BYMONTH) || rule.hasPart(Part.BYWEEKNO)
                                : rule.hasPart(Part.BYDAY));
            default -> true;
        };
    }

    public Optional<Instant> next() {
        return nextAfter(Instant.now(), ZoneId.systemDefault());
    }

    public Optional<Instant> nextAfter(Instant now, ZoneId zone) {
        // The DTSTART minute itself is an instance when it matches the rule, and it is never after now.
        final RecurrenceRuleIterator iterator =
                rule.iterator(now.truncatedTo(ChronoUnit.MINUTES).toEpochMilli(), TimeZone.getTimeZone(zone));
        final long nowMillis = now.toEpochMilli();
        while (iterator.hasNext()) {
            final long instance = iterator.nextMillis();
            if (instance > nowMillis) {
                return Optional.of(Instant.ofEpochMilli(instance));
            }
        }

        return Optional.empty();
    }

    @Override
    public String toString() {
        return rrule;
    }
}
