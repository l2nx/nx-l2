/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.model;

/** Two teams by the parity of the load bot index, which a seeded character name ends in. */
public final class CharacterTeam {
    public static final int NONE = 0;

    private static final int FIRST = 1;

    private CharacterTeam() {}

    public static int ofIndex(int index) {
        return FIRST + (index & 1);
    }

    /** @return {@link #NONE} for a name that ends in no digit, such as a player watching the run */
    public static int ofName(String name) {
        if (name == null || name.isEmpty()) {
            return NONE;
        }
        final char last = name.charAt(name.length() - 1);
        return last >= '0' && last <= '9' ? ofIndex(last - '0') : NONE;
    }
}
