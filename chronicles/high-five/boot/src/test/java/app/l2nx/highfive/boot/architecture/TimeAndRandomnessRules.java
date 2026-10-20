/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

final class TimeAndRandomnessRules {
    static final String[] EXEMPT_PACKAGES = {"app.l2nx.highfive.clock.."};

    private static final List<Class<?>> NOW_TYPES = List.of(
            Instant.class,
            LocalDateTime.class,
            ZonedDateTime.class,
            OffsetDateTime.class,
            LocalDate.class,
            LocalTime.class,
            OffsetTime.class,
            Year.class,
            YearMonth.class,
            MonthDay.class);

    private static final DescribedPredicate<JavaAccess<?>> FORBIDDEN_TARGET = DescribedPredicate.or(List.of(
            exactly(System.class, "currentTimeMillis"),
            exactly(System.class, "nanoTime"),
            nowWithoutClock(),
            anyOverload(Clock.class, "systemUTC"),
            anyOverload(Clock.class, "systemDefaultZone"),
            anyOverload(Clock.class, "system"),
            anyOverload(Calendar.class, "getInstance"),
            exactly(GregorianCalendar.class, JavaConstructor.CONSTRUCTOR_NAME),
            exactly(GregorianCalendar.class, JavaConstructor.CONSTRUCTOR_NAME, TimeZone.class),
            exactly(GregorianCalendar.class, JavaConstructor.CONSTRUCTOR_NAME, Locale.class),
            exactly(GregorianCalendar.class, JavaConstructor.CONSTRUCTOR_NAME, TimeZone.class, Locale.class),
            exactly(Date.class, JavaConstructor.CONSTRUCTOR_NAME),
            anyOverload(Math.class, "random"),
            anyOverload(StrictMath.class, "random"),
            anyOverload(Random.class, JavaConstructor.CONSTRUCTOR_NAME),
            anyOverload(RandomGenerator.class, "getDefault"),
            anyOverload(RandomGenerator.class, "of"),
            exactly(Collections.class, "shuffle", List.class)));

    static final ArchRule NO_SYSTEM_TIME_OR_GLOBAL_RANDOM = noClasses()
            .that()
            .resideInAPackage("app.l2nx.highfive..")
            .and()
            .resideOutsideOfPackages(EXEMPT_PACKAGES)
            .should()
            .accessTargetWhere(FORBIDDEN_TARGET)
            .orShould()
            .dependOnClassesThat()
            .belongToAnyOf(ThreadLocalRandom.class)
            .because("game code reads time and randomness through GameClock and ClockBridge, never globally");

    private TimeAndRandomnessRules() {}

    private static DescribedPredicate<JavaAccess<?>> anyOverload(Class<?> owner, String name) {
        return DescribedPredicate.describe(
                owner.getSimpleName() + "." + name + "(..)",
                access -> access.getTargetOwner().isEquivalentTo(owner)
                        && access.getTarget().getName().equals(name));
    }

    private static DescribedPredicate<JavaAccess<?>> exactly(Class<?> owner, String name, Class<?>... parameters) {
        final List<String> expected = Stream.of(parameters).map(Class::getName).toList();
        return DescribedPredicate.describe(
                owner.getSimpleName() + "." + name + expected,
                access -> access.getTargetOwner().isEquivalentTo(owner)
                        && access.getTarget().getName().equals(name)
                        && parameterNames(access).equals(expected));
    }

    /** {@code now()} and {@code now(ZoneId)} read the system clock; only {@code now(Clock)} is allowed. */
    private static DescribedPredicate<JavaAccess<?>> nowWithoutClock() {
        final List<String> withClock = List.of(Clock.class.getName());
        return DescribedPredicate.describe(
                "java.time now() without a Clock",
                access -> NOW_TYPES.stream()
                                .anyMatch(type -> access.getTargetOwner().isEquivalentTo(type))
                        && access.getTarget().getName().equals("now")
                        && !parameterNames(access).equals(withClock));
    }

    private static List<String> parameterNames(JavaAccess<?> access) {
        return access.getTarget() instanceof CodeUnitAccessTarget target
                ? target.getRawParameterTypes().stream().map(JavaClass::getName).toList()
                : List.of();
    }
}
