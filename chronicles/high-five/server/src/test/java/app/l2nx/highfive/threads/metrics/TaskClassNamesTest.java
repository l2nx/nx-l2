/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TaskClassNamesTest {
    private static final String TEST_CLASS = TaskClassNamesTest.class.getName();

    @Test
    void declaringClassName_shouldBeEnclosingClass_whenLambda() {
        final Runnable lambda = () -> {};

        assertThat(lambda.getClass().getName()).contains("$$Lambda");
        assertThat(TaskClassNames.declaringClassName(lambda.getClass())).isEqualTo(TEST_CLASS);
    }

    @Test
    void declaringClassName_shouldBeCallSiteClass_whenMethodReference() {
        final Runnable reference = TaskClassNamesTest::noop;

        assertThat(TaskClassNames.declaringClassName(reference.getClass())).isEqualTo(TEST_CLASS);
    }

    @Test
    void declaringClassName_shouldBeEnclosingClass_whenAnonymousClass() {
        final Runnable anonymous = new Runnable() {
            @Override
            public void run() {}
        };

        assertThat(TaskClassNames.declaringClassName(anonymous.getClass())).isEqualTo(TEST_CLASS);
    }

    @Test
    void declaringClassName_shouldBeEnclosingClass_whenLocalClass() {
        class LocalTask implements Runnable {
            @Override
            public void run() {}
        }

        assertThat(TaskClassNames.declaringClassName(LocalTask.class)).isEqualTo(TEST_CLASS);
    }

    @Test
    void declaringClassName_shouldBeEnclosingClass_whenLambdaInsideAnonymousClass() {
        final Supplier<Runnable> anonymous = new Supplier<>() {
            @Override
            public Runnable get() {
                return () -> {};
            }
        };

        assertThat(TaskClassNames.declaringClassName(anonymous.get().getClass()))
                .isEqualTo(TEST_CLASS);
    }

    @Test
    void declaringClassName_shouldKeepMemberClass() {
        assertThat(TaskClassNames.declaringClassName(NamedTask.class)).isEqualTo(TEST_CLASS + ".NamedTask");
    }

    @Test
    void declaringClassName_shouldBeMemberClass_whenLambdaInsideMemberClass() {
        assertThat(TaskClassNames.declaringClassName(NamedTask.lambda().getClass()))
                .isEqualTo(TEST_CLASS + ".NamedTask");
    }

    @Test
    void declaringClassName_shouldBeMemberClass_whenAnonymousClassInsideMemberClass() {
        assertThat(TaskClassNames.declaringClassName(NamedTask.anonymous().getClass()))
                .isEqualTo(TEST_CLASS + ".NamedTask");
    }

    @Test
    void declaringClassName_shouldBeEnclosingClass_whenMemberClassOfAnonymousClass() {
        final Supplier<Class<?>> anonymous = new Supplier<>() {
            final class Inner {}

            @Override
            public Class<?> get() {
                return Inner.class;
            }
        };

        assertThat(anonymous.get().getName()).matches(Pattern.quote(TEST_CLASS) + "\\$\\d+\\$Inner");
        assertThat(TaskClassNames.declaringClassName(anonymous.get())).isEqualTo(TEST_CLASS);
    }

    @Test
    void declaringClassName_shouldBeEnclosingClass_whenLambdaInMemberClassOfAnonymousClass() {
        final Supplier<Runnable> anonymous = new Supplier<>() {
            final class Inner {
                Runnable lambda() {
                    return () -> {};
                }
            }

            @Override
            public Runnable get() {
                return new Inner().lambda();
            }
        };

        assertThat(TaskClassNames.declaringClassName(anonymous.get().getClass()))
                .isEqualTo(TEST_CLASS);
    }

    @Test
    void declaringClassName_shouldBeFixedName_whenDynamicProxy() {
        final Runnable proxy = (Runnable) Proxy.newProxyInstance(
                TaskClassNamesTest.class.getClassLoader(),
                new Class<?>[] {Runnable.class},
                (self, method, args) -> null);

        assertThat(TaskClassNames.declaringClassName(proxy.getClass())).isEqualTo(TaskClassNames.PROXY);
    }

    @Test
    void declaringClassName_shouldKeepTopLevelClass() {
        assertThat(TaskClassNames.declaringClassName(Thread.class)).isEqualTo("java.lang.Thread");
    }

    private static void noop() {}

    private static final class NamedTask implements Runnable {
        static Runnable lambda() {
            return () -> {};
        }

        static Runnable anonymous() {
            return new Runnable() {
                @Override
                public void run() {}
            };
        }

        @Override
        public void run() {}
    }
}
