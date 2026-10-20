/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.sevensigns;

/**
 * Maps the cabal column values ({@code DAWN}, {@code DUSK}, {@code NULL}) to the two legacy forms of the game code:
 * the {@code SevenSigns.CABAL_*} ids and the short names returned by {@code SevenSigns.getCabalShortName}.
 */
final class CabalColumns {
    private static final int CABAL_NULL = 0;
    private static final int CABAL_DUSK = 1;
    private static final int CABAL_DAWN = 2;

    private CabalColumns() {}

    static String fromId(int cabal) {
        return switch (cabal) {
            case CABAL_NULL -> null;
            case CABAL_DUSK -> "DUSK";
            case CABAL_DAWN -> "DAWN";
            default -> throw new IllegalArgumentException("Unknown cabal " + cabal);
        };
    }

    static int toId(String column) {
        if (column == null) {
            return CABAL_NULL;
        }
        return "DAWN".equals(column) ? CABAL_DAWN : CABAL_DUSK;
    }

    /**
     * @param shortName {@code "dawn"}, {@code "dusk"}, or {@code null} for no cabal
     */
    static String fromShortName(String shortName) {
        if (shortName == null) {
            return null;
        }
        return switch (shortName) {
            case "dawn" -> "DAWN";
            case "dusk" -> "DUSK";
            default -> throw new IllegalArgumentException("Unknown cabal " + shortName);
        };
    }

    static String toShortName(String column) {
        return column == null ? null : column.toLowerCase();
    }
}
