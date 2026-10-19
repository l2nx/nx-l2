/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.boot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;

/**
 * Extended by each server's {@code boot} module, the only one that sees every module of the server on one classpath.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class JavaConventionsTest {

    private JavaClasses classes;

    @BeforeAll
    void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("app.l2nx");
    }

    @Test
    void noJavaUtilLogging() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("java.util.logging..")
                .check(classes);
    }

    @Test
    void loggersArePrivateStaticFinalAndNamedByRole() {
        fields().that()
                .haveRawType(Logger.class)
                .should()
                .bePrivate()
                .andShould()
                .beStatic()
                .andShould()
                .beFinal()
                .andShould()
                .haveNameMatching("log|[A-Z0-9_]+_LOG")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void lombokBuildersBelongToRecords() {
        classes()
                .that(declareLombokBuilder())
                .should(beRecords())
                .allowEmptyShould(true)
                .check(classes);
    }

    private static DescribedPredicate<JavaClass> declareLombokBuilder() {
        return DescribedPredicate.describe(
                "declare a Lombok builder",
                c -> c.getMethods().stream()
                        .anyMatch(m -> m.getName().equals("builder")
                                && m.getModifiers().contains(JavaModifier.STATIC)
                                && m.getRawReturnType()
                                        .getEnclosingClass()
                                        .map(c::equals)
                                        .orElse(false)
                                && m.getRawReturnType().getMethods().stream()
                                        .anyMatch(b -> b.isAnnotatedWith("lombok.Generated"))));
    }

    private static ArchCondition<JavaClass> beRecords() {
        return new ArchCondition<>("be records") {
            @Override
            public void check(JavaClass c, ConditionEvents events) {
                boolean record = c.reflect().isRecord();
                events.add(
                        new SimpleConditionEvent(c, record, c.getName() + (record ? " is" : " is not") + " a record"));
            }
        };
    }
}
