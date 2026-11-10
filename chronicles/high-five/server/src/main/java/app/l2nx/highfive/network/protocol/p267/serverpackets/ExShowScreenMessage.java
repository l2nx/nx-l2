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

import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.NpcStringId.NSLocalisation;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.SystemMessageId.SMLocalisation;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Kerberos
 */
public class ExShowScreenMessage extends ServerPacket {
    // Positions
    public static final byte TOP_LEFT = 1;
    public static final byte TOP_CENTER = 2;
    public static final byte TOP_RIGHT = 3;
    public static final byte MIDDLE_LEFT = 4;
    public static final byte MIDDLE_CENTER = 5;
    public static final byte MIDDLE_RIGHT = 6;
    public static final byte BOTTOM_CENTER = 7;
    public static final byte BOTTOM_RIGHT = 8;

    private final int type;
    private final int sysMessageId;
    private final int unk1;
    private final int unk2;
    private final int unk3;
    private final boolean fade;
    private final int size;
    private final int position;
    private final boolean effect;
    private final String text;
    private final int time;
    private final int npcString;
    private List<String> parameters;

    /**
     * Display a String on the screen for a given time.
     * @param text the text to display
     * @param time the display time
     */
    public ExShowScreenMessage(String text, int time) {
        type = 2;
        sysMessageId = -1;
        unk1 = 0;
        unk2 = 0;
        unk3 = 0;
        fade = false;
        position = TOP_CENTER;
        this.text = text;
        this.time = time;
        size = 0;
        effect = false;
        npcString = -1;
    }

    /**
     * Display a String on the screen for a given time.
     * @param text the text to display
     * @param position the position on the screen
     * @param time the display time
     */
    public ExShowScreenMessage(String text, int position, int time) {
        type = 2;
        sysMessageId = -1;
        unk1 = 0;
        unk2 = 0;
        unk3 = 0;
        fade = false;
        this.position = position;
        this.text = text;
        this.time = time;
        size = 0;
        effect = false;
        npcString = -1;
    }

    /**
     * Display a String on the screen for a given time.
     * @param text the text to display
     * @param position the position on the screen
     * @param time the display time
     * @param size the font size 0 - normal, 1 - small
     * @param fade the fade effect
     * @param showEffect upper effect
     */
    public ExShowScreenMessage(String text, int position, int time, int size, boolean fade, boolean showEffect) {
        type = 1;
        sysMessageId = -1;
        unk1 = 0;
        unk2 = 0;
        unk3 = 0;
        this.fade = fade;
        this.position = position;
        this.text = text;
        this.time = time;
        this.size = size;
        effect = showEffect;
        npcString = -1;
    }

    /**
     * Display a NPC String on the screen for a given position and time.
     * @param npcString the NPC String Id
     * @param position the position on the screen
     * @param time the display time
     * @param params the String parameters
     */
    public ExShowScreenMessage(NpcStringId npcString, int position, int time, String... params) {
        type = 2;
        sysMessageId = -1;
        unk1 = 0x00;
        unk2 = 0x00;
        unk3 = 0x00;
        fade = false;
        this.position = position;
        text = null;
        this.time = time;
        size = 0x00;
        effect = false;
        this.npcString = npcString.getId();
        if (params != null) {
            addStringParameter(params);
        }
    }

    /**
     * Display a System Message on the screen for a given position and time.
     * @param systemMsg the System Message Id
     * @param position the position on the screen
     * @param time the display time
     * @param params the String parameters
     */
    public ExShowScreenMessage(SystemMessageId systemMsg, int position, int time, String... params) {
        type = 2;
        sysMessageId = systemMsg.getId();
        unk1 = 0x00;
        unk2 = 0x00;
        unk3 = 0x00;
        fade = false;
        this.position = position;
        text = null;
        this.time = time;
        size = 0x00;
        effect = false;
        npcString = -1;
        if (params != null) {
            addStringParameter(params);
        }
    }

