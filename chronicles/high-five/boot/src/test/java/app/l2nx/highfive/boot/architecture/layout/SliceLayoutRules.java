/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.layout;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import app.l2nx.engine.core.partition.DomainEvent;
import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.core.session.command.CommandHandler;
import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CharSelectionInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LoginFail;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class SliceLayoutRules {
    public static final String CHRONICLE_ROOT = "app.l2nx.highfive";
    static final List<String> MIGRATED = List.of("login", "partition");
    static final SliceLayoutRules CHRONICLE = new SliceLayoutRules(CHRONICLE_ROOT, MIGRATED);

    /**
     * Legacy connection classes migrated slices may still use until the network layer has client-connection ports;
     * the list may only shrink.
     */
    static final DescribedPredicate<JavaClass> LEGACY_NETWORK_FACADES = JavaClass.Predicates.belongToAnyOf(
            GameClient.class,
            Disconnection.class,
            SystemMessageId.class,
            CharSelectionInfo.class,
            LoginFail.class,
            SystemMessage.class);

    private static final List<String> SUBPACKAGES =
            List.of("command", "view", "event", "system", "model", "template", "repository");
    private static final List<String> INTERNAL = List.of("system", "model", "template", "repository");
    private static final String ENGINE_CORE = "app.l2nx.engine.core..";
    private static final String HANDLER = "Handler";

    private final String root;
    private final List<String> slices;

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
                .implement(CommandHandler.class)
                .should()
                .resideInAnyPackage(packages("command"))
                .andShould()
                .notBePublic()
                .andShould(beNamedAfterTheCommandItHandles())
                .as("a command handler is package-private in command/, named after the command it handles")
                .allowEmptyShould(true);
    }

    ArchRule handlerConstructors() {
        return constructors()
                .that()
                .areDeclaredInClassesThat()
                .resideInAnyPackage(sliceTrees())
                .and()
                .areDeclaredInClassesThat()
                .implement(CommandHandler.class)
                .should()
                .notBePublic()
                .as("only Spring creates a command handler")
                .allowEmptyShould(true);
    }

    ArchRule commands() {
        return classes()
                .that()
                .resideInAnyPackage(sliceTrees())
                .and()
                .implement(Command.class)
                .should()
                .beRecords()
                .andShould()
                .bePublic()
                .andShould()
                .resideInAnyPackage(packages("command"))
                .as("a command is a public record in command/")
                .allowEmptyShould(true);
    }

    ArchRule commandPackages() {
        return classes()
                .that()
                .resideInAnyPackage(packages("command"))
                .and()
                .areTopLevelClasses()
                .and()
                .doNotHaveSimpleName("package-info")
                .should()
                .implement(Command.class)
                .orShould()
                .implement(CommandHandler.class)
                .as("command/ holds only commands and their handlers")
                .allowEmptyShould(true);
    }

    ArchRule viewEvents() {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .areAssignableTo(ViewEvent.class)
                .should()
                .resideInAnyPackage(packages("view"))
                .as("a view event lives in the view package of a slice")
                .allowEmptyShould(true);
    }

    ArchRule domainEvents() {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .areAssignableTo(DomainEvent.class)
                .should()
                .resideInAnyPackage(packages("event"))
                .as("a domain event lives in the event package of a slice")
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

    // Frozen rules keep a fixed description and violation texts: they key the freeze store.

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
                .dependOnClassesThat(resideInAPackage(root + ".network..").and(not(LEGACY_NETWORK_FACADES)))
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

    private static ArchCondition<JavaClass> beNamedAfterTheCommandItHandles() {
        return new ArchCondition<>("be named after the command it handles") {
            @Override
            public void check(JavaClass handler, ConditionEvents events) {
                final Optional<JavaClass> command = handledCommand(handler);
                final boolean named = command.map(
                                type -> handler.getSimpleName().equals(type.getSimpleName() + HANDLER))
                        .orElse(false);
                events.add(new SimpleConditionEvent(
                        handler,
                        named,
                        handler.getName()
                                + (named ? " is named after " : " is not named after ")
                                + command.map(JavaClass::getName).orElse("a command type it declares")));
            }
        };
    }

    private static Optional<JavaClass> handledCommand(JavaClass handler) {
        return handler.getInterfaces().stream()
                .filter(JavaParameterizedType.class::isInstance)
                .map(JavaParameterizedType.class::cast)
                .filter(type -> type.toErasure().isAssignableTo(CommandHandler.class))
                .flatMap(type -> type.getActualTypeArguments().stream())
                .map(JavaType::toErasure)
                .findFirst();
    }
}
