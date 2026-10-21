/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.protocol;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import app.l2nx.highfive.boot.architecture.layout.SliceLayoutRules;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class LegacyRouteRules {
    static final LegacyRouteRules CHRONICLE = new LegacyRouteRules(SliceLayoutRules.CHRONICLE_ROOT);

    private static final String ADAPTER = ".network.protocol.highfive";
    private static final String SERVER_PACKETS = ADAPTER + ".serverpackets";

    private final String root;

    // Frozen: the description keys the freeze store and the violation texts are its lines, so neither may change.
    ArchRule serverPacketReferences() {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackage(root + ADAPTER + "..")
                .should(notReferenceAServerPacket())
                .as("only the highfive adapter references server packets");
    }

    private ArchCondition<JavaClass> notReferenceAServerPacket() {
        final String packets = root + SERVER_PACKETS;
        return new ArchCondition<>("not reference a server packet") {
            // One line per (top-level origin, top-level packet): nested and anonymous classes renumber on edits.
            private final SortedMap<String, JavaClass> references = new TreeMap<>();

            @Override
            public void init(Collection<JavaClass> allObjectsToTest) {
                references.clear();
            }

            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                origin.getDirectDependenciesFromSelf().stream()
                        .map(Dependency::getTargetClass)
                        .map(JavaClass::getBaseComponentType)
                        .filter(target -> isIn(target, packets))
                        .forEach(target -> references.putIfAbsent(
                                topLevel(origin).getName() + " references "
                                        + topLevel(target).getName(),
                                origin));
            }

            @Override
            public void finish(ConditionEvents events) {
                references.forEach((message, origin) -> events.add(SimpleConditionEvent.violated(origin, message)));
            }
        };
    }

    private static boolean isIn(JavaClass type, String packageName) {
        return type.getPackageName().equals(packageName)
                || type.getPackageName().startsWith(packageName + ".");
    }

    private static JavaClass topLevel(JavaClass type) {
        JavaClass current = type;
        while (current.getEnclosingClass().isPresent()) {
            current = current.getEnclosingClass().get();
        }
        return current;
    }
}