    /**
     * Display a Text, System Message or a NPC String on the screen for the given parameters.
     * @param type 0 - System Message, 1 - Text, 2 - NPC String
     * @param messageId the System Message Id
     * @param position the position on the screen
     * @param unk1
     * @param size the font size 0 - normal, 1 - small
     * @param unk2
     * @param unk3
     * @param showEffect upper effect (0 - disabled, 1 enabled) - position must be 2 (center) otherwise no effect
     * @param time the display time
     * @param fade the fade effect (0 - disabled, 1 enabled)
     * @param text the text to display
     * @param npcString
     * @param params the String parameters
     */
    public ExShowScreenMessage(
            int type,
            int messageId,
            int position,
            int unk1,
            int size,
            int unk2,
            int unk3,
            boolean showEffect,
            int time,
            boolean fade,
            String text,
            NpcStringId npcString,
            String params) {
        this.type = type;
        sysMessageId = messageId;
        this.unk1 = unk1;
        this.unk2 = unk2;
        this.unk3 = unk3;
        this.fade = fade;
        this.position = position;
        this.text = text;
        this.time = time;
        this.size = size;
        effect = showEffect;
        this.npcString = npcString == null ? -1 : npcString.getId();
    }

    /**
     * String parameter for argument S1,S2,.. in npcstring-e.dat
     * @param params the parameter
     */
    public void addStringParameter(String... params) {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }

        for (String param : params) {
            parameters.add(param);
        }
    }

    @Override
    protected boolean receiverDependent() {
        return MultilingualSupportConfig.MULTILANG_ENABLE;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_SCREEN_MESSAGE.writeId(this, buffer);

        // Localisation related.
        if (MultilingualSupportConfig.MULTILANG_ENABLE) {
            final Player player = client.getPlayer();
            if (player != null) {
                final String lang = player.getLang();
                if ((lang != null) && !lang.equals("en")) {
                    if (sysMessageId > -1) {
                        final SystemMessageId sm = SystemMessageId.getSystemMessageId(sysMessageId);
                        if (sm != null) {
                            final SMLocalisation sml = sm.getLocalisation(lang);
                            if (sml != null) {
                                buffer.writeInt(type);
                                buffer.writeInt(-1);
                                buffer.writeInt(position);
                                buffer.writeInt(unk1);
                                buffer.writeInt(size);
                                buffer.writeInt(unk2);
                                buffer.writeInt(unk3);
                                buffer.writeInt(effect);
                                buffer.writeInt(time);
                                buffer.writeInt(fade);
                                buffer.writeInt(-1);
                                buffer.writeString(
                                        sml.getLocalisation(parameters != null ? parameters : Collections.emptyList()));
                                return;
                            }
                        }
                    } else if (npcString > -1) {
                        final NpcStringId ns = NpcStringId.getNpcStringId(npcString);
                        if (ns != null) {
                            final NSLocalisation nsl = ns.getLocalisation(lang);
                            if (nsl != null) {
                                buffer.writeInt(type);
                                buffer.writeInt(-1);
                                buffer.writeInt(position);
                                buffer.writeInt(unk1);
                                buffer.writeInt(size);
                                buffer.writeInt(unk2);
                                buffer.writeInt(unk3);
                                buffer.writeInt(effect);
                                buffer.writeInt(time);
                                buffer.writeInt(fade);
                                buffer.writeInt(-1);
                                buffer.writeString(
                                        nsl.getLocalisation(parameters != null ? parameters : Collections.emptyList()));
                                return;
                            }
                        }
                    }
                }
            }
        }

        buffer.writeInt(type);
        buffer.writeInt(sysMessageId);
        buffer.writeInt(position);
        buffer.writeInt(unk1);
        buffer.writeInt(size);
        buffer.writeInt(unk2);
        buffer.writeInt(unk3);
        buffer.writeInt(effect);
        buffer.writeInt(time);
        buffer.writeInt(fade);
        buffer.writeInt(npcString);
        if (npcString == -1) {
            buffer.writeString(text);
        } else if (parameters != null) {
            for (String s : parameters) {
                buffer.writeString(s);
            }
        }
    }
}
