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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.SystemMessageId.SMLocalisation;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.Arrays;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Forsaiken
 */
@Slf4j
public class SystemMessage extends ServerPacket {
    private static final SMParam[] EMPTY_PARAM_ARRAY = new SMParam[0];

    public class SMParam {
        @Getter
        private final byte type;

        @Getter
        private final Object value;

        public SMParam(byte type, Object value) {
            this.type = type;
            this.value = value;
        }

        public String getStringValue() {
            return (String) value;
        }

        public int getIntValue() {
            return ((Integer) value).intValue();
        }

        public long getLongValue() {
            return ((Long) value).longValue();
        }

        public int[] getIntArrayValue() {
            return (int[]) value;
        }
    }

    // TODO: UnAfraid: Check/Implement id's: 14,15.
    // 15 exists in goddess of destruction but also may works in h5 needs to be verified!
    // private static final byte TYPE_CLASS_ID = 15;
    // id 14 unknown
    public static final byte TYPE_SYSTEM_STRING = 13;
    public static final byte TYPE_PLAYER_NAME = 12;
    public static final byte TYPE_DOOR_NAME = 11;
    public static final byte TYPE_INSTANCE_NAME = 10;
    public static final byte TYPE_ELEMENT_NAME = 9;

    // id 8 - same as 3
    public static final byte TYPE_ZONE_NAME = 7;
    public static final byte TYPE_LONG_NUMBER = 6;
    public static final byte TYPE_CASTLE_NAME = 5;
    public static final byte TYPE_SKILL_NAME = 4;
    public static final byte TYPE_ITEM_NAME = 3;
    public static final byte TYPE_NPC_NAME = 2;
    public static final byte TYPE_INT_NUMBER = 1;
    public static final byte TYPE_TEXT = 0;

    @Getter
    private SMParam[] params;

    @Getter
    private final SystemMessageId systemMessageId;

    private int paramIndex;

    public SystemMessage(int id) {
        systemMessageId = SystemMessageId.getSystemMessageId(id);
        params = systemMessageId.getParamCount() > 0 ? new SMParam[systemMessageId.getParamCount()] : EMPTY_PARAM_ARRAY;
    }

    public SystemMessage(SystemMessageId smId) {
        if (smId == null) {
            throw new NullPointerException("SystemMessageId cannot be null!");
        }

        systemMessageId = smId;
        params = smId.getParamCount() > 0 ? new SMParam[smId.getParamCount()] : EMPTY_PARAM_ARRAY;
    }

    public SystemMessage(String text) {
        if (text == null) {
            throw new NullPointerException();
        }

        systemMessageId = SystemMessageId.getSystemMessageId(SystemMessageId.S1_2.getId());
        params = new SMParam[1];
        addString(text);
    }

    public int getId() {
        return systemMessageId.getId();
    }

    private void append(SMParam param) {
        if (paramIndex >= params.length) {
            params = Arrays.copyOf(params, paramIndex + 1);
            systemMessageId.setParamCount(paramIndex + 1);
            log.info("Wrong parameter count '{}' for SystemMessageId: {}", (paramIndex + 1), systemMessageId);
        }

        params[paramIndex++] = param;
    }

    public SystemMessage addString(String text) {
        append(new SMParam(TYPE_TEXT, text));
        return this;
    }

    /**
     * Appends a Castle name parameter type, the name will be read from CastleName-e.dat.<br>
     * <ul>
     * <li>1-9 Castle names</li>
     * <li>21 Fortress of Resistance</li>
     * <li>22-33 Clan Hall names</li>
     * <li>34 Devastated Castle</li>
     * <li>35 Bandit Stronghold</li>
     * <li>36-61 Clan Hall names</li>
     * <li>62 Rainbow Springs</li>
     * <li>63 Wild Beast Reserve</li>
     * <li>64 Fortress of the Dead</li>
     * <li>81-89 Territory names</li>
     * <li>90-100 null</li>
     * <li>101-121 Fortress names</li>
     * </ul>
     * @param number the conquerable entity
     * @return the system message with the proper parameter
     */
    public SystemMessage addCastleId(int number) {
        append(new SMParam(TYPE_CASTLE_NAME, number));
        return this;
    }

    public SystemMessage addInt(int number) {
        append(new SMParam(TYPE_INT_NUMBER, number));
        return this;
    }

    public SystemMessage addLong(long number) {
        append(new SMParam(TYPE_LONG_NUMBER, number));
        return this;
    }

    public SystemMessage addPcName(Player pc) {
        append(new SMParam(TYPE_PLAYER_NAME, pc.getAppearance().getVisibleName()));
        return this;
    }

    /**
     * ID from doorData.xml
     * @param doorId
     * @return
     */
    public SystemMessage addDoorName(int doorId) {
        append(new SMParam(TYPE_DOOR_NAME, doorId));
        return this;
    }

