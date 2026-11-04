/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper.mail;

import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.highfive.network.SystemMessageId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class P267MailRefusals {
    private static final Map<MailRefusal, SystemMessageId> BY_REFUSAL = byRefusal();
    private static final Map<MailAction, Map<MailRefusal, SystemMessageId>> BY_ACTION = byAction();
    private static final Set<MailRefusal> NAMING_THE_RECEIVER =
            Collections.unmodifiableSet(EnumSet.of(MailRefusal.RECEIVER_BLOCKED, MailRefusal.RECEIVER_GM_ONLY));

    private P267MailRefusals() {}

    public static SystemMessageId id(MailAction action, MailRefusal refusal) {
        final SystemMessageId specific = BY_ACTION.get(action).get(refusal);
        return (specific == null) ? BY_REFUSAL.get(refusal) : specific;
    }

    public static boolean namesReceiver(MailRefusal refusal) {
        return NAMING_THE_RECEIVER.contains(refusal);
    }

    private static Map<MailRefusal, SystemMessageId> byRefusal() {
        final Map<MailRefusal, SystemMessageId> table = new EnumMap<>(MailRefusal.class);
        table.put(MailRefusal.DISABLED, SystemMessageId.THIS_FUNCTION_IS_INACCESSIBLE_RIGHT_NOW_PLEASE_TRY_AGAIN_LATER);
        table.put(
                MailRefusal.ATTACHMENTS_DISABLED,
                SystemMessageId.THIS_FUNCTION_IS_INACCESSIBLE_RIGHT_NOW_PLEASE_TRY_AGAIN_LATER);
        table.put(
                MailRefusal.IN_COMBAT, SystemMessageId.THIS_FUNCTION_IS_INACCESSIBLE_RIGHT_NOW_PLEASE_TRY_AGAIN_LATER);
        table.put(MailRefusal.DEAD, SystemMessageId.THIS_FUNCTION_IS_INACCESSIBLE_RIGHT_NOW_PLEASE_TRY_AGAIN_LATER);
        table.put(
                MailRefusal.HAS_ATTACHMENTS,
                SystemMessageId.THIS_FUNCTION_IS_INACCESSIBLE_RIGHT_NOW_PLEASE_TRY_AGAIN_LATER);
        table.put(MailRefusal.BUSY, SystemMessageId.THIS_FUNCTION_IS_INACCESSIBLE_RIGHT_NOW_PLEASE_TRY_AGAIN_LATER);
        table.put(
                MailRefusal.NOT_IN_PEACE_ZONE,
                SystemMessageId.YOU_CANNOT_RECEIVE_OR_SEND_MAIL_WITH_ATTACHED_ITEMS_IN_NON_PEACE_ZONE_REGIONS);
        table.put(MailRefusal.ACCESS_DENIED, SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
        table.put(
                MailRefusal.SEND_INTERVAL,
                SystemMessageId.THE_PREVIOUS_MAIL_WAS_FORWARDED_LESS_THAN_1_MINUTE_AGO_AND_THIS_CANNOT_BE_FORWARDED);
        table.put(MailRefusal.NOT_ENOUGH_ADENA, SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
        table.put(MailRefusal.TOO_MANY_ATTACHMENTS, SystemMessageId.ITEM_SELECTION_IS_POSSIBLE_UP_TO_8);
        table.put(
                MailRefusal.ITEM_NOT_MAILABLE,
                SystemMessageId.THE_ITEM_THAT_YOU_RE_TRYING_TO_SEND_CANNOT_BE_FORWARDED_BECAUSE_IT_ISN_T_PROPER);
        table.put(
                MailRefusal.COD_WITHOUT_ATTACHMENTS,
                SystemMessageId.IT_S_A_PAYMENT_REQUEST_TRANSACTION_PLEASE_ATTACH_THE_ITEM);
        table.put(
                MailRefusal.COD_AMOUNT_INVALID,
                SystemMessageId.WHEN_NOT_ENTERING_THE_AMOUNT_FOR_THE_PAYMENT_REQUEST_YOU_CANNOT_SEND_ANY_MAIL);
        table.put(MailRefusal.INVALID_TEXT, SystemMessageId.THE_ALLOWED_LENGTH_FOR_A_TITLE_EXCEEDED);
        table.put(
                MailRefusal.RECEIVER_NOT_FOUND,
                SystemMessageId
                        .WHEN_THE_RECIPIENT_DOESN_T_EXIST_OR_THE_CHARACTER_HAS_BEEN_DELETED_SENDING_MAIL_IS_NOT_POSSIBLE);
        table.put(MailRefusal.RECEIVER_SELF, SystemMessageId.YOU_CANNOT_SEND_A_MAIL_TO_YOURSELF);
        table.put(MailRefusal.RECEIVER_BLOCKED, SystemMessageId.C1_HAS_BLOCKED_YOU_YOU_CANNOT_SEND_MAIL_TO_C1);
        table.put(
                MailRefusal.RECEIVER_GM_ONLY,
                SystemMessageId.YOUR_MESSAGE_TO_C1_DID_NOT_REACH_ITS_RECIPIENT_YOU_CANNOT_SEND_MAIL_TO_THE_GM_STAFF);
        table.put(MailRefusal.JAILED, SystemMessageId.YOU_CANNOT_FORWARD_IN_A_NON_PEACE_ZONE_LOCATION);
        table.put(
                MailRefusal.RECEIVER_MAILBOX_FULL,
                SystemMessageId.THE_MAIL_LIMIT_240_HAS_BEEN_EXCEEDED_AND_THIS_CANNOT_BE_FORWARDED);
        table.put(
                MailRefusal.SENDER_MAILBOX_FULL,
                SystemMessageId.THE_MAIL_LIMIT_240_HAS_BEEN_EXCEEDED_AND_THIS_CANNOT_BE_FORWARDED);
        table.put(MailRefusal.INVENTORY_FULL, SystemMessageId.YOUR_INVENTORY_IS_FULL);
        table.put(MailRefusal.WEIGHT_LIMIT, SystemMessageId.YOU_HAVE_EXCEEDED_THE_WEIGHT_LIMIT);
        table.put(MailRefusal.ADENA_LIMIT, SystemMessageId.YOU_HAVE_EXCEEDED_YOUR_OUT_OF_POCKET_ADENA_LIMIT);
        table.put(
                MailRefusal.SENDER_ADENA_LIMIT,
                SystemMessageId
                        .YOU_CANNOT_RECEIVE_IT_BECAUSE_YOU_ARE_UNDER_THE_CONDITION_THAT_THE_OPPONENT_CANNOT_ACQUIRE_ANY_ADENA_FOR_PAYMENT);
        // Protocol 267 has no "attachments taken" flag, so a settled mail still offers Receive (Cancel when sent).
        table.put(MailRefusal.ALREADY_RECEIVED, SystemMessageId.THE_PRODUCT_HAS_ALREADY_BEEN_RECEIVED);
        // The client hides Return unless the list said returnable: a refusal here means the mail settled meanwhile.
        table.put(MailRefusal.NOT_RETURNABLE, SystemMessageId.THE_PRODUCT_HAS_ALREADY_BEEN_RECEIVED);
        table.put(MailRefusal.FAILED, SystemMessageId.SYSTEM_ERROR);
        for (MailRefusal refusal : MailRefusal.values()) {
            if (table.get(refusal) == null) {
                throw new IllegalStateException("No High Five message for " + refusal);
            }
        }
        return Collections.unmodifiableMap(table);
    }

    private static Map<MailAction, Map<MailRefusal, SystemMessageId>> byAction() {
        final Map<MailAction, Map<MailRefusal, SystemMessageId>> table = new EnumMap<>(MailAction.class);
        for (MailAction action : MailAction.values()) {
            table.put(action, new EnumMap<>(MailRefusal.class));
        }
        final Map<MailRefusal, SystemMessageId> send = table.get(MailAction.SEND);
        send.put(MailRefusal.BUSY, SystemMessageId.YOU_CANNOT_FORWARD_DURING_AN_EXCHANGE);
        send.put(MailRefusal.NOT_ENOUGH_ADENA, SystemMessageId.YOU_CANNOT_FORWARD_BECAUSE_YOU_DON_T_HAVE_ENOUGH_ADENA);
        final Map<MailRefusal, SystemMessageId> receive = table.get(MailAction.RECEIVE);
        receive.put(MailRefusal.BUSY, SystemMessageId.YOU_CANNOT_RECEIVE_DURING_AN_EXCHANGE);
        receive.put(
                MailRefusal.NOT_ENOUGH_ADENA, SystemMessageId.YOU_CANNOT_RECEIVE_BECAUSE_YOU_DON_T_HAVE_ENOUGH_ADENA);
        receive.put(MailRefusal.INVENTORY_FULL, SystemMessageId.YOU_COULD_NOT_RECEIVE_BECAUSE_YOUR_INVENTORY_IS_FULL);
        final Map<MailRefusal, SystemMessageId> cancel = table.get(MailAction.CANCEL);
        cancel.put(MailRefusal.BUSY, SystemMessageId.YOU_CANNOT_CANCEL_DURING_AN_EXCHANGE);
        cancel.put(
                MailRefusal.INVENTORY_FULL,
                SystemMessageId.YOU_COULD_NOT_CANCEL_RECEIPT_BECAUSE_YOUR_INVENTORY_IS_FULL);
        cancel.put(
                MailRefusal.ALREADY_RECEIVED,
                SystemMessageId.YOU_CANNOT_CANCEL_SENT_MAIL_SINCE_THE_RECIPIENT_RECEIVED_IT);
        table.replaceAll((action, refusals) -> Collections.unmodifiableMap(refusals));
        return Collections.unmodifiableMap(table);
    }
}
