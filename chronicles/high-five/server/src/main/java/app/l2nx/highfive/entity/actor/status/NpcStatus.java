/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.actor.status;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.Duel;
import app.l2nx.highfive.partition.WorldPartition;

@SingleWriter
public class NpcStatus extends CreatureStatus {
    public NpcStatus(Npc activeChar) {
        super(activeChar);
    }

    @Override
    protected void doReduceHp(double value, Creature attacker, boolean awake, boolean isDOT, boolean isHpConsumption) {
        final Npc npc = (Npc) creature();
        if (npc.isDead()) {
            return;
        }

        if (attacker != null) {
            final Player attackerPlayer = attacker.asPlayer();
            if ((attackerPlayer != null) && attackerPlayer.isInDuel()) {
                attackerPlayer.setDuelState(Duel.DUELSTATE_INTERRUPTED);
            }

            npc.addAttackerToAttackByList(attacker);
        }

        super.doReduceHp(value, attacker, awake, isDOT, isHpConsumption);
    }

    @Override
    public Npc getActiveChar() {
        WorldPartition.checkOwner();
        return (Npc) creature();
    }
}
