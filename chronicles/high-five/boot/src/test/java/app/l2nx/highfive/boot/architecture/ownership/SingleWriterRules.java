/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.ownership;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.entity.actor.templates.CubicTemplate;
import app.l2nx.highfive.entity.actor.templates.DoorTemplate;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.actor.templates.PlayerTemplate;
import app.l2nx.highfive.entity.item.Armor;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.mechanics.olympiad.Olympiad;
import app.l2nx.highfive.partition.WorldPartition;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Stream;

final class SingleWriterRules {
    private static final String CONCURRENT_PACKAGE = "java.util.concurrent";
    private static final String LOCKS_PACKAGE = "java.util.concurrent.locks";
    private static final Set<String> OBJECT_METHODS = Set.of("toString", "equals", "hashCode");
    private static final String WORLD_PARTITION = WorldPartition.class.getName().replace('.', '/');
    private static final String CHECK_OWNER = "checkOwner";

    /**
     * Subclasses of a {@code @SingleWriter} type that stay unannotated; may only shrink, size pinned by
     * {@code SingleWriterArchitectureTest}. Templates are static after load. TODO: annotate {@code Olympiad} with the managers.
     */
    static final Set<Class<?>> UNANNOTATED_SUBCLASSES = Set.of(
            CreatureTemplate.class,
            CubicTemplate.class,
            DoorTemplate.class,
            NpcTemplate.class,
            PlayerTemplate.class,
            ItemTemplate.class,
            Armor.class,
            EtcItem.class,
            Weapon.class,
            Olympiad.class);

    private static final DescribedPredicate<JavaClass> EXTEND_A_SINGLE_WRITER = DescribedPredicate.describe(
            "extend or implement a @SingleWriter type",
            type -> Stream.concat(type.getAllRawSuperclasses().stream(), type.getAllRawInterfaces().stream())
                    .anyMatch(supertype -> supertype.isAnnotatedWith(SingleWriter.class)));

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

    static final ArchRule PUBLIC_METHODS_CHECK_OWNER = classes()
            .that()
            .areAnnotatedWith(SingleWriter.class)
            .should(callWorldPartitionCheckOwnerFirst())
            .allowEmptyShould(true)
            .because("without locks a second writer corrupts state silently; the check fails it loudly at the"
                    + " caller, and only as the first statement, before anything can write");

    static final ArchRule SUBCLASSES_ARE_SINGLE_WRITERS = classes()
            .that(EXTEND_A_SINGLE_WRITER)
            .and(DescribedPredicate.describe(
                    "are not pinned in UNANNOTATED_SUBCLASSES",
                    type -> UNANNOTATED_SUBCLASSES.stream().noneMatch(type::isEquivalentTo)))
            .should()
            .beAnnotatedWith(SingleWriter.class)
            .allowEmptyShould(true)
            .because("@SingleWriter is not inherited and every check reads it on the class itself, so the overrides of"
                    + " an unannotated subclass write the state of its parent unchecked");

    private SingleWriterRules() {}

    private static ArchCondition<JavaClass> callWorldPartitionCheckOwnerFirst() {
        return new ArchCondition<>("call WorldPartition.checkOwner() as the first instruction of every public method") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                final ClassModel bytecode = bytecode(type);
                type.getMethods().stream()
                        .filter(SingleWriterRules::needsOwnerCheck)
                        .forEach(method -> checkFirstInstruction(method, firstInstruction(bytecode, method), events));
            }
        };
    }

    // Javac emits bridge methods public and synthetic; they only delegate to the checked method.
    private static boolean needsOwnerCheck(JavaMethod method) {
        final Set<JavaModifier> modifiers = method.getModifiers();
        return modifiers.contains(JavaModifier.PUBLIC)
                && !modifiers.contains(JavaModifier.SYNTHETIC)
                && !modifiers.contains(JavaModifier.ABSTRACT)
                && !OBJECT_METHODS.contains(method.getName());
    }

    // Bytecode decides: ArchUnit's calls and accesses carry only line numbers, so same-line code or a check in one
    // branch would pass.
    private static void checkFirstInstruction(JavaMethod method, Instruction first, ConditionEvents events) {
        if (isCheckOwner(first)) {
            return;
        }
        final boolean callsCheck = method.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().isEquivalentTo(WorldPartition.class)
                        && call.getName().equals(CHECK_OWNER));
        events.add(SimpleConditionEvent.violated(
                method,
                callsCheck
                        ? method.getFullName() + " runs " + first.opcode() + " before WorldPartition.checkOwner() "
                                + method.getSourceCodeLocation()
                        : method.getFullName() + " does not call WorldPartition.checkOwner() "
                                + method.getSourceCodeLocation()));
    }

    private static Instruction firstInstruction(ClassModel bytecode, JavaMethod method) {
        final MethodModel model = bytecode.methods().stream()
                .filter(candidate -> candidate.methodName().equalsString(method.getName())
                        && candidate.methodType().equalsString(method.getDescriptor()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(method.getFullName() + " is not in its class file"));
        final CodeModel code =
                model.code().orElseThrow(() -> new IllegalStateException(method.getFullName() + " has no body"));
        return code.elementStream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(method.getFullName() + " has no instructions"));
    }

    private static boolean isCheckOwner(Instruction instruction) {
        return instruction instanceof InvokeInstruction invoke
                && invoke.opcode() == Opcode.INVOKESTATIC
                && invoke.owner().asInternalName().equals(WORLD_PARTITION)
                && invoke.name().equalsString(CHECK_OWNER);
    }

    private static ClassModel bytecode(JavaClass type) {
        final URI uri = type.getSource()
                .orElseThrow(() -> new IllegalStateException(type.getName() + " has no class file"))
                .getUri();
        try (InputStream in = uri.toURL().openStream()) {
            return ClassFile.of().parse(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(type.getName() + ": cannot read " + uri, e);
        }
    }
}
