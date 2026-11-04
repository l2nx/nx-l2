/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper.mail;

import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChange;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailStateChanged;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.P267MailNumbers;
import app.l2nx.highfive.network.protocol.p267.mapper.OutboundMapper;
import app.l2nx.protocol.game.p267.serverpackets.ExChangePostState;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.PostState;
import java.util.ArrayList;
import java.util.List;

/** A mail the client was never shown has no number and is left out; the client sees it as it is at its next list. */
public final class MailStateChangedMapper implements OutboundMapper<MailStateChanged> {
    @Override
    public Class<MailStateChanged> eventType() {
        return MailStateChanged.class;
    }

    @Override
    public GameServerPacket map(MailStateChanged event, GameClient viewer) {
        final P267MailNumbers numbers = P267MailNumbers.of(viewer);
        final List<ExChangePostState.Change> changes =
                new ArrayList<>(event.changes().size());
        for (MailChange change : event.changes()) {
            final int number = numbers.known(change.mailId());
            if (number != P267MailNumbers.NO_NUMBER) {
                changes.add(new ExChangePostState.Change(number, state(change.type())));
            }
        }
        if (changes.isEmpty()) {
            return null;
        }
        return new ExChangePostState(event.box() == MailBox.RECEIVED, changes);
    }

    private static PostState state(MailChangeType type) {
        return switch (type) {
            case DELETED -> PostState.DELETED;
            case READ -> PostState.READ;
            case ATTACHMENTS_TAKEN -> PostState.ATTACHMENTS_TAKEN;
        };
    }
}
