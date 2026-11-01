/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.persistence;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

// TODO: generalize to all game code once E4 moves JDBC off the loop (#69), next to dropping JdbcOnLoopListener.
final class LoopSideJdbcRules {
    /** The High Five side of the mail SPI the module calls on the world loop. */
    static final List<String> MAIL_LOOP_SIDE =
            List.of("app.l2nx.highfive.mail.WorldMailItems", "app.l2nx.highfive.mail.WorldMailParticipants");

    static final DescribedPredicate<JavaClass> DATABASE_ACCESS = simpleNameEndingWith("Repository")
            .or(annotatedWith("org.springframework.stereotype.Repository"))
            .or(assignableTo("org.springframework.jdbc.core.simple.JdbcClient"))
            .or(assignableTo("org.springframework.jdbc.core.JdbcOperations"))
            .or(assignableTo("org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations"))
            .or(assignableTo("javax.sql.DataSource"))
            .as("are repositories, JdbcClient, JdbcTemplate or a DataSource");

    static final ArchRule MAIL_LOOP_SIDE_TOUCHES_NO_DATABASE = loopSideTouchesNoDatabase(MAIL_LOOP_SIDE);

    private LoopSideJdbcRules() {}

    /** Their database work belongs to the value transactions and background queries, never to the loop. */
    static ArchRule loopSideTouchesNoDatabase(List<String> names) {
        final DescribedPredicate<JavaClass> loopSide = DescribedPredicate.describe(
                "are " + String.join(", ", names) + " or nested in them",
                type -> names.stream()
                        .anyMatch(name ->
                                type.getName().equals(name) || type.getName().startsWith(name + "$")));
        // An empty match fails, so a renamed class cannot slip out of the rule.
        return noClasses()
                .that(loopSide)
                .should()
                .dependOnClassesThat(DATABASE_ACCESS)
                .allowEmptyShould(false);
    }
}
