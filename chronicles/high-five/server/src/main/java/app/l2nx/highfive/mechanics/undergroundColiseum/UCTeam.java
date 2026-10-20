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
package app.l2nx.highfive.mechanics.undergroundColiseum;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.UndergroundColiseumConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.instance.UCTower;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.managers.games.UndergroundColiseumManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ExPCCafePointInfo;
import app.l2nx.highfive.network.serverpackets.ExPVPMatchUserDie;
import app.l2nx.highfive.network.serverpackets.ServerPacket;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.List;
import java.util.random.RandomGenerator;
import lombok.Getter;
import lombok.Setter;

public class UCTeam {
    public static final byte NOT_DECIDED = 0;
    public static final byte WIN = 1;
    public static final byte FAIL = 2;

    @Getter
    private final int index;

    @Getter
    private final UCArena baseArena;

    protected final int x;
    protected final int y;
    protected final int z;
    private final int npcId;
    private UCTower tower = null;

    @Getter
    private Party party;

    @Getter
    private int killCount;

    @Getter
    private byte status;

    @Setter
    private Party lastParty;

    @Getter
    private int consecutiveWins;

    @Getter
    @Setter
    private long registerTime;

    public UCTeam(int index, UCArena baseArena, int x, int y, int z, int npcId) {
        this.index = index;
        this.baseArena = baseArena;
        this.x = x;
        this.y = y;
        this.z = z;
        this.npcId = npcId;

        setStatus(NOT_DECIDED);
    }

    public void increaseConsecutiveWins() {
        consecutiveWins++;
        if ((consecutiveWins > 1) && (party != null) && (party.getLeader() != null)) {
            UndergroundColiseumManager.getInstance()
                    .updateBestTeam(baseArena.getId(), party.getLeader().getName(), consecutiveWins);
        }
    }

    public void spawnTower() {
        if (tower != null) {
            return;
        }

        final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
        if (template != null) {
            tower = new UCTower(this, template);
            tower.setInvul(false);
            tower.setCurrentHpMp(tower.getMaxHp(), tower.getMaxMp());
            tower.spawnMe(x, y, z);
        }
    }

    public void deleteTower() {
        if (tower != null) {
            tower.deleteMe();
            tower = null;
        }
    }

    public void onKill(Player player, Player killer) {
        if ((player == null) || (killer == null) || (getParty() == null)) {
            return;
        }

        if ((player.getParty() != null) && (player.getParty() == killer.getParty())) {
            return;
        }

        final UCTeam otherTeam = getOtherTeam();
        otherTeam.increaseKillCount();
        player.addDeathCountUC();
        killer.addKillCountUC();

        baseArena.broadcastToAll(new ExPVPMatchUserDie(baseArena));

        if (player.getUCState() == Player.UC_STATE_POINT) {
            for (UCPoint point : baseArena.getPoints()) {
                if (point.checkPlayer(player)) {
                    break;
                }
            }
        }

        if (tower == null) {
            boolean flag = true;
            for (Player member : getParty().getMembers()) {
                if ((member != null) && !member.isDead()) {
                    flag = false;
                }
            }

            if (flag) {
                setStatus(FAIL);
                otherTeam.setStatus(WIN);
                baseArena.runTaskNow();
            }
            return;
        }

        ThreadPool.schedule(
                () -> {
                    if (tower == null) {
                        return;
                    }

                    if (player.isDead()) {
                        resPlayer(player);
                        final RandomGenerator random = ClockBridge.random();
                        player.teleToLocation(x + random.nextInt(2, 51), y + random.nextInt(10, 101), z, true);
                        if (player.hasSummon()) {
                            final Summon summon = player.getSummon();
                            summon.abortAttack();
                            summon.abortCast();
                            if (!summon.isDead()) {
                                summon.setCurrentHp(summon.getMaxHp());
                                summon.setCurrentMp(summon.getMaxMp());
                                summon.teleToLocation(x + random.nextInt(2, 51), y + random.nextInt(10, 101), z, true);
                            }
                        }
                    }
                },
                UndergroundColiseumConfig.UC_RESS_TIME * 1000);
    }

    public void increaseKillCount() {
        killCount++;
    }

    public static void resPlayer(Player player) {
        if (player == null) {
            return;
        }

        player.restoreExp(100.0);
        player.doRevive();
        player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
        player.setCurrentCp(player.getMaxCp());
    }

