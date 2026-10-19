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
package app.l2nx.highfive.scripts.ai.bosses.Anais;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import java.util.ArrayList;
import java.util.List;

/**
 * Anais AI.
 * @author nonom
 */
public class Anais extends Script {
    // NPCs
    private static final int ANAIS = 25701;
    private static final int DIVINE_BURNER = 18915;
    private static final int GRAIL_WARD = 18929;

    // Skill
    private static final SkillHolder DIVINE_NOVA = new SkillHolder(6326, 1);

    // Instances
    List<Npc> divineBurners = new ArrayList<>(4);
    private Player nextTarget = null;
    private Npc current = null;
    private int pot = 0;

    private Anais() {
        addAttackId(ANAIS);
        addSpawnId(DIVINE_BURNER);
        addKillId(GRAIL_WARD);
    }

    private void burnerOnAttack(int pot, Npc anais) {
        final Npc npc = divineBurners.get(pot);
        npc.setDisplayEffect(1);
        npc.setWalking();
        if (pot < 4) {
            current = npc;
            if (getQuestTimer("CHECK", anais, null) == null) {
                startQuestTimer("CHECK", 3000, anais, null);
            }
        } else {
            cancelQuestTimer("CHECK", anais, null);
        }
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        switch (event) {
            case "CHECK": {
                if (!npc.isAttackingNow()) {
                    cancelQuestTimer("CHECK", npc, null);
                }

                if ((current != null) || (pot < 4)) {
                    final WorldObject target = npc.getTarget();
                    nextTarget = (target != null) && target.isPlayer() ? target.asPlayer() : null;
                    final Npc b = divineBurners.get(pot);
                    pot += 1;
                    b.setDisplayEffect(1);
                    b.setWalking();
                    final Npc ward = addSpawn(GRAIL_WARD, new Location(b.getX(), b.getY(), b.getZ()), true, 0);
                    ward.asAttackable().addDamageHate(nextTarget, 0, 999);
                    ward.setRunning();
                    ward.getAI().setIntentionAttack(nextTarget);
                    startQuestTimer("GUARD_ATTACK", 1000, ward, nextTarget, true);
                    startQuestTimer("SUICIDE", 20000, ward, null);
                    ward.getAI().setIntentionAttack(nextTarget);
                }
                break;
            }
            case "GUARD_ATTACK": {
                if (nextTarget != null) {
                    final double distance = npc.calculateDistance2D(nextTarget);
                    if (distance < 100) {
                        npc.doCast(DIVINE_NOVA.getSkill());
                    } else if (distance > 2000) {
                        npc.doDie(null);
                        cancelQuestTimer("GUARD_ATTACK", npc, player);
                    }
                }
                break;
            }
            case "SUICIDE": {
                npc.doCast(DIVINE_NOVA.getSkill());
                cancelQuestTimer("GUARD_ATTACK", npc, nextTarget);
                if (current != null) {
                    current.setDisplayEffect(2);
                    current.setWalking();
                    current = null;
                }

                npc.doDie(null);
                break;
            }
        }

        return super.onEvent(event, npc, player);
    }

    @Override
    public void onAttack(Npc npc, Player attacker, int damage, boolean isSummon) {
        if (pot == 0) {
            burnerOnAttack(0, npc);
        } else if ((npc.getCurrentHp() <= (npc.getMaxRecoverableHp() * 0.75)) && (pot == 1)) {
            burnerOnAttack(1, npc);
        } else if ((npc.getCurrentHp() <= (npc.getMaxRecoverableHp() * 0.5)) && (pot == 2)) {
            burnerOnAttack(2, npc);
        } else if ((npc.getCurrentHp() <= (npc.getMaxRecoverableHp() * 0.25)) && (pot == 3)) {
            burnerOnAttack(3, npc);
        }
    }

    @Override
    public void onSpawn(Npc npc) {
        divineBurners.add(npc);
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        npc.doCast(DIVINE_NOVA.getSkill());
        cancelQuestTimer("GUARD_ATTACK", npc, nextTarget);
        cancelQuestTimer("CHECK", npc, null);
        if (current != null) {
            current.setDisplayEffect(2);
            current.setWalking();
            current = null;
        }
    }

    public static void main(String[] args) {
        new Anais();
    }
}
