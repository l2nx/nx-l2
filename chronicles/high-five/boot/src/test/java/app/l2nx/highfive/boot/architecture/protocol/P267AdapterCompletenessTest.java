/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.core.session.command.CommandHandler;
import app.l2nx.engine.core.session.command.SessionAddressed;
import app.l2nx.engine.core.session.command.SessionCommandHandler;
import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.login.command.AuthenticationResult;
import app.l2nx.highfive.login.command.KickAccount;
import app.l2nx.highfive.login.command.LinkLost;
import app.l2nx.highfive.login.command.PasswordChangeResult;
import app.l2nx.highfive.login.command.RegistrationResult;
import app.l2nx.highfive.login.command.ReportCharacters;
import app.l2nx.highfive.network.protocol.OpcodeTable.Route;
import app.l2nx.highfive.network.protocol.OpcodeTable.RouteKind;
import app.l2nx.highfive.network.protocol.p267.P267Adapter;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.ResolvableType;

@AnalyzeClasses(
        packages = {"app.l2nx.highfive", "app.l2nx.engine"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class P267AdapterCompletenessTest {
    private static final P267Adapter ADAPTER = new P267Adapter();

    @ArchTest
    static void everyCommand_shouldHaveExactlyOneHandler(JavaClasses classes) {
        final Map<Class<?>, List<Class<?>>> handlers = handlersByCommand(classes);
        final List<Class<?>> commands = concrete(classes, Command.class);

        assertThat(commands)
                .as("the import sees the login-link commands")
                .contains(
                        AuthenticationResult.class,
                        PasswordChangeResult.class,
                        KickAccount.class,
                        RegistrationResult.class,
                        ReportCharacters.class,
                        LinkLost.class);
        assertThat(commands)
                .allSatisfy(command -> assertThat(handlers.get(command))
                        .as("handlers of %s", command.getName())
                        .hasSize(1));
    }

    /** The dispatcher offers an addressed command to the account's session; a service handler would never see it. */
    @ArchTest
    static void addressedCommands_shouldRunInTheSession(JavaClasses classes) {
        handlersByCommand(classes).forEach((command, handlers) -> {
            if (SessionAddressed.class.isAssignableFrom(command)) {
                assertThat(handlers)
                        .as("handlers of %s", command.getName())
                        .allMatch(SessionCommandHandler.class::isAssignableFrom);
            }
        });
    }

    @ArchTest
    static void decodedCommands_shouldHaveASessionHandler(JavaClasses classes) {
        final Map<Class<?>, List<Class<?>>> handlers = handlersByCommand(classes);
        final Set<Class<? extends Command>> decoded = ADAPTER.inboundRoutes().stream()
                .filter(route -> route.kind() == RouteKind.COMMAND)
                .map(Route::command)
                .collect(Collectors.toSet());

        assertThat(decoded)
                .allSatisfy(command -> assertThat(handlers.get(command))
                        .as("handlers of %s", command.getName())
                        .singleElement()
                        .matches(SessionCommandHandler.class::isAssignableFrom));
    }

    @ArchTest
    static void everyViewEvent_shouldHaveAMapperOrAnExplicitDrop(JavaClasses classes) {
        final Set<Class<?>> covered = new HashSet<>(ADAPTER.mappedEvents());
        covered.addAll(ADAPTER.droppedEvents());

        assertThat(covered)
                .as("view events without a p267 mapper or drop")
                .containsAll(concrete(classes, ViewEvent.class));
    }

    private static Map<Class<?>, List<Class<?>>> handlersByCommand(JavaClasses classes) {
        return concrete(classes, CommandHandler.class).stream()
                .collect(Collectors.groupingBy(P267AdapterCompletenessTest::commandOf));
    }

    private static Class<?> commandOf(Class<?> handler) {
        final Class<?> command =
                ResolvableType.forClass(handler).as(CommandHandler.class).resolveGeneric(0);
        assertThat(command).as("command type of %s", handler.getName()).isNotNull();
        return command;
    }

    private static List<Class<?>> concrete(JavaClasses classes, Class<?> marker) {
        return classes.stream()
                .filter(type -> type.isAssignableTo(marker))
                .filter(type -> !type.isInterface() && !type.getModifiers().contains(JavaModifier.ABSTRACT))
                .<Class<?>>map(JavaClass::reflect)
                .toList();
    }
}