    public void cleanUp() {
        if (getParty() != null) {
            getParty().setUCState(null);
            party = null;
        }

        party = null;
        lastParty = null;
        consecutiveWins = 0;
        setStatus(NOT_DECIDED);
        killCount = 0;
    }

    public void computeReward() {
        if ((lastParty == null) || (lastParty != getOtherTeam().getParty())) {
            final List<UCReward> rewards = baseArena.getRewards();
            double modifier = 1;
            switch (consecutiveWins) {
                case 1: {
                    modifier = 1.0;
                    break;
                }
                case 2: {
                    modifier = 1.06;
                    break;
                }
                case 3: {
                    modifier = 1.12;
                    break;
                }
                case 4: {
                    modifier = 1.18;
                    break;
                }
                case 5: {
                    modifier = 1.25;
                    break;
                }
                case 6: {
                    modifier = 1.27;
                    break;
                }
                case 7: {
                    modifier = 1.3;
                    break;
                }
                case 8: {
                    modifier = 1.32;
                    break;
                }
                case 9: {
                    modifier = 1.35;
                    break;
                }
                case 10: {
                    modifier = 1.37;
                    break;
                }
                default: {
                    if (consecutiveWins > 10) {
                        modifier = 1.4;
                    }
                    break;
                }
            }

            if ((rewards == null) || rewards.isEmpty()) {
                return;
            }

            for (Player member : getParty().getMembers()) {
                if (member != null) {
                    for (UCReward reward : rewards) {
                        if (reward.id() == -100) {
                            long amount =
                                    reward.allowMidifier() ? (long) (reward.amount() * modifier) : reward.amount();
                            if ((member.getPcCafePoints() + amount) > PremiumSystemConfig.PC_CAFE_MAX_POINTS) {
                                amount = PremiumSystemConfig.PC_CAFE_MAX_POINTS - member.getPcCafePoints();
                            }

                            final SystemMessage sm =
                                    new SystemMessage(SystemMessageId.YOU_HAVE_ACQUIRED_S1_PC_CAFE_POINTS);
                            sm.addInt((int) amount);
                            member.sendPacket(sm);
                            member.setPcCafePoints((int) (member.getPcCafePoints() + amount));
                            member.sendPacket(new ExPCCafePointInfo(member.getPcCafePoints(), (int) amount, 1));
                        } else if (reward.id() == -200) {
                            if (member.getClan() != null) {
                                final long amount =
                                        reward.allowMidifier() ? (long) (reward.amount() * modifier) : reward.amount();
                                member.getClan().addReputationScore((int) amount);
                            }
                        } else if (reward.id() == -300) {
                            final long amount =
                                    reward.allowMidifier() ? (long) (reward.amount() * modifier) : reward.amount();
                            member.setFame((int) (member.getFame() + amount));
                            final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_ACQUIRED_S1_REPUTATION);
                            sm.addInt((int) amount);
                            member.sendPacket(sm);
                            member.updateUserInfo();
                        } else if (reward.id() > 0) {
                            final long amount =
                                    reward.allowMidifier() ? (long) (reward.amount() * modifier) : reward.amount();
                            member.addItem(ItemProcessType.REWARD, reward.id(), amount, null, true);
                        }
                    }
                }
            }
        }
    }

    public void setStatus(byte status) {
        this.status = status;

        if (this.status == WIN) {
            if (getIndex() == 0) {
                baseArena.broadcastToAll(new SystemMessage(SystemMessageId.THE_BLUE_TEAM_IS_VICTORIOUS));
            } else {
                baseArena.broadcastToAll(new SystemMessage(SystemMessageId.THE_RED_TEAM_IS_VICTORIOUS));
            }
        }

        switch (this.status) {
            case NOT_DECIDED: {
                break;
            }
            case WIN: {
                increaseConsecutiveWins();
                computeReward();
                deleteTower();
                break;
            }
            case FAIL: {
                deleteTower();
                break;
            }
        }
    }

    public void broadcastToTeam(ServerPacket packet) {
        final Party party = this.party;
        if (party != null) {
            for (Player member : party.getMembers()) {
                if (member != null) {
                    member.sendPacket(packet);
                }
            }
        }
    }

    public UCTeam getOtherTeam() {
        return baseArena.getTeams()[getOtherTeamIndex()];
    }

    public int getOtherTeamIndex() {
        return index == 0 ? 1 : 0;
    }

    public void setParty(Party party) {
        final Party oldParty = this.party;
        this.party = party;
        if (oldParty != null) {
            oldParty.setUCState(null);
        }

        if (this.party != null) {
            this.party.setUCState(this);
        }
    }
}
