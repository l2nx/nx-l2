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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.config.custom.ChampionMonstersConfig;
import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.NpcNameLocalisationData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.entity.actor.instance.Trap;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.type.TownZone;
import app.l2nx.highfive.managers.TownManager;
import app.l2nx.highfive.mechanics.skill.AbnormalVisualEffect;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public abstract class AbstractNpcInfo extends ServerPacket {
    protected int x;
    protected int y;
    protected int z;
    protected int heading;
    protected int displayId;
    protected boolean attackable;
    protected boolean summoned;
    protected int mAtkSpd;
    protected int pAtkSpd;
    protected final int runSpd;
    protected final int walkSpd;
    protected final int swimRunSpd;
    protected final int swimWalkSpd;
    protected final int flyRunSpd;
    protected final int flyWalkSpd;
    protected double moveMultiplier;
    protected int rhand;
    protected int lhand;
    protected int chest;
    protected int enchantEffect;
    protected float collisionHeight;
    protected float collisionRadius;
    protected String name = "";
    protected String title = "";
    protected final boolean gmSeeInvis;

    public AbstractNpcInfo(Creature creature, boolean gmSeeInvis) {
        summoned = creature.isShowSummonAnimation();
        x = creature.getX();
        y = creature.getY();
        z = creature.getZ();
        heading = creature.getHeading();
        mAtkSpd = creature.getMAtkSpd();
        pAtkSpd = (int) creature.getPAtkSpd();
        moveMultiplier = creature.getMovementSpeedMultiplier();
        runSpd = (int) Math.round(creature.getRunSpeed() / moveMultiplier);
        walkSpd = (int) Math.round(creature.getWalkSpeed() / moveMultiplier);
        swimRunSpd = (int) Math.round(creature.getSwimRunSpeed() / moveMultiplier);
        swimWalkSpd = (int) Math.round(creature.getSwimWalkSpeed() / moveMultiplier);
        flyRunSpd = creature.isFlying() ? runSpd : 0;
        flyWalkSpd = creature.isFlying() ? walkSpd : 0;
        this.gmSeeInvis = gmSeeInvis;
    }

    /**
     * Packet for Npcs
     */
    public static class NpcInfo extends AbstractNpcInfo {
        private final Npc npc;
        private int clanCrest = 0;
        private int allyCrest = 0;
        private int allyId = 0;
        private int clanId = 0;
        private int displayEffect = 0;

        public NpcInfo(Npc cha, Creature attacker) {
            super(cha, attacker.isGM());
            npc = cha;
            displayId = cha.getTemplate().getDisplayId(); // On every subclass
            rhand = cha.getRightHandItem(); // On every subclass
            lhand = cha.getLeftHandItem(); // On every subclass
            enchantEffect = cha.getEnchantEffect();
            collisionHeight = cha.getTemplate().getFCollisionHeight(); // On every subclass
            collisionRadius = cha.getTemplate().getFCollisionRadius(); // On every subclass
            attackable = cha.isAutoAttackable(attacker);

            // npc crest of owning clan/ally of castle
            if (cha.isNpc()
                    && cha.isTargetable()
                    && cha.isShowName()
                    && !cha.isMonster()
                    && cha.isInsideZone(ZoneId.TOWN)
                    && (NpcConfig.SHOW_CREST_WITHOUT_QUEST || cha.getCastle().getShowNpcCrest())
                    && (cha.getCastle().getOwnerId() != 0)) {
                final TownZone town = TownManager.getTown(x, y, z);
                if (town != null) {
                    final int townId = town.getTownId();
                    if ((townId != 33) && (townId != 22)) {
                        final Clan clan =
                                ClanTable.getInstance().getClan(cha.getCastle().getOwnerId());
                        clanCrest = clan.getCrestId();
                        clanId = clan.getId();
                        allyCrest = clan.getAllyCrestId();
                        allyId = clan.getAllyId();
                    }
                }
            }

            displayEffect = cha.getDisplayEffect();
        }

        @Override
        public void writeImpl(GameClient client, PacketWriter buffer) {
            if (npc.isDecayed()) {
                return;
            }

            // Localisation related.
            String[] localisation = null;
            if (MultilingualSupportConfig.MULTILANG_ENABLE) {
                final Player player = client.getPlayer();
                if (player != null) {
                    final String lang = player.getLang();
                    if ((lang != null) && !lang.equals("en")) {
                        localisation = NpcNameLocalisationData.getInstance().getLocalisation(lang, npc.getId());
                        if (localisation != null) {
                            name = localisation[0];
                            title = localisation[1];
                        }
                    }
                }
            }

            ServerPackets.NPC_INFO.writeId(this, buffer);
            buffer.writeInt(npc.getObjectId());
            buffer.writeInt(displayId + 1000000); // npctype id
            buffer.writeInt(attackable);
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
            buffer.writeInt(heading);
            buffer.writeInt(0);
            buffer.writeInt(mAtkSpd);
            buffer.writeInt(pAtkSpd);
            buffer.writeInt(runSpd);
            buffer.writeInt(walkSpd);
            buffer.writeInt(swimRunSpd);
            buffer.writeInt(swimWalkSpd);
            buffer.writeInt(flyRunSpd);
            buffer.writeInt(flyWalkSpd);
            buffer.writeInt(flyRunSpd);
            buffer.writeInt(flyWalkSpd);
            buffer.writeDouble(moveMultiplier);
            buffer.writeDouble(npc.getAttackSpeedMultiplier());
            buffer.writeDouble(collisionRadius);
            buffer.writeDouble(collisionHeight);
            buffer.writeInt(rhand); // right hand weapon
            buffer.writeInt(chest);
            buffer.writeInt(lhand); // left hand weapon
            buffer.writeByte(1); // name above char 1=true ... ??
            buffer.writeByte(npc.isRunning());
            buffer.writeByte(npc.isInCombat());
            buffer.writeByte(npc.isAlikeDead());
            buffer.writeByte(
                    summoned
                            ? 2
                            : 0); // invisible ?? 0=false 1=true 2=summoned (only works if model has a summon animation)
            buffer.writeInt(-1); // High Five NPCString ID
            if ((localisation == null) && npc.getTemplate().isUsingServerSideName()) {
                name = npc.getName(); // On every subclass
            }

            buffer.writeString(name);
            buffer.writeInt(-1); // High Five NPCString ID
            if (npc.isInvisible()) {
                title = "Invisible";
            } else if (localisation == null) {
                if (npc.getTemplate().isUsingServerSideTitle()) {
                    title = npc.getTemplate().getTitle(); // On every subclass
                } else {
                    title = npc.getTitle(); // On every subclass
                }
            }

            // Custom level titles
            if (npc.isMonster() && (NpcConfig.SHOW_NPC_LEVEL || NpcConfig.SHOW_NPC_AGGRESSION)) {
                String t1 = "";
                if (NpcConfig.SHOW_NPC_LEVEL) {
                    t1 += "Lv " + npc.getLevel();
                }

                String t2 = "";
                if (NpcConfig.SHOW_NPC_AGGRESSION) {
                    if (!t1.isEmpty()) {
                        t2 += " ";
                    }

                    final Monster monster = npc.asMonster();
                    if (monster.isAggressive()) {
                        t2 += "[A]"; // Aggressive.
                    }

                    if ((monster.getTemplate().getClans() != null)
                            && (monster.getTemplate().getClanHelpRange() > 0)) {
                        t2 += "[G]"; // Group.
                    }
                }

                t1 += t2;
                if ((title != null) && !title.isEmpty()) {
                    t1 += " " + title;
                }

                title = npc.isChampion() ? ChampionMonstersConfig.CHAMP_TITLE + " " + t1 : t1;
            } else if (ChampionMonstersConfig.CHAMPION_ENABLE && npc.isChampion()) {
                title = (ChampionMonstersConfig.CHAMP_TITLE); // On every subclass
            }

            buffer.writeString(title);
            buffer.writeInt(0); // Title color 0=client default
            buffer.writeInt(0); // pvp flag
            buffer.writeInt(0); // karma
            buffer.writeInt(
                    npc.isInvisible()
                            ? npc.getAbnormalVisualEffects() | AbnormalVisualEffect.STEALTH.getMask()
                            : npc.getAbnormalVisualEffects());
            buffer.writeInt(clanId); // clan id
            buffer.writeInt(clanCrest); // crest id
            buffer.writeInt(allyId); // ally id
            buffer.writeInt(allyCrest); // all crest
            buffer.writeByte(npc.isInsideZone(ZoneId.WATER) ? 1 : npc.isFlying() ? 2 : 0); // C2
            buffer.writeByte(npc.getTeam().getId());
            buffer.writeDouble(collisionRadius);
            buffer.writeDouble(collisionHeight);
            buffer.writeInt(enchantEffect); // C4
            buffer.writeInt(npc.isFlying()); // C6
            buffer.writeInt(0);
            buffer.writeInt(npc.getColorEffect()); // CT1.5 Pet form and skills, Color effect
            buffer.writeByte(npc.isTargetable());
            buffer.writeByte(npc.isShowName());
            buffer.writeInt(npc.getAbnormalVisualEffectSpecial());
            buffer.writeInt(displayEffect);
        }
    }

    public static class TrapInfo extends AbstractNpcInfo {
        private final Trap trap;

        public TrapInfo(Trap cha, Creature attacker) {
            super(cha, (attacker != null) && attacker.isGM());
            trap = cha;
            displayId = cha.getTemplate().getDisplayId();
            attackable = cha.isAutoAttackable(attacker);
            rhand = 0;
            lhand = 0;
            collisionHeight = trap.getTemplate().getFCollisionHeight();
            collisionRadius = trap.getTemplate().getFCollisionRadius();
            if (cha.getTemplate().isUsingServerSideName()) {
                name = cha.getName();
            }

            title = cha.getOwner() != null ? cha.getOwner().getName() : "";
        }

        @Override
        public void writeImpl(GameClient client, PacketWriter buffer) {
            ServerPackets.NPC_INFO.writeId(this, buffer);
            buffer.writeInt(trap.getObjectId());
            buffer.writeInt(displayId + 1000000); // npctype id
            buffer.writeInt(attackable);
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
            buffer.writeInt(heading);
            buffer.writeInt(0);
            buffer.writeInt(mAtkSpd);
            buffer.writeInt(pAtkSpd);
            buffer.writeInt(runSpd);
            buffer.writeInt(walkSpd);
            buffer.writeInt(swimRunSpd);
            buffer.writeInt(swimWalkSpd);
            buffer.writeInt(flyRunSpd);
            buffer.writeInt(flyWalkSpd);
            buffer.writeInt(flyRunSpd);
            buffer.writeInt(flyWalkSpd);
            buffer.writeDouble(moveMultiplier);
            buffer.writeDouble(trap.getAttackSpeedMultiplier());
            buffer.writeDouble(collisionRadius);
            buffer.writeDouble(collisionHeight);
            buffer.writeInt(rhand); // right hand weapon
            buffer.writeInt(chest);
            buffer.writeInt(lhand); // left hand weapon
            buffer.writeByte(1); // name above char 1=true ... ??
            buffer.writeByte(1);
            buffer.writeByte(trap.isInCombat());
            buffer.writeByte(trap.isAlikeDead());
            buffer.writeByte(
                    summoned
                            ? 2
                            : 0); // invisible ?? 0=false 1=true 2=summoned (only works if model has a summon animation)
            buffer.writeInt(-1); // High Five NPCString ID
            buffer.writeString(name);
            buffer.writeInt(-1); // High Five NPCString ID
            buffer.writeString(title);
            buffer.writeInt(0); // title color 0 = client default
            buffer.writeInt(trap.getPvpFlag());
            buffer.writeInt(trap.getKarma());
            buffer.writeInt(
                    trap.isInvisible()
                            ? trap.getAbnormalVisualEffects() | AbnormalVisualEffect.STEALTH.getMask()
                            : trap.getAbnormalVisualEffects());
            buffer.writeInt(0); // clan id
            buffer.writeInt(0); // crest id
            buffer.writeInt(0); // C2
            buffer.writeInt(0); // C2
            buffer.writeByte(0); // C2
            buffer.writeByte(trap.getTeam().getId());
            buffer.writeDouble(collisionRadius);
            buffer.writeDouble(collisionHeight);
            buffer.writeInt(0); // C4
            buffer.writeInt(0); // C6
            buffer.writeInt(0);
            buffer.writeInt(0); // CT1.5 Pet form and skills
            buffer.writeByte(1);
            buffer.writeByte(1);
            buffer.writeInt(0);
        }
    }

    /**
     * Packet for summons.
     */
    public static class SummonInfo extends AbstractNpcInfo {
        private final Summon summon;
        private final int form;
        private final int value;

        public SummonInfo(Summon cha, Creature attacker, int value) {
            super(cha, attacker.isGM());
            summon = cha;
            this.value = value;
            form = cha.getFormId();
            attackable = cha.isAutoAttackable(attacker);
            rhand = cha.getWeapon();
            lhand = 0;
            chest = cha.getArmor();
            enchantEffect = cha.getTemplate().getWeaponEnchant();
            name = cha.getName();
            title = (cha.getOwner() != null) && cha.getOwner().isOnline()
                    ? cha.getOwner().getName()
                    : "";
            displayId = cha.getTemplate().getDisplayId();
            collisionHeight = cha.getTemplate().getFCollisionHeight();
            collisionRadius = cha.getTemplate().getFCollisionRadius();
        }

        @Override
        public void writeImpl(GameClient client, PacketWriter buffer) {
            ServerPackets.NPC_INFO.writeId(this, buffer);
            buffer.writeInt(summon.getObjectId());
            buffer.writeInt(displayId + 1000000); // npctype id
            buffer.writeInt(attackable);
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
            buffer.writeInt(heading);
            buffer.writeInt(0);
            buffer.writeInt(mAtkSpd);
            buffer.writeInt(pAtkSpd);
            buffer.writeInt(runSpd);
            buffer.writeInt(walkSpd);
            buffer.writeInt(swimRunSpd);
            buffer.writeInt(swimWalkSpd);
            buffer.writeInt(flyRunSpd);
            buffer.writeInt(flyWalkSpd);
            buffer.writeInt(flyRunSpd);
            buffer.writeInt(flyWalkSpd);
            buffer.writeDouble(moveMultiplier);
            buffer.writeDouble(summon.getAttackSpeedMultiplier());
            buffer.writeDouble(collisionRadius);
            buffer.writeDouble(collisionHeight);
            buffer.writeInt(rhand); // right hand weapon
            buffer.writeInt(chest);
            buffer.writeInt(lhand); // left hand weapon
            buffer.writeByte(1); // name above char 1=true ... ??
            buffer.writeByte(1); // always running 1=running 0=walking
            buffer.writeByte(summon.isInCombat());
            buffer.writeByte(summon.isAlikeDead());
            buffer.writeByte(
                    summoned ? 2 : value); // invisible ?? 0=false 1=true 2=summoned (only works if model has a summon
            // animation)
            buffer.writeInt(-1); // High Five NPCString ID
            buffer.writeString(name);
            buffer.writeInt(-1); // High Five NPCString ID
            buffer.writeString(title);
            buffer.writeInt(1); // Title color 0=client default
            buffer.writeInt(summon.getPvpFlag());
            buffer.writeInt(summon.getKarma());
            buffer.writeInt(
                    gmSeeInvis && summon.isInvisible()
                            ? summon.getAbnormalVisualEffects() | AbnormalVisualEffect.STEALTH.getMask()
                            : summon.getAbnormalVisualEffects());
            buffer.writeInt(0); // clan id
            buffer.writeInt(0); // crest id
            buffer.writeInt(0); // C2
            buffer.writeInt(0); // C2
            buffer.writeByte(summon.isInsideZone(ZoneId.WATER) ? 1 : summon.isFlying() ? 2 : 0); // C2
            buffer.writeByte(summon.getTeam().getId());
            buffer.writeDouble(collisionRadius);
            buffer.writeDouble(collisionHeight);
            buffer.writeInt(enchantEffect); // C4
            buffer.writeInt(0); // C6
            buffer.writeInt(0);
            buffer.writeInt(form); // CT1.5 Pet form and skills
            buffer.writeByte(1);
            buffer.writeByte(1);
            buffer.writeInt(summon.getAbnormalVisualEffectSpecial());
        }
    }
}
