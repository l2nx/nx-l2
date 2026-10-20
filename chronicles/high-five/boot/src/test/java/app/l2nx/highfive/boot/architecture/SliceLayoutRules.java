/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.stereotype.Repository;

/** The slice layout of spec 003 (wiring and layout, §4) over the migrated slices of a chronicle. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class SliceLayoutRules {
    static final String CHRONICLE_ROOT = "app.l2nx.highfive";
    // Only grows: 04c adds character, movement, chat, message and action.
    static final List<String> MIGRATED = List.of("login");
    static final SliceLayoutRules CHRONICLE = new SliceLayoutRules(CHRONICLE_ROOT, MIGRATED);

    private static final List<String> SUBPACKAGES =
            List.of("command", "view", "event", "system", "model", "template", "repository");
    private static final List<String> INTERNAL = List.of("system", "model", "template", "repository");
    private static final String ENGINE_CORE = "app.l2nx.engine.core..";
    private static final String HANDLER = "Handler";

    private final String root;
    private final List<String> slices;

    /** Spring Modulith over the migrated slices; every other class of the chronicle is ignored. */
    ApplicationModules modules() {
        return ApplicationModules.of(root, DescribedPredicate.not(resideInAnyPackage(sliceTrees())));
    }

    ArchRule subpackages() {
        return classes()
                .that()
                .resideInAnyPackage(sliceTrees())
                .should()
                .resideInAnyPackage(slicePackages())
                .as("slice classes reside in the slice root or a fixed subpackage")
                .allowEmptyShould(true);
    }

    ArchRule handlers() {
        return classes()
                .that()
                .resideInAnyPackage(sliceTrees())
                .and()
                .haveSimpleNameEndingWith(HANDLER)
                .should()
                .resideInAnyPackage(packages("command"))
                .andShould()
                .notBePublic()
                .andShould(beNamedAfterACommandRecord())
                .as("a handler is package-private in command/, named after its command")
                .allowEmptyShould(true);
    }

    ArchRule handlerConstructors() {
        return constructors()
                .that()
                .areDeclaredInClassesThat()
                .resideInAnyPackage(packages("command"))
                .and()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith(HANDLER)
                .should()
                .notBePublic()
                .as("only Spring creates a handler")
                .allowEmptyShould(true);
    }

    ArchRule commands() {
        return classes()
                .that()
                .resideInAnyPackage(packages("command"))
                .and()
                .areTopLevelClasses()
                .and()
                .doNotHaveSimpleName("package-info")
                .and()
                .haveSimpleNameNotEndingWith(HANDLER)
                .should()
                .beRecords()
                .andShould()
                .bePublic()
                .as("a command is a public record in command/")
                .allowEmptyShould(true);
    }

    ArchRule ports() {
        return classes()
                .that()
                .resideInAnyPackage(root + "..", ENGINE_CORE)
                .and()
                .haveSimpleNameEndingWith("Port")
                .should()
                .beInterfaces()
                .andShould()
                .resideInAnyPackage(portPackages())
                .as("a port is an interface in a slice root or engine/core")
                .allowEmptyShould(true);
    }

    // The frozen rules carry a fixed description and fixed violation texts: the description keys the freeze store and
    // the texts match its lines, while the package lists grow with MIGRATED.

    ArchRule repositories() {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .areAnnotatedWith(Repository.class)
                .should(resideInARepositoryPackage())
                .as("a repository lives in the repository package of its slice")
                .allowEmptyShould(true);
    }

    ArchRule noImplOrGateway() {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .should()
                .haveSimpleNameEndingWith("Impl")
                .orShould()
                .haveSimpleNameEndingWith("Gateway")
                .as("no class is named *Impl or *Gateway");
    }

    ArchRule slicesWithoutNetwork() {
        return noClasses()
                .that()
                .resideInAnyPackage(sliceTrees())
                .should()
                .dependOnClassesThat()
                .resideInAPackage(root + ".network..")
                .as("slices do not depend on network")
                .allowEmptyShould(true);
    }

    ArchRule networkUsesSliceApi() {
        return noClasses()
                .that()
                .resideInAPackage(root + ".network..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(Stream.concat(Stream.of(internalPackages()), Stream.of(packages("event")))
                        .toArray(String[]::new))
                .as("network uses a slice only through its root, command and view")
                .allowEmptyShould(true);
    }

    ArchRule legacyUsesSliceApi() {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackages(sliceTrees())
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(internalPackages())
                .as("code outside a slice does not reach its internal packages")
                .allowEmptyShould(true);
    }

    private String[] sliceTrees() {
        return slices.stream().map(slice -> root + "." + slice + "..").toArray(String[]::new);
    }

    private String[] sliceRoots() {
        return slices.stream().map(slice -> root + "." + slice).toArray(String[]::new);
    }

    private String[] packages(String subpackage) {
        return slices.stream()
                .map(slice -> root + "." + slice + "." + subpackage)
                .toArray(String[]::new);
    }

    private String[] slicePackages() {
        return Stream.concat(
                        Stream.of(sliceRoots()),
                        SUBPACKAGES.stream().flatMap(subpackage -> Stream.of(packages(subpackage))))
                .toArray(String[]::new);
    }

    private String[] internalPackages() {
        return INTERNAL.stream()
                .flatMap(subpackage -> Stream.of(packages(subpackage)))
                .toArray(String[]::new);
    }

    private String[] portPackages() {
        return Stream.concat(Stream.of(sliceRoots()), Stream.of(ENGINE_CORE)).toArray(String[]::new);
    }

    private ArchCondition<JavaClass> resideInARepositoryPackage() {
        final List<String> repositoryPackages = List.of(packages("repository"));
        return new ArchCondition<>("reside in the repository package of a slice") {
            @Override
            public void check(JavaClass repository, ConditionEvents events) {
                final boolean inSlice = repositoryPackages.contains(repository.getPackageName());
                events.add(new SimpleConditionEvent(
                        repository,
                        inSlice,
                        ConditionEvent.createMessage(
                                repository,
                                (inSlice ? "resides" : "does not reside") + " in the repository package of a slice")));
            }
        };
    }

    private static ArchCondition<JavaClass> beNamedAfterACommandRecord() {
        return new ArchCondition<>("be named after a command record of its package") {
            @Override
            public void check(JavaClass handler, ConditionEvents events) {
                final String command =
                        handler.getName().substring(0, handler.getName().length() - HANDLER.length());
                final boolean named = handler.getPackage().getClasses().stream()
                        .anyMatch(type -> type.getName().equals(command) && type.isRecord());
                events.add(new SimpleConditionEvent(
                        handler, named, handler.getName() + (named ? " handles " : " handles no record ") + command));
            }
        };
    }
}
