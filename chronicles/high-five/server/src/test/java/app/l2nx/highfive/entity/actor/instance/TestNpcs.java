/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.instance;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Named;

final class TestNpcs {
    static final int NPC_ID = 20_545;

    private TestNpcs() {}

    static NpcTemplate template() {
        final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        return template;
    }

    static <T> T builtOnTheLoop(Supplier<T> factory) {
        final T built = WorldLoopExtension.callInPass(factory);
        assertThat(built).as("npc built on the loop").isNotNull();
        return built;
    }

    static <T> Named<OffLoopCall> call(String name, Supplier<T> factory, Consumer<T> call) {
        return Named.of(name, () -> {
            final T npc = builtOnTheLoop(factory);
            return () -> call.accept(npc);
        });
    }

    @FunctionalInterface
    interface OffLoopCall {
        Runnable prepare();
    }
}