    public SystemMessage addNpcName(Npc npc) {
        return addNpcName(npc.getTemplate());
    }

    public SystemMessage addNpcName(Summon npc) {
        return addNpcName(npc.getId());
    }

    public SystemMessage addNpcName(NpcTemplate template) {
        if (template.isUsingServerSideName()) {
            return addString(template.getName());
        }

        return addNpcName(template.getId());
    }

    public SystemMessage addNpcName(int id) {
        append(new SMParam(TYPE_NPC_NAME, 1000000 + id));
        return this;
    }

    public SystemMessage addItemName(Item item) {
        return addItemName(item.getId());
    }

    public SystemMessage addItemName(ItemTemplate item) {
        return addItemName(item.getId());
    }

    public SystemMessage addItemName(int id) {
        final ItemTemplate item = ItemData.getInstance().getTemplate(id);
        if (item.getDisplayId() != id) {
            return addString(item.getName());
        }

        append(new SMParam(TYPE_ITEM_NAME, id));
        return this;
    }

    public SystemMessage addZoneName(int x, int y, int z) {
        append(new SMParam(TYPE_ZONE_NAME, new int[] {x, y, z}));
        return this;
    }

    public SystemMessage addSkillName(Skill skill) {
        if (skill.getId() != skill.getDisplayId()) {
            return addString(skill.getName());
        }

        return addSkillName(skill.getId(), skill.getLevel());
    }

    public SystemMessage addSkillName(int id) {
        return addSkillName(id, 1);
    }

    public SystemMessage addSkillName(int id, int lvl) {
        append(new SMParam(TYPE_SKILL_NAME, new int[] {id, lvl}));
        return this;
    }

    /**
     * Elemental name - 0(Fire) ...
     * @param type
     * @return
     */
    public SystemMessage addElemental(int type) {
        append(new SMParam(TYPE_ELEMENT_NAME, type));
        return this;
    }

    /**
     * ID from sysstring-e.dat
     * @param type
     * @return
     */
    public SystemMessage addSystemString(int type) {
        append(new SMParam(TYPE_SYSTEM_STRING, type));
        return this;
    }

    /**
     * Instance name from instantzonedata-e.dat
     * @param type id of instance
     * @return
     */
    public SystemMessage addInstanceName(int type) {
        append(new SMParam(TYPE_INSTANCE_NAME, type));
        return this;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.SYSTEM_MESSAGE.writeId(this, buffer);

        // Localisation related.
        if (MultilingualSupportConfig.MULTILANG_ENABLE) {
            final Player player = client.getPlayer();
            if (player != null) {
                final String lang = player.getLang();
                if ((lang != null) && !lang.equals("en")) {
                    final SMLocalisation sml = systemMessageId.getLocalisation(lang);
                    if (sml != null) {
                        final Object[] params = new Object[paramIndex];
                        for (int i = 0; i < paramIndex; i++) {
                            params[i] = this.params[i].getValue();
                        }

                        buffer.writeInt(SystemMessageId.S1_2.getId());
                        buffer.writeInt(1);
                        buffer.writeInt(TYPE_TEXT);
                        buffer.writeString(sml.getLocalisation(params));
                        return;
                    }
                }
            }
        }

        buffer.writeInt(systemMessageId.getId());
        buffer.writeInt(this.params.length);
        for (SMParam param : this.params) {
            if (param == null) {
                log.warn("Found null parameter for SystemMessageId {}", systemMessageId);
                continue;
            }

            buffer.writeInt(param.getType());
            switch (param.getType()) {
                case TYPE_TEXT:
                case TYPE_PLAYER_NAME: {
                    buffer.writeString(param.getStringValue());
                    break;
                }
                case TYPE_LONG_NUMBER: {
                    buffer.writeLong(param.getLongValue());
                    break;
                }
                case TYPE_ITEM_NAME:
                case TYPE_CASTLE_NAME:
                case TYPE_INT_NUMBER:
                case TYPE_NPC_NAME:
                case TYPE_ELEMENT_NAME:
                case TYPE_SYSTEM_STRING:
                case TYPE_INSTANCE_NAME:
                case TYPE_DOOR_NAME: {
                    buffer.writeInt(param.getIntValue());
                    break;
                }
                case TYPE_SKILL_NAME: {
                    final int[] array = param.getIntArrayValue();
                    buffer.writeInt(array[0]); // SkillId
                    buffer.writeInt(array[1]); // SkillLevel
                    break;
                }
                case TYPE_ZONE_NAME: {
                    final int[] array = param.getIntArrayValue();
                    buffer.writeInt(array[0]); // x
                    buffer.writeInt(array[1]); // y
                    buffer.writeInt(array[2]); // z
                    break;
                }
            }
        }
    }
}
