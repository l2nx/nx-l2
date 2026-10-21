/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.mechanics.olympiad.Hero;
import app.l2nx.highfive.mechanics.olympiad.Olympiad;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.util.StatSet;
import app.l2nx.network.codec.PacketWriter;
import java.util.Map;

/**
 * @author -Wooden-, KenM, godson
 */
public class ExHeroList extends ServerPacket {
    private final Map<Integer, StatSet> heroList;

    public ExHeroList() {
        heroList = Hero.getInstance().getHeroes();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_HERO_LIST.writeId(this, buffer);
        buffer.writeInt(heroList.size());
        for (StatSet hero : heroList.values()) {
            buffer.writeString(hero.getString(Olympiad.CHAR_NAME));
            buffer.writeInt(hero.getInt(Olympiad.CLASS_ID));
            buffer.writeString(hero.getString(Hero.CLAN_NAME, ""));
            buffer.writeInt(hero.getInt(Hero.CLAN_CREST, 0));
            buffer.writeString(hero.getString(Hero.ALLY_NAME, ""));
            buffer.writeInt(hero.getInt(Hero.ALLY_CREST, 0));
            buffer.writeInt(hero.getInt(Hero.COUNT));
        }
    }
}
