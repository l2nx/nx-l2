/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.ownership;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldRegion;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

@AnalyzeClasses(packages = "app.l2nx.highfive", importOptions = ImportOption.DoNotIncludeTests.class)
class SingleWriterArchitectureTest {
    private static final int SINGLE_WRITER_TYPES = 252;
    private static final int UNANNOTATED_SUBCLASSES = 10;

    @ArchTest
    static final ArchRule noConcurrentState = SingleWriterRules.NO_CONCURRENT_STATE.allowEmptyShould(false);

    @ArchTest
    static final ArchRule noLockCalls = SingleWriterRules.NO_LOCK_CALLS.allowEmptyShould(false);

    @ArchTest
    static final ArchRule noPublicMutableFields = SingleWriterRules.NO_PUBLIC_MUTABLE_FIELDS;

    @ArchTest
    static final ArchRule publicMethodsCheckOwner =
            SingleWriterRules.PUBLIC_METHODS_CHECK_OWNER.allowEmptyShould(false);

    @ArchTest
    static final ArchRule subclassesAreSingleWriters =
            SingleWriterRules.SUBCLASSES_ARE_SINGLE_WRITERS.allowEmptyShould(false);

    @ArchTest
    static void singleWriterTypes_shouldMatchThePin(JavaClasses classes) {
        final long actual = classes.stream()
                .filter(type -> type.isAnnotatedWith(SingleWriter.class))
                .count();

        assertThat(actual)
                .as("@SingleWriter types in app.l2nx.highfive. A removed annotation takes a class out of every"
                        + " single-writer check; after annotating or deleting a class, set"
                        + " SingleWriterArchitectureTest.SINGLE_WRITER_TYPES to the new count")
                .isEqualTo(SINGLE_WRITER_TYPES);
    }

    @ArchTest
    static void singleWriterTypes_shouldContainTheCoreWorldState(JavaClasses classes) {
        final List<String> actual = classes.stream()
                .filter(type -> type.isAnnotatedWith(SingleWriter.class))
                .map(JavaClass::getName)
                .toList();

        assertThat(actual)
                .as("@SingleWriter types in app.l2nx.highfive")
                .contains(World.class.getName(), WorldRegion.class.getName(), ScratchBuffers.class.getName());
    }

    @ArchTest
    static void unannotatedSubclasses_shouldMatchThePin(JavaClasses classes) {
        assertThat(SingleWriterRules.UNANNOTATED_SUBCLASSES)
                .as("SingleWriterRules.UNANNOTATED_SUBCLASSES may only shrink; after removing an entry, set"
                        + " SingleWriterArchitectureTest.UNANNOTATED_SUBCLASSES to the new size")
                .hasSize(UNANNOTATED_SUBCLASSES)
                .allSatisfy(exception -> {
                    final JavaClass type = classes.get(exception);
                    assertThat(type.isAnnotatedWith(SingleWriter.class))
                            .as("%s is annotated now; remove it from UNANNOTATED_SUBCLASSES", type.getName())
                            .isFalse();
                });
    }
}
