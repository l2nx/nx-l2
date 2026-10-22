/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.time;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.BaseStream;

final class ScheduledWorkRules {
    static final String GAME_PACKAGE = "app.l2nx.highfive..";

    static final String PARSE_POOL = "app.l2nx.highfive.data.xml.IXmlReader";
    static final String BRIDGES = "app.l2nx.highfive.partition..";
    // Its shutdown waits for the login server on the Spring lifecycle thread, never on the world loop.
    static final String GS_LINK = "app.l2nx.highfive.network.gslink.LoginServerLink";

    private static final List<List<String>> OBJECT_WAIT_PARAMETERS =
            List.of(List.of(), List.of("long"), List.of("long", "int"));

    static final ArchRule SCHEDULED_WORK_GOES_THROUGH_THE_LOOP = noClasses()
            .that()
            .resideInAPackage(GAME_PACKAGE)
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(Timer.class, TimerTask.class)
            .because("scheduled game work goes through the world loop, which meters it and reports task failures;"
                    + " a java.util.Timer thread dies silently on the first exception");

    private static final DescribedPredicate<JavaAccess<?>> OWN_THREADS = DescribedPredicate.or(List.of(
            constructorOf(Thread.class),
            anyMethod(Thread.class, "ofPlatform"),
            anyMethod(Thread.class, "ofVirtual"),
            anyMethod(Thread.class, "startVirtualThread"),
            anyMethodOf(Executors.class),
            constructorOf(ExecutorService.class),
            DescribedPredicate.describe(
                    "CompletableFuture.*Async(..)",
                    access -> access.getTargetOwner().isEquivalentTo(CompletableFuture.class)
                            && access.getTarget().getName().endsWith("Async")),
            anyMethod(Collection.class, "parallelStream"),
            anyMethod(BaseStream.class, "parallel")));

    static final ArchRule NO_THREADS_OR_EXECUTORS = noClasses()
            .that()
            .resideInAPackage(GAME_PACKAGE)
            .and()
            .resideOutsideOfPackage(BRIDGES)
            .and()
            .doNotHaveFullyQualifiedName(PARSE_POOL)
            .should()
            .beAssignableTo(Thread.class)
            .orShould()
            .accessTargetWhere(OWN_THREADS)
            .allowEmptyShould(true)
            .because("the world loop is the only thread of game code: any other thread is a second writer of game"
                    + " state; work without game state goes to BackgroundTasks");

    static final ArchRule NO_EXECUTOR_FIELDS = noFields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage(GAME_PACKAGE)
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage(BRIDGES)
            .and()
            .areDeclaredInClassesThat()
            .doNotHaveFullyQualifiedName(PARSE_POOL)
            .should()
            .haveRawType(assignableTo(ExecutorService.class))
            .allowEmptyShould(true)
            .because("an executor held by game code runs game work off the world loop");

    private static final DescribedPredicate<JavaAccess<?>> BLOCKING_CALLS = DescribedPredicate.or(List.of(
            anyMethod(Thread.class, "sleep"),
            anyMethod(Thread.class, "join"),
            objectWait(),
            anyMethodStartingWith(LockSupport.class, "park"),
            anyMethod(CountDownLatch.class, "await"),
            anyMethodStartingWith(Semaphore.class, "acquire"),
            anyMethod(Future.class, "get"),
            anyMethod(CompletableFuture.class, "join")));

    static final ArchRule NO_BLOCKING_CALLS = noClasses()
            .that()
            .resideInAPackage(GAME_PACKAGE)
            .and()
            .resideOutsideOfPackage(BRIDGES)
            .and()
            .doNotHaveFullyQualifiedName(PARSE_POOL)
            .and()
            .doNotHaveFullyQualifiedName(GS_LINK)
            .should()
            .accessTargetWhere(BLOCKING_CALLS)
            .allowEmptyShould(true)
            .because("game code runs on the world loop, so a call that blocks its thread freezes the whole world;"
                    + " a wait becomes a loop timer that runs the next step");

    private ScheduledWorkRules() {}

    private static DescribedPredicate<JavaAccess<?>> constructorOf(Class<?> type) {
        return DescribedPredicate.describe(
                "new " + type.getSimpleName() + "(..)",
                access -> access.getTargetOwner().isAssignableTo(type)
                        && access.getTarget().getName().equals(JavaConstructor.CONSTRUCTOR_NAME));
    }

    private static DescribedPredicate<JavaAccess<?>> anyMethod(Class<?> owner, String name) {
        return DescribedPredicate.describe(
                owner.getSimpleName() + "." + name + "(..)",
                access -> access.getTargetOwner().isAssignableTo(owner)
                        && access.getTarget().getName().equals(name));
    }

    private static DescribedPredicate<JavaAccess<?>> anyMethodOf(Class<?> owner) {
        return DescribedPredicate.describe(
                owner.getSimpleName() + ".*(..)",
                access -> access.getTargetOwner().isEquivalentTo(owner));
    }

    private static DescribedPredicate<JavaAccess<?>> anyMethodStartingWith(Class<?> owner, String prefix) {
        return DescribedPredicate.describe(
                owner.getSimpleName() + "." + prefix + "*(..)",
                access -> access.getTargetOwner().isAssignableTo(owner)
                        && access.getTarget().getName().startsWith(prefix));
    }

    // Object.wait is final, so a call to wait(), wait(long) or wait(long, int) on any type is the monitor wait.
    private static DescribedPredicate<JavaAccess<?>> objectWait() {
        return DescribedPredicate.describe(
                "Object.wait(..)",
                access -> access instanceof JavaMethodCall call
                        && call.getName().equals("wait")
                        && OBJECT_WAIT_PARAMETERS.contains(call.getTarget().getRawParameterTypes().stream()
                                .map(JavaClass::getName)
                                .toList()));
    }
}
