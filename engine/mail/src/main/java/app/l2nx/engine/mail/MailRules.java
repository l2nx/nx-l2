/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

/** The settings and the character's state an action passes before anything is read or reserved for it. */
public final class MailRules {
    private MailRules() {}

    /**
     * @param attachments the action moves attachments or cash on delivery
     * @param ownValues the action moves the character's own items or adena
     * @return {@code null} when the action may go on
     */
    public static MailRefusal refusal(
            MailSettings settings, ParticipantState state, boolean attachments, boolean ownValues) {
        if (!settings.enabled()) {
            return MailRefusal.DISABLED;
        }
        if (ownValues && !state.tradeAllowed()) {
            return MailRefusal.ACCESS_DENIED;
        }
        if (attachments) {
            final MailSettings.Attachments rules = settings.attachments();
            if (!rules.enabled()) {
                return MailRefusal.ATTACHMENTS_DISABLED;
            }
            if (rules.peaceZoneOnly() && !state.peaceZone()) {
                return MailRefusal.NOT_IN_PEACE_ZONE;
            }
            if (rules.denyInCombat() && state.inCombat()) {
                return MailRefusal.IN_COMBAT;
            }
            if (rules.denyWhenDead() && state.dead()) {
                return MailRefusal.DEAD;
            }
        }
        if (ownValues && state.busy()) {
            return MailRefusal.BUSY;
        }
        return null;
    }
}
