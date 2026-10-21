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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCArena;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCTeam;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

public class ExPVPMatchRecord extends ServerPacket {
    public static final int START = 0;
    public static final int UPDATE = 1;
    public static final int FINISH = 2;

    private final int type;
    private final int winnerTeam;
    private final int blueKills;
    private final int redKills;
    private final List<Member> blueList;
    private final List<Member> redList;

    public ExPVPMatchRecord(int type, int winnerTeam, UCArena arena) {
        this.type = type;
        this.winnerTeam = winnerTeam;

        final UCTeam blueTeam = arena.getTeams()[0];
        blueKills = blueTeam.getKillCount();
        final UCTeam redTeam = arena.getTeams()[1];
        redKills = redTeam.getKillCount();

        blueList = new ArrayList<>(9);

        if (blueTeam.getParty() != null) {
            for (Player memberObject : blueTeam.getParty().getMembers()) {
                if (memberObject != null) {
                    blueList.add(
                            new Member(memberObject.getName(), memberObject.getUCKills(), memberObject.getUCDeaths()));
                }
            }
        }

        redList = new ArrayList<>(9);

        if (redTeam.getParty() != null) {
            for (Player memberObject : redTeam.getParty().getMembers()) {
                if (memberObject != null) {
                    redList.add(
                            new Member(memberObject.getName(), memberObject.getUCKills(), memberObject.getUCDeaths()));
                }
            }
        }
    }

    public ExPVPMatchRecord(int type, int winnerTeam) {
        this.type = type;
        this.winnerTeam = winnerTeam;
        blueKills = 0;
        redKills = 0;
        blueList = new ArrayList<>(9);
        redList = new ArrayList<>(9);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_PVP_MATCH_RECORD.writeId(this, buffer);
        buffer.writeInt(type);
        buffer.writeInt(winnerTeam);
        buffer.writeInt(winnerTeam == 0 ? 0 : winnerTeam == 1 ? 2 : 1);
        buffer.writeInt(blueKills);
        buffer.writeInt(redKills);
        buffer.writeInt(blueList.size());
        for (Member member : blueList) {
            buffer.writeString(member.name);
            buffer.writeInt(member.kills);
            buffer.writeInt(member.deaths);
        }

        buffer.writeInt(redList.size());
        for (Member member : redList) {
            buffer.writeString(member.name);
            buffer.writeInt(member.kills);
            buffer.writeInt(member.deaths);
        }
    }

    public static class Member {
        public String name;
        public int kills;
        public int deaths;

        public Member(String name, int kills, int deaths) {
            this.name = name;
            this.kills = kills;
            this.deaths = deaths;
        }
    }
}
