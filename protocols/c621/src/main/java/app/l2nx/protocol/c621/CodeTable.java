/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.c621;

import java.util.Arrays;
import java.util.function.ToIntFunction;

public final class CodeTable<E extends Enum<E>> {
    private final E[] byCode;

    /**
     * @throws IllegalArgumentException when a code is negative or two constants share one
     */
    public CodeTable(E[] values, ToIntFunction<E> code) {
        int max = -1;
        for (E value : values) {
            final int valueCode = code.applyAsInt(value);
            if (valueCode < 0) {
                throw new IllegalArgumentException(value + " has the negative code " + valueCode);
            }
            max = Math.max(max, valueCode);
        }
        byCode = Arrays.copyOf(values, max + 1);
        Arrays.fill(byCode, null);
        for (E value : values) {
            final int valueCode = code.applyAsInt(value);
            if (byCode[valueCode] != null) {
                throw new IllegalArgumentException(
                        value + " and " + byCode[valueCode] + " share the code " + valueCode);
            }
            byCode[valueCode] = value;
        }
    }

    /**
     * @return {@code null} when the code is unknown
     */
    public E of(int code) {
        return (code >= 0) && (code < byCode.length) ? byCode[code] : null;
    }
}
