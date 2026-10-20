/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

/** What a stored skill row restores: an active effect (with its reuse, if any) or only a running reuse. */
public enum SkillRestoreType {
    EFFECT,
    REUSE
}
