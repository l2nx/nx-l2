/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.ownership;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.partition.WorldPartition;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Collections;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.IntStream;

final class SingleWriterRules {
    private static final String CONCURRENT_PACKAGE = "java.util.concurrent";
    private static final String LOCKS_PACKAGE = "java.util.concurrent.locks";
    private static final Set<String> OBJECT_METHODS = Set.of("toString", "equals", "hashCode");

    // A ScheduledFuture field is a loop timer handle (allowed by the spec) and TimeUnit is a plain enum; neither is
    // shared state. Other futures complete on another thread, so they stay forbidden.
    private static final DescribedPredicate<JavaClass> CONCURRENT_STATE_TYPE = DescribedPredicate.describe(
            "a concurrent collection, atomic, lock, synchronizer or executor type",
            type -> type.getPackageName().startsWith(CONCURRENT_PACKAGE)
                    && !type.isAssignableTo(ScheduledFuture.class)
                    && !type.isEnum());

    private static final DescribedPredicate<JavaMethodCall> LOCK_CALL = DescribedPredicate.describe(
            "a java.util.concurrent.locks method or a Collections.synchronized* wrapper",
            call -> call.getTargetOwner().getPackageName().equals(LOCKS_PACKAGE)
                    || (call.getTargetOwner().isEquivalentTo(Collections.class)
                            && call.getName().startsWith("synchronized")));

    // A field typed by its interface (Map) hides the implementation, so creating concurrent state counts as well.
    private static final DescribedPredicate<JavaCall<?>> CREATES_CONCURRENT_STATE = DescribedPredicate.describe(
            "a constructor or factory of a concurrent collection, atomic, lock, synchronizer or executor type",
            call -> switch (call) {
                case JavaConstructorCall constructor -> CONCURRENT_STATE_TYPE.test(constructor.getTargetOwner());
                case JavaMethodCall method ->
                    CONCURRENT_STATE_TYPE.test(method.getTarget().getRawReturnType());
                default -> false;
            });

    static final ArchRule NO_CONCURRENT_STATE = CompositeArchRule.of(noFields()
                    .that()
                    .areDeclaredInClassesThat()
                    .areAnnotatedWith(SingleWriter.class)
                    .should()
                    .haveRawType(CONCURRENT_STATE_TYPE)
                    .orShould()
                    .haveModifier(JavaModifier.VOLATILE)
                    .allowEmptyShould(true))
            .and(noClasses()
                    .that()
                    .areAnnotatedWith(SingleWriter.class)
                    .should()
                    .callCodeUnitWhere(CREATES_CONCURRENT_STATE)
                    .allowEmptyShould(true))
            .because("a @SingleWriter class has one writer, its owner loop; concurrent state only costs time and"
                    + " hides a second writer");

    static final ArchRule NO_LOCK_CALLS = noClasses()
            .that()
            .areAnnotatedWith(SingleWriter.class)
            .should()
            .callMethodWhere(LOCK_CALL)
            .allowEmptyShould(true)
            .because("a @SingleWriter class takes no locks");

    static final ArchRule NO_PUBLIC_MUTABLE_FIELDS = noFields()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(SingleWriter.class)
            .and()
            .arePublic()
            .should()
            .notBeFinal()
            .allowEmptyShould(true)
            .because("a public field write cannot carry the ownership check");

    // javac emits bridge methods public and synthetic; they only delegate to the checked method.
    static final ArchRule PUBLIC_METHODS_CHECK_OWNER = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(SingleWriter.class)
            .and()
            .arePublic()
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and(DescribedPredicate.describe(
                    "are not toString, equals or hashCode", method -> !OBJECT_METHODS.contains(method.getName())))
            .should(callWorldPartitionCheckOwner())
            .allowEmptyShould(true)
            .because("without locks a second writer corrupts state silently; the check fails it loudly at the"
                    + " caller, and only as the first statement, before anything can write");

    private SingleWriterRules() {}

    private static ArchCondition<JavaMethod> callWorldPartitionCheckOwner() {
        return new ArchCondition<>("call WorldPartition.checkOwner() before any other call or field access") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                final OptionalInt checkLine = method.getMethodCallsFromSelf().stream()
                        .filter(SingleWriterRules::isCheckOwner)
                        .mapToInt(JavaCall::getLineNumber)
                        .min();
                if (checkLine.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(
                            method, method.getFullName() + " does not call WorldPartition.checkOwner()"));
                    return;
                }

                final IntStream otherCalls = method.getMethodCallsFromSelf().stream()
                        .filter(call -> !isCheckOwner(call))
                        .mapToInt(JavaCall::getLineNumber);
                final IntStream fieldAccesses =
                        method.getFieldAccesses().stream().mapToInt(JavaFieldAccess::getLineNumber);
                final OptionalInt firstOther =
                        IntStream.concat(otherCalls, fieldAccesses).min();
                if (firstOther.isPresent() && firstOther.getAsInt() < checkLine.getAsInt()) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            method.getFullName() + " calls WorldPartition.checkOwner() at line " + checkLine.getAsInt()
                                    + " after other code at line " + firstOther.getAsInt()));
                }
            }
        };
    }

    private static boolean isCheckOwner(JavaMethodCall call) {
        return call.getTargetOwner().isEquivalentTo(WorldPartition.class)
                && call.getName().equals("checkOwner");
    }
}
