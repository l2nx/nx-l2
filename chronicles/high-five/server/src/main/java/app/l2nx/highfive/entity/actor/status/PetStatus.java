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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import lombok.Getter;
import lombok.Setter;

public class PetStatus extends SummonStatus {
    @Getter
    @Setter
    private int currentFed = 0; // Current Fed of the Pet

    public PetStatus(Pet activeChar) {
        super(activeChar);
    }

    @Override
    public void reduceHp(double value, Creature attacker) {
        reduceHp(value, attacker, true, false, false);
    }

    @Override
    public void reduceHp(double value, Creature attacker, boolean awake, boolean isDOT, boolean isHpConsumption) {
        final Pet pet = getActiveChar();
        if (pet.isDead()) {
            return;
        }

        super.reduceHp(value, attacker, awake, isDOT, isHpConsumption);

        if (attacker == null) {
            return;
        }

        if (!isDOT && (pet.getOwner() != null)) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_PET_RECEIVED_S2_DAMAGE_BY_C1);
            sm.addString(attacker.getName());
            sm.addInt((int) value);
            pet.sendPacket(sm);
        }

        pet.getAI().notifyActionAttacked(attacker);
    }

    @Override
    public Pet getActiveChar() {
        return super.getActiveChar().asPet();
    }
}
