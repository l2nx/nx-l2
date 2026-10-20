/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads.metrics;

import java.lang.reflect.Proxy;

public final class TaskClassNames {
    static final String PROXY = "proxy";

    private static final String LAMBDA_MARKER = "$$Lambda";

    private TaskClassNames() {}

    /**
     * Canonical name of the class that declares a task: lambdas, anonymous and local classes collapse into their
     * declaring class, proxies into {@code proxy}. Their own names carry per-run addresses or shifting indices.
     */
    public static String declaringClassName(Class<?> type) {
        if (Proxy.isProxyClass(type)) {
            return PROXY;
        }
        String name = type.getName();
        final int lambda = name.indexOf(LAMBDA_MARKER);
        if (lambda > 0) {
            name = name.substring(0, lambda);
        }
        for (int dollar = name.indexOf('$'); dollar > 0; dollar = name.indexOf('$', dollar + 1)) {
            if ((dollar + 1 < name.length()) && Character.isDigit(name.charAt(dollar + 1))) {
                name = name.substring(0, dollar);
                break;
            }
        }
        // String-based because a lambda's hidden class has no enclosing class to ask.
        return name.replace('$', '.');
    }
}
